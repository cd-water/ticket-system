package com.cdwater.ticket.booking.booking;

import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.event.vo.EventMetaVO;

/** 抢票与选座在下单链上的差异全部收敛到这个接口 */
public interface BookingStrategy {

    /** 该策略适用的购票模式 */
    int mode();

    /**
     * 在 Redis 上抢占资源（预扣库存 / 获取座位租约）。
     * 返回补偿令牌；抢占失败直接抛 BizException，此时无需补偿。
     */
    Long acquire(long userId, EventMetaVO event, Long seatId, long orderNo);

    /** 下单事务失败时用 acquire 的令牌归还。抢票令牌是 userId，选座令牌是 seatId */
    void compensate(EventMetaVO event, Long token);

    /** 取消与超时关单时按订单自身信息归还：抢票取 userId，选座取 seatId */
    void release(EventMetaVO event, Order order);

    /** 支付成功后固化资源 */
    void confirm(EventMetaVO event, Order order);
}
