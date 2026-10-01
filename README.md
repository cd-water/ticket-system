# Ticket-System 高并发票务系统

## 项目介绍

高并发票务系统，支持抢票与选座双模式购票，覆盖下单、支付、超时关单全链路，提供认证鉴权与缓存限流基础能力，核心解决热点并发下的防超卖与消息可靠性。

## 技术栈

**后端**：Java 21、Spring Boot 3.5、Maven 多模块、Spring Security + JWT（JJWT）、MyBatis-Plus、Redis（Redisson）、RabbitMQ、AWS S3 SDK（MinIO）、MapStruct、Lombok

**前端**：Vue 3、TypeScript、Vite、pnpm、Tailwind CSS 4、shadcn-vue（reka-ui）、Pinia（持久化插件）、Vue Router、vee-validate + zod、axios

**基础设施**：Docker Compose — MySQL 8、Redis 7、RabbitMQ 4.1、MinIO

## 核心设计

- **抢票秒杀**：基于 Redis Lua 单脚本原子合并“一人一单校验 + 库存预扣减”，配合 DB 条件更新双层兜底防超卖
- **选座锁定**：基于 Redis SET NX EX 原子完成“检测-抢占-限时”座位租约，TTL 到期自动释放，杜绝“一座多人”
- **可靠消息**：Outbox 模式（本地消息表）消除“业务已落库、消息未发出”的宕机丢失窗口 —— 超时关单、支付短信随业务同事务落库，中继轮询到期投递保证至少一次，消费端 CAS / SETNX 幂等去重
- **认证方案**：采用 JWT 实现双 Token 机制，短 Access 无状态支撑高并发鉴权，长 Refresh 存 Redis 管控会话
- **缓存限流**：采用 Cache-Aside 缓存策略，针对性解决缓存击穿、缓存穿透、缓存雪崩三大经典问题；座位图 Bitmap 缓存扛高频刷新；基于 Redis + Lua 脚本实现令牌桶限流，把守秒杀入口
- **支付竞态**：支付回调与超时关单基于订单状态机 + CAS 乐观锁仲裁，先到先得、输家自动分流，杜绝双重处理

## 项目结构

```
├── ticket-common/    # 公共基础：Result 响应封装、异常、实体；统一声明技术栈依赖
├── ticket-auth/      # 认证模块
├── ticket-event/     # 演出/赛事模块
├── ticket-booking/   # 购票/订单模块（依赖 auth、event）
├── ticket-boot/      # 启动模块：唯一可运行入口，端口 8600
├── frontend/         # Vue 3 前端，端口 5600，/api 代理到后端 8600
├── docs/             # 设计文档：ER 图、架构图、时序图
└── infra/
    ├── docker/       # docker-compose：MySQL / Redis / RabbitMQ / MinIO
    ├── db/           # 建表 schema.sql、初始数据 seed.sql
    └── rabbitmq/     # 延迟消息插件
```

模块依赖方向：`ticket-boot → ticket-booking → { ticket-auth, ticket-event } → ticket-common`

## 命令

### 基础设施（后端启动前必须先启动）

```bash
cd infra/docker
cp .env.example .env    # 已有 .env 则跳过
docker compose up -d    # MySQL :3306、Redis :6379、RabbitMQ :5672/:15672、MinIO :9000/:9001
```

`schema.sql` / `seed.sql` 仅在 MySQL 数据卷首次初始化时执行。

### 后端（Maven 多模块，使用 wrapper）

```bash
./mvnw verify                          # 构建全部模块并运行测试
./mvnw -DskipTests package             # 跳过测试打包
./mvnw spring-boot:run -pl ticket-boot # 启动应用，端口 8600
./mvnw test -pl ticket-boot -am -Dtest=TicketSystemApplicationTests   # 运行单个测试（-am 连带构建依赖模块）
```

Windows 下使用 `mvnw.cmd`。配置位于 `ticket-boot/src/main/resources/application.yml`。

### 前端

```bash
cd frontend
pnpm install
pnpm dev       # 开发服务器，端口 5600，/api 代理到 localhost:8600
pnpm build     # 类型检查（vue-tsc）+ 生产构建
```
