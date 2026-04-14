local key = KEYS[1]
local incomingHash = ARGV[1]
local ttl = tonumber(ARGV[2])

local value = redis.call("GET", key)

-- Case 1: Key does not exist → reserve
if not value then
    local newValue = incomingHash .. ":IN_PROGRESS"
    redis.call("SET", key, newValue, "EX", ttl)
    return "PROCEED"
end

-- Parse existing value
local delimiter = ":"
local i = string.find(value, delimiter)
local existingHash = string.sub(value, 1, i - 1)
local status = string.sub(value, i + 1)

-- Case 2: Same key, different body → error
if existingHash ~= incomingHash then
    return "HASH_MISMATCH"
end

-- Case 3: Same request → return current status
return status