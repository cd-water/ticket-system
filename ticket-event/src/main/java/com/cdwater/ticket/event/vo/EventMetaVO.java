package com.cdwater.ticket.event.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EventMetaVO {

    private Long id;

    private String name;

    private String address;

    private BigDecimal price;

    private Integer mode;

    /** 仅选座模式有值，前端可据此在未拉座位图前先排版 */
    private Integer rowCount;

    private Integer colCount;
}
