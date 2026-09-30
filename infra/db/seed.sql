-- 用户（密码 Aa123456 / 无密码）
INSERT INTO `t_user` (`phone`, `password`)
VALUES ('13800000001', '$2a$10$SwwMs7T7E2rWloWEZP9ABeANabe992RETeA4.0HOfr2AqszhwSkiu'),
       ('13800000002', NULL);

-- 活动（1-抢票模式 2-选座模式）
INSERT INTO `t_event` (`id`, `name`, `address`, `price`, `mode`, `row_count`, `col_count`)
VALUES (1, 'Bilibili World 2023（BW2023）', '上海市青浦区诸光路1888号国家会展中心（上海）', 98.00, 1, 0, 0),
       (2, '周杰伦2023嘉年华世界巡回演唱会-上海站', '上海市徐汇区天钥桥路666号上海体育场', 600.00, 2, 4, 10);

-- 库存（关联抢票活动1）
INSERT INTO `t_event_stock` (`event_id`, `stock`)
VALUES (1, 1000);

-- 座位（关联选座活动2：4 排 × 10 座 = 40 座）
INSERT INTO `t_event_seat` (`event_id`, `row_no`, `col_no`)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 40)
SELECT 2, CEIL(n / 10), MOD(n - 1, 10) + 1
FROM seq;
