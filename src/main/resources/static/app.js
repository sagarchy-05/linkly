/* ============================================================
   curtli — landing page logic
   Vanilla JS, no build step. Talks to /api/shorten and
   /api/bulk-shorten. Persists "Your links" in localStorage.
   ============================================================ */

(() => {
  'use strict';

  // ----- Constants -----------------------------------------------
  const MAX_ROWS      = 20;
  const HISTORY_KEY   = 'curtli.history.v1';
  const HISTORY_LIMIT = 200;
  const TOAST_MS      = 1800;

  // ----- DOM -----------------------------------------------------
  const $form          = document.getElementById('shorten-form');
  const $rows          = document.getElementById('rows');
  const $addRow        = document.getElementById('add-row');
  const $rowCounter    = document.getElementById('row-counter');
  const $submitBtn     = document.getElementById('submit-btn');
  const $banner        = document.getElementById('banner');
  const $resultsSection= document.getElementById('results-section');
  const $results       = document.getElementById('results');
  const $history       = document.getElementById('history');
  const $historyEmpty  = document.getElementById('history-empty');
  const $clearHistory  = document.getElementById('clear-history');
  const $toast         = document.getElementById('toast');

  const tplRow      = document.getElementById('tpl-row');
  const tplSuccess  = document.getElementById('tpl-result-success');
  const tplFailure  = document.getElementById('tpl-result-failure');
  const tplHistory  = document.getElementById('tpl-history-item');

  // ----- Init ----------------------------------------------------
  addRow();                  // start with one blank row
  renderHistory();

  $form.addEventListener('submit', onSubmit);
  $addRow.addEventListener('click', () => addRow());
  $clearHistory.addEventListener('click', clearHistory);

  // ----- Row management -----------------------------------------

  function addRow() {
    const count = $rows.children.length;
    if (count >= MAX_ROWS) return;

    const node = tplRow.content.firstElementChild.cloneNode(true);
    $rows.appendChild(node);

    const $remove = node.querySelector('.row-remove');
    $remove.addEventListener('click', () => removeRow(node));

    // Focus the URL input of the just-added row (skip the first auto-add)
    if (count > 0) {
      node.querySelector('.input-url').focus();
    }

    updateRowChrome();
  }

  function removeRow(node) {
    if ($rows.children.length <= 1) return;
    node.classList.add('removing');
    node.addEventListener('animationend', () => {
      node.remove();
      updateRowChrome();
    }, { once: true });
  }

  function updateRowChrome() {
    const rows = $rows.querySelectorAll('.row');
    const count = rows.length;

    rows.forEach(r => {
      const $remove = r.querySelector('.row-remove');
      $remove.disabled = count <= 1;
    });

    $rowCounter.textContent = `${count} / ${MAX_ROWS}`;
    $addRow.disabled = count >= MAX_ROWS;
  }

  // ----- Validation ---------------------------------------------

  function readRow(rowEl) {
    const longUrl     = rowEl.querySelector('.input-url').value.trim();
    const customAlias = rowEl.querySelector('.input-alias').value.trim();
    return { rowEl, longUrl, customAlias: customAlias || null };
  }

  function validateRow(row) {
    clearRowError(row.rowEl);
    if (!row.longUrl) {
      return 'Please enter a URL.';
    }
    if (!/^https?:\/\//i.test(row.longUrl)) {
      return 'URL must start with http:// or https://';
    }
    if (row.longUrl.length > 2048) {
      return 'URL is too long (max 2048 chars).';
    }
    if (row.customAlias && !/^[a-zA-Z0-9_-]{3,16}$/.test(row.customAlias)) {
      return 'Alias must be 3-16 chars (letters, digits, _ or -).';
    }
    return null;
  }

  function setRowError(rowEl, msg) {
    const $err = rowEl.querySelector('[data-error]');
    $err.textContent = msg;
    $err.hidden = false;
    rowEl.querySelector('.input-url').classList.add('invalid');
  }

  function clearRowError(rowEl) {
    const $err = rowEl.querySelector('[data-error]');
    $err.textContent = '';
    $err.hidden = true;
    rowEl.querySelector('.input-url').classList.remove('invalid');
    rowEl.querySelector('.input-alias').classList.remove('invalid');
  }

  // ----- Submission ---------------------------------------------

  async function onSubmit(event) {
    event.preventDefault();
    hideBanner();

    const rows = Array.from($rows.querySelectorAll('.row')).map(readRow);
    const nonEmpty = rows.filter(r => r.longUrl || r.customAlias);

    if (nonEmpty.length === 0) {
      showBanner('Add at least one URL before shortening.');
      return;
    }

    // Validate client-side first
    let hasError = false;
    for (const row of nonEmpty) {
      const err = validateRow(row);
      if (err) {
        setRowError(row.rowEl, err);
        hasError = true;
      }
    }
    if (hasError) return;

    setLoading(true);

    try {
      const payload = nonEmpty.map(r => ({
        longUrl: r.longUrl,
        customAlias: r.customAlias,
        expiresInDays: null,
      }));

      const { successful, failed } = payload.length === 1
        ? await shortenSingle(payload[0])
        : await shortenBulk(payload);

      renderResults(successful, failed, nonEmpty);

      // Persist successes to history
      successful.forEach(s => addToHistory({
        shortCode: s.shortCode,
        shortUrl:  s.shortUrl,
        longUrl:   s.longUrl,
        createdAt: Date.now(),
      }));
      renderHistory();

      // Reset form: remove all rows but the first, clear inputs
      Array.from($rows.querySelectorAll('.row')).forEach((r, i) => {
        if (i === 0) {
          r.querySelector('.input-url').value = '';
          r.querySelector('.input-alias').value = '';
          clearRowError(r);
        } else {
          r.remove();
        }
      });
      updateRowChrome();

      // Smooth-scroll to results
      requestAnimationFrame(() => {
        $resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });

    } catch (err) {
      handleSubmitError(err, nonEmpty);
    } finally {
      setLoading(false);
    }
  }

  // ----- API calls ----------------------------------------------

  async function shortenSingle(body) {
    const res = await fetch('/api/shorten', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw await buildHttpError(res);
    const data = await res.json();
    return { successful: [data], failed: [] };
  }

  async function shortenBulk(bodyArray) {
    const res = await fetch('/api/bulk-shorten', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(bodyArray),
    });
    // bulk returns 200 (partial/full success) or 400 (all failed) — both with JSON body
    if (res.status === 200 || res.status === 400) {
      return await res.json();
    }
    throw await buildHttpError(res);
  }

  async function buildHttpError(res) {
    let body = null;
    try { body = await res.json(); } catch (_) {}
    const err = new Error(body?.message || body?.error || `HTTP ${res.status}`);
    err.status = res.status;
    err.retryAfter = parseInt(res.headers.get('Retry-After') || '0', 10);
    err.body = body;
    return err;
  }

  function handleSubmitError(err, rows) {
    if (!err.status) {
      // Network-level failure
      showBanner('Could not reach the server. Check your connection and try again.');
      return;
    }
    if (err.status === 429) {
      const secs = err.retryAfter || 60;
      showBanner(`You're going a bit fast. Try again in ${secs} second${secs === 1 ? '' : 's'}.`);
      return;
    }
    if (err.status === 400 && rows.length === 1) {
      setRowError(rows[0].rowEl, err.message || 'Invalid request.');
      return;
    }
    showBanner(err.message || `Unexpected error (${err.status}).`);
  }

  // ----- Rendering ----------------------------------------------

  function renderResults(successful, failed, originalRows) {
    $results.innerHTML = '';

    const all = [
      ...successful.map(s => ({ kind: 'success', data: s })),
      ...failed.map(f => ({ kind: 'failure', data: f })),
    ];

    if (all.length === 0) {
      $resultsSection.classList.add('hidden');
      return;
    }

    all.forEach((item, i) => {
      const node = item.kind === 'success'
        ? renderSuccess(item.data)
        : renderFailure(item.data);
      node.style.animationDelay = `${i * 50}ms`;
      $results.appendChild(node);
    });

    $resultsSection.classList.remove('hidden');
  }

  function renderSuccess(data) {
    const node = tplSuccess.content.firstElementChild.cloneNode(true);
    const $short = node.querySelector('[data-short]');
    const $long  = node.querySelector('[data-long]');
    const $copy  = node.querySelector('[data-copy]');

    $short.textContent = stripProtocol(data.shortUrl);
    $short.href = data.shortUrl;
    $long.textContent = data.longUrl;
    $copy.addEventListener('click', () => copyToClipboard(data.shortUrl, $copy));

    return node;
  }

  function renderFailure(data) {
    const node = tplFailure.content.firstElementChild.cloneNode(true);
    node.querySelector('[data-long]').textContent  = data.longUrl || '(no URL)';
    node.querySelector('[data-error]').textContent = data.errorMessage || 'Unknown error';
    return node;
  }

  // ----- History (localStorage) ---------------------------------

  function loadHistory() {
    try {
      const raw = localStorage.getItem(HISTORY_KEY);
      const list = raw ? JSON.parse(raw) : [];
      return Array.isArray(list) ? list : [];
    } catch (_) {
      return [];
    }
  }

  function saveHistory(list) {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(list));
    } catch (_) {
      // Storage full / disabled — silently skip
    }
  }

  function addToHistory(item) {
    const list = loadHistory();
    // Dedupe by shortCode — newest wins
    const filtered = list.filter(x => x.shortCode !== item.shortCode);
    filtered.unshift(item);
    saveHistory(filtered.slice(0, HISTORY_LIMIT));
  }

  function removeFromHistory(shortCode) {
    const list = loadHistory().filter(x => x.shortCode !== shortCode);
    saveHistory(list);
  }

  function clearHistory() {
    if (!confirm('Clear all link history? This cannot be undone.')) return;
    saveHistory([]);
    renderHistory();
    showToast('History cleared');
  }

  function renderHistory() {
    const list = loadHistory();
    $history.innerHTML = '';

    if (list.length === 0) {
      $historyEmpty.classList.remove('hidden');
      $clearHistory.classList.add('hidden');
      return;
    }

    $historyEmpty.classList.add('hidden');
    $clearHistory.classList.remove('hidden');

    list.forEach((item, i) => {
      const node = tplHistory.content.firstElementChild.cloneNode(true);
      const $short = node.querySelector('[data-short]');
      const $long  = node.querySelector('[data-long]');
      const $meta  = node.querySelector('[data-meta]');
      const $copy  = node.querySelector('[data-copy]');
      const $remove= node.querySelector('[data-remove]');

      $short.textContent = stripProtocol(item.shortUrl);
      $short.href = item.shortUrl;
      $long.textContent = item.longUrl;
      $meta.textContent = formatRelative(item.createdAt);

      $copy.addEventListener('click', () => copyToClipboard(item.shortUrl, $copy));
      $remove.addEventListener('click', () => {
        node.classList.add('removing');
        node.addEventListener('animationend', () => {
          removeFromHistory(item.shortCode);
          renderHistory();
        }, { once: true });
      });

      node.style.animationDelay = `${Math.min(i, 10) * 35}ms`;
      $history.appendChild(node);
    });
  }

  // ----- UI helpers ---------------------------------------------

  function setLoading(loading) {
    if (loading) {
      $submitBtn.classList.add('loading');
      $submitBtn.disabled = true;
      $submitBtn.querySelector('.btn-label').textContent = 'Shortening…';
    } else {
      $submitBtn.classList.remove('loading');
      $submitBtn.disabled = false;
      $submitBtn.querySelector('.btn-label').textContent = 'Shorten';
    }
  }

  function showBanner(msg) {
    $banner.textContent = msg;
    $banner.classList.remove('hidden');
  }

  function hideBanner() {
    $banner.classList.add('hidden');
  }

  let toastTimer;
  function showToast(msg) {
    $toast.textContent = msg;
    $toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => $toast.classList.remove('show'), TOAST_MS);
  }

  async function copyToClipboard(text, btnEl) {
    try {
      await navigator.clipboard.writeText(text);
      btnEl.classList.add('copied');
      // Swap the icon path to a check briefly
      const $svg = btnEl.querySelector('svg');
      const originalHTML = $svg.innerHTML;
      $svg.innerHTML = '<path d="M20 6L9 17l-5-5" />';
      showToast('Copied');
      setTimeout(() => {
        $svg.innerHTML = originalHTML;
        btnEl.classList.remove('copied');
      }, 1200);
    } catch (_) {
      // Fallback for non-secure contexts (some older browsers)
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); showToast('Copied'); } catch (_) {}
      ta.remove();
    }
  }

  function stripProtocol(url) {
    return url.replace(/^https?:\/\//i, '');
  }

  function formatRelative(ts) {
    const diff = Date.now() - ts;
    const sec = Math.floor(diff / 1000);
    if (sec < 60)  return 'just now';
    const min = Math.floor(sec / 60);
    if (min < 60)  return `${min} min ago`;
    const hr = Math.floor(min / 60);
    if (hr < 24)   return `${hr} hour${hr === 1 ? '' : 's'} ago`;
    const day = Math.floor(hr / 24);
    if (day < 30)  return `${day} day${day === 1 ? '' : 's'} ago`;
    const d = new Date(ts);
    return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  }

})();
