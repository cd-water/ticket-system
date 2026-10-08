package com.cdwater.ticket;

import com.cdwater.ticket.common.constant.RedisKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedissonClient redisson;

    @BeforeEach
    void clean() {
        redisson.getKeys().deleteByPattern("cache:event:*");
        redisson.getKeys().deleteByPattern("seat:*");
    }

    @Test
    void 活动列表按模式与关键词分页() throws Exception {
        mvc.perform(get("/api/events").param("mode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].price").value(98.00))
                .andExpect(jsonPath("$.data.records[0].name").value("Bilibili World 2023（BW2023）"));

        mvc.perform(get("/api/events").param("mode", "2").param("keyword", "周杰伦"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(2));

        mvc.perform(get("/api/events").param("mode", "1").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2));

        mvc.perform(get("/api/events").param("mode", "1").param("keyword", "不存在"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void 活动元信息带购票模式() throws Exception {
        mvc.perform(get("/api/events/1"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.mode").value(1));
        mvc.perform(get("/api/events/2"))
                .andExpect(jsonPath("$.data.mode").value(2))
                .andExpect(jsonPath("$.data.rowCount").value(4));
        mvc.perform(get("/api/events/99"))
                .andExpect(jsonPath("$.code").value("C404"));
    }

    @Test
    void 抢票详情库存来自Redis() throws Exception {
        mvc.perform(get("/api/events/1/ticket"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.stock").value(1000));
    }

    @Test
    void 模式与接口不匹配返回C400() throws Exception {
        mvc.perform(get("/api/events/1/seat"))
                .andExpect(jsonPath("$.code").value("C400"));
        mvc.perform(get("/api/events/2/ticket"))
                .andExpect(jsonPath("$.code").value("C400"));
        mvc.perform(get("/api/events/99/ticket"))
                .andExpect(jsonPath("$.code").value("C404"));
    }

    @Test
    void 座位图行优先铺开并标出已售() throws Exception {
        mvc.perform(get("/api/events/2/seat"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.rowCount").value(4))
                .andExpect(jsonPath("$.data.colCount").value(10))
                .andExpect(jsonPath("$.data.seats.length()").value(40))
                .andExpect(jsonPath("$.data.seats[0].rowNo").value(1))
                .andExpect(jsonPath("$.data.seats[0].colNo").value(1))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(1))
                .andExpect(jsonPath("$.data.seats[0].status").value(0))
                // seed 中 seatId=2 已售
                .andExpect(jsonPath("$.data.seats[1].status").value(1))
                .andExpect(jsonPath("$.data.seats[10].rowNo").value(2))
                .andExpect(jsonPath("$.data.seats[10].colNo").value(1))
                .andExpect(jsonPath("$.data.seats[10].seatId").value(11));
    }

    @Test
    void 不存在的活动写下空标记实现缓存穿透防护() throws Exception {
        mvc.perform(get("/api/events/99")).andExpect(jsonPath("$.code").value("C404"));
        mvc.perform(get("/api/events/99")).andExpect(jsonPath("$.code").value("C404"));
        assertThat(redis.opsForValue().get(RedisKey.eventCacheNull(99))).isEqualTo(RedisKey.NULL_MARKER);
    }

    @Test
    void 座位锁定状态不进缓存且随TTL过期恢复可选() throws Exception {
        long eventId = 2;
        long seatId = 30;
        // seatId=30 → 数组下标 29
        redis.opsForValue().set(RedisKey.seatLock(eventId, seatId), "12345", Duration.ofSeconds(2));
        mvc.perform(get("/api/events/2/seat"))
                .andExpect(jsonPath("$.data.seats[29].status").value(1));

        // 锁本身没有被缓存：删掉 key 后立刻变可选
        redis.delete(RedisKey.seatLock(eventId, seatId));
        mvc.perform(get("/api/events/2/seat"))
                .andExpect(jsonPath("$.data.seats[29].status").value(0));
    }
}
