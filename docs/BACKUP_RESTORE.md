# 本地备份与隔离恢复验收

## 范围与实际结果

2026-11-06在Windows PowerShell、Docker Desktop Linux Engine29.9.2上实际执行。默认Compose本地存储模式：PostgreSQL17.6数据库和`/data/files`命名卷作为一组备份。API短暂停止以暂停本应用写入，归档完成后自动恢复；假定没有其他程序直接写库/写文件。不要把该假定当成多系统生产环境的一致性保证。

完整演练实际通过：33张public表行数一致、19个对象文件内容SHA256一致、两个备份归档SHA256一致、库存非负与预占约束成立；恢复数据库和恢复文件卷上的隔离API可以登录、选择企业、读取订单，且授权下载图片与原文件哈希一致。最终耗时20.10秒是本次小数据量本地演练总时长，不是生产RTO或RPO。表行数检查不等同逐字段业务对账。

证据：[脱敏结果](backup-restore-verification.json)、`backup-restore-drill.log`、`backup-source-auth-check.log`、`backup-source-core-check.log`。最终原始dump、文件归档与含企业/对象ID的manifest保留在忽略的`backups/acceptance-final-20261106/`，不提交。源库/源卷未被恢复覆盖。`backup-safety-check.log`另验证拒绝覆盖已有备份及拒绝路径穿越RunId。

## 运行

先从根目录启动既有核心服务，保留原有.env和.demo-password：

```powershell
docker compose --env-file .env -f infra/compose.yml up -d --no-build --wait --wait-timeout 90
./infra/verify-backup-restore.ps1 -RunId drill-20261106-001
```

每轮必须使用新RunId；脚本拒绝覆盖备份目录、同名恢复容器及文件卷。操作会短暂停API，请避开正在编辑/提交订单的时间。脚本支持Windows PowerShell，并为Linux/macOS PowerShell的`docker`命令提供兼容调用；后两种宿主尚未实测，不把CI配置当Linux执行证据。

脚本依次：

1. 检查现有PG/API、本地文件命名卷与独立目标名称。
2. 暂停源API；采集全部public表行数；`pg_dump -Fc --no-owner --no-acl`；归档对象文件及其SHA256；生成manifest。
3. 恢复源API。使用`docker cp`传输二进制dump，避免Windows PowerShell文本重定向损坏归档。
4. 在全新容器中以`pg_restore --exit-on-error --single-transaction`恢复；没有使用`--clean`，不指向原库。
5. 校验归档、表行数、文件哈希及库存约束。
6. 创建独立恢复文件卷；运行恢复API，随机绑定127.0.0.1本地端口；使用恢复的demo1.admin账号登录、选择企业、读订单。
7. 恢复API禁用消息、AI、搜索、向量和demo初始化；Redis使用独立会话namespace，避免与源API会话混用。完成或失败后停止恢复容器，保留现场/归档和恢复文件卷。

归档可用于将来制定恢复计划，但本脚本只自动恢复到隔离目标。不会自动将恢复结果替换源服务。不要对已有生产库直接运行`pg_restore`；切换计划应包含停写、验证、密钥、回滚和明确授权。

## 未覆盖内容与运维边界

- 备份未加密，只保留本机，且含账号密码哈希、业务及可能的客户数据。应按敏感数据管理；本脚本未实现加密、异地保存、保留策略或定时任务。
- 不备份.env、密码明文、Shopify加密主密钥、S3凭据或数据库集群角色；实际灾难恢复须有独立安全的密钥保管方案。更换加密主密钥可能导致原平台令牌无法解密。
- 不包含Redis会话和RabbitMQ卷。恢复API演练禁用消息，不能据此宣称生产消息系统灾难恢复已验收。生产需评估Outbox/ProcessedEvent与消息残留的恢复策略，防止丢失或重复副作用。
- 不备份S3桶；检测到S3后端会拒绝此本地演练。S3需独立版本/备份恢复验证。
- OpenSearch和独立向量库是派生索引，应根据恢复后的主库重建；知识库已有向量可以重建，但跨模型重新嵌入仍未实现。
- 这是默认本地演示环境恢复，不是完整生产容灾、持续归档/PITR、多机高可用或压力验收。
- 恢复端口只绑定本机，测试容器在结束后停止；临时Redis会话会按应用TTL过期。脚本不清理既有业务卷、不自动删除恢复现场。

## CI与后续

工作流已在浏览器验收后加入同一演练，并设置作业超时和只读仓库权限。YAML语法本地解析通过；远程Actions仍缺目标仓库和执行条件，未运行。不要将backups目录上传为CI artifact。

官方依据：[pg_dump17](https://www.postgresql.org/docs/17/app-pgdump.html)、[pg_restore17](https://www.postgresql.org/docs/17/app-pgrestore.html)。


2026-11-09核实：GitHub Ubuntu runner上的恢复步骤已通过，提交511f1a4、运行42472427358，证据见remote-ci-acceptance.json。上方远程未执行记录为历史；macOS宿主与生产恢复仍未验证。
