package com.cdwater.ticket.booking.booking;

import com.cdwater.ticket.booking.lua.FlashStockLua;
import com.cdwater.ticket.common.constant.EventMode;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.event.service.EventService;
import com.cdwater.ticket.event.vo.EventMetaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlashBookingStrategy implements BookingStrategy {

    private final FlashStockLua lua;
    private final EventService eventService;

    @Override
    public int mode() {
        return EventMode.TICKET;
    }

    @Override
    public Long acquire(long userId, EventMetaVO event, Long seatId, long orderNo) {
        // 跑 Lua 前先保证库存已播种：内部是 SETNX，多实例并发下只有一个赢家，值取 DB 当前库存
        eventService.currentStock(event.getId());

        String stockKey = RedisKey.stock(event.getId());
        String orderedKey = RedisKey.ordered(event.getId());
        long result = lua.tryDecr(stockKey, orderedKey, userId);
        if (result == FlashStockLua.DUPLICATED) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "您已下过该活动的订单");
        }
        if (result != FlashStockLua.OK) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "票已售罄");
        }
        // 令牌就是 userId，DB 阶段失败时用它定位 ordered 集合里要归还的成员
        return userId;
    }

    @Override
    public void compensate(EventMetaVO event, Long token) {
        lua.release(RedisKey.stock(event.getId()), RedisKey.ordered(event.getId()), token);
    }

    @Override
    public void release(EventMetaVO event, Order order) {
        lua.release(RedisKey.stock(event.getId()), RedisKey.ordered(event.getId()), order.getUserId());
    }

    @Override
    public void confirm(EventMetaVO event, Order order) {
        // 抢票没有座位可固化，库存已在 acquire 阶段扣减
    }
}
