package com.cdwater.ticket.event.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.common.result.PageResult;
import com.cdwater.ticket.common.result.Result;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 活动接口 Mock 实现：活动表、库存、座位图全部由内存数据生成，不查库。
 * 座位按行优先铺开，seatId = (排号 - 1) * 每排座数 + 座号，与 seed.sql 的插入顺序一致。
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    /** 购票模式：1-抢票 2-选座 */
    private static final int MODE_TICKET = 1;
    private static final int MODE_SEAT = 2;

    // ponytail: 静态内存数据，仅供前端联调 —— 改动重启即失，真实实现落库后整体删除
    private static final List<MockEvent> EVENTS = List.of(
            event(1L, "Bilibili World 2023（BW2023）", "上海市青浦区诸光路1888号国家会展中心（上海）", "98.00", MODE_TICKET, 0, 0, 1000),
            event(2L, "周杰伦2023嘉年华世界巡回演唱会-上海站", "上海市徐汇区天钥桥路666号上海体育场", "600.00", MODE_SEAT, 4, 10, 40),
            event(3L, "淘宝造物节 2026", "杭州国际博览中心", "128.00", MODE_TICKET, 0, 0, 5000),
            event(4L, "喜剧之王单口季·上海站", "上海中心大厦", "380.00", MODE_SEAT, 6, 12, 72),
            event(5L, "上海车展 2026", "国家会展中心（上海）", "280.00", MODE_TICKET, 0, 0, 3000),
            event(6L, "邓紫棋 I AM GLORIA 巡回演唱会·上海站", "梅赛德斯-奔驰文化中心", "880.00", MODE_SEAT, 5, 16, 80)
    );

    /** 各选座活动已售出的 seatId，用于让座位图有已售/可选两种状态可渲染 */
    private static final Map<Long, Set<Long>> SOLD_SEATS = Map.of(
            2L, Set.of(2L, 3L, 11L, 12L, 13L, 22L, 24L, 35L, 36L),
            4L, Set.of(1L, 2L, 13L, 14L, 25L, 37L, 38L, 50L, 63L, 64L, 71L),
            6L, Set.of(3L, 4L, 17L, 18L, 19L, 33L, 49L, 65L, 66L, 67L, 80L)
    );

    @GetMapping
    public Result<PageResult<EventCard>> list(
            @RequestParam int mode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        List<EventCard> matched = EVENTS.stream()
                .filter(e -> e.getMode() == mode)
                .filter(e -> keyword == null || keyword.isBlank() || e.getName().contains(keyword))
                .map(EventController::toCard)
                .toList();

        return Result.success(page(matched, page, size));
    }

    /** 详情页入口：先取元信息拿 mode，再按模式拉抢票或座位详情，省一次试探请求 */
    @GetMapping("/{eventId}")
    public Result<EventMetaVO> meta(@PathVariable long eventId) {
        MockEvent event = require(eventId);

        EventMetaVO vo = new EventMetaVO();
        vo.setId(event.getId());
        vo.setName(event.getName());
        vo.setAddress(event.getAddress());
        vo.setPrice(event.getPrice());
        vo.setMode(event.getMode());
        vo.setRowCount(event.getRowCount());
        vo.setColCount(event.getColCount());
        return Result.success(vo);
    }

    @GetMapping("/{eventId}/ticket")
    public Result<TicketDetailVO> ticketDetail(@PathVariable long eventId) {
        MockEvent event = require(eventId);
        if (event.getMode() != MODE_TICKET) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该活动非抢票模式");
        }

        TicketDetailVO vo = new TicketDetailVO();
        vo.setId(event.getId());
        vo.setName(event.getName());
        vo.setAddress(event.getAddress());
        vo.setPrice(event.getPrice());
        vo.setStock(event.getStock());
        return Result.success(vo);
    }

    @GetMapping("/{eventId}/seat")
    public Result<SeatDetailVO> seatDetail(@PathVariable long eventId) {
        MockEvent event = require(eventId);
        if (event.getMode() != MODE_SEAT) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该活动非选座模式");
        }

        Set<Long> sold = SOLD_SEATS.getOrDefault(eventId, Set.of());
        List<SeatVO> seats = new ArrayList<>(event.getRowCount() * event.getColCount());
        for (int row = 1; row <= event.getRowCount(); row++) {
            for (int col = 1; col <= event.getColCount(); col++) {
                long seatId = (long) (row - 1) * event.getColCount() + col;
                seats.add(new SeatVO(row, col, seatId, sold.contains(seatId) ? 1 : 0));
            }
        }

        SeatDetailVO vo = new SeatDetailVO();
        vo.setId(event.getId());
        vo.setName(event.getName());
        vo.setAddress(event.getAddress());
        vo.setPrice(event.getPrice());
        vo.setRowCount(event.getRowCount());
        vo.setColCount(event.getColCount());
        vo.setSeats(seats);
        return Result.success(vo);
    }

    private static MockEvent require(long eventId) {
        return EVENTS.stream()
                .filter(e -> e.getId() == eventId)
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND.getCode(), "活动不存在"));
    }

    private static EventCard toCard(MockEvent event) {
        EventCard card = new EventCard();
        card.setId(event.getId());
        card.setName(event.getName());
        card.setAddress(event.getAddress());
        card.setPrice(event.getPrice());
        return card;
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

    private static MockEvent event(long id, String name, String address, String price,
                                   int mode, int rowCount, int colCount, int stock) {
        MockEvent e = new MockEvent();
        e.setId(id);
        e.setName(name);
        e.setAddress(address);
        e.setPrice(new BigDecimal(price));
        e.setMode(mode);
        e.setRowCount(rowCount);
        e.setColCount(colCount);
        e.setStock(stock);
        return e;
    }

    @Data
    public static class MockEvent {
        private Long id;
        private String name;
        private String address;
        private BigDecimal price;
        private int mode;
        private int rowCount;
        private int colCount;
        private int stock;
    }

    @Data
    public static class EventCard {
        private Long id;
        private String name;
        private String address;
        private BigDecimal price;
    }

    @Data
    public static class EventMetaVO {
        private Long id;
        private String name;
        private String address;
        private BigDecimal price;
        private int mode;
        /** 仅选座模式有值，前端可据此在未拉座位图前先排版 */
        private int rowCount;
        private int colCount;
    }

    @Data
    public static class TicketDetailVO {
        private Long id;
        private String name;
        private String address;
        private BigDecimal price;
        private int stock;
    }

    @Data
    public static class SeatDetailVO {
        private Long id;
        private String name;
        private String address;
        private BigDecimal price;
        private int rowCount;
        private int colCount;
        private List<SeatVO> seats;
    }

    @Data
    public static class SeatVO {
        private int rowNo;
        private int colNo;
        private long seatId;
        /** 0-可选 1-不可选（已售出与锁定中合并，前端不区分） */
        private int status;

        public SeatVO() {
        }

        public SeatVO(int rowNo, int colNo, long seatId, int status) {
            this.rowNo = rowNo;
            this.colNo = colNo;
            this.seatId = seatId;
            this.status = status;
        }
    }
}
