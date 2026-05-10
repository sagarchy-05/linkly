package com.sagar.linkly.filter;

import com.sagar.linkly.service.RateLimiterService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {
    private final RateLimiterService limiter;
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        if ("POST".equals(http.getMethod()) && (http.getRequestURI().startsWith("/api/shorten") || http.getRequestURI().startsWith("/api/bulk-shorten"))) {
            String ip = http.getRemoteAddr();
            if (!limiter.tryAcquire(ip)) {
                HttpServletResponse out = (HttpServletResponse) res;
                out.setStatus(429);
                out.setHeader("Retry-After", "60");
                out.getWriter().write("{\"error\":\"rate_limited\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}