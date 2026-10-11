// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 独立方案、不可改写计算快照、指定复核与范围导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class PlanService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  /** 注入事务目录和可测UTC时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PlanService(Store db, AccessService access, AdminService admin, Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
  }

  Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  void write(String permission) {
    admin.lock();
    access.require(permission);
  }

  boolean visible(PackPlan p) {
    var a = access.current();
    String s = access.role().scope;
    return s.equals("ALL")
        || Objects.equals(p.departmentId, a.departmentId)
            && (s.equals("DEPARTMENT")
                || Objects.equals(p.reviewerId, a.id)
                || Objects.equals(p.createdBy, a.id)
                || edited(p, a.id));
  }

  boolean edited(PackPlan p, Long account) {
    return !db.query(
            PlanEditor.class, "from PlanEditor where planId=?1 and accountId=?2", p.id, account)
        .isEmpty();
  }

  PackPlan plan(Long id) {
    access.require("plans");
    var p = db.get(PackPlan.class, id);
    if (!visible(p)) throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  void version(PackPlan p, Map<String, Object> b) {
    Rules.check(p.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  void draft(PackPlan p) {
    Rules.check(p.state.equals("DRAFT"), "STATE_INVALID");
  }

  List<CargoLine> lines(Long id) {
    return db.query(CargoLine.class, "from CargoLine where planId=?1 order by code", id);
  }

  PackingEngine.Input input(PackPlan p) {
    return new PackingEngine.Input(
        new PackingEngine.Space(p.length, p.width, p.height, p.maxWeightGrams),
        lines(p.id).stream()
            .map(
                l ->
                    new PackingEngine.Cargo(
                        l.code,
                        l.name,
                        l.length,
                        l.width,
                        l.height,
                        l.quantity,
                        l.weightGrams,
                        l.rotate,
                        l.topLoadGrams))
            .toList());
  }

  String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encode controlled snapshot", e);
    }
  }

  <T> T decode(String s, Class<T> type) {
    try {
      return json.readValue(s, type);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored snapshot", e);
    }
  }

  String digest(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  void editor(PackPlan p) {
    var id = access.current().id;
    if (!edited(p, id)) {
      var e = new PlanEditor();
      e.planId = p.id;
      e.accountId = id;
      db.save(e);
    }
  }

  void event(PackPlan p, String action, String note) {
    var e = new PlanEvent();
    e.planId = p.id;
    e.revisionId = p.activeRevisionId;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = now();
    db.save(e);
    access.audit("PLAN_" + action, p.id, p.departmentId);
  }

  void changed(PackPlan p) {
    p.version++;
    p.updatedAt = now();
  }

  void invalidated(PackPlan p) {
    p.activeRevisionId = null;
    changed(p);
    editor(p);
  }

  void reviewer(PackPlan p, Long id) {
    var a = db.get(Account.class, id);
    var r = db.get(AccessRole.class, a.roleId);
    Rules.check(
        a.enabled
            && db.get(Department.class, a.departmentId).enabled
            && r.permissions.contains("review")
            && r.permissions.contains("plans")
            && (r.scope.equals("ALL") || Objects.equals(a.departmentId, p.departmentId)),
        "REVIEWER_UNAVAILABLE");
    Rules.check(!Objects.equals(p.createdBy, id) && !edited(p, id), "INDEPENDENT_REQUIRED");
  }

  Map<String, Object> meta(PackPlan p) {
    return Map.of(
        "code",
        p.code,
        "title",
        p.title,
        "departmentId",
        p.departmentId,
        "reviewerId",
        p.reviewerId,
        "category",
        p.category,
        "note",
        p.note);
  }

  PlanRevision revision(PackPlan p, Long id) {
    var r = db.get(PlanRevision.class, id);
    Rules.check(Objects.equals(r.planId, p.id), "REVISION_MISMATCH");
    Rules.check(
        r.digest.equals(digest(r.inputJson + "\n" + r.metadataJson + "\n" + r.layoutJson)),
        "SNAPSHOT_CHANGED");
    return r;
  }

  Map<String, Object> revisionView(PlanRevision r) {
    return Map.of(
        "id",
        r.id,
        "kind",
        r.kind,
        "createdBy",
        r.createdBy,
        "createdAt",
        r.createdAt,
        "digest",
        r.digest,
        "input",
        decode(r.inputJson, PackingEngine.Input.class),
        "metadata",
        decode(r.metadataJson, Map.class),
        "layout",
        decode(r.layoutJson, PackingEngine.Layout.class));
  }

  /** 范围内全部方案，有界列表供客户端查询分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<PackPlan> plans() {
    access.require("plans");
    return db.all(PackPlan.class).stream().filter(this::visible).toList();
  }

  /** 明细和历史先校验方案范围，参与账号仅输出显示名称。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var p = plan(id);
    List<PlanRevision> revisions =
        db.query(PlanRevision.class, "from PlanRevision where planId=?1 order by id", id);
    var events = db.query(PlanEvent.class, "from PlanEvent where planId=?1 order by id", id);
    Set<Long> people = new LinkedHashSet<>();
    people.add(p.createdBy);
    people.add(p.reviewerId);
    events.forEach(e -> people.add(e.actorId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("plan", p);
    out.put("cargo", lines(id));
    out.put(
        "revisions",
        revisions.stream()
            .map(
                r ->
                    Map.of(
                        "id",
                        r.id,
                        "kind",
                        r.kind,
                        "createdAt",
                        r.createdAt,
                        "createdBy",
                        r.createdBy,
                        "digest",
                        r.digest))
            .toList());
    out.put("events", events);
    out.put(
        "editors",
        db.query(PlanEditor.class, "from PlanEditor where planId=?1", id).stream()
            .map(e -> e.accountId)
            .toList());
    out.put(
        "people",
        people.stream()
            .map(
                i -> {
                  var a = db.get(Account.class, i);
                  return Map.of("id", a.id, "name", a.displayName);
                })
            .toList());
    out.put(
        "active",
        p.activeRevisionId == null ? null : revisionView(revision(p, p.activeRevisionId)));
    return out;
  }

  /** 历史快照可读取但无更新接口；先验证归属。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> history(Long id, Long rev) {
    return revisionView(revision(plan(id), rev));
  }

  /** 规划所需最小目录；本人范围不开放部门账号清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> options() {
    access.require("plans");
    var a = access.current();
    boolean all = access.role().scope.equals("ALL"),
        assigned = access.role().scope.equals("ASSIGNED");
    var accounts =
        db.all(Account.class).stream()
            .filter(
                u ->
                    u.enabled
                        && db.get(Department.class, u.departmentId).enabled
                        && (all || Objects.equals(u.departmentId, a.departmentId))
                        && (!assigned || Objects.equals(u.id, a.id)))
            .map(
                u ->
                    Map.of(
                        "id",
                        u.id,
                        "name",
                        u.displayName,
                        "departmentId",
                        u.departmentId,
                        "reviewer",
                        db.get(AccessRole.class, u.roleId).permissions.contains("review")))
            .toList();
    return Map.of(
        "accounts",
        accounts,
        "departments",
        db.all(Department.class).stream()
            .filter(d -> d.enabled && (all || Objects.equals(d.id, a.departmentId)))
            .toList(),
        "categories",
        db.all(DictionaryEntry.class).stream()
            .filter(d -> d.enabled && d.type.equals("PACKAGING"))
            .toList());
  }

  /** 创建或修订输入，稳定编号与部门不改；修订使活动布局失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PackPlan save(Long id, Map<String, Object> b) {
    write("design");
    PackPlan p = id == null ? new PackPlan() : plan(id);
    if (id == null) {
      Rules.check(db.all(PackPlan.class).size() < admin.setting("max_plans"), "RESOURCE_LIMIT");
      p.code = Rules.text(b.get("code"), 30, true).toUpperCase(Locale.ROOT);
      Rules.check(p.code.matches("[A-Z0-9_-]{2,30}"), "INVALID_INPUT");
      p.departmentId = Rules.id(b.get("departmentId"));
      Rules.check(access.department(p.departmentId), "OUT_OF_SCOPE");
      p.createdBy = access.current().id;
      p.createdAt = now();
    } else {
      version(p, b);
      draft(p);
      Rules.check(
          p.code.equals(b.get("code"))
              && Objects.equals(p.departmentId, Rules.id(b.get("departmentId"))),
          "IDENTITY_LOCKED");
    }
    Rules.check(db.get(Department.class, p.departmentId).enabled, "DEPARTMENT_DISABLED");
    p.title = Rules.text(b.get("title"), 120, true);
    p.category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        !db.query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='PACKAGING' and code=?1 and enabled=true",
                p.category)
            .isEmpty(),
        "CATEGORY_INVALID");
    p.length = Rules.integer(b.get("length"), 1, 20000);
    p.width = Rules.integer(b.get("width"), 1, 20000);
    p.height = Rules.integer(b.get("height"), 1, 20000);
    p.maxWeightGrams = CargoCsv.grams(b.get("maxWeightKg"), false);
    p.note = Rules.text(b.get("note"), 800, false);
    Long reviewer = Rules.id(b.get("reviewerId"));
    Rules.check(!reviewer.equals(access.current().id), "INDEPENDENT_REQUIRED");
    if (id == null) Rules.check(!reviewer.equals(p.createdBy), "INDEPENDENT_REQUIRED");
    reviewer(p, reviewer);
    p.reviewerId = reviewer;
    if (id == null) {
      p.updatedAt = now();
      db.save(p);
      db.flush();
      editor(p);
    } else invalidated(p);
    event(p, id == null ? "CREATE" : "INPUT_CHANGE", "");
    return p;
  }

  PackingEngine.Cargo cargo(Map<String, Object> b) {
    return new PackingEngine.Cargo(
        Rules.text(b.get("code"), 30, true),
        Rules.text(b.get("name"), 120, true),
        Rules.integer(b.get("length"), 1, 20000),
        Rules.integer(b.get("width"), 1, 20000),
        Rules.integer(b.get("height"), 1, 20000),
        Rules.integer(b.get("quantity"), 1, 120),
        CargoCsv.grams(b.get("weightKg"), false),
        Rules.flag(b.get("rotate")),
        CargoCsv.grams(b.get("topLoadKg"), true));
  }

  void assign(CargoLine l, PackingEngine.Cargo c) {
    l.code = c.code();
    l.name = c.name();
    l.length = c.length();
    l.width = c.width();
    l.height = c.height();
    l.quantity = c.quantity();
    l.weightGrams = c.weightGrams();
    l.rotate = c.rotate();
    l.topLoadGrams = c.topLoadGrams();
  }

  void validateCargo(PackPlan p, List<PackingEngine.Cargo> c) {
    PackingEngine.validateInput(
        new PackingEngine.Input(
            new PackingEngine.Space(p.length, p.width, p.height, p.maxWeightGrams), c));
  }

  /** 增删改箱件均以方案版本控制；服务端核验数量和唯一编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PackPlan saveCargo(Long id, Long lineId, Map<String, Object> b) {
    write("design");
    var p = plan(id);
    version(p, b);
    draft(p);
    var c = cargo(b);
    var rows = lines(id);
    var l = lineId == null ? new CargoLine() : db.get(CargoLine.class, lineId);
    if (lineId != null) {
      Rules.check(Objects.equals(l.planId, id), "OUT_OF_SCOPE");
      Rules.check(l.code.equals(c.code()), "IDENTITY_LOCKED");
    }
    List<PackingEngine.Cargo> combined = new ArrayList<>(input(p).cargo());
    if (lineId != null) combined.removeIf(x -> x.code().equals(l.code));
    combined.add(c);
    validateCargo(p, combined);
    l.planId = id;
    assign(l, c);
    if (lineId == null) db.save(l);
    invalidated(p);
    event(p, "CARGO_CHANGE", c.code());
    return p;
  }

  /** 删除草稿箱件保留旧快照；最后一项可删，计算必须有需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PackPlan deleteCargo(Long id, Long lineId, Map<String, Object> b) {
    write("design");
    var p = plan(id);
    version(p, b);
    draft(p);
    var l = db.get(CargoLine.class, lineId);
    Rules.check(Objects.equals(l.planId, id), "OUT_OF_SCOPE");
    String code = l.code;
    db.delete(l);
    invalidated(p);
    event(p, "CARGO_DELETE", code);
    return p;
  }

  /** CSV先完整解析预检，未写入数据库；必须在同一计划范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<PackingEngine.Cargo> preview(Long id, Map<String, Object> b) {
    access.require("design");
    var p = plan(id);
    var c = CargoCsv.parse(Objects.toString(b.get("csv"), ""));
    validateCargo(p, c);
    return c;
  }

  /** 显式覆盖当前箱件，整批成功或回滚；旧计算版本仍保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PackPlan importCsv(Long id, Map<String, Object> b) {
    write("design");
    var p = plan(id);
    version(p, b);
    draft(p);
    var c = CargoCsv.parse(Objects.toString(b.get("csv"), ""));
    validateCargo(p, c);
    for (var row : lines(id)) db.delete(row);
    for (var item : c) {
      var l = new CargoLine();
      l.planId = id;
      assign(l, item);
      db.save(l);
    }
    invalidated(p);
    event(p, "CSV_REPLACE", String.valueOf(c.size()));
    return p;
  }

  PlanRevision saveRevision(
      PackPlan p, PackingEngine.Input in, PackingEngine.Layout layout, String kind) {
    Rules.check(
        db.query(PlanRevision.class, "from PlanRevision where planId=?1", p.id).size()
            < admin.setting("max_revisions"),
        "REVISION_LIMIT");
    var r = new PlanRevision();
    r.planId = p.id;
    r.createdBy = access.current().id;
    r.kind = kind;
    r.inputJson = encode(in);
    r.metadataJson = encode(meta(p));
    r.layoutJson = encode(layout);
    r.digest = digest(r.inputJson + "\n" + r.metadataJson + "\n" + r.layoutJson);
    r.createdAt = now();
    db.save(r);
    db.flush();
    p.activeRevisionId = r.id;
    changed(p);
    editor(p);
    event(p, kind, "");
    return r;
  }

  /** 计算保存新快照，不以浏览器结果或上一版代替本次计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> calculate(Long id, Map<String, Object> b) {
    write("design");
    var p = plan(id);
    version(p, b);
    draft(p);
    var in = input(p);
    return revisionView(saveRevision(p, in, PackingEngine.calculate(in), "CALCULATE"));
  }

  /** 手工调整完整位置清单；省略的单件明确变为未装入，任何非法几何整批拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> manual(Long id, Map<String, Object> b) {
    write("design");
    var p = plan(id);
    version(p, b);
    draft(p);
    Rules.check(p.activeRevisionId != null, "CALCULATE_FIRST");
    if (!(b.get("placements") instanceof List<?> list) || list.size() > 120)
      throw new Problem(400, "INVALID_INPUT");
    List<PackingEngine.Move> moves = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) throw new Problem(400, "INVALID_INPUT");
      moves.add(
          new PackingEngine.Move(
              Rules.text(m.get("uid"), 40, true),
              Rules.integer(m.get("x"), 0, 20000),
              Rules.integer(m.get("y"), 0, 20000),
              Rules.integer(m.get("z"), 0, 20000),
              Rules.flag(m.get("rotated"))));
    }
    var in = input(p);
    return revisionView(saveRevision(p, in, PackingEngine.manual(in, moves), "MANUAL"));
  }

  /** 送审、指定人复核、撤回与重新修订遵守不可自审和冻结规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PackPlan action(Long id, String action, Map<String, Object> b) {
    write(Set.of("APPROVE", "RETURN").contains(action) ? "review" : "design");
    var p = plan(id);
    version(p, b);
    String note = Rules.text(b.get("note"), 800, false);
    if (!Set.of("APPROVE", "RETURN").contains(action)) editor(p);
    switch (action) {
      case "SUBMIT" -> {
        draft(p);
        Rules.check(p.activeRevisionId != null, "CALCULATE_FIRST");
        var r = revision(p, p.activeRevisionId);
        Rules.check(
            r.inputJson.equals(encode(input(p))) && r.metadataJson.equals(encode(meta(p))),
            "SNAPSHOT_CHANGED");
        var layout = decode(r.layoutJson, PackingEngine.Layout.class);
        Rules.check(!layout.placements().isEmpty(), "NO_PLACED_BOXES");
        reviewer(p, p.reviewerId);
        if (!layout.unplaced().isEmpty()) {
          Rules.check(Boolean.TRUE.equals(b.get("acknowledgeUnplaced")), "UNPLACED_ACK_REQUIRED");
          Rules.check(note.length() >= 4, "NOTE_REQUIRED");
        }
        p.state = "PENDING";
      }
      case "APPROVE", "RETURN" -> {
        Rules.check(p.state.equals("PENDING"), "STATE_INVALID");
        Rules.check(Objects.equals(p.reviewerId, access.current().id), "REVIEWER_ONLY");
        reviewer(p, p.reviewerId);
        Rules.check(note.length() >= 4, "NOTE_REQUIRED");
        revision(p, p.activeRevisionId);
        p.state = action.equals("APPROVE") ? "APPROVED" : "DRAFT";
      }
      case "WITHDRAW" -> {
        Rules.check(p.state.equals("PENDING"), "STATE_INVALID");
        Rules.check(note.length() >= 4, "NOTE_REQUIRED");
        p.state = "DRAFT";
      }
      case "REOPEN" -> {
        Rules.check(p.state.equals("APPROVED"), "STATE_INVALID");
        Rules.check(note.length() >= 4, "NOTE_REQUIRED");
        p.state = "DRAFT";
        p.activeRevisionId = null;
        editor(p);
      }
      case "CANCEL" -> {
        draft(p);
        Rules.check(note.length() >= 4, "NOTE_REQUIRED");
        p.state = "CANCELLED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    changed(p);
    event(p, action, note);
    return p;
  }

  /** 当前范围状态及已计算需求/装入数量，不把未计算计划当成零缺项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    access.require("reports");
    var rows = plans();
    Map<String, Long> states = new LinkedHashMap<>();
    for (String s : List.of("DRAFT", "PENDING", "APPROVED", "CANCELLED"))
      states.put(s, rows.stream().filter(p -> p.state.equals(s)).count());
    int computed = 0, placed = 0, unplaced = 0;
    for (var p : rows)
      if (p.activeRevisionId != null) {
        var l = decode(revision(p, p.activeRevisionId).layoutJson, PackingEngine.Layout.class);
        computed++;
        placed += l.placements().size();
        unplaced += l.unplaced().size();
      }
    return Map.of(
        "plans",
        rows.size(),
        "states",
        states,
        "computedPlans",
        computed,
        "placed",
        placed,
        "unplaced",
        unplaced);
  }

  /** 审计最小元数据按部门范围过滤，指派范围不能读取整个部门审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<AuditEvent> audit() {
    access.require("audit");
    Rules.check(!access.role().scope.equals("ASSIGNED"), "OUT_OF_SCOPE");
    return db.all(AuditEvent.class).stream()
        .filter(a -> access.department(a.departmentId))
        .toList();
  }

  /** 只导出已授权快照并附其复核事件；不将旧布局误标为当前批准方案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> export(Long id, Long rev) {
    access.require("export");
    var p = plan(id);
    var r = revision(p, rev);
    return Map.of(
        "currentPlanState",
        p.state,
        "isCurrentRevision",
        Objects.equals(p.activeRevisionId, rev),
        "revision",
        revisionView(r),
        "reviewEvents",
        db.query(
            PlanEvent.class,
            "from PlanEvent where planId=?1 and revisionId=?2 order by id",
            id,
            rev));
  }

  /** CSV按快照输入重建尺寸和状态，文本阻止公式执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv(Long id, Long rev) {
    var e = export(id, rev);
    var r = revision(plan(id), rev);
    var l = decode(r.layoutJson, PackingEngine.Layout.class);
    StringBuilder s =
        new StringBuilder(
            "\uFEFFrevision_id,box_id,name,status,x_mm,y_mm,z_mm,length_mm,width_mm,height_mm,weight_kg,support_id\r\n");
    for (var box : l.placements())
      s.append(rev)
          .append(',')
          .append(Rules.csv(box.uid()))
          .append(',')
          .append(Rules.csv(box.name()))
          .append(",PLACED,")
          .append(box.x())
          .append(',')
          .append(box.y())
          .append(',')
          .append(box.z())
          .append(',')
          .append(box.dx())
          .append(',')
          .append(box.dy())
          .append(',')
          .append(box.dz())
          .append(',')
          .append(BigDecimal.valueOf(box.weightGrams(), 3))
          .append(',')
          .append(Rules.csv(box.supportUid()))
          .append("\r\n");
    for (var box : l.unplaced())
      s.append(rev)
          .append(',')
          .append(Rules.csv(box.uid()))
          .append(',')
          .append(Rules.csv(box.name()))
          .append(",UNPLACED,,,,,,,,\r\n");
    return s.toString();
  }

  /** 可打印SVG来自服务端已核验历史坐标；不包含脚本或外部图片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String svg(Long id, Long rev) {
    export(id, rev);
    var p = plan(id);
    var r = revision(p, rev);
    return SvgReport.render(
        p.code,
        rev,
        decode(r.inputJson, PackingEngine.Input.class),
        decode(r.layoutJson, PackingEngine.Layout.class));
  }
}
