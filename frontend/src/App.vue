<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import {
  Box,
  Layers,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  History,
  LogOut,
  Plus,
  Search,
  ArrowLeft,
  RefreshCw,
  X,
  Download,
  Move3D,
} from "@lucide/vue";
import { api, resetApi, download } from "./api.js";
import {
  t,
  language,
  errorMessage,
  stateName,
  cloneRecord,
  filterRows,
} from "./ui.js";
import { moves, replaceMove } from "./geometry.js";
import BoxView from "./components/BoxView.vue";
import AdminPanel from "./components/AdminPanel.vue";
const me = ref(null),
  section = ref("plans"),
  pending = ref(false),
  ready = ref(false),
  error = ref(""),
  notice = ref("");
const login = ref({ username: "", password: "" }),
  plans = ref([]),
  options = ref({ accounts: [], departments: [], categories: [] }),
  detail = ref(null),
  view = ref(null),
  tab = ref("layout");
const report = ref(null),
  audit = ref([]),
  q = ref(""),
  state = ref(""),
  order = ref("newest"),
  page = ref(1),
  selected = ref(""),
  dialog = ref(null),
  preview = ref(null);
const can = (p) => me.value?.permissions.includes(p);
const icons = {
  plans: Layers,
  reports: BarChart3,
  users: Users,
  roles: ShieldCheck,
  settings: Settings,
  audit: History,
};
const filtered = computed(() =>
  filterRows(plans.value, q.value, state.value).sort((a, b) =>
    order.value === "oldest" ? a.id - b.id : b.id - a.id,
  ),
);
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
const plan = computed(() => detail.value?.plan);
const isEditor = computed(() => detail.value?.editors.includes(me.value?.id));
const canDesign = computed(
  () => can("design") && plan.value?.state === "DRAFT",
);
const canMove = computed(
  () => canDesign.value && view.value?.id === detail.value?.active?.id,
);
const reviewers = computed(() =>
  options.value.accounts.filter(
    (a) =>
      a.reviewer &&
      a.id !== me.value?.id &&
      !detail.value?.editors.includes(a.id),
  ),
);
const box = computed(
  () =>
    view.value?.layout.placements.find((b) => b.uid === selected.value) ||
    view.value?.layout.unplaced.find((b) => b.uid === selected.value),
);
const kg = (grams) => Number(grams) / 1000;
const time = (date) =>
  new Date(date).toLocaleString(language.value === "zh" ? "zh-CN" : "en-GB");
const person = (id) =>
  detail.value?.people.find((p) => p.id === id)?.name ||
  options.value.accounts.find((p) => p.id === id)?.name ||
  `#${id}`;
watch([q, state, order], () => (page.value = 1));
watch(section, () => {
  detail.value = null;
  view.value = null;
  q.value = "";
  state.value = "";
  page.value = 1;
  load();
});
function handle(e) {
  error.value = errorMessage(e);
  if (e.status === 401) {
    me.value = null;
    resetApi();
    detail.value = null;
    view.value = null;
  }
}
/** 未知网络结果不自动重试；只有服务端确认成功才更改页面。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  error.value = "";
  notice.value = "";
  try {
    const result = await api(path, { method, body });
    notice.value = path.endsWith("preview-csv")
      ? t("预检通过，尚未导入。", "Preview validated; not imported yet.")
      : t("已保存。", "Saved.");
    return result;
  } catch (e) {
    handle(e);
    return null;
  } finally {
    pending.value = false;
  }
}
/** 批次重读实时权限和范围，读取失败时隐藏旧记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  ready.value = false;
  try {
    me.value = await api("/auth/me");
    [plans.value, options.value] = await Promise.all([
      api("/plans"),
      api("/options"),
    ]);
    if (section.value === "reports") report.value = await api("/reports");
    if (section.value === "audit") audit.value = await api("/audit");
    if (detail.value) {
      detail.value = await api("/plans/" + detail.value.plan.id);
      view.value = detail.value.active;
      selected.value = "";
    }
    ready.value = true;
  } catch (e) {
    handle(e);
  }
}
async function signIn() {
  const result = await perform("/auth/login", "POST", login.value);
  login.value.password = "";
  if (result) {
    resetApi();
    me.value = result;
    section.value = result.menus[0]?.code || "plans";
    notice.value = "";
    await load();
  }
}
async function signOut() {
  await perform("/auth/logout", "POST", {});
  me.value = null;
  detail.value = null;
  view.value = null;
  resetApi();
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.status !== 401) handle(e);
  }
});
async function open(row) {
  error.value = "";
  try {
    detail.value = await api("/plans/" + row.id);
    view.value = detail.value.active;
    tab.value = view.value ? "layout" : "cargo";
    selected.value = "";
  } catch (e) {
    handle(e);
  }
}
async function history(id) {
  if (!id) {
    view.value = null;
    return;
  }
  try {
    view.value = await api(`/plans/${plan.value.id}/revisions/${id}`);
    selected.value = "";
  } catch (e) {
    handle(e);
  }
}
function planDialog(edit = false) {
  preview.value = null;
  dialog.value = {
    kind: "plan",
    body: edit
      ? {
          ...cloneRecord(plan.value),
          maxWeightKg: String(kg(plan.value.maxWeightGrams)),
        }
      : {
          code: "",
          title: "",
          departmentId: options.value.departments[0]?.id || "",
          reviewerId: "",
          category: options.value.categories[0]?.code || "",
          length: "",
          width: "",
          height: "",
          maxWeightKg: "",
          note: "",
        },
  };
}
function cargoDialog(row = null) {
  dialog.value = {
    kind: "cargo",
    body: row
      ? {
          ...cloneRecord(row),
          version: plan.value.version,
          weightKg: String(kg(row.weightGrams)),
          topLoadKg: String(kg(row.topLoadGrams)),
        }
      : {
          code: "",
          name: "",
          length: "",
          width: "",
          height: "",
          quantity: 1,
          weightKg: "",
          topLoadKg: "0",
          rotate: false,
          version: plan.value.version,
        },
  };
}
function csvDialog() {
  preview.value = null;
  dialog.value = {
    kind: "csv",
    body: { csv: "", version: plan.value.version },
  };
}
async function fileCsv(event) {
  const file = event.target.files[0];
  if (!file) return;
  preview.value = null;
  try {
    if (!file.name.toLowerCase().endsWith(".csv") || file.size > 65536)
      throw new Error("CSV_LIMIT");
    dialog.value.body.csv = new TextDecoder("utf-8", { fatal: true }).decode(
      await file.arrayBuffer(),
    );
  } catch (e) {
    error.value = errorMessage(e);
  }
  event.target.value = "";
}
async function previewCsv() {
  const r = await perform(`/plans/${plan.value.id}/preview-csv`, "POST", {
    csv: dialog.value.body.csv,
  });
  if (r) preview.value = { csv: dialog.value.body.csv, rows: r };
}
function moveDialog(item) {
  const source = view.value.layout.placements.find((b) => b.uid === item.uid);
  dialog.value = {
    kind: "move",
    base: cloneRecord(view.value.layout),
    body: {
      uid: item.uid,
      x: source?.x ?? 0,
      y: source?.y ?? 0,
      z: source?.z ?? 0,
      rotated: source?.rotated ?? false,
      version: plan.value.version,
    },
    cargo: view.value.input.cargo.find((c) => c.code === item.code),
  };
}
function confirm(action, label, item = null) {
  dialog.value = {
    kind: "action",
    action,
    label,
    body: { version: plan.value.version, note: "", acknowledgeUnplaced: false },
    item,
    base: view.value ? cloneRecord(view.value.layout) : null,
  };
}
/** 表单携带打开时的版本，服务端拒绝陈旧调整；CSV替换需先预检当前文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function saveDialog() {
  const d = dialog.value;
  let path,
    method = "POST",
    body = d.body;
  if (d.kind === "plan") {
    path = "/plans" + (body.id ? "/" + body.id : "");
    if (body.id) method = "PUT";
  } else if (d.kind === "cargo") {
    path = `/plans/${plan.value.id}/cargo${body.id ? "/" + body.id : ""}`;
    if (body.id) method = "PUT";
  } else if (d.kind === "csv") {
    if (preview.value?.csv !== body.csv) return;
    path = `/plans/${plan.value.id}/import-csv`;
  } else if (d.kind === "move") {
    path = `/plans/${plan.value.id}/manual`;
    body = {
      version: body.version,
      placements: replaceMove(d.base, {
        uid: body.uid,
        x: Number(body.x),
        y: Number(body.y),
        z: Number(body.z),
        rotated: body.rotated,
      }),
    };
  } else if (d.kind === "action") {
    if (d.action === "delete-cargo") {
      path = `/plans/${plan.value.id}/cargo/${d.item.id}`;
      method = "DELETE";
    } else if (d.action === "remove-box") {
      path = `/plans/${plan.value.id}/manual`;
      body = {
        version: body.version,
        placements: moves(d.base).filter((b) => b.uid !== d.item.uid),
      };
    } else path = `/plans/${plan.value.id}/${d.action}`;
  } else if (d.kind === "password") {
    path = "/auth/password";
  }
  const result = await perform(path, method, body);
  if (result) {
    dialog.value = null;
    preview.value = null;
    if (d.kind === "password") {
      me.value = null;
      detail.value = null;
      view.value = null;
      resetApi();
    } else {
      await load();
      if (d.kind === "plan" && !body.id) await open(result);
    }
  }
}
async function calculate() {
  const result = await perform(`/plans/${plan.value.id}/calculate`, "POST", {
    version: plan.value.version,
  });
  if (result) {
    await load();
    tab.value = "layout";
  }
}
async function exportFile(format) {
  try {
    await download(
      `/plans/${plan.value.id}/revisions/${view.value.id}/export.${format}`,
      `packplan-${plan.value.code}-r${view.value.id}.${format}`,
    );
  } catch (e) {
    handle(e);
  }
}
const actionLabels = {
  submit: () => t("提交独立复核", "Submit for review"),
  approve: () => t("批准并冻结", "Approve and freeze"),
  return: () => t("退回修订", "Return for changes"),
  withdraw: () => t("撤回送审", "Withdraw review"),
  reopen: () => t("重新修订", "Reopen for revision"),
  cancel: () => t("取消方案", "Cancel plan"),
};
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="login-intro">
      <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
      <div>
        <span class="eyebrow">PackPlan</span>
        <h1>{{ t("把每一箱的位置看清楚", "A clear place for every box") }}</h1>
        <p>
          {{ t("装箱与三维装柜规划", "Packing and 3D cargo-space planning") }}
        </p>
        <div class="flow-line">
          {{
            t(
              "箱件清单 → 布局规划 → 独立复核",
              "Cargo list → Layout → Independent review",
            )
          }}
        </div>
      </div>
      <small
        >{{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}<br /><a
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          >www.zhuatech.cn</a
        ></small
      >
    </section>
    <section class="login-form">
      <button
        class="language"
        @click="language = language === 'zh' ? 'en' : 'zh'"
      >
        {{ language === "zh" ? "English" : "中文" }}
      </button>
      <form @submit.prevent="signIn">
        <h2>{{ t("登录工作台", "Sign in") }}</h2>
        <p class="muted">
          {{
            t(
              "使用个人账号进入授权范围。",
              "Use your personal account to access assigned work.",
            )
          }}
        </p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="login.username"
            name="username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            name="password"
            autocomplete="current-password"
            type="password"
            required
            maxlength="128" /></label
        ><button class="primary full" :disabled="pending">
          {{ pending ? t("正在登录…", "Signing in…") : t("登录", "Sign in") }}
        </button>
        <p class="small muted">
          {{
            t(
              "公开源码学习版 · 非商业使用；商用须书面授权。",
              "Source-available learning edition. Commercial use requires written authorization.",
            )
          }}
        </p>
      </form>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技 LOGO" /><strong
          >PackPlan</strong
        ><span>{{ t("装箱与三维装柜", "Packing & 3D loading") }}</span></a
      >
      <nav>
        <button
          v-for="menu in me.menus"
          :key="menu.code"
          :class="{ active: section === menu.code }"
          @click="section = menu.code"
        >
          <component :is="icons[menu.code] || Box" :size="17" />{{
            language === "zh" ? menu.name : menu.nameEn
          }}
        </button>
      </nav>
      <div class="sidebar-foot">
        <span>{{ me.displayName }}</span
        ><small>{{ me.role }}</small
        ><button @click="section = 'about'">
          {{ t("关于与商业咨询", "About & contact") }}</button
        ><button @click="signOut">
          <LogOut :size="16" />{{ t("退出", "Sign out") }}
        </button>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <span>{{ t("规划工作空间", "Planning workspace") }}</span>
        <div class="actions">
          <button @click="language = language === 'zh' ? 'en' : 'zh'">
            {{ language === "zh" ? "English" : "中文" }}</button
          ><button
            @click="
              dialog = {
                kind: 'password',
                body: { oldPassword: '', newPassword: '' },
              }
            "
          >
            {{ t("修改密码", "Change password") }}
          </button>
        </div>
      </header>
      <div class="content">
        <p v-if="error" class="error" role="alert">
          {{ error
          }}<button aria-label="dismiss error" @click="error = ''">×</button>
        </p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(section)"
          :section="section"
          :pending="pending"
          :perform="perform"
          @error="handle"
        />
        <section v-else-if="section === 'plans' && !detail">
          <header class="page-heading">
            <div>
              <span class="eyebrow">PackPlan</span>
              <h1>{{ t("装柜方案", "Load plans") }}</h1>
              <p class="muted">
                {{
                  t(
                    "需求、计算与复核分别留存，批准版本冻结。",
                    "Demand, calculations and review are retained. Approved revisions are frozen.",
                  )
                }}
              </p>
            </div>
            <div class="actions">
              <button :disabled="pending" @click="load">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
              ><button
                v-if="can('design')"
                class="primary"
                :disabled="pending || !ready"
                @click="planDialog()"
              >
                <Plus :size="16" />{{ t("新建方案", "New plan") }}
              </button>
            </div>
          </header>
          <div class="panel">
            <div class="filter-bar">
              <div class="search">
                <Search :size="16" /><input
                  v-model="q"
                  :placeholder="
                    t('搜索编号、名称与状态', 'Search code, title or state')
                  "
                />
              </div>
              <select v-model="state" aria-label="plan state filter">
                <option value="">{{ t("所有状态", "All states") }}</option>
                <option
                  v-for="s in ['DRAFT', 'PENDING', 'APPROVED', 'CANCELLED']"
                  :key="s"
                  :value="s"
                >
                  {{ stateName(s) }}
                </option></select
              ><select v-model="order" aria-label="sort order">
                <option value="newest">{{ t("编号倒序", "Newest ID") }}</option>
                <option value="oldest">
                  {{ t("编号顺序", "Oldest ID") }}
                </option></select
              ><small>{{ filtered.length }} {{ t("条", "records") }}</small>
            </div>
            <div v-if="!ready" class="empty">
              {{
                t(
                  "数据尚未就绪，请刷新重新读取。",
                  "Data not ready. Refresh to reload.",
                )
              }}
            </div>
            <div v-else class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("编号 / 名称", "Code / title") }}</th>
                    <th>{{ t("柜内尺寸 mm", "Internal size mm") }}</th>
                    <th>{{ t("货物总重上限 kg", "Cargo weight limit kg") }}</th>
                    <th>{{ t("状态", "State") }}</th>
                    <th>{{ t("更新", "Updated") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in shown" :key="row.id">
                    <td>
                      <strong>{{ row.code }}</strong
                      ><span class="sub">{{ row.title }}</span>
                    </td>
                    <td>
                      {{ row.length }} × {{ row.width }} × {{ row.height }}
                    </td>
                    <td>{{ kg(row.maxWeightGrams) }}</td>
                    <td>
                      <span :class="['badge', row.state]">{{
                        stateName(row.state)
                      }}</span>
                    </td>
                    <td class="small">{{ time(row.updatedAt) }}</td>
                    <td>
                      <button @click="open(row)">
                        {{ t("打开", "Open") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!shown.length">
                    <td colspan="6" class="empty">
                      {{
                        t(
                          "暂无方案。创建方案并录入实际箱件。",
                          "No plans. Create a plan with actual cargo data.",
                        )
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <footer class="pagination">
              <button :disabled="page === 1" @click="page--">
                {{ t("上一页", "Previous") }}</button
              ><span
                >{{ page }} /
                {{ Math.max(1, Math.ceil(filtered.length / 15)) }}</span
              ><button :disabled="page * 15 >= filtered.length" @click="page++">
                {{ t("下一页", "Next") }}
              </button>
            </footer>
          </div>
        </section>
        <section
          v-else-if="section === 'plans' && detail && ready"
          class="plan-detail"
        >
          <button
            class="back"
            @click="
              detail = null;
              view = null;
            "
          >
            <ArrowLeft :size="16" />{{ t("全部方案", "All plans") }}
          </button>
          <header class="page-heading">
            <div>
              <span class="eyebrow">{{ plan.code }} · V{{ plan.version }}</span>
              <h1>{{ plan.title }}</h1>
              <span :class="['badge', plan.state]">{{
                stateName(plan.state)
              }}</span
              ><span class="muted small">
                · {{ t("指定复核", "Assigned review") }}：{{
                  person(plan.reviewerId)
                }}</span
              >
            </div>
            <div class="actions">
              <button :disabled="pending" @click="load">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
              ><button
                v-if="canDesign"
                :disabled="pending"
                @click="planDialog(true)"
              >
                {{ t("修改规格", "Edit specifications") }}</button
              ><button
                v-if="canDesign"
                class="primary"
                :disabled="pending || !detail.cargo.length"
                @click="calculate"
              >
                {{ t("计算新布局", "Calculate new layout") }}</button
              ><button
                v-if="canDesign && detail.active"
                :disabled="pending"
                @click="confirm('submit', actionLabels.submit())"
              >
                {{ actionLabels.submit() }}</button
              ><button
                v-if="
                  plan.state === 'PENDING' &&
                  can('review') &&
                  plan.reviewerId === me.id &&
                  !isEditor
                "
                class="primary"
                :disabled="pending"
                @click="confirm('approve', actionLabels.approve())"
              >
                {{ actionLabels.approve() }}</button
              ><button
                v-if="
                  plan.state === 'PENDING' &&
                  can('review') &&
                  plan.reviewerId === me.id &&
                  !isEditor
                "
                :disabled="pending"
                @click="confirm('return', actionLabels.return())"
              >
                {{ actionLabels.return() }}</button
              ><button
                v-if="plan.state === 'PENDING' && can('design')"
                :disabled="pending"
                @click="confirm('withdraw', actionLabels.withdraw())"
              >
                {{ actionLabels.withdraw() }}</button
              ><button
                v-if="plan.state === 'APPROVED' && can('design')"
                :disabled="pending"
                @click="confirm('reopen', actionLabels.reopen())"
              >
                {{ actionLabels.reopen() }}</button
              ><button
                v-if="canDesign"
                :disabled="pending"
                @click="confirm('cancel', actionLabels.cancel())"
              >
                {{ actionLabels.cancel() }}
              </button>
            </div>
          </header>
          <div class="spec-strip">
            <span
              >{{ plan.length }} × {{ plan.width }} × {{ plan.height }} mm</span
            ><span
              >{{ t("货物上限", "Cargo limit") }}
              {{ kg(plan.maxWeightGrams) }} kg</span
            ><span
              >{{ detail.cargo.reduce((s, c) => s + c.quantity, 0) }}
              {{ t("需求箱件", "required boxes") }}</span
            ><span>{{ plan.note || "—" }}</span>
          </div>
          <nav class="tabs">
            <button
              v-for="name in ['layout', 'cargo', 'history']"
              :key="name"
              :class="{ active: tab === name }"
              @click="tab = name"
            >
              {{
                name === "layout"
                  ? t("三维布局", "3D layout")
                  : name === "cargo"
                    ? t("箱件需求", "Cargo demand")
                    : t("版本与复核", "Versions & review")
              }}
            </button>
          </nav>
          <section v-if="tab === 'cargo'" class="panel">
            <header class="section-toolbar">
              <h2>{{ t("箱件清单", "Cargo list") }}</h2>
              <div class="actions">
                <button
                  v-if="canDesign"
                  :disabled="pending"
                  @click="csvDialog()"
                >
                  {{ t("CSV预检与替换", "Preview & replace CSV") }}</button
                ><button
                  v-if="canDesign"
                  :disabled="pending"
                  @click="cargoDialog()"
                >
                  <Plus :size="15" />{{ t("新增箱件", "Add cargo type") }}
                </button>
              </div>
            </header>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("编号 / 名称", "Code / name") }}</th>
                    <th>{{ t("外包络尺寸 mm", "Envelope size mm") }}</th>
                    <th>{{ t("数量", "Quantity") }}</th>
                    <th>{{ t("单件 kg", "Unit kg") }}</th>
                    <th>{{ t("水平旋转", "Horizontal rotation") }}</th>
                    <th>{{ t("累积上部限重 kg", "Upper-load limit kg") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in detail.cargo" :key="c.id">
                    <td>
                      <strong>{{ c.code }}</strong
                      ><span class="sub">{{ c.name }}</span>
                    </td>
                    <td>{{ c.length }} × {{ c.width }} × {{ c.height }}</td>
                    <td>{{ c.quantity }}</td>
                    <td>{{ kg(c.weightGrams) }}</td>
                    <td>
                      {{
                        c.rotate ? t("允许", "Allowed") : t("禁止", "Denied")
                      }}
                    </td>
                    <td>{{ kg(c.topLoadGrams) }}</td>
                    <td>
                      <div v-if="canDesign" class="actions">
                        <button :disabled="pending" @click="cargoDialog(c)">
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          :disabled="pending"
                          @click="
                            confirm(
                              'delete-cargo',
                              t('删除当前需求行', 'Delete current demand row'),
                              c,
                            )
                          "
                        >
                          {{ t("删除", "Delete") }}
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!detail.cargo.length">
                    <td colspan="7" class="empty">
                      {{ t("尚未录入箱件。", "No cargo entered.") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section v-if="tab === 'layout'">
            <div class="layout-toolbar">
              <label
                >{{ t("显示版本", "View revision")
                }}<select
                  :value="view?.id || ''"
                  @change="history($event.target.value)"
                >
                  <option value="">
                    {{ t("选择历史版本", "Select a revision") }}
                  </option>
                  <option
                    v-for="r in detail.revisions"
                    :key="r.id"
                    :value="r.id"
                  >
                    R{{ r.id }} · {{ r.kind }}
                    {{
                      r.id === detail.active?.id
                        ? t("（当前）", "(current)")
                        : ""
                    }}
                  </option>
                </select></label
              >
              <div v-if="view && can('export')" class="actions">
                <button
                  v-for="format in ['svg', 'csv', 'json']"
                  :key="format"
                  @click="exportFile(format)"
                >
                  <Download :size="14" />{{ format.toUpperCase() }}
                </button>
              </div>
            </div>
            <div v-if="!view" class="panel empty">
              {{
                t(
                  "尚无活动布局。录入需求后计算，旧版本在版本列表中保留。",
                  "No active layout. Enter demand and calculate; historical revisions remain available.",
                )
              }}
            </div>
            <template v-else
              ><p v-if="view.id !== detail.active?.id" class="notice">
                {{
                  t(
                    "正在查看历史快照，不能修改其坐标。",
                    "Viewing a historical snapshot; its coordinates cannot be modified.",
                  )
                }}
              </p>
              <div class="layout-metrics">
                <div>
                  <small>{{ t("装入 / 未装入", "Placed / unplaced") }}</small
                  ><strong
                    >{{ view.layout.placements.length }} /
                    {{ view.layout.unplaced.length }}</strong
                  >
                </div>
                <div>
                  <small>{{ t("空间利用率", "Space utilization") }}</small
                  ><strong>{{ view.layout.volumeUtilization }}%</strong>
                </div>
                <div>
                  <small>{{ t("已装入重量", "Placed weight") }}</small
                  ><strong
                    >{{ kg(view.layout.weightGrams) }} <small>kg</small></strong
                  >
                </div>
                <div>
                  <small>{{ t("历史版本", "Revision") }}</small
                  ><strong>R{{ view.id }}</strong>
                </div>
              </div>
              <div class="layout-grid">
                <section class="panel drawing">
                  <BoxView
                    :input="view.input"
                    :layout="view.layout"
                    :selected="selected"
                    @select="selected = $event"
                  />
                </section>
                <aside class="panel box-inspector">
                  <h2>
                    <Move3D :size="18" />{{ t("箱件位置", "Box position") }}
                  </h2>
                  <template v-if="box"
                    ><strong>{{ box.uid }}</strong>
                    <p>{{ box.name }}</p>
                    <dl v-if="box.dx">
                      <dt>{{ t("位置 X / Y / Z", "Position X / Y / Z") }}</dt>
                      <dd>{{ box.x }} / {{ box.y }} / {{ box.z }} mm</dd>
                      <dt>
                        {{ t("长 × 宽 × 高", "Length × width × height") }}
                      </dt>
                      <dd>{{ box.dx }} × {{ box.dy }} × {{ box.dz }} mm</dd>
                      <dt>{{ t("直接支撑", "Direct support") }}</dt>
                      <dd>
                        {{ box.supportUid || t("柜底", "Cargo-space floor") }}
                      </dd>
                      <dt>{{ t("重量", "Weight") }}</dt>
                      <dd>{{ kg(box.weightGrams) }} kg</dd>
                    </dl>
                    <p v-else class="warning">
                      {{
                        t(
                          "此箱件当前未装入。",
                          "This box is currently unplaced.",
                        )
                      }}
                    </p>
                    <div v-if="canMove" class="actions">
                      <button :disabled="pending" @click="moveDialog(box)">
                        {{
                          box.dx
                            ? t("调整位置", "Adjust position")
                            : t("尝试放入", "Try placement")
                        }}</button
                      ><button
                        v-if="box.dx"
                        :disabled="pending"
                        @click="
                          confirm(
                            'remove-box',
                            t(
                              '从当前布局移出此箱件',
                              'Remove this box from current layout',
                            ),
                            box,
                          )
                        "
                      >
                        {{ t("移到未装入", "Mark unplaced") }}
                      </button>
                    </div></template
                  >
                  <p v-else class="muted">
                    {{
                      t(
                        "点击图中的箱件或清单查看位置。",
                        "Select a box in the diagram or list.",
                      )
                    }}
                  </p>
                  <p class="small muted">
                    {{
                      t(
                        "布局是规划参考；现场核验通行、固定与实际货物强度。",
                        "Layout is a planning reference. Inspect physical access, restraint and cargo strength on site.",
                      )
                    }}
                  </p>
                </aside>
              </div>
              <div class="panel">
                <header class="section-toolbar">
                  <h2>{{ t("已装入位置清单", "Placed positions") }}</h2>
                  <small
                    >{{ view.layout.placements.length }}
                    {{ t("件", "boxes") }}</small
                  >
                </header>
                <div class="table-wrap position-table">
                  <table>
                    <thead>
                      <tr>
                        <th>{{ t("单件编号", "Box ID") }}</th>
                        <th>X / Y / Z mm</th>
                        <th>{{ t("尺寸 mm", "Size mm") }}</th>
                        <th>{{ t("支撑", "Support") }}</th>
                        <th>{{ t("重量 kg", "Weight kg") }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="b in view.layout.placements"
                        :key="b.uid"
                        :class="{ highlight: selected === b.uid }"
                        @click="selected = b.uid"
                      >
                        <td>
                          <button @click.stop="selected = b.uid">
                            {{ b.uid }}</button
                          ><span class="sub">{{ b.name }}</span>
                        </td>
                        <td>{{ b.x }} / {{ b.y }} / {{ b.z }}</td>
                        <td>{{ b.dx }} × {{ b.dy }} × {{ b.dz }}</td>
                        <td>{{ b.supportUid || t("柜底", "Floor") }}</td>
                        <td>{{ kg(b.weightGrams) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <div class="panel">
                <header class="section-toolbar">
                  <h2>{{ t("未装入清单", "Unplaced list") }}</h2>
                  <span :class="{ warning: view.layout.unplaced.length }"
                    >{{ view.layout.unplaced.length }}
                    {{ t("件", "boxes") }}</span
                  >
                </header>
                <div class="unplaced-list">
                  <button
                    v-for="b in view.layout.unplaced"
                    :key="b.uid"
                    @click="selected = b.uid"
                  >
                    {{ b.uid }} · {{ b.name }}
                  </button>
                  <p v-if="!view.layout.unplaced.length" class="muted">
                    {{
                      t(
                        "当前需求全部排入。",
                        "All requested boxes are placed in this layout.",
                      )
                    }}
                  </p>
                </div>
                <p class="small footnote">
                  {{
                    t(
                      "未装入仅表示当前方案未安排这些箱件；不证明不存在其他可行布局。",
                      "Unplaced means these boxes are absent from this plan; it does not prove no other feasible layout exists.",
                    )
                  }}
                </p>
              </div>
              <details class="panel">
                <summary>
                  {{ t("参考次序与一致性摘要", "Reference order & digest") }}
                </summary>
                <p class="small">
                  {{ view.layout.referenceOrder.join(" → ") }}
                </p>
                <p class="small muted">
                  {{
                    t(
                      "先低层、后高层，同层按X、Y排序；不是经过路径或装载设备验证的执行指令。",
                      "Lower layers first, then X/Y order. This is not a path- or equipment-validated loading instruction.",
                    )
                  }}
                </p>
                <code class="digest">{{ view.digest }}</code>
              </details></template
            >
          </section>
          <section v-if="tab === 'history'" class="panel">
            <header class="section-toolbar">
              <h2>
                {{ t("不可改写的版本与事件", "Retained revisions & events") }}
              </h2>
            </header>
            <div class="history-list">
              <article v-for="r in detail.revisions" :key="'r' + r.id">
                <strong>R{{ r.id }} · {{ r.kind }}</strong
                ><span>{{ time(r.createdAt) }} · {{ person(r.createdBy) }}</span
                ><button
                  @click="
                    history(r.id);
                    tab = 'layout';
                  "
                >
                  {{ t("查看快照", "View snapshot") }}</button
                ><code class="digest">{{ r.digest }}</code>
              </article>
              <article v-for="e in detail.events" :key="'e' + e.id">
                <strong>{{ e.action }}</strong
                ><span
                  >{{ person(e.actorId) }} · {{ time(e.createdAt) }} ·
                  {{ e.revisionId ? "R" + e.revisionId : "—" }}</span
                >
                <p>{{ e.note || "—" }}</p>
              </article>
            </div>
          </section>
        </section>
        <section v-else-if="section === 'reports' && ready">
          <header class="page-heading">
            <div>
              <h1>{{ t("规划统计", "Planning reports") }}</h1>
              <p class="muted">
                {{
                  t(
                    "仅统计当前权限范围和活动计算版本。",
                    "Only current scope and active computed revisions are counted.",
                  )
                }}
              </p>
            </div>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="layout-metrics">
            <div>
              <small>{{ t("全部方案", "All plans") }}</small
              ><strong>{{ report.plans }}</strong>
            </div>
            <div>
              <small>{{ t("已计算方案", "Computed plans") }}</small
              ><strong>{{ report.computedPlans }}</strong>
            </div>
            <div>
              <small>{{ t("装入箱件", "Placed boxes") }}</small
              ><strong>{{ report.placed }}</strong>
            </div>
            <div>
              <small>{{ t("未装入箱件", "Unplaced boxes") }}</small
              ><strong>{{ report.unplaced }}</strong>
            </div>
          </div>
          <div class="panel report-states">
            <div v-for="(count, name) in report.states" :key="name">
              <span :class="['badge', name]">{{ stateName(name) }}</span
              ><strong>{{ count }}</strong>
            </div>
          </div>
        </section>
        <section v-else-if="section === 'audit' && ready" class="panel">
          <header class="section-toolbar">
            <h1>{{ t("审计记录", "Audit trail") }}</h1>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("动作", "Action") }}</th>
                  <th>{{ t("账号", "Account") }}</th>
                  <th>{{ t("对象", "Object") }}</th>
                  <th>{{ t("时间", "Time") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in audit.slice().reverse()" :key="e.id">
                  <td>{{ e.action }}</td>
                  <td>{{ e.actor }}</td>
                  <td>{{ e.objectId }}</td>
                  <td>{{ time(e.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section v-else-if="section === 'about'" class="panel about">
          <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
          <h1>PackPlan 1.0.0</h1>
          <p>
            {{
              t(
                "知华科技（上海如静知华信息科技有限公司）",
                "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
              )
            }}
          </p>
          <p>
            {{
              t(
                "源码公开、非商业使用。未经公司书面授权不得商用。",
                "Source available for non-commercial learning and research. Written company authorization is required for commercial use.",
              )
            }}
          </p>
          <p>
            {{
              t(
                "商业授权或深度定制开发请联系知华科技。",
                "Contact ZhiHua Technology for commercial licensing or custom development.",
              )
            }}
          </p>
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >https://www.zhuatech.cn/</a
          >
          <p v-if="language === 'zh'">微信：zhuatech / zhuatech2</p>
          <div v-else class="contact-links">
            <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a
            ><a href="mailto:jack@zhuatech.cn">jack@zhuatech.cn</a
            ><a
              href="https://wa.me/8617521234993"
              target="_blank"
              rel="noopener"
              >WhatsApp +86 17521234993</a
            >
          </div>
        </section>
      </div>
    </main>
  </div>
  <div v-if="dialog" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.kind"
    >
      <header>
        <h2>
          {{
            dialog.kind === "plan"
              ? t("柜内规格与复核岗位", "Cargo-space specifications & reviewer")
              : dialog.kind === "cargo"
                ? t("箱件需求", "Cargo demand")
                : dialog.kind === "csv"
                  ? t("CSV预检与替换", "CSV preview & replacement")
                  : dialog.kind === "move"
                    ? t("调整单件坐标", "Adjust one box")
                    : dialog.kind === "password"
                      ? t("修改密码", "Change password")
                      : dialog.label
          }}
        </h2>
        <button
          :disabled="pending"
          aria-label="close dialog"
          @click="
            dialog = null;
            preview = null;
          "
        >
          <X :size="18" />
        </button>
      </header>
      <form @submit.prevent="saveDialog">
        <template v-if="dialog.kind === 'plan'"
          ><div class="form-grid">
            <label
              >{{ t("稳定编号", "Stable code")
              }}<input
                v-model="dialog.body.code"
                :disabled="!!dialog.body.id"
                required
                maxlength="30"
                pattern="[A-Za-z0-9_-]{2,30}" /></label
            ><label
              >{{ t("方案名称", "Plan title")
              }}<input
                v-model="dialog.body.title"
                required
                maxlength="120" /></label
            ><label
              >{{ t("所属部门", "Team")
              }}<select
                v-model="dialog.body.departmentId"
                :disabled="!!dialog.body.id"
                required
              >
                <option
                  v-for="d in options.departments"
                  :key="d.id"
                  :value="d.id"
                >
                  {{ d.name }}
                </option>
              </select></label
            ><label
              >{{ t("包装分类", "Packaging category")
              }}<select v-model="dialog.body.category" required>
                <option
                  v-for="d in options.categories"
                  :key="d.code"
                  :value="d.code"
                >
                  {{ language === "zh" ? d.name : d.nameEn }}
                </option>
              </select></label
            ><label
              >{{ t("内长 mm / X", "Internal length mm / X")
              }}<input
                v-model.number="dialog.body.length"
                type="number"
                min="1"
                max="20000"
                step="1"
                required /></label
            ><label
              >{{ t("内宽 mm / Y", "Internal width mm / Y")
              }}<input
                v-model.number="dialog.body.width"
                type="number"
                min="1"
                max="20000"
                step="1"
                required /></label
            ><label
              >{{ t("内高 mm / Z", "Internal height mm / Z")
              }}<input
                v-model.number="dialog.body.height"
                type="number"
                min="1"
                max="20000"
                step="1"
                required /></label
            ><label
              >{{ t("允许货物总重 kg", "Allowed total cargo kg")
              }}<input
                v-model="dialog.body.maxWeightKg"
                inputmode="decimal"
                required
            /></label>
          </div>
          <label
            >{{ t("指定独立复核", "Assigned independent reviewer")
            }}<select v-model="dialog.body.reviewerId" required>
              <option value="">
                {{ t("选择另一个复核账号", "Choose another reviewer") }}
              </option>
              <option v-for="a in reviewers" :key="a.id" :value="a.id">
                {{ a.name }}
              </option>
            </select></label
          >
          <p v-if="!reviewers.length" class="warning small">
            {{
              t(
                "请先在账号管理创建独立复核账号。",
                "Create an independent reviewer account first.",
              )
            }}
          </p>
          <label
            >{{ t("规格说明", "Specification note")
            }}<textarea
              v-model="dialog.body.note"
              maxlength="800"
            ></textarea></label
        ></template>
        <template v-else-if="dialog.kind === 'cargo'"
          ><div class="form-grid">
            <label
              >{{ t("箱件类型编号", "Cargo type code")
              }}<input
                v-model="dialog.body.code"
                :disabled="!!dialog.body.id"
                required
                pattern="[A-Z0-9_]{2,30}"
                maxlength="30" /></label
            ><label
              >{{ t("名称", "Name")
              }}<input
                v-model="dialog.body.name"
                required
                maxlength="120" /></label
            ><label
              >{{ t("长度 mm", "Length mm")
              }}<input
                v-model.number="dialog.body.length"
                type="number"
                min="1"
                max="20000"
                step="1"
                required /></label
            ><label
              >{{ t("宽度 mm", "Width mm")
              }}<input
                v-model.number="dialog.body.width"
                type="number"
                min="1"
                max="20000"
                step="1"
                required /></label
            ><label
              >{{ t("高度 mm", "Height mm")
              }}<input
                v-model.number="dialog.body.height"
                type="number"
                min="1"
                max="20000"
                step="1"
                required /></label
            ><label
              >{{ t("数量", "Quantity")
              }}<input
                v-model.number="dialog.body.quantity"
                type="number"
                min="1"
                max="120"
                step="1"
                required /></label
            ><label
              >{{ t("单件实际毛重 kg", "Unit gross weight kg")
              }}<input
                v-model="dialog.body.weightKg"
                inputmode="decimal"
                required /></label
            ><label
              >{{
                t("允许累积上部重量 kg", "Allowed cumulative upper weight kg")
              }}<input
                v-model="dialog.body.topLoadKg"
                inputmode="decimal"
                required
            /></label>
          </div>
          <label class="check"
            ><input v-model="dialog.body.rotate" type="checkbox" />{{
              t(
                "允许水平旋转90°，始终保持竖直",
                "Allow horizontal 90° rotation; keep upright",
              )
            }}</label
          >
          <p class="small muted">
            {{
              t(
                "上部重量填0表示不允许堆放其他箱件。尺寸包含包装或托盘的完整外包络。",
                "Zero upper load prohibits stacking. Dimensions include the full packaging or pallet envelope.",
              )
            }}
          </p></template
        >
        <template v-else-if="dialog.kind === 'csv'"
          ><p>
            {{
              t(
                "导入会替换当前草稿箱件，旧计算版本仍保留。",
                "Import replaces current draft cargo; historical calculations remain available.",
              )
            }}
          </p>
          <label
            >{{ t("UTF-8 CSV文件，最多64KB", "UTF-8 CSV file, at most 64KB")
            }}<input
              type="file"
              accept=".csv,text/csv"
              @change="fileCsv" /></label
          ><label
            >CSV<textarea
              v-model="dialog.body.csv"
              class="csv-input"
              :placeholder="'code,name,length_mm,width_mm,height_mm,quantity,weight_kg,rotate,top_load_kg'"
              required
              @input="preview = null"
            ></textarea></label
          ><button
            type="button"
            :disabled="pending || !dialog.body.csv"
            @click="previewCsv"
          >
            {{ t("逐行预检", "Validate preview") }}
          </button>
          <div v-if="preview" class="preview">
            <strong
              >{{ preview.rows.length }} {{ t("种，合计", "types; total") }}
              {{ preview.rows.reduce((s, c) => s + c.quantity, 0) }}
              {{ t("件", "boxes") }}</strong
            >
            <p v-for="c in preview.rows" :key="c.code">
              {{ c.code }} · {{ c.name }} · {{ c.quantity }} × {{ c.length }}/{{
                c.width
              }}/{{ c.height }} mm · {{ kg(c.weightGrams) }} kg
            </p>
          </div></template
        >
        <template v-else-if="dialog.kind === 'move'"
          ><p>
            <strong>{{ dialog.body.uid }}</strong> · {{ dialog.cargo.name }}
          </p>
          <div class="form-grid">
            <label v-for="axis in ['x', 'y', 'z']" :key="axis"
              >{{ axis.toUpperCase() }} mm<input
                v-model.number="dialog.body[axis]"
                type="number"
                min="0"
                max="20000"
                step="1"
                required
            /></label>
          </div>
          <label class="check"
            ><input
              v-model="dialog.body.rotated"
              type="checkbox"
              :disabled="!dialog.cargo.rotate"
            />{{ t("水平旋转90°", "Rotate horizontally 90°") }}</label
          >
          <p class="small muted">
            {{
              t(
                "全部箱件必须保持边界、支撑、载重与非重叠有效，失败时不会部分保存。",
                "The complete layout must satisfy bounds, support, load and non-overlap. Failure saves nothing.",
              )
            }}
          </p></template
        >
        <template v-else-if="dialog.kind === 'password'"
          ><label
            >{{ t("原密码", "Current password")
            }}<input
              v-model="dialog.body.oldPassword"
              type="password"
              autocomplete="current-password"
              required /></label
          ><label
            >{{ t("新密码", "New password")
            }}<input
              v-model="dialog.body.newPassword"
              type="password"
              autocomplete="new-password"
              required
              minlength="12"
              maxlength="72" /></label
        ></template>
        <template v-else-if="dialog.kind === 'action'"
          ><p>
            {{ dialog.label }} {{ dialog.item?.uid || dialog.item?.code || "" }}
          </p>
          <p v-if="dialog.action === 'remove-box'" class="warning">
            {{
              t(
                "此箱件将进入未装入清单，需求数量不变。",
                "This box moves to the unplaced list; demand quantity is unchanged.",
              )
            }}
          </p>
          <p v-if="dialog.action === 'reopen'" class="warning">
            {{
              t(
                "活动批准布局将失效，修改后需要重新计算和复核。历史仍保留。",
                "The active approval is invalidated. Changes require calculation and review again. History remains.",
              )
            }}
          </p>
          <template
            v-if="
              dialog.action === 'submit' &&
              detail.active?.layout.unplaced.length
            "
            ><p class="warning">
              {{ detail.active.layout.unplaced.length }}
              {{
                t(
                  "件未装入，须明确排除并说明。",
                  "boxes unplaced. Explicitly acknowledge their exclusion.",
                )
              }}
            </p>
            <label class="check"
              ><input
                v-model="dialog.body.acknowledgeUnplaced"
                type="checkbox"
                required
              />{{
                t(
                  "已核对并确认未装入清单",
                  "I checked and acknowledge the unplaced list",
                )
              }}</label
            ></template
          ><label v-if="!['delete-cargo', 'remove-box'].includes(dialog.action)"
            >{{ t("复核或变更说明", "Review or change note")
            }}<textarea
              v-model="dialog.body.note"
              :required="
                dialog.action !== 'submit' ||
                !!detail.active?.layout.unplaced.length
              "
              :minlength="
                dialog.action !== 'submit' ||
                detail.active?.layout.unplaced.length
                  ? 4
                  : 0
              "
              maxlength="800"
            ></textarea></label
        ></template>
        <footer>
          <button
            type="button"
            :disabled="pending"
            @click="
              dialog = null;
              preview = null;
            "
          >
            {{ t("取消", "Cancel") }}</button
          ><button
            class="primary"
            :disabled="
              pending ||
              (dialog.kind === 'csv' && preview?.csv !== dialog.body.csv)
            "
          >
            {{
              pending
                ? t("处理中…", "Working…")
                : dialog.kind === "csv"
                  ? t("确认替换箱件", "Confirm cargo replacement")
                  : t("确认保存", "Confirm save")
            }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
