package com.cdwater.ticket.event.service;

import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.event.vo.EventMetaVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBitSet;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 活动详情缓存：防穿透用空标记，防击穿用互斥锁，防雪崩用 TTL 抖动。
 * 查库动作由调用方以 loader 传入，缓存层不感知数据来源。
 */
@Component
@RequiredArgsConstructor
public class EventCacheManager {

    private static final Duration CACHE_TTL = Duration.ofSeconds(300);
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    public EventMetaVO getEvent(long eventId, Function<Long, EventMetaVO> loader) {
        String key = RedisKey.eventCache(eventId);
        EventMetaVO cached = read(key);
        if (cached != null) {
            return cached;
        }
        // 空标记说明活动确实不存在，直接拒，不再打 DB
        if (Boolean.TRUE.equals(redis.hasKey(RedisKey.eventCacheNull(eventId)))) {
            throw notFound();
        }

        RLock lock = redisson.getLock(RedisKey.eventCacheLock(eventId));
        if (!tryLock(lock)) {
            // 没抢到锁的请求睡一下重读缓存，通常此时别人已回填；仍无则放行查库
            sleep();
            EventMetaVO refilled = read(key);
            if (refilled != null) {
                return refilled;
            }
            return load(eventId, key, loader);
        }
        try {
            // 双重检查：等锁期间可能已被回填
            EventMetaVO refilled = read(key);
            return refilled != null ? refilled : load(eventId, key, loader);
        } finally {
            lock.unlock();
        }
    }

    /** 座位图从未播种过返回 false，由调用方从 DB 播种 */
    public boolean hasSoldBitmap(long eventId) {
        return redisson.getBitSet(RedisKey.seatSold(eventId)).isExists();
    }

    /** 已售座位解码自 Bitmap，bit 位 = seatId - 1 */
    public Set<Long> decodeSold(long eventId) {
        byte[] bytes = redisson.getBitSet(RedisKey.seatSold(eventId)).toByteArray();
        java.util.BitSet bits = java.util.BitSet.valueOf(bytes);
        Set<Long> ids = new HashSet<>();
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            ids.add(i + 1L);
        }
        return ids;
    }

    public void seedSoldSeats(long eventId, Set<Long> soldSeatIds) {
        RBitSet bitSet = redisson.getBitSet(RedisKey.seatSold(eventId));
        for (Long seatId : soldSeatIds) {
            bitSet.set(seatId - 1);
        }
    }

    public void markSeatSold(long eventId, long seatId) {
        redisson.getBitSet(RedisKey.seatSold(eventId)).set(seatId - 1);
    }

    /** 座位租约有 TTL，故绝不缓存 —— 否则 TTL 边界会展示错误的可选座位 */
    public boolean isSeatLocked(long eventId, long seatId) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKey.seatLock(eventId, seatId)));
    }

    private EventMetaVO load(long eventId, String key, Function<Long, EventMetaVO> loader) {
        EventMetaVO vo = loader.apply(eventId);
        if (vo == null) {
            redis.opsForValue().set(RedisKey.eventCacheNull(eventId), RedisKey.NULL_MARKER, NULL_TTL);
            throw notFound();
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(vo), jitter());
        } catch (Exception ignored) {
            // 写缓存失败不应影响读，直接返回结果
        }
        return vo;
    }

    private EventMetaVO read(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, EventMetaVO.class);
        } catch (Exception ignored) {
            // 缓存内容损坏，按未命中处理
            return null;
        }
    }

    private static BizException notFound() {
        return new BizException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
    }

    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(0, LOCK_LEASE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 雪崩防护：TTL 叠加随机抖动，避免同一批 key 同时过期 */
    private static Duration jitter() {
        return CACHE_TTL.plusSeconds(ThreadLocalRandom.current().nextInt(120));
    }
}
