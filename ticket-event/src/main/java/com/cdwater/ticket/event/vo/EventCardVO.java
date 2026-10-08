package com.cdwater.ticket.event.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EventCardVO {

    private Long id;

    private String name;

    private String address;

    private BigDecimal price;
}
