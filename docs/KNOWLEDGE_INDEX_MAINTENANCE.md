# 知识库派生索引维护

## 使用方式

登录企业管理员，打开AI助手的知识库索引维护卡片，刷新状态后点击重建当前企业向量索引。未配置独立向量库时按钮禁用，说明使用主库检索、无需重建；其他角色显示仅管理员可维护。接口不会接受客户端tenant_id，租户由当前认证成员关系确定。

适用场景：独立向量库丢失或重建、导入事务失败遗留孤立向量、手工修复派生索引。操作只读取当前企业主库已有嵌入，完整替换该企业的独立索引。不会修改知识正文，不触发模型，不占AI调用额度；即使模型功能被关闭，只要独立向量库仍配置，也可修复现有索引。

这不是更换模型后的重新向量化。更换模型或嵌入算法仍须重新生成全部知识片段嵌入，不可把旧模型向量当新模型使用。自动重新嵌入、定时清理未实现。

## 一致性与失败恢复

- 主库按企业使用事务级advisory lock，导入和索引重建互斥，避免重建快照遗漏并发导入。没有对其他企业加全局锁。
- 独立pgvector库内的删除与写入在一个事务中执行。读请求看到提交前或提交后的完整索引，数据库写入失败会回滚，不留下只完成一部分的替换。
- 重复重建以主库为准，不新增重复记录，其他企业不受影响；主库当前没有片段时，只清空当前企业孤立向量。
- 主库与独立向量库没有分布式事务。网络在提交确认时中断可能无法立即确定结果；接口返回未确认成功，可安全重试。重建后的审计写入失败也不会改变知识正文，重新执行可恢复派生索引与审计记录。
- 导入主库失败仍可能残留独立向量；回答继续以主库企业和文档二次验证，重建负责清理残留，不声称已实现自动清理。

实现依据：[Spring TransactionTemplate](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)、[PostgreSQL事务级advisory lock](https://www.postgresql.org/docs/17/explicit-locking.html#ADVISORY-LOCKS)。

## 接口

| 请求 | 权限与结果 |
|---|---|
| GET /api/ai/knowledge/index | ADMIN；enabled、chunks、basis，片段数来自当前企业主库 |
| POST /api/ai/knowledge/index/rebuild | ADMIN+CSRF；indexedChunks、modelCalls=0、basis；未配置向量库503，向量库操作未确认成功502 |

不支持传入其他企业ID，正文内容/向量不返回给维护接口调用方。每次重建记录KNOWLEDGE_INDEX_REBUILD审计；重复操作可有多条真实操作审计，向量内容保持幂等。

## 实际验收（2026-11-05）

- `vector-maintenance-backend.log`：完整41项后端真实集成通过，零失败/错误/跳过；新增测试真实pgvector重建10次、清理孤立向量、不影响另一企业、数据库约束中途失败恢复原索引、空来源清理。
- `vector-maintenance-browser.log`：全量13项浏览器通过，55.8秒；包含页面重建/来源检索、另一企业重建、运营账号GET/POST拒绝、旧核心流程及PWA/资源失败恢复。
- `vector-maintenance-vitest.log`：4项通过；`vector-maintenance-web-build.log`类型检查/构建通过；镜像构建及启动见vector-maintenance-images.log和vector-maintenance-start.log。
- OpenAPI YAML实际解析32条路径（vector-maintenance-openapi.log）。外部模型/Shopify/S3仍未真实联调。

第一次故障注入测试误以为重复ID会报唯一键错，但索引使用upsert，重复属于正常更新；已改为真实CHECK约束注入中途失败后通过。早期失败日志保留vector-maintenance-tests-first.log，不计为通过。
