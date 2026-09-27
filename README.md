# ticket-system 高并发票务系统

## 技术栈

**后端**：Java 21、Spring Boot 3.5、Maven 多模块、Spring Security + JWT（JJWT）、MyBatis-Plus、Redis（Redisson）、RabbitMQ（延迟消息插件）、AWS S3 SDK（MinIO）、MapStruct、Lombok

**前端**：Vue 3、TypeScript、Vite、pnpm、Tailwind CSS 4、shadcn-vue（reka-ui）、Pinia（持久化插件）、Vue Router、vee-validate + zod、axios

**基础设施**：Docker Compose — MySQL 8、Redis 7、RabbitMQ 4.1、MinIO

## 项目结构

```
├── ticket-common/    # 公共基础：Result 响应封装、异常、实体；统一声明技术栈依赖
├── ticket-auth/      # 认证模块
├── ticket-event/     # 演出/赛事模块
├── ticket-booking/   # 购票/订单模块（依赖 auth、event）
├── ticket-boot/      # 启动模块：唯一可运行入口，端口 8600
├── frontend/         # Vue 3 前端，端口 5600，/api 代理到后端 8600
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

`schema.sql` / `seed.sql` 仅在 MySQL 数据卷首次初始化时执行。种子账号：`13800000001` / `Aa123456`。

### 后端（Maven 多模块，使用 wrapper）

```bash
./mvnw verify                          # 构建全部模块并运行测试
./mvnw -DskipTests package             # 跳过测试打包
./mvnw spring-boot:run -pl ticket-boot # 启动应用，端口 8600
./mvnw test -pl ticket-boot -Dtest=TicketSystemApplicationTests   # 运行单个测试
```

Windows 下使用 `mvnw.cmd`。配置位于 `ticket-boot/src/main/resources/application.yml`。

### 前端

```bash
cd frontend
pnpm install
pnpm dev       # 开发服务器，端口 5600，/api 代理到 localhost:8600
pnpm build     # 类型检查（vue-tsc）+ 生产构建
```
