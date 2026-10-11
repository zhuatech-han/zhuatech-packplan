// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 页面语言切换，不翻译业务输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
const states = {
  DRAFT: ["草稿", "Draft"],
  PENDING: ["待独立复核", "In review"],
  APPROVED: ["已批准冻结", "Approved and frozen"],
  CANCELLED: ["已取消", "Cancelled"],
};
/** 只映射服务端实际状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const stateName = (s) => (states[s] ? t(...states[s]) : s);
const errors = {
  UNAUTHENTICATED: [
    "会话失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号、密码不正确或已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有这项操作权限。", "Permission denied."],
  OUT_OF_SCOPE: ["记录不在当前数据范围。", "Record is outside your scope."],
  VERSION_CONFLICT: [
    "记录已变化，请刷新核对。",
    "Record changed. Refresh and check.",
  ],
  INDEPENDENT_REQUIRED: [
    "复核人必须独立，不能是任何历史编辑人。",
    "The reviewer must be independent of every historical editor.",
  ],
  STATE_INVALID: [
    "当前状态不能执行此操作。",
    "Action unavailable in this state.",
  ],
  INVALID_INPUT: [
    "字段格式不正确，请检查。",
    "Invalid fields. Check the form.",
  ],
  INVALID_WEIGHT: [
    "公斤最多三位小数，范围0.001–100000；上部载重可为0。",
    "Kilograms require up to three decimals, 0.001–100000; top load may be zero.",
  ],
  INVALID_DIMENSION: [
    "尺寸必须为1–20000整数毫米。",
    "Dimensions require integer mm from 1 to 20000.",
  ],
  CARGO_LIMIT: ["最多20种、合计120件箱件。", "Up to 20 types and 120 boxes."],
  CARGO_CODE_INVALID: [
    "箱件编号须唯一，2–30位大写字母、数字或下划线。",
    "Box type codes must be unique: 2–30 uppercase letters, digits or underscores.",
  ],
  BOX_ID_INVALID: [
    "箱件编号重复或不属于当前需求。",
    "Duplicate box ID or unknown demand.",
  ],
  BOX_OVERLAP: [
    "箱件相互重叠，调整未保存。",
    "Boxes overlap. Adjustment not saved.",
  ],
  OUT_OF_BOUNDS: ["箱件超出柜内边界。", "Box exceeds cargo-space bounds."],
  UNSUPPORTED_BOX: [
    "离地箱件须由一个箱件完整支撑底面。",
    "An elevated box requires full base coverage by one supporting box.",
  ],
  STACK_LOAD_EXCEEDED: [
    "累积上部重量超过支撑箱许可。",
    "Cumulative upper weight exceeds the supporting box limit.",
  ],
  ROTATION_DENIED: [
    "这个箱件未允许水平旋转。",
    "Horizontal rotation is not allowed for this box.",
  ],
  WEIGHT_EXCEEDED: [
    "总重量超过柜内允许货物总重。",
    "Total cargo weight exceeds the space limit.",
  ],
  REVIEWER_UNAVAILABLE: [
    "指定复核人停用或权限／部门不匹配。",
    "Reviewer disabled or permissions/team incompatible.",
  ],
  REVIEWER_ONLY: [
    "仅指定复核账号可以批准或退回。",
    "Only the assigned reviewer can approve or return.",
  ],
  CALCULATE_FIRST: [
    "请先保存一次有效计算结果。",
    "Save a valid calculation first.",
  ],
  NO_PLACED_BOXES: [
    "没有装入箱件，不能送审。",
    "No placed boxes. Cannot submit.",
  ],
  UNPLACED_ACK_REQUIRED: [
    "请明确确认未装入清单并填写排除说明。",
    "Acknowledge unplaced boxes and explain their exclusion.",
  ],
  NOTE_REQUIRED: [
    "请填写至少4个字符的说明。",
    "Enter a note of at least four characters.",
  ],
  CSV_INVALID: [
    "CSV表头、列数、引号或真假值不正确。",
    "Invalid CSV header, column count, quotes or boolean.",
  ],
  CSV_LIMIT: ["CSV不得超过64KB。", "CSV must be at most 64KB."],
  REVISION_LIMIT: [
    "历史版本已达到系统上限，保留既有历史。",
    "Revision limit reached; existing history is retained.",
  ],
  RESOURCE_LIMIT: ["记录数量超过系统上限。", "Resource limit reached."],
  SNAPSHOT_CHANGED: [
    "历史摘要或当前输入不一致，操作拒绝。",
    "Snapshot digest or current input mismatch.",
  ],
  REVISION_MISMATCH: [
    "版本不属于当前方案。",
    "Revision belongs to another plan.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72 UTF-8字节，含大小写和数字。",
    "Password requires 12–72 UTF-8 bytes, uppercase/lowercase/digits.",
  ],
  LAST_ADMIN: [
    "必须保留一个完整管理员。",
    "Keep at least one full administrator.",
  ],
  CONFLICT: [
    "编号重复或目录仍被引用。",
    "Duplicate code or referenced directory.",
  ],
  CATEGORY_INVALID: [
    "包装分类已停用或不存在。",
    "Packaging category disabled or missing.",
  ],
  RESULT_UNKNOWN: [
    "提交结果未知，请刷新核对后再操作。",
    "Submission result unknown. Refresh and check.",
  ],
  NETWORK_ERROR: [
    "连接失败，请刷新重试。",
    "Connection failed. Refresh and retry.",
  ],
  ACCOUNT_ASSIGNED: [
    "已有业务历史，不能更换账号部门。",
    "Business history prevents team reassignment.",
  ],
  IDENTITY_LOCKED: [
    "稳定编号或所属部门不能修改。",
    "Code or team is immutable.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Current password is incorrect."],
};
/** 错误不被当作成功，未知代码明确显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成：", "Action failed: ") + e.message;
/** API记录拷贝后编辑，不修改原列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
/** 过滤与分页仅影响展示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function filterRows(rows, q, state) {
  const n = q.trim().toLowerCase();
  return rows.filter(
    (r) =>
      (!state || r.state === state) &&
      (!n || JSON.stringify(r).toLowerCase().includes(n)),
  );
}
/** 日期输入转换为UTC；无效日期拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function utc(value) {
  const d = new Date(value);
  if (!Number.isFinite(d.getTime())) throw new Error("INVALID_INPUT");
  return d.toISOString();
}
