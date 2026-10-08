-- 归还 Redis 预扣：DB 兜底失败时调用，否则两个数据源永久漂移
redis.call('INCR', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[1])
return 1
