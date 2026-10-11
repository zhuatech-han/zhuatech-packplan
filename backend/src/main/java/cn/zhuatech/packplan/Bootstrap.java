// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次目录与随机外部管理员，业务库为空。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  static final List<String> CODES =
      List.of(
          "plans", "design", "review", "export", "reports", "users", "roles", "settings", "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  /** 读取配置，不记录初始密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${packplan.admin-username}") String username,
      @Value("${packplan.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 只在空身份库初始化；重启不覆盖权限和业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-zA-Z0-9_.-]{3,60}"), "INVALID_INPUT");
    for (String code : CODES) {
      var p = new Permission();
      p.code = code;
      p.name = code;
      db.save(p);
    }
    var d = new Department();
    d.name = "主部门 / Main department";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var admin = role("管理员 / Administrator", "ALL", CODES);
    role("方案设计 / Planner", "DEPARTMENT", List.of("plans", "design", "export", "reports"));
    role("独立复核 / Reviewer", "DEPARTMENT", List.of("plans", "review", "export", "reports", "audit"));
    role("指定复核 / Assigned reviewer", "ASSIGNED", List.of("plans", "review", "export"));
    role("部门查阅 / Reader", "DEPARTMENT", List.of("plans", "export", "reports"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    db.save(a);
    String[][] menus = {
      {"plans", "装柜方案", "Load plans", "plans"},
      {"reports", "规划统计", "Planning reports", "reports"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles & permissions", "roles"},
      {"settings", "部门与设置", "Teams & settings", "settings"},
      {"audit", "审计记录", "Audit trail", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var c :
        new String[][] {
          {"CARTON", "纸箱", "Carton"},
          {"CRATE", "木箱", "Crate"},
          {"PALLET", "带托盘外包络", "Pallet envelope"}
        }) {
      var x = new DictionaryEntry();
      x.type = "PACKAGING";
      x.code = c[0];
      x.name = c[1];
      x.nameEn = c[2];
      db.save(x);
    }
    setting("max_plans", "500");
    setting("max_revisions", "30");
  }

  private AccessRole role(String name, String scope, List<String> codes) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions.addAll(codes);
    return db.save(r);
  }

  private void setting(String code, String value) {
    var s = new SystemSetting();
    s.code = code;
    s.value = value;
    db.save(s);
  }
}
