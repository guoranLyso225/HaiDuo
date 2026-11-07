# 第三阶段性能实测（部分）

2026-11-05；Windows Docker Desktop Linux Engine 29.9.2；容器可见总内存7.621GiB，测试时五服务及OpenSearch并行运行。k6 1.4.2，5 VU、20秒，每次sleep0.2秒。仅GET /api/session/csrf，不访问业务记录，业务数据量不影响该用例，不代表订单或库存吞吐。

实际475请求，失败0/475，23.644289请求/秒，平均10.6ms、中位8.93ms、p95 19.12ms、最大89.21ms。证据phase-three-k6.log。无业务并发性能结论。

复现（根目录，先启动完整Compose）：

```powershell
docker run --rm -v D:/HaiDuo/infra/k6:/scripts:ro grafana/k6:1.4.2 run /scripts/session.js
```

Linux/macOS将卷路径替换为当前目录绝对路径，必要时设置BASE_URL为可访问地址。未在Linux/macOS宿主实际执行。

## 认证订单只读基线

同一Windows/Docker环境，增强搜索、向量、Prometheus、Grafana、Collector与五服务同时运行。当前企业已有60订单，每次GET /api/orders?size=20。5 VU、20秒、每轮sleep0.2秒；475个业务读取迭代，另有6个登录/会话/选择企业/数据准备请求，总481请求。错误0/481，聚合HTTP平均11.09ms，中位9.23ms，p95 20.42ms，最大63.59ms；这些聚合延迟包含准备请求，不伪称纯订单端点分位。未执行写入压力、容量极限或生产环境压测。

证据phase-three-k6-orders.log，脚本infra/k6/orders.js。运行需通过环境注入DEMO_PASSWORD，可选DEMO_USER及BASE_URL，禁止在命令或日志中输出密码。此数据规模不能外推到大规模卖家订单。
