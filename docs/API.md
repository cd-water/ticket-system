# Ticket-System API 接口文档

前后端协作契约。设计背景见 [`docs/diagrams/`](./diagrams/README.md)。

- 基址：`http://localhost:8600`，前端统一走 `/api` 前缀
- 配套 ER 图与 schema：[`infra/db/schema.sql`](../infra/db/schema.sql)

---

## 通用约定

### 响应信封

所有接口返回 `Result<T>`，**HTTP 状态码恒为 200**，成败由 `code` 表达。

```json
{ "code": "A200", "message": "Success", "data": { } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | string | `A2xx` 成功 / `C4xx` 客户端错误 / `S5xx` 服务端错误 |
| `message` | string | 提示文案，可直接展示给用户 |
| `data` | any | 业务数据，无数据时为 `null` |

### 鉴权

标记 ✅ 的接口需在请求头携带 Access Token：

```
Authorization: Bearer {accessToken}
```

| Token | 形态 | 有效期 |
|---|---|---|
| Access Token | JWT | 30 分钟 |
| Refresh Token | UUID 字符串 | 7 天 |

### 分页

**请求参数**（query string）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `size` | int | 否 | 10 | 每页条数 |

**响应结构**（`PageResult<T>`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | long | 总条数 |
| `records` | array | 当前页数据 |
| `page` | int | 当前页码 |
| `size` | int | 每页条数 |

### 接口一览

| # | 方法 | 路径 | 鉴权 |
|---|---|---|---|
| 1 | POST | `/api/auth/send-code` | — |
| 2 | POST | `/api/auth/sms-login` | — |
| 3 | POST | `/api/auth/pwd-login` | — |
| 4 | POST | `/api/auth/refresh` | — |
| 5 | POST | `/api/auth/logout` | — |
| 6 | POST | `/api/auth/password` | ✅ |
| 7 | GET | `/api/events` | — |
| 8 | GET | `/api/events/{eventId}/ticket` | — |
| 9 | GET | `/api/events/{eventId}/seat` | — |
| 10 | POST | `/api/orders` | ✅ |
| 11 | POST | `/api/pay` | ✅ |
| 12 | GET | `/api/orders` | ✅ |
| 13 | GET | `/api/events/{eventId}` | — |
| 14 | POST | `/api/orders/cancel` | ✅ |

---

## 1. 认证

### 1.1 发送验证码

`POST /api/auth/send-code`　鉴权：否

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | string | 是 | 手机号 |

**响应**：`data` 为 `null`

---

### 1.2 验证码登录（自动注册）

`POST /api/auth/sms-login`　鉴权：否

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | string | 是 | 手机号 |
| `code` | string | 是 | 6 位验证码 |

**响应**

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "b3f1c2d4-5e6a-4b7c-8d9e-0f1a2b3c4d5e",
    "userInfo": { "id": 1, "phone": "13800000001" }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `accessToken` | string | 访问令牌 |
| `refreshToken` | string | 刷新令牌 |
| `userInfo.id` | long | 用户 ID |
| `userInfo.phone` | string | 手机号 |

手机号未注册时自动创建账号。

---

### 1.3 密码登录

`POST /api/auth/pwd-login`　鉴权：否

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | string | 是 | 手机号 |
| `password` | string | 是 | 密码 |

**响应**：同 [1.2](#12-验证码登录自动注册)

---

### 1.4 刷新 Token

`POST /api/auth/refresh`　鉴权：否

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `refreshToken` | string | 是 | 登录时下发的 refreshToken |

**响应**：同 [1.2](#12-验证码登录自动注册)，为轮换后的新 Token 对

---

### 1.5 登出

`POST /api/auth/logout`　鉴权：否

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `refreshToken` | string | 是 | 当前持有的 refreshToken |

**响应**：`data` 为 `null`

---

### 1.6 修改密码

`POST /api/auth/password`　鉴权：✅

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `newPassword` | string | 是 | 新密码，长度 8–20 |

无需提供旧密码。成功后前端需清除本地登录态并跳转登录页。

**响应**：`data` 为 `null`

---

## 2. 活动

### 2.1 活动列表

`GET /api/events`　鉴权：否

**请求**（query string）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `mode` | int | 是 | — | `1`=抢票模式，`2`=选座模式 |
| `keyword` | string | 否 | — | 活动名称模糊匹配 |
| `page` | int | 否 | 1 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |

**响应**：`PageResult<EventCard>`，`records` 元素即 `EventCard`：

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "total": 1,
    "records": [
      {
        "id": 1,
        "name": "Bilibili World 2023（BW2023）",
        "address": "上海市青浦区诸光路1888号国家会展中心（上海）",
        "price": 98.00
      }
    ],
    "page": 1,
    "size": 10
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 活动 ID |
| `name` | string | 活动名称 |
| `address` | string | 活动地址 |
| `price` | decimal | 活动票价（元） |

---

### 2.2 活动元信息

`GET /api/events/{eventId}`　鉴权：否

详情页入口：先取本接口拿到 `mode`，再按模式调 [2.3](#23-抢票活动详情) 或 [2.4](#24-选座活动详情)。

**请求**：路径参数 `eventId`（long，必填）

**响应**

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "id": 1,
    "name": "Bilibili World 2023（BW2023）",
    "address": "上海市青浦区诸光路1888号国家会展中心（上海）",
    "price": 98.00,
    "mode": 1,
    "rowCount": 0,
    "colCount": 0
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 活动 ID |
| `name` | string | 活动名称 |
| `address` | string | 活动地址 |
| `price` | decimal | 活动票价 |
| `mode` | int | `1`=抢票，`2`=选座 |
| `rowCount` | int | 总排数，选座模式有意义 |
| `colCount` | int | 每排座数，选座模式有意义 |

---

### 2.3 抢票活动详情

`GET /api/events/{eventId}/ticket`　鉴权：否　适用：`mode=1`

**请求**：路径参数 `eventId`（long，必填）

**响应**

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "id": 1,
    "name": "Bilibili World 2023（BW2023）",
    "address": "上海市青浦区诸光路1888号国家会展中心（上海）",
    "price": 98.00,
    "stock": 1000
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 活动 ID |
| `name` | string | 活动名称 |
| `address` | string | 活动地址 |
| `price` | decimal | 活动票价 |
| `stock` | int | 剩余可购票数，含已锁定未支付部分。取自 Redis，非数据库 |

---

### 2.4 选座活动详情

`GET /api/events/{eventId}/seat`　鉴权：否　适用：`mode=2`

**请求**：路径参数 `eventId`（long，必填）

**响应**

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "id": 2,
    "name": "周杰伦2023嘉年华世界巡回演唱会-上海站",
    "address": "上海市徐汇区天钥桥路666号上海体育场",
    "price": 600.00,
    "rowCount": 4,
    "colCount": 10,
    "seats": [
      { "rowNo": 1, "colNo": 1, "seatId": 1, "status": 0 },
      { "rowNo": 1, "colNo": 2, "seatId": 2, "status": 1 },
      { "rowNo": 1, "colNo": 3, "seatId": 3, "status": 1 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 活动 ID |
| `name` | string | 活动名称 |
| `address` | string | 活动地址 |
| `price` | decimal | 活动票价 |
| `rowCount` | int | 总排数 |
| `colCount` | int | 每排座数 |
| `seats` | array | 长度 = `rowCount × colCount`，按行优先排列 |
| `seats[].rowNo` | int | 排号，从 1 开始 |
| `seats[].colNo` | int | 座号，从 1 开始 |
| `seats[].seatId` | long | 座位 ID，下单时需回传 |
| `seats[].status` | int | `0`=可选，`1`=不可选 |

按 `colCount` 对 `seats` 切片即可分行渲染。**不可选**涵盖已售出与锁定中（他人正在下单）两种情况，前端不区分。

---

## 3. 订单

### 3.0 订单状态

| status | 含义 |
|---|---|
| `0` | 待支付 |
| `1` | 已支付 |
| `2` | 已取消（超时关闭） |

接口统一返回数字，展示文案由前端映射。

### 3.1 提交订单

`POST /api/orders`　鉴权：✅

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `eventId` | long | 是 | 活动 ID |
| `seatId` | long | 条件必填 | 座位 ID，`mode=2` 时必填，`mode=1` 时不传 |

**响应**

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "orderNo": "7845129365720192512",
    "amount": 600.00,
    "expireTime": "2026-10-03T15:30:00",
    "eventName": "周杰伦2023嘉年华世界巡回演唱会-上海站",
    "eventAddress": "上海市徐汇区天钥桥路666号上海体育场",
    "seat": { "rowNo": 2, "colNo": 5 }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderNo` | string | 订单号，支付与查询均以此为键。雪花算法 ID 超出 JS 安全整数范围，故以字符串返回 |
| `amount` | decimal | 应付金额 |
| `expireTime` | datetime | 支付截止时间，前端据此渲染倒计时 |
| `eventName` | string | 活动名称 |
| `eventAddress` | string | 活动地址 |
| `seat` | object | `{rowNo, colNo}`，`mode=1` 时为 `null` |

---

### 3.2 取消订单 / 超时关单

`POST /api/orders/cancel`　鉴权：✅

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `orderNo` | string | 是 | 订单号，取自下单响应的 `orderNo` |

**响应**：`data` 为 `null`

订单状态由 `0-待支付` 转为 `2-已取消`，同时把座位或库存归还给活动。
订单不存在返回 `C404`，订单已是已支付/已取消返回 `C409`。

---

### 3.3 支付订单

`POST /api/pay`　鉴权：✅

**请求**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `orderNo` | string | 是 | 订单号，取自下单响应的 `orderNo`，原样回传 |

**响应**：`data` 为 `null`，前端提示「支付成功」

> 支付链接与扫码流程后期补充。

---

### 3.4 订单分页查询

`GET /api/orders`　鉴权：✅

**请求**（query string）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `orderNo` | string | 否 | — | 订单号精确匹配，传即单条查询 |
| `status` | int | 否 | — | 订单状态筛选：`0` / `1` / `2` |
| `page` | int | 否 | 1 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |

**响应**：`PageResult<OrderVO>`，按下单时间倒序。`records` 元素即 `OrderVO`：

```json
{
  "code": "A200",
  "message": "Success",
  "data": {
    "total": 1,
    "records": [
      {
        "orderNo": "7845129365720192512",
        "eventId": 2,
        "eventName": "周杰伦2023嘉年华世界巡回演唱会-上海站",
        "eventAddress": "上海市徐汇区天钥桥路666号上海体育场",
        "eventPrice": 600.00,
        "amount": 600.00,
        "status": 0,
        "seat": { "rowNo": 2, "colNo": 5 },
        "createTime": "2026-10-03T15:03:00",
        "expireTime": "2026-10-03T15:18:00",
        "payTime": null
      }
    ],
    "page": 1,
    "size": 10
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderNo` | string | 订单号 |
| `eventId` | long | 活动 ID |
| `eventName` | string | 活动名称 |
| `eventAddress` | string | 活动地址 |
| `eventPrice` | decimal | 活动票价 |
| `amount` | decimal | 应付金额 |
| `status` | int | 订单状态，见 [3.0](#30-订单状态) |
| `seat` | object | `{rowNo, colNo}`，`mode=1` 时为 `null` |
| `createTime` | datetime | 下单时间 |
| `expireTime` | datetime | 支付截止时间 |
| `payTime` | datetime | 支付时间，未支付为 `null` |

> 超时关单为异步处理，倒计时归零时状态不会立即变化，需以 `orderNo` 精确查询轮询确认。

