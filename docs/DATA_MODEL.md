# 数据模型与约束

所有业务时间timestamptz/UTC，金额NUMERIC(19,4)，汇率NUMERIC(19,8)，实际确认贡献NUMERIC(19,2)。Java使用BigDecimal，接口金额/汇率返回十进制字符串。HALF_UP，费用折算4位、最终贡献2位。

| 概念 | 表/模型 | 范围与关键约束 |
|---|---|---|
| Tenant | tenant | 企业UUID |
| User | app_user | 全局登录账号、BCrypt，仅demo自动初始化 |
| Membership | membership | username+tenant，服务端每次验证 |
| Role/Permission | Role枚举及能力集合、membership.role CHECK | ADMIN/OPERATIONS/WAREHOUSE/FINANCE，固定策略；不伪造自定义权限编辑 |
| Store | store | tenant+name唯一；MOCK或SHOPIFY |
| Product/Sku | product/sku | tenant+code唯一，SKU外键绑定同企业商品；多SKU独立成本库存 |
| Warehouse | warehouse | tenant+id及tenant+name唯一 |
| Inventory | inventory | tenant+SKU+warehouse，on_hand/reserved/可用量约束 |
| InventoryReservation | inventory_reservation | tenant+order+SKU唯一，仓库及RESERVED/RELEASED/CONSUMED |
| InventoryMovement | inventory_movement/stock_receipt | 订单库存流水、入库幂等request_key |
| Order/OrderItem | sales_order/order_item | tenant+store+external_id；整单仓库、金额及采购快照 |
| Shipment | shipment | tenant+order唯一，版本化物流状态与手工运单 |
| ShipmentEvent | shipment_event | tenant+event_key、tenant+shipment+version唯一；MOCK/applied标识 |
| Fee | order_fee | tenant+order+kind唯一，原币金额/币种/汇率/来源/生效时间 |
| ExchangeRateSnapshot | exchange_rate_snapshot | 独立不可变历史行；订单/费用复合租户FK；V20防重复冲突产生多余快照 |
| ProfitConfirmation | profit_confirmation | tenant+request_key、tenant+order+version唯一；不可变人工实际贡献，不覆盖预估 |
| SyncJob/Outbox/Processed | sync_job/outbox_event/processed_event | tenant+event唯一，发布确认与事务消费去重 |
| AuditLog | audit_log | tenant限定，不存密码、token或完整地址；与业务/Outbox同事务 |
| PlatformConnection/Webhook | platform_connection/webhook_inbox | shop域名唯一、加密令牌；企业店铺绑定与投递/事件去重 |
| StoredFile | stored_file | tenant+file复合FK，授权读取，本地/S3企业UUID前缀 |
| Knowledge/AI | knowledge_document/knowledge_chunk/ai_usage/copy_draft | 主库企业限定；向量为派生索引，来源须回查主库；额度独立事务 |

库存按订单锁和有序SKU条件更新，多商品任一不足全部回滚。订单导入按企业/店铺/订单分组排序，快照捕获使用事务级advisory lock避免ON CONFLICT前重复建快照。费用与实际确认均先锁订单，保持核对时读取一致。

独立汇率快照和实际确认版本禁止UPDATE/DELETE。测试临时库保留有快照的企业直到容器销毁；不能用删除历史快照来通过测试或重置演示库存。业务更正追加新版本，历史记录保留。

金融口径：净收入×当时汇率－采购成本－实际录入的运费/平台费，不含广告、工资、固定开支、税。预估未录费用按0；人工实际确认要求付款、整单发货、无退款且两项费用明确录入（允许0）及成本完整，须核对说明，不宣称自动对账。

原始首版不要求复杂拆单/自动退款对账；平台部分履约或预占后明细变化标记核对，阻止继续预占/发货，不擅自退库。

## WooCommerce连接（V21/V22）

store.platform新增WOOCOMMERCE。woo_connection以(tenant_id,store_id)为主键并复合外键引用store；base_url全局唯一，防止同一来源在不同企业重复绑定。key_cipher、secret_cipher、webhook_cipher均为AES-GCM密文；加密主密钥只来自环境。

Webhook复用webhook_inbox和Outbox：kind=WOO_WEBHOOK，order_gid列作为历史列名，此时存储Woo数字订单ID字符串；event_id为woo:SHA256(raw-body)，唯一范围含tenant/store/topic，delivery_id另有唯一约束。不保存完整原始客户payload。源订单更新时间相同但业务字段变化时重新应用权威状态，完全相同则无副作用；旧时间版本不覆盖。