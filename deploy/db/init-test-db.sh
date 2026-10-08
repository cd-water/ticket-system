#!/bin/bash
# 测试库必须独立于开发库：测试夹具会清空 t_order / t_payment / t_outbox 并重置库存与座位，
# 与开发库共用 schema 时，一次 ./mvnw verify 就会连带清掉开发数据。
# 这里复用 01-schema.sql / 02-seed.sql，不复制表定义。
set -euo pipefail

TEST_DB=ticket_system_test

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e \
  "CREATE DATABASE IF NOT EXISTS ${TEST_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${TEST_DB}" < /docker-entrypoint-initdb.d/01-schema.sql
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${TEST_DB}" < /docker-entrypoint-initdb.d/02-seed.sql

echo "${TEST_DB} 初始化完成（复用 01-schema.sql / 02-seed.sql）"
