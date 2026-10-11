# PackPlan 部署、升级与恢复 / Deployment

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

1. Python3.11+执行`python3 scripts/init-env.py`，生成随机私有`.env`；不要提交。Docker Compose v2执行`docker compose config --quiet`及`docker compose -p packplan-local up -d --build --wait --wait-timeout 240`。
2. 默认仅绑定127.0.0.1:8132。数据库与后端无宿主机端口。Compose按MySQL健康→后端健康→前端顺序启动；两应用容器使用非root账号。
3. 空库Flyway V1创建身份目录，V2创建方案、箱件、布局快照、历史编辑人与复核事件；应用只校验结构。首次管理员密码由外部注入，重启不覆盖目录或业务。
4. 对外部署前取得书面商业授权；配置可信HTTPS反向代理并设`COOKIE_SECURE=true`，控制网络访问、定期备份与恢复演练。内置MySQL使用隔离网络和TLS；外部数据库应验证TLS身份和可信CA。不要绕过证书校验。
5. 升级先备份并停止写入；保存当前镜像版本，构建新镜像，运行Flyway版本迁移。不要编辑执行过的SQL。回退须旧镜像加可信备份恢复到新实例，不直接覆盖现库。
6. 备份：`python3 scripts/backup.py --project packplan-local --output /private/tmp/packplan-backup.zip`。该工具暂停指定后端写入，读取内置MySQL逻辑备份与SHA256清单，创建0600文件后重启后端；恢复检查包成员与摘要。
7. 恢复：新私有配置用不同WEB_PORT，`python3 scripts/restore.py /private/tmp/packplan-backup.zip --project packplan-restored --env-file /private/tmp/packplan-restored.env`。只接受可信备份，只创建全新项目、卷、网络，拒绝覆盖已存在资源；恢复后核对方案、布局快照、复核事件、权限与报表。
8. 停止本实例：`docker compose -p packplan-local down`，保留持久化卷。仅明确舍弃自己的测试数据时才使用`down -v`。禁止清理其他项目的资源。

会话在后端内存，重启/恢复后重新登录。升级工具不实现在线双活、零停机或异地灾备。备份含密码散列和实际业务，私有保存，不入源码、截图或Git。

English: initialize private random configuration, start the three-service stack and verify health. Use explicit project names for backup and restore. Backup pauses writes and carries a manifest hash. Restore refuses existing resources and is restricted to localhost until separately verified. External deployment requires authorized commercial use, trusted HTTPS, isolation, backups and independent security review. Never overwrite an existing database or another project's volumes.
