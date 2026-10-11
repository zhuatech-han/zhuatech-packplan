// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import jakarta.persistence.*;
import java.time.Instant;

/** 计算或手工版本快照；布局和输入不可改写，SHA256只验证一致性。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "plan_revision")
public class PlanRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long planId;

  @Column(nullable = false)
  public Long createdBy;

  @Column(nullable = false, length = 20)
  public String kind;

  @Column(nullable = false, columnDefinition = "longtext")
  public String inputJson;

  @Column(nullable = false, columnDefinition = "longtext")
  public String metadataJson;

  @Column(nullable = false, columnDefinition = "longtext")
  public String layoutJson;

  @Column(nullable = false, length = 64)
  public String digest;

  @Column(nullable = false)
  public Instant createdAt;
}
