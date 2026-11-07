# 阶段二交付与验证

阶段二本地实现和验收已完成；外部真实联调未完成。2026-11-05，Windows 本机 Docker Desktop Linux 引擎。阶段一历史结果见 PHASE_ONE_REPORT.md。本报告区分实现、自动化模拟合约和真实外部联调，不代表全项目完成。

## 已实现

- Redis 服务端会话；业务变更、审计、Outbox 同事务。发布确认、mandatory/returns、持久消息；发布最多5次，退避2/4/8/16秒后死亡。消费者事务性去重，最多3次，失败进入持久死信队列。
- 同步任务页面：内部投递与平台同步分开标记；管理员可重放 DEAD 或消费失败事件。已处理和处理中事件不能重放；运营仅查看，跨企业对象404。
- MOCK连接器固定演示订单；Shopify GraphQL Admin API 2026-10订单读取、游标分页、429/5xx与THROTTLED有上限重试；OAuth一次性state、HMAC、只读订单权限、可刷新离线令牌、AES-256-GCM加密保存与轮换。
- Webhook原始字节HMAC、服务端店铺绑定、投递/事件双重去重；只保存订单标识和摘要，事务写入收件箱及Outbox后202。后台重新读取权威订单，以updatedAt拒绝旧事件，避免乱序覆盖。
- 订单同步保留历史采购/汇率快照；已预占订单取消释放库存；已发货取消不擅自退库。部分履约、已预占后外部发货或商品变更标记人工核对，阻止继续预占/发货。外部已履约订单不重复扣本地库存。
- 商品PNG/JPEG上传，真实解码、2MB/4096像素边界；本地持久卷默认实现、S3适配器。租户UUID目录/对象前缀，图片通过授权接口读取且no-store，无公开静态文件目录。
- 默认五服务Compose：web/api/PostgreSQL/Redis/RabbitMQ；Nginx动态Docker DNS，API容器替换后无需重启前端代理。基础镜像固定版本和摘要。

## 实际证据

| 验证 | 结果 | 证据 |
|---|---|---|
| Maven真实PG/RabbitMQ及契约回归 | 29通过，0失败/错误/跳过 | phase-two-regression.log |
| 前端类型检查/生产构建 | 通过，有大chunk提示 | frontend-build.log、compose-web-build.log |
| 前后端镜像构建 | 通过 | compose-build.log、compose-web-build.log、compose-api-build.log |
| 五服务健康启动 | 通过 | compose-status.log；Compose up --wait |
| 消费失败回滚/人工重放 | 通过 | stage-two-recovery.log；verify-stage-two.ps1 |
| 停止RabbitMQ、实际替换API容器再恢复 | 通过，processed_event和完成任务各1 | stage-two-recovery.log |
| Redis会话/本地图片重启持久化 | 通过 | stage-two-recovery.log |
| 浏览器最终回归 | 6通过，29.8秒 | phase-two-browser.log |

测试中修复：店铺创建缺少审计/Outbox；汇率参数校验；非法存储配置不应静默退回本地；Nginx旧IP导致502；测试baseURL、异步刷新等待；PowerShell原生命令进度输出及同名函数递归。失败日志不作为通过证据，最终通过日志记录在表中。

## 模拟与外部依赖

- MOCK固定样例明确标注，不调用真实店铺。Shopify OAuth/刷新、分页、限流、Webhook由固定模拟HTTP与真实PostgreSQL测试；没有真实Shopify凭据，未宣称真实平台联调成功。
- S3 SDK签名及隔离前缀通过本地HTTP契约；没有真实S3凭据，实际云端权限、存储兼容性未验证。本地存储已实际上传/授权读取/重启验证。
- 真实Shopify需要应用client ID/secret、店铺授权、可访问的HTTPS回调及Webhook地址、32字节Base64加密密钥，可能涉及受保护客户数据审批。不会自动开通账号、付费或修改真实店铺。
- 真实S3需要bucket、region、access/secret、可选兼容endpoint与相应权限；费用和云资源由用户配置。

## 边界与后续

Shopify仅请求read_orders权限，默认可读取平台允许的最近60天订单；未申请read_all_orders历史权限。每次最多100页×5订单，每订单最多50行，超过时明确失败并保留重放入口，不静默截断；尚无增量时间窗口、自动Webhook订阅管理、全历史批量导入、履约写回和退款明细结算。订单当前读取currentSubtotalPriceSet商品净收入，运费/税及平台费用需按已定义成本另录，不构成会计净利润。同步配置汇率只影响后续新订单。过期/撤销授权导致任务失败，需要重新授权后重放。部分履约人工核对尚无自动拆单修复流程。

消费者成功但发布确认状态丢失通过重置PENDING故障注入验证；物理重启测试验证已落库待投递事件恢复，不声称精确命中了发布确认的微秒窗口。仅保留最近100事件/任务，未实现长期归档和清理；替换图片保留旧授权对象，尚无孤儿回收。HTTP每次请求完整响应限10秒、最多3次，流式超过2MB立即取消；不声称无限网络恢复。此项目不是生产就绪声明。

下一优先级：真实凭据可用时执行Shopify/S3真实验收；进入阶段三前审阅剩余边界，再完成搜索、AI、PWA、监控、CI和实际压测。阶段三本轮未开始。

官方参考：[Shopify授权](https://shopify.dev/docs/apps/build/authentication-authorization/authenticate-standalone-apps)、[Webhook验签](https://shopify.dev/docs/apps/build/webhooks/verify-deliveries)、[订单查询](https://shopify.dev/docs/api/admin-graphql/2026-10/queries/orders)、[Rabbit发布确认](https://docs.spring.io/spring-amqp/reference/amqp/template.html)、[S3 SDK](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html)、[Nginx代理解析](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)。

验收结束已停止api/web临时服务，数据库、Redis、RabbitMQ和文件卷保留；compose-status.log记录停止前的健康状态。无后台构建或测试等待进程。
