package com.cdwater.ticket.booking.lua;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** 抢票库存的 Lua 封装，返回码含义见 stock.lua 注释 */
@Component
public class FlashStockLua {

    public static final long OK = 1;
    public static final long SOLD_OUT = -1;
    public static final long DUPLICATED = -2;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> tryDecr = load("lua/stock.lua");
    private final DefaultRedisScript<Long> release = load("lua/release.lua");

    public FlashStockLua(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long tryDecr(String stockKey, String orderedKey, long userId) {
        Long result = redis.execute(tryDecr, List.of(stockKey, orderedKey), String.valueOf(userId));
        return result == null ? SOLD_OUT : result;
    }

    public void release(String stockKey, String orderedKey, long userId) {
        redis.execute(release, List.of(stockKey, orderedKey), String.valueOf(userId));
    }

    private static DefaultRedisScript<Long> load(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
