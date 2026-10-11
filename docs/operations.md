# PackPlan 操作手册 / Operations

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 首次使用

登录管理员，建立方案设计与独立复核两个不同账号。空库没有预造箱件或方案。新建方案填写实测柜内长宽高、允许货物总重和另一个复核账号；分类和参数来自后台。

## 箱件与CSV

外包络尺寸包含托盘和包装；高度固定，勾选水平旋转只交换长宽。公斤最多三位小数。允许累积上部重量为0意味着该箱上方不可放其他箱件，不是未知值。CSV使用固定表头和UTF-8，先查看逐行预检，再明确确认替换当前箱件。修改任何需求或规格使活动布局失效；旧快照保留。

## 查看与调整

计算保存一个新版本，显示已装入/未装入、装入重量与体积占比。视图用轴测投影，支持视角、缩放、选中和单箱查看；它不依据像素判断碰撞。点击箱件或位置清单查看X/Y/Z、尺寸与直接支撑。调整只发送整数坐标和水平旋转，服务端校验完整布局。移出箱件会进入未装入清单，需求数量不变；悬空、部分支撑、重叠、越界和超载整批拒绝。

X沿长度、Y沿宽度、Z沿高度；原点位于柜底一个角。没有柜门位置/通行路径模型。参考次序先低层再按X/Y，不能作为已验证的装载设备指令。逐箱检查实际进柜路线、门框、叉车、捆扎、通风、危险品及真实货物抗压。

## 独立复核

指定复核人不能是任何历史编辑人；更换角色或重新指派不能清除编辑历史。部分布局允许送审，但必须确认未装入清单并填写排除说明。全部未装入方案不可送审。指定账号可批准或退回，保留说明和事件。批准后规格、箱件和布局冻结。重新修订填写原因，会清除活动批准布局；修改后需再次计算和复核。取消只允许草稿，并保留历史。

## 历史与导出

选择历史R编号只读查看。每个版本保存输入、元信息、布局和SHA256摘要；摘要不是防篡改公证。SVG图、CSV位置清单和JSON快照均检查实时权限。JSON包含当前方案状态、是否当前版本和该版本复核事件，旧版本不能被自动当成当前批准方案。CSV包括未装入箱件，文本做公式前缀防护。

## English overview

Create distinct planner and reviewer accounts; enter measured space and envelope dimensions. Preview CSV before explicit replacement. Calculate and inspect the retained 3D projection; edit integer positions through the same complete server-side validator. Omitted boxes become explicitly unplaced. Submit with an exclusion acknowledgement if partial. Only the assigned non-editor can review. Approved revisions are frozen; reopening requires a reason and a new calculation/review. Historical exports remain scoped and clearly identify whether they are the current revision. Verify all physical access, restraint and cargo strength independently.
