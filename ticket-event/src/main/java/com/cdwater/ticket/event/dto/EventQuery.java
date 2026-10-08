package com.cdwater.ticket.event.dto;

import lombok.Data;

@Data
public class EventQuery {

    /** 1-抢票 2-选座，必填 */
    private Integer mode;

    private String keyword;

    private long page = 1;

    private long size = 10;
}
