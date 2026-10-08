package com.cdwater.ticket.event.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TicketDetailVO {

    private Long id;

    private String name;

    private String address;

    private BigDecimal price;

    /** 取自 Redis 而非数据库 */
    private int stock;
}
