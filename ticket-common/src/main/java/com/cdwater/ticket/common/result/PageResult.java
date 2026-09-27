package com.cdwater.ticket.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private long total;
    private List<T> records;
    private long page;
    private long size;

    private PageResult() {
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.total = page.getTotal();
        r.records = page.getRecords();
        r.page = page.getCurrent();
        r.size = page.getSize();
        return r;
    }
}