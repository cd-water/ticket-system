package com.cdwater.ticket.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cdwater.ticket.common.entity.Event;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper extends BaseMapper<Event> {
}
