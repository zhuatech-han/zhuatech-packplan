// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import jakarta.persistence.*;

/** 永久编辑人关系；角色切换和重新指派不消除复核利益冲突。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "plan_editor")
public class PlanEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long planId;

  @Column(nullable = false)
  public Long accountId;
}
