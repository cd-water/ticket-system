package com.cdwater.ticket.event.controller;

import com.cdwater.ticket.common.result.PageResult;
import com.cdwater.ticket.common.result.Result;
import com.cdwater.ticket.event.dto.EventQuery;
import com.cdwater.ticket.event.service.EventService;
import com.cdwater.ticket.event.vo.EventCardVO;
import com.cdwater.ticket.event.vo.EventMetaVO;
import com.cdwater.ticket.event.vo.SeatDetailVO;
import com.cdwater.ticket.event.vo.TicketDetailVO;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Validated
public class EventController {

    private final EventService eventService;

    @GetMapping
    public Result<PageResult<EventCardVO>> list(@RequestParam @NotNull Integer mode,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        EventQuery query = new EventQuery();
        query.setMode(mode);
        query.setKeyword(keyword);
        query.setPage(page);
        query.setSize(size);
        return Result.success(eventService.list(query));
    }

    @GetMapping("/{eventId}")
    public Result<EventMetaVO> meta(@PathVariable long eventId) {
        return Result.success(eventService.getEvent(eventId));
    }

    @GetMapping("/{eventId}/ticket")
    public Result<TicketDetailVO> ticketDetail(@PathVariable long eventId) {
        return Result.success(eventService.ticketDetail(eventId));
    }

    @GetMapping("/{eventId}/seat")
    public Result<SeatDetailVO> seatDetail(@PathVariable long eventId) {
        return Result.success(eventService.seatDetail(eventId));
    }
}
