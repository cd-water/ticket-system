# 设计图索引

浏览器直接打开 `.html` 即可查看，均为无依赖的单文件 SVG。

## 系统设计

| 图 | 文件 | 说明 |
|---|---|---|
| 架构图 | [ticket-architecture.html](ticket-architecture.html) | 模块化单体分层：boot 聚合三大业务模块，共用 common 基座并访问 MySQL / Redis / Kafka |
| 数据库 ER 图 | [ticket-er.html](ticket-er.html) | 七张表的实体关系与字段，含基数与可空标记 |
| 状态机图 | [ticket-state.html](ticket-state.html) | 订单 / 支付单 / 发件箱：转换均以「状态仍为初始值」为条件，靠 CAS 判定唯一赢家 |

## 时序图

| 图 | 文件 | 说明 |
|---|---|---|
| 认证鉴权 | [seq-auth.html](seq-auth.html) | JWT 双 Token：Access 本地验签零查询，过期后携 Refresh 静默换新并轮换 |
| 下单 | [seq-order.html](seq-order.html) | 抢票与选座双模式：Lua 原子预扣资源，订单与关单消息同事务落库 |
| 超时关单 | [seq-timeout-close.html](seq-timeout-close.html) | Outbox 中继投递 Kafka，消费者先 CAS 关单再按模式回补资源 |
| 支付 | [seq-payment.html](seq-payment.html) | 微信 Native 回调触发落账，与超时关单争抢同一状态，输家发起退款 |
