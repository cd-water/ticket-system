package com.cdwater.ticket.booking.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.common.result.PageResult;
import com.cdwater.ticket.common.result.Result;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单 / 支付接口 Mock 实现：订单表与已售座位维护在内存里，进程重启即恢复初始数据。
 * 下单、支付会真实改动内存状态，便于前端把「下单 → 支付 → 列表刷新」整条链路跑通。
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final int MODE_TICKET = 1;
    private static final int MODE_SEAT = 2;

    /** 订单状态：0-待支付 1-已支付 2-已取消（超时关闭） */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELLED = 2;

    /** 支付有效期 15 分钟，与接口文档示例 15:03 → 15:18 一致 */
    private static final int EXPIRE_MINUTES = 15;

    /** 雪花算法形态的自增订单号，前端可当普通数字处理 */
    private static final AtomicLong ORDER_NO_SEQ = new AtomicLong(7845129365720192512L);

    // ponytail: 静态可变内存数据，仅供前端联调 —— 非线程安全、重启即失，真实实现落库后整体删除
    private static final List<OrderVO> ORDERS = new ArrayList<>();

    /** 下单占用掉的座位：选座模式用，key 为活动 ID */
    private static final Map<Long, Set<Long>> SOLD_SEATS = new HashMap<>();

    /** 抢票模式剩余库存，key 为活动 ID */
    private static final Map<Long, Integer> STOCK = new HashMap<>();

    /**
     * 活动快照：订单要回显活动名、地址、票价。此处刻意复制一份而不跨模块共享
     * EventController 的数据——Mock 是一次性的，真实实现到位后两处都会换成 service 调用。
     */
    // ponytail: 与 EventController 的活动表重复，联调期接受这份重复，换成 service 层后自然消失
    private static final List<Snapshot> EVENTS = List.of(
            snapshot(1L, "Bilibili World 2023（BW2023）", "上海市青浦区诸光路1888号国家会展中心（上海）", "98.00", MODE_TICKET, 0, 0),
            snapshot(2L, "周杰伦2023嘉年华世界巡回演唱会-上海站", "上海市徐汇区天钥桥路666号上海体育场", "600.00", MODE_SEAT, 4, 10),
            snapshot(3L, "淘宝造物节 2026", "杭州国际博览中心", "128.00", MODE_TICKET, 0, 0),
            snapshot(4L, "喜剧之王单口季·上海站", "上海中心大厦", "380.00", MODE_SEAT, 6, 12),
            snapshot(5L, "上海车展 2026", "国家会展中心（上海）", "280.00", MODE_TICKET, 0, 0),
            snapshot(6L, "邓紫棋 I AM GLORIA 巡回演唱会·上海站", "梅赛德斯-奔驰文化中心", "880.00", MODE_SEAT, 5, 16)
    );

    static {
        STOCK.put(1L, 1000);
        STOCK.put(3L, 5000);
        STOCK.put(5L, 3000);
        seed();
    }

    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        List<OrderVO> matched = ORDERS.stream()
                .filter(o -> orderNo == null || orderNo.isBlank() || String.valueOf(o.getOrderNo()).equals(orderNo))
                .filter(o -> status == null || o.getStatus() == status)
                .sorted(Comparator.comparing(OrderVO::getCreateTime).reversed())
                .toList();

        return Result.success(page(matched, page, size));
    }

    @PostMapping("/orders")
    public Result<CreateOrderVO> create(@RequestBody @Valid CreateOrderRequest request) {
        Snapshot event = EVENTS.stream()
                .filter(e -> e.getId() == request.getEventId())
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND.getCode(), "活动不存在"));

        SeatPosition seat = resolveSeat(event, request.getSeatId());

        LocalDateTime now = LocalDateTime.now();
        OrderVO order = new OrderVO();
        order.setOrderNo(ORDER_NO_SEQ.incrementAndGet());
        order.setEventId(event.getId());
        order.setEventName(event.getName());
        order.setEventAddress(event.getAddress());
        order.setEventPrice(event.getPrice());
        order.setAmount(event.getPrice());
        order.setStatus(STATUS_UNPAID);
        order.setSeat(seat);
        order.setSeatId(seat == null ? null : request.getSeatId());
        order.setCreateTime(now);
        order.setExpireTime(now.plusMinutes(EXPIRE_MINUTES));
        ORDERS.add(order);

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setAmount(order.getAmount());
        vo.setExpireTime(order.getExpireTime());
        vo.setEventName(order.getEventName());
        vo.setEventAddress(order.getEventAddress());
        vo.setSeat(seat);
        return Result.success(vo);
    }

    @PostMapping("/pay")
    public Result<Void> pay(@RequestBody @Valid PayRequest request) {
        OrderVO order = ORDERS.stream()
                .filter(o -> String.valueOf(o.getOrderNo()).equals(request.getOrderNo()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在"));

        // 与超时关单争抢同一状态：只认「仍为待支付」的赢家，先到先得
        if (order.getStatus() != STATUS_UNPAID) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "订单已支付或已关闭");
        }
        order.setStatus(STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        return Result.success();
    }

    /** 手动取消与前端倒计时归零共用同一入口 */
    @PostMapping("/orders/cancel")
    public Result<Void> cancel(@RequestBody @Valid CancelRequest request) {
        OrderVO order = ORDERS.stream()
                .filter(o -> String.valueOf(o.getOrderNo()).equals(request.getOrderNo()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在"));

        if (order.getStatus() != STATUS_UNPAID) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "订单已支付或已关闭");
        }
        order.setStatus(STATUS_CANCELLED);
        releaseResources(order);
        return Result.success();
    }

    /** 取消或超时关单时把座位与库存还给活动，座位图才能重新可选 */
    private static void releaseResources(OrderVO order) {
        Snapshot event = EVENTS.stream().filter(e -> e.getId() == order.getEventId()).findFirst().orElse(null);
        if (event == null) return;

        if (event.getMode() == MODE_TICKET) {
            STOCK.merge(order.getEventId(), 1, Integer::sum);
            return;
        }
        Set<Long> sold = SOLD_SEATS.get(order.getEventId());
        if (order.getSeatId() != null && sold != null) {
            sold.remove(order.getSeatId());
        }
    }

    /** 选座模式要求 seatId 且座位未被占用；抢票模式扣减内存库存并返回空座位 */
    private static SeatPosition resolveSeat(Snapshot event, Long seatId) {
        if (event.getMode() == MODE_TICKET) {
            Integer stock = STOCK.get(event.getId());
            if (stock == null || stock <= 0) {
                throw new BizException(ResultCode.CONFLICT.getCode(), "票已售罄");
            }
            STOCK.put(event.getId(), stock - 1);
            return null;
        }

        if (seatId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "选座模式必须指定座位");
        }
        Set<Long> sold = SOLD_SEATS.computeIfAbsent(event.getId(), k -> new HashSet<>());
        if (seatId < 1 || seatId > event.getRowCount() * event.getColCount()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "座位不存在");
        }
        if (!sold.add(seatId)) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "座位已被占用");
        }

        return toPosition(event, seatId);
    }

    /** 预置几条覆盖全部状态的订单，前端一进来就能看到待支付倒计时、已支付与已取消三种展示 */
    private static void seed() {
        seedOrder(1L, null, STATUS_UNPAID, 2);
        seedOrder(2L, 25L, STATUS_PAID, 30);
        seedOrder(4L, 5L, STATUS_UNPAID, 1);
        seedOrder(1L, null, STATUS_CANCELLED, 60);

        SOLD_SEATS.put(2L, new HashSet<>(Set.of(2L, 3L, 11L, 12L, 13L, 22L, 24L, 35L, 36L)));
        SOLD_SEATS.put(4L, new HashSet<>(Set.of(1L, 2L, 13L, 14L, 25L, 37L, 38L, 50L, 63L, 64L, 71L)));
    }

    private static void seedOrder(long eventId, Long seatId, int status, int createdMinutesAgo) {
        Snapshot event = EVENTS.stream().filter(e -> e.getId() == eventId).findFirst().orElseThrow();

        LocalDateTime created = LocalDateTime.now().minusMinutes(createdMinutesAgo);
        OrderVO order = new OrderVO();
        order.setOrderNo(ORDER_NO_SEQ.incrementAndGet());
        order.setEventId(eventId);
        order.setEventName(event.getName());
        order.setEventAddress(event.getAddress());
        order.setEventPrice(event.getPrice());
        order.setAmount(event.getPrice());
        order.setStatus(status);
        order.setSeat(seatId == null ? null : toPosition(event, seatId));
        order.setSeatId(seatId);
        order.setCreateTime(created);
        order.setExpireTime(created.plusMinutes(EXPIRE_MINUTES));
        if (status == STATUS_PAID) {
            order.setPayTime(created.plusMinutes(5));
        }
        ORDERS.add(order);
        if (seatId != null) {
            SOLD_SEATS.computeIfAbsent(eventId, k -> new HashSet<>()).add(seatId);
        }
    }

    /** seatId 换算成排号座号：行优先，seatId = (排号 - 1) * 每排座数 + 座号 */
    private static SeatPosition toPosition(Snapshot event, long seatId) {
        return new SeatPosition((int) ((seatId - 1) / event.getColCount() + 1),
                (int) ((seatId - 1) % event.getColCount() + 1));
    }

    /** 内存列表翻页，语义与 MyBatis-Plus 分页插件一致：越界返回空页而非报错 */
    private static <T> PageResult<T> page(List<T> all, long page, long size) {
        int from = (int) Math.min((page - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        Page<T> paged = new Page<>(page, size);
        paged.setTotal(all.size());
        paged.setRecords(all.subList(from, to));
        return PageResult.of(paged);
    }

    private static Snapshot snapshot(long id, String name, String address, String price,
                                     int mode, int rowCount, int colCount) {
        Snapshot s = new Snapshot();
        s.setId(id);
        s.setName(name);
        s.setAddress(address);
        s.setPrice(new BigDecimal(price));
        s.setMode(mode);
        s.setRowCount(rowCount);
        s.setColCount(colCount);
        return s;
    }

    @Data
    public static class CreateOrderRequest {
        @NotNull
        private Long eventId;
        /** 选座模式必填，抢票模式不传 */
        private Long seatId;
    }

    @Data
    public static class PayRequest {
        /** 收字符串而非 long：与响应保持同一形态，前端拿到即可原样回传 */
        @NotBlank
        private String orderNo;
    }

    @Data
    public static class CancelRequest {
        @NotBlank
        private String orderNo;
    }

    @Data
    public static class CreateOrderVO {
        /** 雪花算法 ID 超出 JS 安全整数范围，序列化为字符串避免前端丢精度 */
        @JsonSerialize(using = ToStringSerializer.class)
        private long orderNo;
        private BigDecimal amount;
        private LocalDateTime expireTime;
        private String eventName;
        private String eventAddress;
        private SeatPosition seat;
    }

    @Data
    public static class OrderVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private long orderNo;
        private Long eventId;
        private String eventName;
        private String eventAddress;
        private BigDecimal eventPrice;
        private BigDecimal amount;
        private int status;
        private SeatPosition seat;
        /** 取消/关单时按它归还座位，与 seat 展示字段解耦 */
        private Long seatId;
        private LocalDateTime createTime;
        private LocalDateTime expireTime;
        private LocalDateTime payTime;
    }

    @Data
    public static class SeatPosition {
        private int rowNo;
        private int colNo;

        public SeatPosition() {
        }

        public SeatPosition(int rowNo, int colNo) {
            this.rowNo = rowNo;
            this.colNo = colNo;
        }
    }

    @Data
    public static class Snapshot {
        private Long id;
        private String name;
        private String address;
        private BigDecimal price;
        private int mode;
        private int rowCount;
        private int colCount;
    }
}
