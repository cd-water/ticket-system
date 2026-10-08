-- 1. 用户表
CREATE TABLE `t_user`
(
    `id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID（主键）',
    `phone`    VARCHAR(20)  NOT NULL COMMENT '手机号',
    `password` VARCHAR(100) NULL COMMENT '密码（BCrypt）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';

-- 2. 活动表
CREATE TABLE `t_event`
(
    `id`        BIGINT         NOT NULL AUTO_INCREMENT COMMENT '活动ID（主键）',
    `name`      VARCHAR(100)   NOT NULL COMMENT '活动名称',
    `address`   VARCHAR(200)   NOT NULL COMMENT '活动地址',
    `price`     DECIMAL(10, 2) NOT NULL COMMENT '活动票价',
    `mode`      TINYINT        NOT NULL COMMENT '购票模式（1-抢票 2-选座）',
    `row_count` INT            NOT NULL DEFAULT 0 COMMENT '排数（仅选座模式）',
    `col_count` INT            NOT NULL DEFAULT 0 COMMENT '每排座数（仅选座模式）',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '活动表';

-- 3. 活动库存表
CREATE TABLE `t_event_stock`
(
    `id`       BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存ID（主键）',
    `event_id` BIGINT NOT NULL COMMENT '关联活动ID',
    `stock`    INT    NOT NULL DEFAULT 0 COMMENT '库存',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event` (`event_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '活动库存表';

-- 4. 活动座位表
CREATE TABLE `t_event_seat`
(
    `id`       BIGINT  NOT NULL AUTO_INCREMENT COMMENT '座位ID（主键）',
    `event_id` BIGINT  NOT NULL COMMENT '关联活动ID',
    `row_no`   INT     NOT NULL COMMENT '排号（从1开始）',
    `col_no`   INT     NOT NULL COMMENT '座号（从1开始）',
    `status`   TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0-待售 1-已售）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pos` (`event_id`, `row_no`, `col_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '活动座位表';

-- 5. 订单表
CREATE TABLE `t_order`
(
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单ID（主键）',
    `order_no`    BIGINT         NOT NULL COMMENT '订单号（雪花算法）',
    `user_id`     BIGINT         NOT NULL COMMENT '关联用户ID',
    `event_id`    BIGINT         NOT NULL COMMENT '关联活动ID',
    `seat_id`     BIGINT         NULL COMMENT '关联座位ID（选座模式必填，NULL-抢票模式）',
    `amount`      DECIMAL(10, 2) NOT NULL COMMENT '应付金额',
    `status`      TINYINT        NOT NULL DEFAULT 0 COMMENT '状态（0-待支付 1-已支付 2-已取消）',
    `expire_time` DATETIME       NOT NULL COMMENT '支付截止时间',
    `pay_time`    DATETIME       NULL COMMENT '支付时间',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_user_event_status` (`user_id`, `event_id`, `status`),
    KEY `idx_user` (`user_id`),
    KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单表';

-- 6. 支付单表（微信支付 Native 扫码）
CREATE TABLE `t_payment`
(
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '支付ID（主键）',
    `out_trade_no`   BIGINT         NOT NULL COMMENT '商户订单号（雪花算法，发给微信、回调凭它定位）',
    `order_id`       BIGINT         NOT NULL COMMENT '关联订单ID',
    `amount`         DECIMAL(10, 2) NOT NULL COMMENT '支付金额（元，调微信下单时转换为分）',
    `status`         TINYINT        NOT NULL DEFAULT 0 COMMENT '状态（0-未支付 1-支付成功 2-支付失败 3-已关闭）',
    `code_url`       VARCHAR(64)    NULL COMMENT '支付链接（Native下单同步返回，前端生成二维码）',
    `transaction_id` VARCHAR(32)    NULL COMMENT '微信支付订单号（微信生成，对账用）',
    `expire_time`    DATETIME       NOT NULL COMMENT '支付截止时间（下单 time_expire）',
    `pay_time`       DATETIME       NULL COMMENT '支付完成时间（回调 success_time）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
    UNIQUE KEY `uk_transaction_id` (`transaction_id`),
    UNIQUE KEY `uk_order` (`order_id`),
    KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付单表';

-- 7. 消息发件箱表（Outbox 模式）
CREATE TABLE `t_outbox`
(
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '消息ID（主键）',
    `topic`        VARCHAR(64)   NOT NULL COMMENT '目标主题',
    `message_key`  VARCHAR(64)   NOT NULL COMMENT '消息键',
    `payload`      VARCHAR(1024) NOT NULL COMMENT '消息体（JSON）',
    `deliver_time` DATETIME      NOT NULL COMMENT '期望投递时间',
    `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态（0-待投递 1-已投递）',
    PRIMARY KEY (`id`),
    KEY `idx_status_deliver` (`status`, `deliver_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息发件箱表';
