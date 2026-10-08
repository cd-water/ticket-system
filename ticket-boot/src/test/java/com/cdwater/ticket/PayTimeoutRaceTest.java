package com.cdwater.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.dto.CreateOrderRequest;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.mapper.PaymentMapper;
import com.cdwater.ticket.booking.service.OrderCloseService;
import com.cdwater.ticket.booking.service.OrderService;
import com.cdwater.ticket.booking.service.PaymentService;
import com.cdwater.ticket.booking.outbox.OrderTimeoutSweeper;
import com.cdwater.ticket.common.constant.PayStatus;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.EventStock;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.entity.Payment;
import com.cdwater.ticket.event.mapper.EventSeatMapper;
import com.cdwater.ticket.event.mapper.EventStockMapper;
import com.cdwater.ticket.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付与超时关单都只认「仍为待支付」的赢家，CAS 由数据库行锁串行化。
 * 验证两件事：只有一个赢家，且资源不会被两边同时改动。
 */
@SpringBootTest
@ActiveProfiles("test")
class PayTimeoutRaceTest {

    private static final long SEAT_EVENT = 2;
    private static final long SEAT_ID = 26;
    private static final long FLASH_EVENT = 5;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderCloseService orderCloseService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private EventSeatMapper eventSeatMapper;

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private OrderTimeoutSweeper sweeper;

    @Autowired
    private TestSupport testSupport;

    @Autowired
    private StringRedisTemplate redis;

    private long userId;

    @BeforeEach
    void reset() {
        testSupport.resetBookingState();
        testSupport.deleteUsersByPrefix("1394000");
        userId = testSupport.createUsers(1, "1394000").get(0);
    }

    @AfterEach
    void cleanup() {
        testSupport.deleteUsersByPrefix("1394000");
    }

    private CreateOrderRequest seatRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setEventId(SEAT_EVENT);
        request.setSeatId(SEAT_ID);
        return request;
    }

    private CreateOrderRequest flashRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setEventId(FLASH_EVENT);
        return request;
    }

    private Payment paymentOf(long orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, order.getId()));
    }

    @Test
    void 选座订单支付与关单并发只有一个赢家() throws Exception {
        long orderNo = orderService.create(userId, seatRequest()).getOrderNo();

        AtomicBoolean paid = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        spawn(start, () -> {
            try {
                paymentService.pay(userId, String.valueOf(orderNo));
                paid.set(true);
            } catch (Exception ignored) {
                // 竞态输家
            } finally {
                done.countDown();
            }
        });
        spawn(start, () -> {
            orderCloseService.close(orderService.findByOrderNo(orderNo));
            done.countDown();
        });

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        Order settled = orderService.findByOrderNo(orderNo);
        boolean closed = settled.getStatus() == 2;
        assertThat(paid.get() ^ closed).as("支付与关单必须且只能有一个赢家").isTrue();

        Payment payment = paymentOf(orderNo);
        EventSeat seat = eventSeatMapper.selectById(SEAT_ID);
        boolean locked = redis.opsForValue().get(RedisKey.seatLock(SEAT_EVENT, SEAT_ID)) != null;

        assertThat(locked).as("订单已终结，租约必须释放").isFalse();

        if (settled.getStatus() == 1) {
            assertThat(payment.getStatus()).isEqualTo(PayStatus.SUCCESS);
            assertThat(seat.getStatus()).isEqualTo(1);
        } else {
            assertThat(payment.getStatus()).isEqualTo(PayStatus.CLOSED);
            assertThat(seat.getStatus()).isZero();
        }
    }

    @Test
    void 抢票订单支付与关单并发只有一个赢家() throws Exception {
        long orderNo = orderService.create(userId, flashRequest()).getOrderNo();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        spawn(start, () -> {
            try {
                paymentService.pay(userId, String.valueOf(orderNo));
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });
        spawn(start, () -> {
            orderCloseService.close(orderService.findByOrderNo(orderNo));
            done.countDown();
        });

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

        Order settled = orderService.findByOrderNo(orderNo);
        assertThat(settled.getStatus()).isIn(1, 2);
        assertThat(paymentOf(orderNo).getStatus())
                .isEqualTo(settled.getStatus() == 1 ? PayStatus.SUCCESS : PayStatus.CLOSED);
    }

    @Test
    void 重复支付回调幂等返回成功() {
        long orderNo = orderService.create(userId, flashRequest()).getOrderNo();
        String outTradeNo = String.valueOf(paymentOf(orderNo).getOutTradeNo());

        paymentService.handleCallback(outTradeNo, "wx-mock-001", true);
        paymentService.handleCallback(outTradeNo, "wx-mock-001", true);
        paymentService.handleCallback(outTradeNo, "wx-mock-001", true);

        assertThat(orderService.findByOrderNo(orderNo).getStatus()).isEqualTo(1);
    }

    @Test
    void 支付失败回调不改订单只关支付单() {
        long orderNo = orderService.create(userId, flashRequest()).getOrderNo();
        String outTradeNo = String.valueOf(paymentOf(orderNo).getOutTradeNo());

        paymentService.handleCallback(outTradeNo, "wx-mock-002", false);

        assertThat(orderService.findByOrderNo(orderNo).getStatus()).isZero();
        assertThat(paymentOf(orderNo).getStatus()).isEqualTo(PayStatus.FAILED);
    }

    @Test
    void 兜底定时任务关闭已过期未支付订单() throws Exception {
        // 顺序要紧：先记下扣减前的库存，否则「还回原值」这条断言会不证自明
        int before = dbStock(FLASH_EVENT);
        long orderNo = orderService.create(userId, flashRequest()).getOrderNo();
        assertThat(dbStock(FLASH_EVENT)).isEqualTo(before - 1);

        // 把过期时间推到过去，模拟「关单消息投递或消费失败、订单卡在待支付」
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .set(Order::getExpireTime, LocalDateTime.now().minusMinutes(1)));

        sweeper.sweep();

        assertThat(orderService.findByOrderNo(orderNo).getStatus())
                .as("到点未支付的订单不该永远卡在待支付").isEqualTo(2);
        assertThat(paymentOf(orderNo).getStatus()).isEqualTo(PayStatus.CLOSED);
        assertThat(dbStock(FLASH_EVENT)).as("兜底关单同样要归还库存").isEqualTo(before);
    }

    @Test
    void 兜底定时任务不碰未过期的订单() throws Exception {
        long orderNo = orderService.create(userId, flashRequest()).getOrderNo();

        sweeper.sweep();

        assertThat(orderService.findByOrderNo(orderNo).getStatus()).isZero();
    }

    private int dbStock(long eventId) {
        EventStock stock = eventStockMapper.selectOne(Wrappers.<EventStock>lambdaQuery()
                .eq(EventStock::getEventId, eventId));
        return stock.getStock();
    }

    private static void spawn(CountDownLatch start, Runnable task) {
        Thread thread = new Thread(() -> {
            try {
                start.await();
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();
    }
}
