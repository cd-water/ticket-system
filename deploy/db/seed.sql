-- 用户（13800000001 密码 Aa123456；13800000002 无密码，仅短信登录）
INSERT INTO `t_user` (`phone`, `password`)
VALUES ('13800000001', '$2a$10$SwwMs7T7E2rWloWEZP9ABeANabe992RETeA4.0HOfr2AqszhwSkiu'),
       ('13800000002', NULL);

-- 活动（1-抢票模式 2-选座模式）
INSERT INTO `t_event` (`id`, `name`, `address`, `price`, `mode`, `row_count`, `col_count`)
VALUES (1, 'Bilibili World 2023（BW2023）', '上海市青浦区诸光路1888号国家会展中心（上海）', 98.00, 1, 0, 0),
       (2, '周杰伦2023嘉年华世界巡回演唱会-上海站', '上海市徐汇区天钥桥路666号上海体育场', 600.00, 2, 4, 10),
       (3, '淘宝造物节 2026', '杭州国际博览中心', 128.00, 1, 0, 0),
       (4, '喜剧之王单口季·上海站', '上海中心大厦', 380.00, 2, 6, 12),
       (5, '上海车展 2026', '国家会展中心（上海）', 280.00, 1, 0, 0),
       (6, '邓紫棋 I AM GLORIA 巡回演唱会·上海站', '梅赛德斯-奔驰文化中心', 880.00, 2, 5, 16);

-- 库存（仅抢票模式活动需要）
INSERT INTO `t_event_stock` (`event_id`, `stock`)
VALUES (1, 1000),
       (3, 5000),
       (5, 3000);

-- 座位：行优先铺开，seatId = (排号-1)*每排座数 + 座号，与插入顺序一致
-- 活动 2：4 排 × 10 座，已售 9 个
INSERT INTO `t_event_seat` (`event_id`, `row_no`, `col_no`, `status`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 40)
SELECT 2, CEIL(n / 10), MOD(n - 1, 10) + 1,
       IF(FIND_IN_SET(n, '2,3,11,12,13,22,24,35,36') > 0, 1, 0)
FROM seq;

-- 活动 4：6 排 × 12 座，已售 11 个
INSERT INTO `t_event_seat` (`event_id`, `row_no`, `col_no`, `status`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 72)
SELECT 4, CEIL(n / 12), MOD(n - 1, 12) + 1,
       IF(FIND_IN_SET(n, '1,2,13,14,25,37,38,50,63,64,71') > 0, 1, 0)
FROM seq;

-- 活动 6：5 排 × 16 座，已售 11 个
INSERT INTO `t_event_seat` (`event_id`, `row_no`, `col_no`, `status`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 80)
SELECT 6, CEIL(n / 16), MOD(n - 1, 16) + 1,
       IF(FIND_IN_SET(n, '3,4,17,18,19,33,49,65,66,67,80') > 0, 1, 0)
FROM seq;
