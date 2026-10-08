-- 抢票预扣：一人一单校验与库存扣减必须落在同一个脚本里原子完成
-- 返回 1 成功 / -1 售罄 / -2 重复下单
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end
local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock or stock <= 0 then
    return -1
end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
