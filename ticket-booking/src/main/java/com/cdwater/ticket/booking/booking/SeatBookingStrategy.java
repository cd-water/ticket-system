package com.cdwater.ticket.booking.booking;

import com.cdwater.ticket.common.constant.EventMode;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.event.service.EventCacheManager;
import com.cdwater.ticket.event.service.EventService;
import com.cdwater.ticket.event.vo.EventMetaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SeatBookingStrategy implements BookingStrategy {

    private final StringRedisTemplate redis;
    private final EventCacheManager cacheManager;
    private final EventService eventService;

    @Override
    public int mode() {
        return EventMode.SEAT;
    }

    @Override
    public Long acquire(long userId, EventMetaVO event, Long seatId, long orderNo) {
        if (seatId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "选座模式必须指定座位");
        }
        // seatId 是 t_event_seat 的全局主键，各活动区间并不从 1 开始，
        // 因此只能按 (eventId, seatId) 查库确认归属，不能用 rowCount*colCount 做区间推断
        EventSeat seat = eventService.findSeat(event.getId(), seatId);
        if (seat == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "座位不存在");
        }
        // 已售座位必须在此时就挡住：租约只挡并发抢占，挡不住「seed 里本来就已售」的座位，
        // 否则用户能下单成功、拿到租约，直到支付才因 DB 的 status=0 条件更新失败
        if (seat.getStatus() != 0) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "座位已被占用");
        }
        // NX EX 原子完成检测-抢占-限时；TTL 与订单有效期相等，租约不会先于订单失效
        Boolean locked = redis.opsForValue().setIfAbsent(
                RedisKey.seatLock(event.getId(), seatId), String.valueOf(orderNo), Duration.ofMinutes(15));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "座位已被占用");
        }
        return seatId;
    }

    @Override
    public void compensate(EventMetaVO event, Long token) {
        redis.delete(RedisKey.seatLock(event.getId(), token));
    }

    @Override
    public void release(EventMetaVO event, Order order) {
        if (order.getSeatId() != null) {
            redis.delete(RedisKey.seatLock(event.getId(), order.getSeatId()));
        }
    }

    @Override
    public void confirm(EventMetaVO event, Order order) {
        if (order.getSeatId() == null) {
            return;
        }
        cacheManager.markSeatSold(event.getId(), order.getSeatId());
        redis.delete(RedisKey.seatLock(event.getId(), order.getSeatId()));
    }
}
