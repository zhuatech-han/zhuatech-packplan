[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" width="150" alt="知华科技 LOGO">

# PackPlan · 知华装箱与三维装柜规划

**1.0.0 · 公开源码学习版／非商业源码版 · Java 21 / Spring Boot / Vue 3 / MySQL 8.4**

知华科技（上海如静知华信息科技有限公司）提供。[知华科技官网](https://www.zhuatech.cn/)。

面向外贸企业、制造工厂和货运协调团队的箱件规划工作台。把实测柜内规格、箱件外包络、重量、旋转许可、计算版本和独立复核放在同一份可追溯记录中，研究“当前方案装入多少、每箱放哪里、哪些箱件还没安排”。适合单企业、小规模规划与软件学习，不宣称已验证客户收益或生产容量。

## 从清单到可复核的布局

```text
实测柜内规格 → 录入箱件 / CSV逐行预检 → 确认替换 → 计算新布局
                                                  ↓
                           三维示意 / 位置清单 / 未装入清单
                                                  ↓
                           手工位置修订 → 完整服务端几何核验
                                                  ↓
                       确认未装入排除项 → 指定独立复核 → 批准冻结
                                                  ↓
                                  SVG图 / CSV清单 / JSON快照
```

这是**软件规划记录**。批准并不证明实物已经装载，不执行开门、叉车或自动装柜指令。实际通行路线、柜门尺寸、货物抗压、捆扎固定和运输安全由现场人员另行核验。

### 已实现功能

| 工作内容 | 当前可操作能力 |
|---|---|
| 柜内规格 | 稳定编号、名称、部门、包装分类、实测长宽高、允许货物总重、说明、指定独立复核账号 |
| 箱件需求 | 类型编号、名称、完整外包络尺寸、数量、单件毛重、水平旋转许可、允许累积上部重量；草稿增删改 |
| CSV导入 | 固定表头、UTF-8、64KB上限、逐行预检、显式确认整批替换；坏行、重复编号或超限整批拒绝 |
| 布局计算 | 自主确定性有界启发式、四种需求排序比较、逐箱坐标、支撑关系、未装入列表、数量守恒与利用率 |
| 手工修订 | 选择单件后改X/Y/Z及许可的水平旋转；从未装入清单尝试加入或明确移出；整布局校验成功才保存新版本 |
| 三维查看 | 自有轴测投影、视角和缩放、箱件选择、选中单件查看、完整位置与尺寸清单；不靠像素判定碰撞 |
| 版本留存 | 每次计算/手调保存不可改写输入、元信息、布局与SHA256；修改需求使活动结果失效，旧快照仍可查 |
| 独立复核 | 指定非历史编辑人批准/退回；部分布局须确认未装入清单和排除说明；全部未装入不可送审；批准冻结、撤回与有原因重新修订 |
| 导出与统计 | 当前范围统计；按指定历史版本下载SVG图、CSV清单、JSON快照；JSON快照标明当前状态与是否当前版本 |
| 身份与后台 | BCrypt12、同源会话与CSRF、登录失败节流、账号/角色/权限/菜单/部门/包装分类/参数、最后管理员保护、密码重置撤销旧会话 |
| 范围与审计 | ALL / DEPARTMENT / ASSIGNED服务端范围、历史编辑人与指定复核关系、业务事件和最小动作审计；查询筛选、排序、15条分页 |
| 部署与恢复 | Flyway V1/V2、三服务健康检查、非root应用容器、随机私有配置、停写逻辑备份和全新独立实例恢复工具 |

### 用户端与管理端

- **方案设计端**：维护本部门需求、CSV预检、计算、手工调整、送审、版本查看与导出。
- **复核端**：查看授权方案与历史；只有明确被指定、且从未编辑该方案的账号可批准或退回。
- **指定复核端**：ASSIGNED范围，仅查看本部门本人创建、历史编辑或被指派的方案；内建此岗位只赋读取、复核与导出权限。
- **部门查阅端**：只读本部门方案、布局、导出和统计。
- **管理端**：维护账号与系统目录、参数和审计；ALL范围也不能绕过独立复核、冻结或几何约束。

账号/部门停用和改密使旧会话失效，角色权限实时检查。更换角色或重新指定复核人不能清除历史编辑关系。部门范围不是独立SaaS租户隔离。

## 计算口径与边界

- 竖直轴对齐矩形箱件，尺寸**整数毫米**1–20000，公斤最多三位小数，内部整数克；拒绝隐式舍入。水平旋转只交换长宽，不倾斜、不倒置。
- 一方案一个自定义柜内空间，最多20种、合计120件。货物限重0.001–100000kg；不含柜体皮重。单件上部载重为0表示禁止其上放置其他箱件。
- 箱件可接触但不可重叠。离地箱件必须由**一个**下方箱件完整覆盖底面，不能把多个小箱拼成支撑。上方全部后代箱件重量累计到每个祖先支撑箱的声明限额中。
- 算法`SUPPORTED-EXTREME-POINT-1`比较按体积、重量、底面积、上部载重许可排序的四种结果；有限候选位置按Z/X/Y测试，优先装入件数、其次体积。**不保证全局最优，也不证明未装入箱件绝对装不下。**
- 利用率＝装入箱件外包络体积／声明柜内总体积；报告明确区分未计算方案和活动计算方案。参考次序是低层优先、同层X/Y排序，不是通过通行路径、设备或装载安全验证的指令。
- 没有多柜优化、异形/软包、重心、轴荷/地板点载荷、柜门/进出路径、抗压实测、摩擦/捆扎力、危险品规则、机器人或叉车控制。输入承压许可须来自实际核验，软件不替代货物强度测试。
- 没有仓库库存、运输订单、运费结算、报关、在线支付、自动ERP/WMS连接器、AI或付费第三方调用。SVG轴测图用于查看，部分遮挡可用“仅看选中箱件”；不是WebGL/CAD工程模型。
- 单实例、内存会话；重启需重新登录。写入用数据库目录行锁串行，适合低容量部署；列表每实体最多10000条，前端搜索/排序/分页。默认总方案500（100–1000）、单方案历史30（5–50），取消项和旧版本保留并占用容量。
- SHA256用于输入/布局一致性检查；有权限的数据库管理员仍可能同时改写数据和摘要。追加应用日志不是不可篡改公证。

## 实际运行页面

下列图片来自当前运行代码的独立`TEST`验收资料，仅使用虚构的 TEST 箱件与坐标，不含真实客户资料、密码或密钥。

| 页面 | 功能说明 |
|---|---|
| ![登录](docs/screenshots/01-login.jpg) | 个人账号认证与品牌授权说明 |
| ![设计工作台](docs/screenshots/02-plans.jpg) | 范围内方案、尺寸和状态 |
| ![三维布局](docs/screenshots/03-layout.png) | 实际坐标、支撑、利用率与单箱选择 |
| ![箱件清单](docs/screenshots/04-cargo.jpg) | 尺寸、重量、旋转与上部载重许可 |
| ![独立复核](docs/screenshots/05-review.jpg) | 指定账号复核已计算方案 |
| ![角色权限](docs/screenshots/06-roles.jpg) | 岗位、接口权限与数据范围 |
| ![规划统计](docs/screenshots/07-reports.jpg) | 当前范围与活动版本计数 |
| ![英文界面](docs/screenshots/08-english.jpg) | 英文用户操作页面 |
| ![手机页面](docs/screenshots/09-mobile.png) | 窄屏导航与可操作布局 |

## 架构、环境和目录

浏览器 → 非root Nginx同源代理 → Spring Security会话/CSRF → 实时权限及事务 → JPA → MySQL8.4。Flyway升级结构，Hibernate仅验证。

| 层 | 版本与用途 |
|---|---|
| 后端 | Java21、Spring Boot4.0.7、Security、JPA、Flyway、MySQL Connector/J；Maven3.9与Spotless |
| 前端 | Vue3.5.43、Vite8.1.5、JavaScript、Lucide；Node24.19.0、Prettier、ESLint；自有SVG轴测投影 |
| 部署 | Docker、Compose v2、MySQL8.4、Nginx1.29；Java应用与前端非root |
| 验收脚本 | Python3.11+，业务验收仅标准库；Black25.1用于脚本格式 |

```text
backend/src/main/java/cn/zhuatech/packplan/  几何、CSV、方案版本、复核与身份
backend/src/main/resources/db/migration/  V1身份目录 / V2方案箱件与快照
backend/src/test/                         单元及HTTP/JPA集成测试
frontend/src/                            设计、复核、只读和后台界面
frontend/public/brand/                   原始正式Logo
docs/                                   API、操作、算法、部署、安全与截图
scripts/                                随机配置、验收、备份恢复、发布检查
compose.yaml                            独立MySQL、后端及前端
```

### 首次安装与启动

需要Docker和Compose v2；首次依赖下载需能访问官方包仓库：

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose -p packplan-local up -d --build --wait --wait-timeout 240
```

访问[http://127.0.0.1:8132/](http://127.0.0.1:8132/)，健康检查[http://127.0.0.1:8132/actuator/health](http://127.0.0.1:8132/actuator/health)。

首次管理员`admin`，密码读取本机私有`.env`的`ADMIN_PASSWORD`。无共享弱密码；脚本生成独立随机密码、0600文件且拒绝覆盖。空库初始化管理员、五岗位、菜单、包装分类和参数，不伪造业务数据。建立另一个独立复核账号后创建方案。改变环境变量不会重置已有用户密码。

### 数据库与配置

结构脚本：[V1__identity.sql](backend/src/main/resources/db/migration/V1__identity.sql)、[V2__packing.sql](backend/src/main/resources/db/migration/V2__packing.sql)。包含外键、稳定编号唯一约束、尺寸/重量/状态约束和查询索引。Flyway空库按顺序执行；升级先停写备份，新版本新增迁移，不修改已执行SQL。

| 配置字段 | 要求 |
|---|---|
| `MYSQL_ROOT_PASSWORD` / `DATABASE_PASSWORD` | 独立随机数据库管理/业务密码，必填 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 仅空库初始账号；密码12–72 UTF-8字节，含大小写与数字 |
| `WEB_PORT` / `BIND_ADDRESS` | 默认8132 / 127.0.0.1；可覆盖避免其他项目冲突 |
| `COOKIE_SECURE` | 本机HTTP为false，对外可信HTTPS为true |
| `DATABASE_URL` / `DATABASE_USER` | 可选外部MySQL；应验证TLS身份与可信CA，备份工具仅支持内置数据库 |

[.env.example](.env.example)仅字段和安全说明；`.env`不得入Git。Compose不暴露数据库和后端端口。端口覆盖：`WEB_PORT=8232 docker compose -p packplan-local up -d`，访问和健康地址相应改为8232。内置MySQL隔离网络使用TLS，外部数据库应配置`sslMode=VERIFY_IDENTITY`。

### 本地开发与测试

本机开发需要Java21、Maven3.9、Node24.19与MySQL8.4。建立`zhuatech_packplan`库并注入`DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`、`ADMIN_PASSWORD`；数据库URL含`connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`。

```bash
cd backend
mvn spring-boot:run
# 另开终端，项目根目录
cd frontend
npm ci
npm run dev
```

开发前端默认5173，经Vite代理本机8080；部署只请求同源`/api`。检查命令：

```bash
cd backend
mvn spotless:check clean verify
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
npm audit
cd ..
python3 -m py_compile scripts/*.py
python3 scripts/release-check.py
docker compose config --quiet
# 仅独立localhost测试实例，显式允许创建TEST资料
python3 scripts/smoke.py --base http://127.0.0.1:8132 --env-file .env --state-output /private/tmp/packplan-quality-state.json --create-test-data
```

测试状态含随机测试密码，0600私有保存，不随源码上传。复验使用`--state-input`读取同一快照；重新登录后核对方案、箱件、历史、身份目录、事件、非登录审计及导出。实际结果见[验收记录](docs/verification.md)。镜像构建执行测试，无跳过测试参数。

### 部署、备份和恢复

对外部署须书面商业授权、可信HTTPS、访问隔离、独立安全验收及备份。备份暂停指定实例后端写入，创建带摘要的私有逻辑包后恢复服务；SQL包含密码散列和业务，保持私有。

```bash
python3 scripts/backup.py --project packplan-local --output /private/tmp/packplan-backup.zip
# 使用另一份私有配置和端口，只恢复全新资源
python3 scripts/restore.py /private/tmp/packplan-backup.zip --project packplan-restored --env-file /private/tmp/packplan-restored.env
```

只恢复可信备份；工具拒绝覆盖既有项目、卷或网络。旧版本回退使用匹配旧应用与备份恢复到新实例，不能直接覆盖现库。[部署恢复](docs/deployment.md) · [操作手册](docs/operations.md) · [接口说明](docs/api.md) · [算法与数据](docs/architecture.md) · [安全说明](docs/security.md)。

## 常见问题与贡献

- `UNSUPPORTED_BOX`：核对高度及完整单箱底面支撑；多箱拼接底面不被此模型接受。
- `STACK_LOAD_EXCEEDED`：祖先箱承担全部后代箱重量，移走上层或修正有依据的实际许可。
- `VERSION_CONFLICT`：刷新核对，勿自动重试。网络中断显示结果未知，也先核对。
- 没有可选复核人：建立另一个有读取和复核权限的启用账号；历史编辑人不能被选作独立复核人。
- 目录或业务为空：检查数据范围、指定关系和初始化操作；默认没有业务示例。
- 数据库等待失败：检查本实例配置与日志；已有卷密码不随`.env`自动改变，勿清理其他项目卷。

问题反馈和贡献请附脱敏复现及相关版本，禁止上传客户清单、密码、Cookie或数据库备份。安全漏洞私下发至[han@zhuatech.cn](mailto:han@zhuatech.cn)或[jack@zhuatech.cn](mailto:jack@zhuatech.cn)，勿在公开Issue泄露细节。软件按现状提供，不保证特定用途、适销性、实物装载安全或持续运营可用性。

## 授权与联系知华科技

自有代码按[LICENSE](LICENSE)提供，**源码公开、非商业使用**；仅限个人学习、技术研究和非商业交流，未经上海如静知华信息科技有限公司书面授权不得商用。非OSI标准开源许可，第三方版权与协议见[NOTICE](NOTICE)。

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 [https://www.zhuatech.cn/](https://www.zhuatech.cn/)，或添加微信 zhuatech、zhuatech2 咨询。

**商业授权或深度定制开发请联系知华科技。** 官网：[www.zhuatech.cn](https://www.zhuatech.cn/)；微信`zhuatech`、`zhuatech2`。

| 微信 zhuatech | 微信 zhuatech2 |
|---|---|
| <img src="docs/images/wechat-zhuatech.png" width="220" alt="知华科技微信 zhuatech"> | <img src="docs/images/wechat-zhuatech2.png" width="220" alt="知华科技微信 zhuatech2"> |
