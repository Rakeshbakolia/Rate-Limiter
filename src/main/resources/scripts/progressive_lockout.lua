-- progressive_lockout.lua
-- Evaluation logic for the token bucket rate limiter combined with a lockout state machine.
-- Returns: [is_allowed (0 or 1), remaining_tokens, penalty_duration_seconds]

local bucket_key = KEYS[1]
local strike_key = KEYS[2]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local current_time = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local strike_ttl = tonumber(ARGV[6])

-- 1. Check if user is currently locked out
local lockout_expiry = tonumber(redis.call('HGET', strike_key, 'lockout_expiry'))
if lockout_expiry and lockout_expiry > current_time then
    local remaining_lockout = lockout_expiry - current_time
    -- return 0 for disallowed, 0 remaining tokens, and the remaining lockout duration
    return {0, 0, remaining_lockout}
end

-- 2. Token Bucket Evaluation
local bucket = redis.call('HMGET', bucket_key, 'tokens', 'last_updated')
local tokens = tonumber(bucket[1]) or capacity
local last_updated = tonumber(bucket[2]) or current_time

-- Calculate refilled tokens based on time passed
local time_passed = math.max(0, current_time - last_updated)
local tokens_to_add = math.floor(time_passed * refill_rate)
tokens = math.min(capacity, tokens + tokens_to_add)

if tokens >= requested then
    -- Allow Request: Deduct tokens and update state
    tokens = tokens - requested
    redis.call('HMSET', bucket_key, 'tokens', tokens, 'last_updated', current_time)
    redis.call('EXPIRE', bucket_key, 60)
    return {1, tokens, 0}
else
    -- Deny Request & Apply Strike
    local current_strikes = redis.call('HINCRBY', strike_key, 'count', 1)
    
    -- Parse Lockout Tiers (ARGV[5] is comma-separated)
    local tiers = {}
    for match in (ARGV[5]..","):gmatch("(.-)"..",") do 
        table.insert(tiers, tonumber(match)) 
    end
    
    -- Determine penalty duration based on strike count (capping at max tier index)
    local penalty_index = math.min(current_strikes, #tiers)
    local penalty_duration = tiers[penalty_index]
    
    local new_expiry = current_time + penalty_duration
    redis.call('HMSET', strike_key, 'lockout_expiry', new_expiry)
    redis.call('EXPIRE', strike_key, strike_ttl) 
    
    return {0, tokens, penalty_duration}
end
