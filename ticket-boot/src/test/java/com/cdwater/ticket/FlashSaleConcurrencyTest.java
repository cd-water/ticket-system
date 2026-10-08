package com.cdwater.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.dto.CreateOrderRequest;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.service.OrderService;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.EventStock;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.event.mapper.EventStockMapper;
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

/** 直接打 service 层：要证明的是并发正确性，HTTP 层由 OrderApiContractTest 覆盖 */
@SpringBootTest
@ActiveProfiles("test")
class FlashSaleConcurrencyTest {

    private static final long EVENT_ID = 5;
    private static final int STOCK = 100;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private TestSupport testSupport;

    @Autowired
    private StringRedisTemplate redis;

    private List<Long> users;

    @BeforeEach
    void reset() {
        testSupport.resetBookingState();
        // 夹具把库存还原成 seed 值（3000），本用例要的是 100 张
        testSupport.resetStock(EVENT_ID, STOCK);
        testSupport.deleteUsersByPrefix("1391000");
        users = testSupport.createUsers(200, "1391000");
    }

    @AfterEach
    void cleanup() {
        testSupport.deleteUsersByPrefix("1391000");
    }

    @Test
    void 两百人抢一百张票只成功一百单且库存归零() throws Exception {
        assertThat(users).hasSize(200);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users.size());
        ExecutorService pool = Executors.newFixedThreadPool(64);
        for (Long userId : users) {
            pool.submit(() -> {
                try {
                    start.await();
                    orderService.create(userId, flashRequest());
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
        assertThat(done.await(90, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(unexpected.get()).isZero();
        assertThat(success.get()).isEqualTo(STOCK);
        assertThat(conflict.get()).isEqualTo(200 - STOCK);

        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getEventId, EVENT_ID)))
                .isEqualTo(STOCK);
        EventStock db = eventStockMapper.selectOne(Wrappers.<EventStock>lambdaQuery()
                .eq(EventStock::getEventId, EVENT_ID));
        assertThat(db.getStock()).isZero();
        assertThat(redis.opsForValue().get(RedisKey.stock(EVENT_ID))).isEqualTo("0");
        assertThat(orderedSetSize()).isEqualTo(STOCK);
    }

    @Test
    void DB兜底失败时归还Redis预扣() {
        testSupport.deleteUsersByPrefix("1392000");
        List<Long> two = testSupport.createUsers(2, "1392000");

        // 人为制造 Redis 与 DB 的漂移：Redis 说还有 2 张，DB 条件更新说一张都没有
        orderMapper.delete(Wrappers.<Order>lambdaQuery().eq(Order::getEventId, EVENT_ID));
        eventStockMapper.update(null, Wrappers.<EventStock>lambdaUpdate()
                .eq(EventStock::getEventId, EVENT_ID).set(EventStock::getStock, 0));
        redis.opsForValue().set(RedisKey.stock(EVENT_ID), "2");
        redis.delete(RedisKey.ordered(EVENT_ID));

        int failed = 0;
        for (Long userId : two) {
            try {
                orderService.create(userId, flashRequest());
            } catch (BizException e) {
                failed++;
            }
        }

        // 两个请求都通过了 Lua，却都被 DB 条件更新拦下
        assertThat(failed).isEqualTo(2);
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery().eq(Order::getEventId, EVENT_ID)))
                .isZero();
        // 预扣被完整归还：Redis 回到起始值 2，ordered 集合为空
        assertThat(redis.opsForValue().get(RedisKey.stock(EVENT_ID))).isEqualTo("2");
        assertThat(orderedSetSize()).isZero();
        testSupport.deleteUsersByPrefix("1392000");
    }

    private long orderedSetSize() {
        Long size = redis.opsForSet().size(RedisKey.ordered(EVENT_ID));
        return size == null ? 0 : size;
    }

    private CreateOrderRequest flashRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setEventId(EVENT_ID);
        return request;
    }
}
