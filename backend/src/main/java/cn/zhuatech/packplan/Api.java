// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 固定白名单业务与目录接口，权限和几何全部由事务服务检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class Api {
  final PlanService service;
  final AdminService admin;

  /** 连接业务和身份目录服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Api(PlanService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 查询当前授权范围，不接受动态实体名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind}")
  public Object read(@PathVariable String kind) {
    return switch (kind) {
      case "plans" -> service.plans();
      case "options" -> service.options();
      case "reports" -> service.report();
      case "audit" -> service.audit();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 创建真实空方案；不会制造装载或复核事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans")
  public Object create(@RequestBody Map<String, Object> b) {
    return service.save(null, b);
  }

  /** 草稿修改会使活动计算失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/plans/{id}")
  public Object save(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.save(id, b);
  }

  /** 有权限的用户可读取版本与历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/plans/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 箱件新增以方案版本控制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans/{id}/cargo")
  public Object addCargo(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.saveCargo(id, null, b);
  }

  /** 稳定箱件编号不变，输入变化需要重新计算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/plans/{id}/cargo/{line}")
  public Object cargo(
      @PathVariable Long id, @PathVariable Long line, @RequestBody Map<String, Object> b) {
    return service.saveCargo(id, line, b);
  }

  /** 删除当前草稿需求，不删除已经保存的旧布局。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/plans/{id}/cargo/{line}")
  public Object delete(
      @PathVariable Long id, @PathVariable Long line, @RequestBody Map<String, Object> b) {
    return service.deleteCargo(id, line, b);
  }

  /** 固定动作入口，不允许浏览器自报复核状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/plans/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return switch (action) {
      case "calculate" -> service.calculate(id, b);
      case "manual" -> service.manual(id, b);
      case "preview-csv" -> service.preview(id, b);
      case "import-csv" -> service.importCsv(id, b);
      default -> service.action(id, action.toUpperCase(Locale.ROOT), b);
    };
  }

  /** 历史快照只能读取，不能被更新或冒用其他方案编号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/plans/{id}/revisions/{rev}")
  public Object revision(@PathVariable Long id, @PathVariable Long rev) {
    return service.history(id, rev);
  }

  /** 三种导出都使用当前登录范围，不接收用户文件路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/plans/{id}/revisions/{rev}/export.{format}")
  public ResponseEntity<?> export(
      @PathVariable Long id, @PathVariable Long rev, @PathVariable String format) {
    return switch (format) {
      case "json" ->
          ResponseEntity.ok()
              .header("Cache-Control", "no-store")
              .header(
                  "Content-Disposition",
                  "attachment; filename=packplan-" + id + "-r" + rev + ".json")
              .body(service.export(id, rev));
      case "csv" ->
          file(
              "text/csv;charset=UTF-8",
              "packplan-" + id + "-r" + rev + ".csv",
              service.csv(id, rev));
      case "svg" ->
          file(
              "image/svg+xml;charset=UTF-8",
              "packplan-" + id + "-r" + rev + ".svg",
              service.svg(id, rev));
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  ResponseEntity<String> file(String type, String name, String content) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(type))
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "attachment; filename=" + name)
        .body(content);
  }

  /** 后台固定目录受完整管理员范围保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object directory(@PathVariable String kind) {
    return kind.equals("options") ? admin.options() : admin.read(kind);
  }

  /** 新目录对象通过严格字段校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object add(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 后台修改保留最后管理员并拒绝旧版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }
}
