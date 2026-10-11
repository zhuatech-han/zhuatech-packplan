// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 实际HTTP/Security/Flyway/JPA回归，测试数据均为TEST且私有随机凭证不打印。官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PlanIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:packplan;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("packplan.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired Bootstrap bootstrap;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, planner, reviewer, assigned, outsider;
  long plannerId, reviewerId, assignedId, planId;
  String suffix;

  Map<String, Object> m(Object... values) {
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) out.put(values[i].toString(), values[i + 1]);
    return out;
  }

  MockHttpServletRequestBuilder req(String path, String method, Map<String, Object> b) {
    var r =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (b != null)
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    return r;
  }

  MvcResult result(MockHttpSession session, String path, String method, Map<String, Object> b)
      throws Exception {
    return mvc.perform(req(path, method, b).session(session)).andReturn();
  }

  JsonNode call(MockHttpSession session, String path, String method, Map<String, Object> b)
      throws Exception {
    var r = result(session, path, method, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(
      MockHttpSession s, String p, String method, Map<String, Object> b, int status, String code)
      throws Exception {
    var r = result(s, p, method, b);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).get("code").asString());
  }

  MockHttpSession login(String username) throws Exception {
    var r =
        mvc.perform(req("/auth/login", "POST", m("username", username, "password", PASSWORD)))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long user(String name, int role, long dept) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "roleId",
                role,
                "departmentId",
                dept,
                "password",
                PASSWORD,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  @BeforeEach
  void setup() throws Exception {
    jdbc.update("update pack_plan set active_revision_id=null");
    for (String table :
        List.of(
            "plan_event",
            "plan_editor",
            "cargo_line",
            "plan_revision",
            "pack_plan",
            "audit_event",
            "account",
            "role_permission",
            "department",
            "nav_menu",
            "dictionary_entry",
            "system_setting",
            "permission",
            "access_role")) {
      jdbc.update("delete from " + table);
      if (!table.equals("role_permission"))
        jdbc.execute("alter table " + table + " alter column id restart with 1");
    }
    bootstrap.run(null);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    plannerId = user("plan-" + suffix, 2, 1);
    reviewerId = user("review-" + suffix, 3, 1);
    assignedId = user("assigned-" + suffix, 4, 1);
    long dept =
        call(
                admin,
                "/admin/departments",
                "POST",
                m("name", "TEST outside", "zone", "UTC", "enabled", true))
            .get("id")
            .asLong();
    user("outside-" + suffix, 2, dept);
    planner = login("plan-" + suffix);
    reviewer = login("review-" + suffix);
    assigned = login("assigned-" + suffix);
    outsider = login("outside-" + suffix);
    planId =
        call(
                planner,
                "/plans",
                "POST",
                m(
                    "code",
                    "TEST_" + suffix,
                    "title",
                    "TEST export load",
                    "departmentId",
                    1,
                    "reviewerId",
                    reviewerId,
                    "category",
                    "CARTON",
                    "length",
                    200,
                    "width",
                    100,
                    "height",
                    100,
                    "maxWeightKg",
                    "100",
                    "note",
                    "TEST dimensions verified"))
            .get("id")
            .asLong();
    addCargo(8);
  }

  long version() throws Exception {
    return call(planner, "/plans/" + planId, "GET", null).get("plan").get("version").asLong();
  }

  long addCargo(int quantity) throws Exception {
    return call(
            planner,
            "/plans/" + planId + "/cargo",
            "POST",
            m(
                "version",
                version(),
                "code",
                "AA",
                "name",
                "TEST =box",
                "length",
                50,
                "width",
                50,
                "height",
                50,
                "quantity",
                quantity,
                "weightKg",
                "1",
                "rotate",
                true,
                "topLoadKg",
                "5"))
        .get("version")
        .asLong();
  }

  JsonNode calculate() throws Exception {
    return call(planner, "/plans/" + planId + "/calculate", "POST", m("version", version()));
  }

  JsonNode act(MockHttpSession who, String action, Map<String, Object> b) throws Exception {
    b.put("version", version());
    return call(who, "/plans/" + planId + "/" + action, "POST", b);
  }

  @Test
  void completeIndependentReviewAndFreeze() throws Exception {
    var r = calculate();
    assertEquals(8, r.get("layout").get("placements").size());
    act(planner, "submit", m("note", "TEST ready"));
    act(reviewer, "approve", m("note", "TEST independent dimensional review"));
    fail(
        planner,
        "/plans/" + planId + "/calculate",
        "POST",
        m("version", version()),
        409,
        "STATE_INVALID");
    var e =
        call(
            planner,
            "/plans/" + planId + "/revisions/" + r.get("id").asLong() + "/export.json",
            "GET",
            null);
    assertEquals("APPROVED", e.get("currentPlanState").asString());
    assertTrue(e.get("isCurrentRevision").asBoolean());
    assertEquals(3, e.get("reviewEvents").size());
  }

  @Test
  void staleVersionRejectedWithoutNewSnapshot() throws Exception {
    long old = version();
    calculate();
    fail(
        planner,
        "/plans/" + planId + "/calculate",
        "POST",
        m("version", old),
        409,
        "VERSION_CONFLICT");
    assertEquals(1, call(planner, "/plans/" + planId, "GET", null).get("revisions").size());
  }

  @Test
  void submitRequiresCalculation() throws Exception {
    fail(
        planner,
        "/plans/" + planId + "/submit",
        "POST",
        m("version", version()),
        409,
        "CALCULATE_FIRST");
  }

  @Test
  void partialNeedsExplicitAcknowledgement() throws Exception {
    var d = call(planner, "/plans/" + planId, "GET", null).get("plan");
    var b = json.convertValue(d, Map.class);
    b.put("maxWeightKg", "2");
    call(planner, "/plans/" + planId, "PUT", b);
    calculate();
    fail(
        planner,
        "/plans/" + planId + "/submit",
        "POST",
        m("version", version(), "note", "TEST only these two boxes"),
        409,
        "UNPLACED_ACK_REQUIRED");
    act(planner, "submit", m("note", "TEST remaining boxes excluded", "acknowledgeUnplaced", true));
  }

  @Test
  void otherReviewerCannotApprove() throws Exception {
    calculate();
    act(planner, "submit", m("note", "TEST ready"));
    fail(
        assigned,
        "/plans/" + planId + "/approve",
        "POST",
        m("version", version(), "note", "TEST attempt"),
        403,
        "OUT_OF_SCOPE");
  }

  @Test
  void assignedReviewerSeesOnlyAssignedPlan() throws Exception {
    var d = call(planner, "/plans/" + planId, "GET", null).get("plan");
    var b = json.convertValue(d, Map.class);
    b.put("reviewerId", assignedId);
    b.put("maxWeightKg", "100");
    call(planner, "/plans/" + planId, "PUT", b);
    assertEquals(1, call(assigned, "/plans", "GET", null).size());
    calculate();
    act(planner, "submit", m("note", "TEST ready"));
    act(assigned, "approve", m("note", "TEST independent assigned review"));
    fail(assigned, "/admin/users", "GET", null, 403, "FORBIDDEN");
  }

  @Test
  void plannerCannotSelfReviewAfterRoleChange() throws Exception {
    var users = call(admin, "/admin/users", "GET", null);
    var a =
        users
            .valueStream()
            .filter(x -> x.get("id").asLong() == plannerId)
            .findFirst()
            .orElseThrow();
    var b = json.convertValue(a, Map.class);
    b.put("roleId", 1);
    b.put("password", "");
    call(admin, "/admin/users/" + plannerId, "PUT", b);
    var d = call(planner, "/plans/" + planId, "GET", null).get("plan");
    var edit = json.convertValue(d, Map.class);
    edit.put("reviewerId", plannerId);
    edit.put("maxWeightKg", "100");
    fail(planner, "/plans/" + planId, "PUT", edit, 409, "INDEPENDENT_REQUIRED");
  }

  @Test
  void crossDepartmentReadAndExportDenied() throws Exception {
    var r = calculate();
    fail(outsider, "/plans/" + planId, "GET", null, 403, "OUT_OF_SCOPE");
    fail(
        outsider,
        "/plans/" + planId + "/revisions/" + r.get("id").asLong() + "/export.json",
        "GET",
        null,
        403,
        "OUT_OF_SCOPE");
    assertEquals(0, call(outsider, "/plans", "GET", null).size());
  }

  @Test
  void malformedCsvDoesNotReplaceExistingLines() throws Exception {
    var before = call(planner, "/plans/" + planId, "GET", null);
    fail(
        planner,
        "/plans/" + planId + "/import-csv",
        "POST",
        m("version", version(), "csv", CargoCsv.HEADER + "\nAA,TEST,50.5,20,20,1,1,true,0"),
        400,
        "INVALID_INPUT");
    var after = call(planner, "/plans/" + planId, "GET", null);
    assertEquals(before.get("cargo"), after.get("cargo"));
    assertEquals(before.get("plan").get("version"), after.get("plan").get("version"));
  }

  @Test
  void previewDoesNotWriteAndReplaceIsAtomic() throws Exception {
    String csv = CargoCsv.HEADER + "\nBB,TEST replacement,80,60,50,2,1.125,false,0\n";
    long v = version();
    call(planner, "/plans/" + planId + "/preview-csv", "POST", m("csv", csv));
    assertEquals(v, version());
    call(planner, "/plans/" + planId + "/import-csv", "POST", m("version", v, "csv", csv));
    var detail = call(planner, "/plans/" + planId, "GET", null);
    assertEquals("BB", detail.get("cargo").get(0).get("code").asString());
    assertEquals(1125, detail.get("cargo").get(0).get("weightGrams").asLong());
  }

  @Test
  void duplicateCsvRollsBackAllLines() throws Exception {
    String row = "AA,TEST,20,20,20,1,1,false,0\n";
    fail(
        planner,
        "/plans/" + planId + "/import-csv",
        "POST",
        m("version", version(), "csv", CargoCsv.HEADER + "\n" + row + row),
        400,
        "CARGO_CODE_INVALID");
    assertEquals(
        8,
        call(planner, "/plans/" + planId, "GET", null).get("cargo").get(0).get("quantity").asInt());
  }

  @Test
  void manualOverlapAndForgedDimensionsRejectedOrRebuilt() throws Exception {
    calculate();
    fail(
        planner,
        "/plans/" + planId + "/manual",
        "POST",
        m(
            "version",
            version(),
            "placements",
            List.of(
                m("uid", "AA-001", "x", 0, "y", 0, "z", 0, "rotated", false),
                m("uid", "AA-002", "x", 1, "y", 0, "z", 0, "rotated", false))),
        400,
        "BOX_OVERLAP");
    var r =
        call(
            planner,
            "/plans/" + planId + "/manual",
            "POST",
            m(
                "version",
                version(),
                "placements",
                List.of(
                    m(
                        "uid",
                        "AA-001",
                        "x",
                        0,
                        "y",
                        0,
                        "z",
                        0,
                        "rotated",
                        false,
                        "weightGrams",
                        0,
                        "dx",
                        1))));
    assertEquals(1000, r.get("layout").get("weightGrams").asLong());
    assertEquals(50, r.get("layout").get("placements").get(0).get("dx").asInt());
    assertEquals(7, r.get("layout").get("unplaced").size());
  }

  @Test
  void historicalSnapshotsSurviveReopen() throws Exception {
    var r = calculate();
    long rev = r.get("id").asLong();
    act(planner, "submit", m("note", "TEST ready"));
    act(reviewer, "approve", m("note", "TEST approved"));
    act(planner, "reopen", m("note", "TEST packing revised"));
    assertTrue(call(planner, "/plans/" + planId, "GET", null).get("active").isNull());
    assertEquals(r, call(planner, "/plans/" + planId + "/revisions/" + rev, "GET", null));
  }

  @Test
  void disabledReviewerBlocksSubmission() throws Exception {
    calculate();
    var a =
        call(admin, "/admin/users", "GET", null)
            .valueStream()
            .filter(x -> x.get("id").asLong() == reviewerId)
            .findFirst()
            .orElseThrow();
    var b = json.convertValue(a, Map.class);
    b.put("enabled", false);
    b.put("password", "");
    call(admin, "/admin/users/" + reviewerId, "PUT", b);
    fail(
        planner,
        "/plans/" + planId + "/submit",
        "POST",
        m("version", version(), "note", "TEST ready"),
        409,
        "REVIEWER_UNAVAILABLE");
    fail(reviewer, "/plans", "GET", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void revokedPermissionImmediatelyBlocksOldSession() throws Exception {
    var role = call(admin, "/admin/roles", "GET", null).get(1);
    var b = json.convertValue(role, Map.class);
    b.put("permissions", List.of("plans", "export"));
    call(admin, "/admin/roles/" + role.get("id").asLong(), "PUT", b);
    fail(
        planner,
        "/plans/" + planId + "/calculate",
        "POST",
        m("version", version()),
        403,
        "FORBIDDEN");
  }

  @Test
  void csrfRequiredForRealWrite() throws Exception {
    var r =
        mvc.perform(
                post("/api/plans/" + planId + "/calculate")
                    .session(planner)
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("version", version()))))
            .andReturn();
    assertEquals(403, r.getResponse().getStatus());
  }

  @Test
  void lastAdminCannotBeDisabled() throws Exception {
    var a = call(admin, "/admin/users", "GET", null).get(0);
    var b = json.convertValue(a, Map.class);
    b.put("enabled", false);
    b.put("password", "");
    fail(admin, "/admin/users/1", "PUT", b, 409, "LAST_ADMIN");
    assertTrue(call(admin, "/auth/me", "GET", null).has("id"));
  }

  @Test
  void accountDepartmentIsLockedOnceReferenced() throws Exception {
    var a =
        call(admin, "/admin/users", "GET", null)
            .valueStream()
            .filter(x -> x.get("id").asLong() == plannerId)
            .findFirst()
            .orElseThrow();
    var b = json.convertValue(a, Map.class);
    b.put("departmentId", 2);
    b.put("password", "");
    fail(admin, "/admin/users/" + plannerId, "PUT", b, 409, "ACCOUNT_ASSIGNED");
  }

  @Test
  void activeLayoutInvalidatesOnCargoChange() throws Exception {
    calculate();
    var detail = call(planner, "/plans/" + planId, "GET", null);
    var c = json.convertValue(detail.get("cargo").get(0), Map.class);
    c.put("version", version());
    c.put("weightKg", "1");
    c.put("topLoadKg", "5");
    c.put("quantity", 7);
    call(planner, "/plans/" + planId + "/cargo/" + c.get("id"), "PUT", c);
    var d = call(planner, "/plans/" + planId, "GET", null);
    assertTrue(d.get("active").isNull());
    assertEquals(1, d.get("revisions").size());
  }

  @Test
  void allUnplacedCannotBeSubmitted() throws Exception {
    calculate();
    call(
        planner,
        "/plans/" + planId + "/manual",
        "POST",
        m("version", version(), "placements", List.of()));
    fail(
        planner,
        "/plans/" + planId + "/submit",
        "POST",
        m("version", version(), "note", "TEST nothing fits", "acknowledgeUnplaced", true),
        409,
        "NO_PLACED_BOXES");
  }

  @Test
  void csvAndSvgExportsReflectPersistedSnapshot() throws Exception {
    var r = calculate();
    String base = "/plans/" + planId + "/revisions/" + r.get("id").asLong() + "/export.";
    var csv = result(planner, base + "csv", "GET", null);
    assertEquals(200, csv.getResponse().getStatus());
    assertTrue(csv.getResponse().getContentAsString().contains("AA-001"));
    var svg = result(planner, base + "svg", "GET", null);
    assertEquals(200, svg.getResponse().getStatus());
    assertTrue(svg.getResponse().getContentAsString().contains("<svg"));
    assertFalse(svg.getResponse().getContentAsString().contains("<script"));
  }

  @Test
  void initializedNavigationIsEnabledAndScoped() throws Exception {
    assertEquals(6, call(admin, "/auth/me", "GET", null).get("menus").size());
    assertEquals(2, call(planner, "/auth/me", "GET", null).get("menus").size());
    assertEquals(1, call(assigned, "/auth/me", "GET", null).get("menus").size());
  }
}
