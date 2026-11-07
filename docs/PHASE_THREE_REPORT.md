历史阶段记录；最新版本、验收及范围以[首版交付报告](COMPLETION_REPORT.md)为准，本文未完成项可能已在后续补齐。

# 第三阶段实现与验收

2026-11-05，本机Windows/Docker Desktop Linux Engine 29.9.2。第三阶段本地实现与验收已完成，外部真实联调及远程CI仍待外部条件，不等同生产上线。

## 已编写与实际证据

| 能力 | 代码 | 实际验证 |
|---|---|---|
| PWA清单/静态缓存/离线提示 | 已实现，API不进入Cache Storage，订单库存不离线补交 | phase-three-pwa.log：1通过；完整浏览器含该用例 |
| 增强商品搜索 | 企业独立索引、数据库二次验证；关闭/故障降级数据库；管理员手动重建，最多5000商品 | 实际OpenSearch3.3.2查询、跨企业唯一商品拒绝；phase-three-ai-browser.log及search-fallback.log |
| 知识库 | 文本/Markdown切分800字、步长600；引用文档ID/标题/块序号；资料不足明确返回 | 真实PG测试、浏览器导入/引用通过 |
| pgvector | 独立向量库，企业条件与主库二次限定，维度过滤，最多20候选 | VectorContainerTest真实pgvector0.8.1通过；浏览器使用真实向量服务 |
| 文案草稿 | 仅PENDING_REVIEW，不自动发布 | 浏览器生成与数据库保存通过 |
| Spring AI | 1.1.8聊天/嵌入可配置适配，AI_ENABLED默认false；AI_DEMO明确固定演示 | ModelAdapterTest本机模拟HTTP通过；未真实调用外部模型 |
| 调用限制 | 15秒外层、3秒连接/10秒读取；单次网络不自动重试；每日UTC100模型调用额度预扣、失败不退 | 代码完成，真实PG额度边界通过（phase-three-quota-tests.log）、模拟阻塞模型15秒超时通过（phase-three-model-tests.log） |
| 监控 | 独立9090指标、Prometheus、Grafana预置面板、OTLP Collector | 指标up=1与Collector收到spans，见monitoring-check.json、traces.log；Grafana健康及面板自动配置日志 |
| 后端回归 | JUnit+真实PG/Rabbit/pgvector+HTTP契约 | phase-three-regression.log：33通过、0失败/错误/跳过 |
| 前端单元 | Vitest4.1.12 | phase-three-vitest.log：3通过 |
| 镜像/前端 | Java21、TypeScript、Vite | phase-three-backend.log、frontend.log、images.log通过；大chunk提示保留 |
| CI | 后端/前端/浏览器作业，固定Action版本、Node/Java | 本地等效检查；未连接GitHub仓库，远程未执行 |
| 浏览器 | 核心+AI/搜索+PWA完整验收 | phase-three-browser-regression.log：8通过，35.0秒 |
| 压测 | k6可复现会话/认证订单基线 | PHASE_THREE_PERFORMANCE.md；不代表业务吞吐 |

日志表中的简写均位于docs，前缀phase-three-。最终浏览器日志phase-three-browser-regression.log以本轮最终结果为准。

## 增强功能运行

默认infra/compose.yml仅五服务。保留.env，增强服务使用infra/compose.enhanced.yml及infra/compose.monitoring.yml，不替换核心PostgreSQL镜像和数据卷。

搜索设置SEARCH_URL=http://opensearch:9200；向量设置VECTOR_DATABASE_URL=jdbc:postgresql://vector:5432/knowledge、VECTOR_DB_USER=haiduo、VECTOR_DB_PASSWORD为自选密码。显式AI_DEMO=true启用固定演示；真实模型使用AI_ENABLED=true、AI_DEMO=false、AI_BASE_URL、AI_API_KEY、AI_CHAT_MODEL、AI_EMBEDDING_MODEL，模型名由用户配置，地址需HTTPS或localhost。真实模型可能产生费用，未提供凭据时保持禁用。

```sh
docker compose --env-file .env -f infra/compose.yml -f infra/compose.enhanced.yml --profile search --profile ai up -d --build --wait --wait-timeout 180
```

商品页“重建当前企业搜索索引”后增强查询；新商品不会自动进入索引，需要手动重建。PWA通过localhost或HTTPS使用，浏览器安装入口取决于设备与浏览器；操作系统安装交互未验证。仅缓存静态assets/清单/图标/离线页，不缓存敏感接口。

监控额外设置GRAFANA_PASSWORD，再合并-f infra/compose.monitoring.yml并加--profile monitoring。Prometheus http://localhost:9091，Grafana http://localhost:3001，管理员admin与自选密码。9090及Collector4318只在容器网络内，不经过前端代理。默认无监控时核心业务可运行。

Windows使用npm.cmd/npx.cmd可避免PowerShell脚本执行策略阻止npm.ps1；无需修改系统执行策略。Linux/macOS替换命令与卷路径，宿主平台未实际执行。

## 未验证外部依赖与边界

真实模型、Shopify、S3凭据缺失，均未声称真实联调成功。远程CI需要用户自己的GitHub仓库/权限，不自动上传或发布。搜索及向量演示关闭安全插件/仅本机暴露，仅适合本地学习；生产需要认证TLS及访问策略。

向量为派生索引：导入会写独立库，主库回滚时可能残留孤立向量，回答始终再次限定主库企业及文档，孤立数据不可用于回答；暂未做自动清理/重建。启用新向量库或更换嵌入模型后需重新导入资料；不自动混用旧向量。关闭向量库时使用主库数组相似度，此为基础降级而非pgvector。每企业最多50文档，每份12000字；不支持PDF/Office。固定演示嵌入为字符哈希，明确非真实语义模型。

监控仅本地学习面板、指标与trace接收，没有告警、长期trace存储或生产SLO。k6仅会话接口，不虚构业务性能。多规格/拆单/真实物流/实际结算及更完整模块划分等全项目边界继续保留，不能将阶段三本地交付解释为全功能生产验收。

依赖审计：初次Vitest3.2.4发现已知漏洞，已升级固定4.1.12并更新锁文件；官方npm审计0已知漏洞（phase-three-npm-audit.json），不代表不存在未知漏洞。

最后边界验收：关闭增强配置后，数据库搜索与核心订单可用，AI明确DISABLED（phase-three-core-only-check.log）。OpenAPI23条路径YAML解析通过，全部profile Compose config检查通过。远程GitHub CI与OS安装交互仍未执行。

最终清理：本轮api/web及搜索、向量、监控服务已停止，原数据库/Redis/RabbitMQ及所有数据卷保留。Grafana面板API实际读取2面板通过（phase-three-grafana-dashboard-check.json）；本地管理员密码位于忽略的.env。增强演示临时容器保留为停止状态，首次使用正式profile向量库需重新导入知识资料。
