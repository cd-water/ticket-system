package com.cdwater.ticket.event.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdwater.ticket.common.constant.EventMode;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.Event;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.EventStock;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.common.result.PageResult;
import com.cdwater.ticket.event.dto.EventQuery;
import com.cdwater.ticket.event.mapper.EventMapper;
import com.cdwater.ticket.event.mapper.EventSeatMapper;
import com.cdwater.ticket.event.mapper.EventStockMapper;
import com.cdwater.ticket.event.vo.EventCardVO;
import com.cdwater.ticket.event.vo.EventMetaVO;
import com.cdwater.ticket.event.vo.SeatDetailVO;
import com.cdwater.ticket.event.vo.SeatVO;
import com.cdwater.ticket.event.vo.TicketDetailVO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMapper eventMapper;
    private final EventStockMapper eventStockMapper;
    private final EventSeatMapper eventSeatMapper;
    private final EventCacheManager cacheManager;
    private final RedissonClient redisson;

    public PageResult<EventCardVO> list(EventQuery query) {
        Page<Event> page = eventMapper.selectPage(new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Event>lambdaQuery()
                        .eq(Event::getMode, query.getMode())
                        .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                                Event::getName, query.getKeyword())
                        .orderByAsc(Event::getId));

        Page<EventCardVO> mapped = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        mapped.setRecords(page.getRecords().stream().map(EventService::toCard).toList());
        return PageResult.of(mapped);
    }

    public EventMetaVO getEvent(long eventId) {
        return cacheManager.getEvent(eventId, this::loadEventMeta);
    }

    public TicketDetailVO ticketDetail(long eventId) {
        EventMetaVO meta = getEvent(eventId);
        if (meta.getMode() != EventMode.TICKET) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该活动非抢票模式");
        }
        TicketDetailVO vo = new TicketDetailVO();
        vo.setId(meta.getId());
        vo.setName(meta.getName());
        vo.setAddress(meta.getAddress());
        vo.setPrice(meta.getPrice());
        vo.setStock(currentStock(eventId));
        return vo;
    }

    public SeatDetailVO seatDetail(long eventId) {
        EventMetaVO meta = getEvent(eventId);
        if (meta.getMode() != EventMode.SEAT) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该活动非选座模式");
        }
        Set<Long> sold = soldSeatIds(eventId);

        List<SeatVO> seats = new ArrayList<>(meta.getRowCount() * meta.getColCount());
        for (int row = 1; row <= meta.getRowCount(); row++) {
            for (int col = 1; col <= meta.getColCount(); col++) {
                long seatId = (long) (row - 1) * meta.getColCount() + col;
                // 已售与锁定中合并为「不可选」，前端不区分
                boolean taken = sold.contains(seatId) || cacheManager.isSeatLocked(eventId, seatId);
                seats.add(new SeatVO(row, col, seatId, taken ? 1 : 0));
            }
        }

        SeatDetailVO vo = new SeatDetailVO();
        vo.setId(meta.getId());
        vo.setName(meta.getName());
        vo.setAddress(meta.getAddress());
        vo.setPrice(meta.getPrice());
        vo.setRowCount(meta.getRowCount());
        vo.setColCount(meta.getColCount());
        vo.setSeats(seats);
        return vo;
    }

    /** 剩余库存读 Redis；首次访问时从 DB 播种，SETNX 保证多实例并发下只有一个赢家 */
    public int currentStock(long eventId) {
        RBucket<Object> bucket = redisson.getBucket(RedisKey.stock(eventId));
        Object cached = bucket.get();
        if (cached != null) {
            return Integer.parseInt(String.valueOf(cached));
        }
        EventStock stock = eventStockMapper.selectOne(Wrappers.<EventStock>lambdaQuery()
                .eq(EventStock::getEventId, eventId));
        int value = stock == null ? 0 : stock.getStock();
        bucket.trySet(String.valueOf(value));
        return value;
    }

    public EventMetaVO loadEventMeta(long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            return null;
        }
        EventMetaVO vo = new EventMetaVO();
        vo.setId(event.getId());
        vo.setName(event.getName());
        vo.setAddress(event.getAddress());
        vo.setPrice(event.getPrice());
        vo.setMode(event.getMode());
        vo.setRowCount(event.getRowCount());
        vo.setColCount(event.getColCount());
        return vo;
    }

    public EventSeat findSeat(long eventId, long seatId) {
        return eventSeatMapper.selectOne(Wrappers.<EventSeat>lambdaQuery()
                .eq(EventSeat::getEventId, eventId)
                .eq(EventSeat::getId, seatId));
    }

    public List<EventSeat> findSeats(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return List.of();
        }
        return eventSeatMapper.selectBatchIds(seatIds);
    }

    private Set<Long> soldSeatIds(long eventId) {
        if (cacheManager.hasSoldBitmap(eventId)) {
            return cacheManager.decodeSold(eventId);
        }
        Set<Long> ids = new HashSet<>();
        for (EventSeat seat : eventSeatMapper.selectList(Wrappers.<EventSeat>lambdaQuery()
                .eq(EventSeat::getEventId, eventId)
                .eq(EventSeat::getStatus, 1))) {
            ids.add(seat.getId());
        }
        cacheManager.seedSoldSeats(eventId, ids);
        return ids;
    }

    private static EventCardVO toCard(Event event) {
        EventCardVO vo = new EventCardVO();
        vo.setId(event.getId());
        vo.setName(event.getName());
        vo.setAddress(event.getAddress());
        vo.setPrice(event.getPrice());
        return vo;
    }
}
