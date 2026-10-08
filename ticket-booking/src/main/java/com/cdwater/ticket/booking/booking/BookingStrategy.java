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

    /**
     * 关单时在 DB 事务内归还持久化资源（抢票把 t_event_stock 加回去）。
     * 必须与订单状态变更同事务，否则「订单已取消但库存没还」会永久少卖。
     */
    void releasePersistent(EventMetaVO event, Order order);

    /**
     * 关单事务提交后再释放缓存侧资源（Redis 计数 / 座位租约）。
     * 放在提交后是刻意的：Redis 不可回滚，若放在事务内，
     * 提交失败会留下「Redis 已归还、订单仍待支付」的超卖窗口。
     */
    void releaseCached(EventMetaVO event, Order order);

    /** 支付成功后固化资源 */
    void confirm(EventMetaVO event, Order order);
}
