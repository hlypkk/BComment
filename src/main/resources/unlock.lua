--比较线程表示是否与锁中的表示一致
if(redis.call('get',KEYS[1]) == ARGV[1]) then
    --释放锁 del key
    return redis.call('del',KEYS[1])
end
return 0