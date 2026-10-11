// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import jakarta.persistence.*;
import java.time.Instant;

/** 业务操作追加事件，记录动作、版本、账号与说明，不保存密码或文件。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "plan_event")
public class PlanEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long planId;

  @Column(nullable = true)
  public Long revisionId;

  @Column(nullable = false)
  public Long actorId;

  @Column(nullable = false, length = 30)
  public String action;

  @Column(nullable = false, length = 800)
  public String note;

  @Column(nullable = false)
  public Instant createdAt;
}
