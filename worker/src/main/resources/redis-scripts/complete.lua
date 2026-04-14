local value = redis.call("GET", KEYS[1])
local incomingHash = ARGV[1]
local ttl = tonumber(ARGV[2])

if not value then
    return "NOT_FOUND"
end

local delimiter = ":"
local i = string.find(value, delimiter)
local existingHash = string.sub(value, 1, i - 1)
local status = string.sub(value, i + 1)

-- Same key, different body → error
if existingHash ~= incomingHash then
    return "HASH_MISMATCH"
end

if status == "IN_PROGRESS" then
    local newValue = incomingHash .. ":COMPLETED"
    redis.call("SET", KEYS[1], newValue, "EX", ttl)
    return "COMPLETED"
end

if status == "COMPLETED" then
    return status
end

return "INVALID_STATE"