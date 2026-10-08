package com.cdwater.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.dto.CreateOrderRequest;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.service.OrderService;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.event.mapper.EventSeatMapper;
import com.cdwater.ticket.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SeatConcurrencyTest {

    private static final long EVENT_ID = 2;
    private static final long SEAT_ID = 25;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private EventSeatMapper eventSeatMapper;

    @Autowired
    private TestSupport testSupport;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        testSupport.resetBookingState();
        testSupport.deleteUsersByPrefix("1393000");
    }

    @AfterEach
    void cleanup() {
        testSupport.deleteUsersByPrefix("1393000");
    }

    @Test
    void 五十人抢同一座位只有一人成功() throws Exception {
        List<Long> users = testSupport.createUsers(50, "1393000");

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users.size());
        ExecutorService pool = Executors.newFixedThreadPool(50);
        for (Long userId : users) {
            pool.submit(() -> {
                try {
                    start.await();
                    CreateOrderRequest request = new CreateOrderRequest();
                    request.setEventId(EVENT_ID);
                    request.setSeatId(SEAT_ID);
                    orderService.create(userId, request);
                    success.incrementAndGet();
                } catch (BizException e) {
                    if (ResultCode.CONFLICT.getCode().equals(e.getCode())) {
                        conflict.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(unexpected.get()).isZero();
        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(49);
        // 下单阶段只拿租约，不动 DB 座位状态
        assertThat(eventSeatMapper.selectById(SEAT_ID).getStatus()).isZero();
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getEventId, EVENT_ID).eq(Order::getSeatId, SEAT_ID))).isEqualTo(1);
        assertThat(redis.opsForValue().get(RedisKey.seatLock(EVENT_ID, SEAT_ID))).isNotNull();
    }
}
