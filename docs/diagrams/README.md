# 设计图索引

浏览器直接打开 `.html` 文件查看，均为无依赖的单文件 SVG。

## 结构图

| 图 | 文件 | 说明 |
|---|---|---|
| 架构图 | [ticket-architecture.html](ticket-architecture.html) | 模块化单体分层：boot 聚合 booking / auth / event 业务模块与 common 基座，向下依赖 MySQL、Redis、Kafka |
| 数据库 ER 图 | [ticket-er.html](ticket-er.html) | 七张表实体关系：活动派生库存与座位，用户/活动/座位汇聚为订单，订单关联支付单与发件箱 |

## 时序图

| 图 | 文件 | 说明 |
|---|---|---|
| 认证鉴权 | [seq-auth.html](seq-auth.html) | JWT 双 Token：双登录入口签发、Access 无状态本地验权（零查询）、过期后 Refresh 轮换刷新、登出仅删 Refresh |
| 下单 | [seq-order.html](seq-order.html) | 抢票 Lua 预扣（资格键+库存）/ 选座 SETNX 抢座双模式分流，订单与超时关单发件箱消息同事务落库 |
| 超时关单 | [seq-timeout-close.html](seq-timeout-close.html) | Outbox 中继轮询到期消息投递 MQ，消费者先 CAS 关单再按模式回补（抢票 INCR+DEL 资格键 / 选座清位图） |
| 支付 | [seq-payment.html](seq-payment.html) | 微信 Native 下单出码、异步回调验签后单事务落账（CAS×2 + 库存/座位 + 短信发件箱），与超时关单 CAS 竞态仲裁，输家退款 |
