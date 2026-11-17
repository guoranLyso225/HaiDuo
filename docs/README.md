# 文档导航

首次使用从[使用手册](USER_GUIDE.md)开始。

后续功能提案见[可用性及分级审批设计](USABILITY_DESIGN.md)。该文档中的审批、编辑和冲销规则尚未实现，不能作为现有功能宣传。

| 目的 | 文档 |
|---|---|
| 本地环境与启动 | [环境配置](LOCAL_SETUP.md)、[项目README](../README.md) |
| 当前进度与待办 | [实施记录](IMPLEMENTATION_PLAN.md)、[剩余工作](REMAINING_WORK.md) |
| 验收结论 | [交付报告](COMPLETION_REPORT.md)、[验收矩阵](FINAL_ACCEPTANCE_MATRIX.md) |
| 系统设计 | [架构](ARCHITECTURE.md)、[数据模型](DATA_MODEL.md) |
| 接口 | [接口说明](API.md)、[OpenAPI](openapi.yaml) |
| 本地平台联调 | [Woo接入](WOOCOMMERCE_INTEGRATION.md)、[建店说明](WOOCOMMERCE_LOCAL.md) |
| 运维 | [备份恢复](BACKUP_RESTORE.md)、[监控排查](MONITORING_RUNBOOK.md) |
| 知识库维护 | [索引维护](KNOWLEDGE_INDEX_MAINTENANCE.md) |

PHASE_*及IMPLEMENTATION_HISTORY为历史阶段记录，不代替当前验收结论。JSON保留实际检查和验收证据，不含真实连接凭据。

2026-11-17整理：172个原始.log/.err文件已从本目录移出，归档于本机`backups/docs-cleanup-20261117/raw-logs.zip`，同目录manifest.json记录逐文件SHA256。已逐项校验归档内容后移除原文件。历史报告里的日志文件名可在该归档查询；归档受Git忽略，不随仓库发布。没有重新执行历史测试。
