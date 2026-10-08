package com.cdwater.ticket.event.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SeatDetailVO {

    private Long id;

    private String name;

    private String address;

    private BigDecimal price;

    private Integer rowCount;

    private Integer colCount;

    /** 长度 = rowCount × colCount，按行优先排列 */
    private List<SeatVO> seats;
}
