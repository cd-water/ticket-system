package com.cdwater.ticket.common.constant;

/** Redis key 集中命名，避免各处硬编码拼错 */
public final class RedisKey {

    /** 活动不存在时的空标记哨兵值 */
    public static final String NULL_MARKER = "NULL";

    private RedisKey() {
    }

    public static String stock(long eventId) {
        return "ticket:stock:" + eventId;
    }

    public static String ordered(long eventId) {
        return "ticket:ordered:" + eventId;
    }

    public static String seatLock(long eventId, long seatId) {
        return "seat:lock:" + eventId + ":" + seatId;
    }

    public static String seatSold(long eventId) {
        return "seat:sold:" + eventId;
    }

    public static String eventCache(long eventId) {
        return "cache:event:" + eventId;
    }

    public static String eventCacheNull(long eventId) {
        return "cache:event:null:" + eventId;
    }

    public static String eventCacheLock(long eventId) {
        return "cache:lock:event:" + eventId;
    }

    public static String refreshToken(String uuid) {
        return "auth:refresh:" + uuid;
    }

    public static String userTokens(long userId) {
        return "auth:user-tokens:" + userId;
    }

    public static String smsCode(String phone) {
        return "auth:sms:" + phone;
    }

    public static String orderRate(long userId) {
        return "rate:order:" + userId;
    }
}
