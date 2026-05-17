local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillPerSec = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local cost = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1]) or capacity
local lastTs = tonumber(data[2]) or now

local elapsed = math.max(0, now - lastTs)
tokens = math.min(capacity, tokens + elapsed * refillPerSec)

local allowed = 0
if tokens >= cost then
    tokens = tokens - cost
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'ts', now)

-- Calculate exact time required to refill the bucket from 0 to full.
-- We use math.ceil to ensure we don't under-calculate, and add a small 5-second buffer.
local ttl = math.ceil(capacity / refillPerSec) + 5
redis.call('EXPIRE', key, ttl)

return {allowed, math.floor(tokens)}