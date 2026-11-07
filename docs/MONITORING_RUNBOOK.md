# 监控告警与排查

## 规则及口径

| 规则 | 条件 | 等待时间 | 含义 |
|---|---|---|---|
| HaiDuoApiUnavailable | `haiduo`抓取目标up=0，或目标指标完全缺失 | 5分钟 | 可能是应用、Actuator或监控网络异常；不直接等同业务已中断 |
| HaiDuoApiHighErrorRate | 5分钟窗口内5xx比例>5%，请求速率>=1/s | 持续10分钟 | 业务HTTP服务端错误偏高；排除Actuator与/api/health；4xx不计服务端错误 |

每15秒抓取及评估。阈值是本地演示初始选择，不是经过生产流量校准的SLO。低流量抑制用于减少偶发单次失败的比例噪声，因此低流量错误需结合日志人工查看。没有请求时不除零、不报警；目标失联另由第一条规则处理。按job聚合，适用于当前单API实例配置，多副本上线须重新审视可用性口径。

规则可在Prometheus查看（启动monitoring profile后）：http://localhost:9091/alerts 。目前没有Alertmanager、通知接收人或发送渠道，只会在Prometheus显示pending/firing。没有配置邮件/企业微信等，也没有发送外部通知。

## 检查与复现

从项目根目录运行。Docker须启动；命令使用固定的Prometheus镜像。测试使用合成序列，不停业务服务，不等待真实5/10分钟。

Windows PowerShell：

```powershell
docker run --rm --entrypoint promtool -v "${PWD}/infra/monitoring:/etc/prometheus:ro" prom/prometheus:v3.7.3 check config /etc/prometheus/prometheus.yml
docker run --rm --entrypoint promtool -v "${PWD}/infra/monitoring:/rules:ro" -w /rules prom/prometheus:v3.7.3 test rules alerts.test.yml
```

Linux/macOS将`${PWD}`保留为shell环境变量即可使用相同命令；对应宿主未实测。CI已加入以上检查，但GitHub远程工作流尚未执行。

已有核心五服务时，仅启动Prometheus，不改变API配置：

```sh
docker compose --env-file .env -f infra/compose.yml -f infra/compose.monitoring.yml --profile monitoring up -d --no-deps prometheus
```

完整监控/trace启动仍按阶段三文档。修改规则后重新启动Prometheus使其读取配置，不要求开启公开热重载管理接口。

## 告警后如何处理

1. 检查`docker compose --env-file .env -f infra/compose.yml ps`，区分API退出、健康检查失败与仅指标抓取失败。查看Prometheus Targets中的lastError。
2. 查看API、PostgreSQL、Redis、RabbitMQ状态与最近日志。使用请求关联ID排查，不复制密码、令牌或完整客户地址到日志/工单。
3. 不要通过清库、删除卷或盲目重复订单写入消除故障。请求结果不明时先查原订单/幂等键；消息失败通过有权限的同步任务页面重放，勿绕过幂等记录。
4. 恢复依赖后查看up是否回到1、告警是否恢复、订单/库存是否一致；有业务错误则按请求ID定位并修复。

本轮实际结果：规则文件5组测试SUCCESS，配置检查成功，运行中2条规则health=ok/state=inactive，API up=1、HTTP指标25条序列。证据：`alerts-rule-tests.log`、`alerts-config-check.log`、`alerts-runtime-check.json`。未停API验证真实告警发送，未配置外部通知；不可描述为生产值班体系完成。

官方依据：[告警规则](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)、[规则单元测试](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/)。
