# 阶段一业务闭环验收报告

日期：2026-11-04。结论：阶段一“登录、租户权限、商品、CSV订单导入、库存预占、发货、利润和看板，形成可演示业务闭环”的交付目标已在下述首版边界内验证通过。此结论不代表全项目需求或生产上线验收通过。

## 已实现并实际验证

- 数据库账号、BCrypt、Redis服务端会话、CSRF、安全Cookie默认值、企业选择与四角色。
- 本企业成员创建/角色调整、最后管理员保护；跨企业成员修改拒绝。
- Product/SKU创建、成本、模拟店铺、平台商品映射和审计。
- CSV文件/文本导入、UTF-8 BOM、店铺/SKU/仓库校验、重复导入10次仅1订单/明细。
- 多仓库存、幂等入库、独立预占记录、取消、整单发货与运单、统一库存流水。
- 采购与汇率快照、原币费用/汇率/来源/生效时间，预估人民币贡献利润及398.00样例。
- 销售/订单/预估贡献/低库存/库存不足异常订单，店铺/时间筛选与ECharts趋势；金额汇总来自数据库。
- 商品/订单/库存服务器分页与总数，用户/企业限定的TanStack Query缓存；API no-store。
- 桌面侧栏、手机抽屉、浏览器时区显示、离线提示和订单/库存写入拦截。
- Spring Data JPA目录读取与Modulith catalog边界，库存事务仍用显式SQL；日志携带请求关联标识。

## 验收证据

| 检查 | 实际结果 | 证据 |
|---|---|---|
| 后端打包 | BUILD SUCCESS | docs/backend-build.log |
| 前端类型/生产构建 | 通过，有大chunk提示 | docs/frontend-build.log |
| Testcontainers最终测试 | 9通过、0失败/错误/跳过 | docs/testcontainers.log；其中6库存、1利润、2关联标识 |
| 浏览器最终回归 | 5通过，43.9秒 | docs/browser-tests.log |
| 权限与隔离HTTP脚本 | 通过 | infra/verify-auth.ps1、infra/verify-order-isolation.ps1的实际执行输出 |
| 并发库存 | 200不同订单、20工作线程，成功100，其他库存不足 | InventoryContainerTest继承的真实PostgreSQL测试 |
| 整单回滚、重复取消/发货、跨仓 | 通过 | 同上，所选仓不足不借用另一仓100库存 |
| 入库响应丢失 | 后端成功后浏览器断响应，刷新重试duplicate=true且仅增加10 | core.spec.ts |
| 空库初始化及重复启动 | 10迁移、2企业、8成员、2SKU、2订单；改为97的库存重启仍97 | 独立haiduo_phase1_verification数据库的实际SQL输出 |
| Redis会话恢复 | API重启后原Cookie恢复用户和已选企业 | 本轮实际HTTP验证，临时cookie文件已删除 |
| 外币费用 | 10USD×7.2=72CNY，原币/汇率/来源可读取 | 本轮实际HTTP验证 |
| 依赖在线审计 | 0已知漏洞 | docs/npm-audit.json，不代表不存在未知漏洞 |

Testcontainers使用PostgreSQL17.6和动态端口，不依赖开发数据库迁移，不使用内存数据库。浏览器使用Playwright1.56.1与本机Chromium1161显式路径；未宣称已验证所有浏览器。

## 首版边界与尚未完成

- CSV每订单一行、单履约仓库、整单发货；复杂拆单、多行订单和多规格编辑未实现。
- 所有贡献利润明确为ESTIMATED；未实现结算锁定/不可变实际结算版本，不冒充实际利润。未录费用按0预估，缺少历史采购快照的订单排除。
- 商品图片与对象存储留待阶段二存储适配器；当前没有伪造图片上传能力。
- 店铺和承运商为明确标记的模拟/手工能力。Shopify、Webhook、物流轨迹、RabbitMQ/Outbox均未完成。
- JPA已用于目录，Modulith已建立catalog边界；根包订单/权限控制器依赖仍需进一步模块拆分，不能宣称完整架构治理完成。
- 汇率快照当前内嵌订单/费用；尚无独立ExchangeRateSnapshot表。阶段二SyncJob/OutboxEvent/ProcessedEvent尚未实现。
- 图片、完整英文资源、完整成员邀请/密码找回、生产运维/部署、审计保留策略及跨浏览器全面测试未完成。
- PWA、搜索/AI、监控、k6和完整CI在阶段三，未开始；没有真实模型输出或性能报告。

## 启动、演示与服务清理

完整操作见README.md，接口说明见API.md和核心OpenAPI契约openapi.yaml，模型与边界见ARCHITECTURE.md。演示账号仅demo profile启用，凭据来自环境变量；开发依赖密码只保存在忽略的.env。

验收后停止临时API、Vite及独立种子验证API；PostgreSQL/Redis开发容器保留。独立验收数据库保留作为证据，不清空现有用户数据。没有部署或修改真实平台数据。

## 下一阶段

阶段二尚未开始。按原顺序应先实现Transactional Outbox/RabbitMQ、同步任务与可靠重试，再接Shopify/Webhook和文件存储；真实平台/S3需要凭据。后续阶段完成前不得将本报告扩展解释为全项目完成。
