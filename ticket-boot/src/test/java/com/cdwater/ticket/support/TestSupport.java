package com.cdwater.ticket.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.auth.mapper.UserMapper;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.mapper.OutboxMapper;
import com.cdwater.ticket.booking.mapper.PaymentMapper;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.EventStock;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.entity.Outbox;
import com.cdwater.ticket.common.entity.Payment;
import com.cdwater.ticket.common.entity.User;
import com.cdwater.ticket.event.mapper.EventSeatMapper;
import com.cdwater.ticket.event.mapper.EventStockMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 测试夹具：造用户、清业务数据。生产代码不引用。 */
@Component
@RequiredArgsConstructor
public class TestSupport {

    /** 各抢票活动的种子库存，与 seed.sql 一致 */
    private static final long FLASH_EVENT_1 = 1;
    private static final long FLASH_EVENT_3 = 3;
    private static final long FLASH_EVENT_5 = 5;
    private static final int STOCK_1 = 1000;
    private static final int STOCK_3 = 5000;
    private static final int STOCK_5 = 3000;

    /**
     * 各选座活动的已售座位，与 seed.sql 一致。
     * 值是「活动内座位序号 n」（seed 里 FIND_IN_SET 的那个 n），不是 t_event_seat.id ——
     * id 是全局主键，活动 4/6 的区间并不从 1 开始。
     */
    private static final Map<Long, String> SEED_SOLD_SEAT_INDEXES = Map.of(
            2L, "2,3,11,12,13,22,24,35,36",
            4L, "1,2,13,14,25,37,38,50,63,64,71",
            6L, "3,4,17,18,19,33,49,65,66,67,80");

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OutboxMapper outboxMapper;
    private final EventSeatMapper eventSeatMapper;
    private final EventStockMapper eventStockMapper;
    private final RedissonClient redisson;

    /** 造一批手机号唯一的用户；「一人一单」并发测试要求每个线程是不同用户 */
    public List<Long> createUsers(int count, String phonePrefix) {
        List<Long> ids = new ArrayList<>(count);
        // t_user.phone 是 VARCHAR(20)，只取纳秒后 6 位保证长度不溢出且同批不重复
        long nonce = System.nanoTime() % 1_000_000;
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setPhone(phonePrefix + "-" + i + "-" + nonce);
            userMapper.insert(user);
            ids.add(user.getId());
        }
        return ids;
    }

    /** 按前缀清理历史测试用户，避免多次运行后表膨胀 */
    public void deleteUsersByPrefix(String phonePrefix) {
        userMapper.delete(Wrappers.<User>lambdaQuery().likeRight(User::getPhone, phonePrefix));
    }

    /**
     * 把订单相关状态恢复到 seed 基线。
     * 契约测试共用种子用户，而 t_order 有 (user_id, event_id, status) 唯一键，
     * 上一条用例留下的待支付单会让下一条拿到 C409，因此每条用例前都要重置。
     */
    public void resetBookingState() {
        orderMapper.delete(Wrappers.<Order>lambdaQuery());
        paymentMapper.delete(Wrappers.<Payment>lambdaQuery());
        outboxMapper.delete(Wrappers.<Outbox>lambdaQuery());
        restoreSeats();
        resetStock(FLASH_EVENT_1, STOCK_1);
        resetStock(FLASH_EVENT_3, STOCK_3);
        resetStock(FLASH_EVENT_5, STOCK_5);
        redisson.getKeys().deleteByPattern("seat:*");
        redisson.getKeys().deleteByPattern("ticket:stock:*");
        redisson.getKeys().deleteByPattern("ticket:ordered:*");
        // RRateLimiter 的 key 带 hash tag，实际形如 {rate:order:1}:value，
        // 前缀模式 "rate:order:*" 匹配不到带花括号的 key，必须用两侧通配
        redisson.getKeys().deleteByPattern("*rate:order:*");
        redisson.getKeys().deleteByPattern("mq:sms:sent:*");
    }

    public void resetStock(long eventId, int stock) {
        eventStockMapper.update(null, Wrappers.<EventStock>lambdaUpdate()
                .eq(EventStock::getEventId, eventId).set(EventStock::getStock, stock));
    }

    /**
     * 座位状态回到 seed 基线。
     * 全置 0 会抹掉已售座位，座位图用例就再也验不到已售渲染；
     * 而 seed 的已售清单是活动内序号，必须按 (row_no, col_no) 排序后取第 n 个拿到真实 id。
     */
    private void restoreSeats() {
        for (Map.Entry<Long, String> entry : SEED_SOLD_SEAT_INDEXES.entrySet()) {
            long eventId = entry.getKey();
            eventSeatMapper.update(null, Wrappers.<EventSeat>lambdaUpdate()
                    .eq(EventSeat::getEventId, eventId)
                    .set(EventSeat::getStatus, 0));

            List<EventSeat> ordered = eventSeatMapper.selectList(Wrappers.<EventSeat>lambdaQuery()
                    .eq(EventSeat::getEventId, eventId)
                    .orderByAsc(EventSeat::getRowNo)
                    .orderByAsc(EventSeat::getColNo));
            for (String index : entry.getValue().split(",")) {
                int position = Integer.parseInt(index.trim());
                eventSeatMapper.update(null, Wrappers.<EventSeat>lambdaUpdate()
                        .eq(EventSeat::getId, ordered.get(position - 1).getId())
                        .set(EventSeat::getStatus, 1));
            }
        }
    }
}
