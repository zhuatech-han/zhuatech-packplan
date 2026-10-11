// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import jakarta.persistence.*;
import java.time.Instant;

/** 柜内规格、指定复核岗位与冻结状态；版本拒绝覆盖并保留历史。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pack_plan")
public class PackPlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 30)
  public String code;

  @Column(nullable = false, length = 120)
  public String title;

  @Column(nullable = false)
  public Long departmentId;

  @Column(nullable = false)
  public Long createdBy;

  @Column(nullable = false)
  public Long reviewerId;

  @Column(nullable = false, length = 60)
  public String category;

  @Column(nullable = false, length = 20)
  public String state = "DRAFT";

  @Column(nullable = false)
  public long version = 1;

  @Column(nullable = false)
  public int length;

  @Column(nullable = false)
  public int width;

  @Column(nullable = false)
  public int height;

  @Column(nullable = false)
  public long maxWeightGrams;

  @Column(nullable = false, length = 800)
  public String note = "";

  @Column(nullable = true)
  public Long activeRevisionId;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;
}
