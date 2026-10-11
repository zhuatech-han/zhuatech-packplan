// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import jakarta.persistence.*;

/** 计划箱件需求，坐标毫米与重量克，稳定编号和明确承压许可。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cargo_line")
public class CargoLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long planId;

  @Column(nullable = false, length = 30)
  public String code;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false)
  public int length;

  @Column(nullable = false)
  public int width;

  @Column(nullable = false)
  public int height;

  @Column(nullable = false)
  public int quantity;

  @Column(nullable = false)
  public long weightGrams;

  @Column(nullable = false)
  public boolean rotate;

  @Column(nullable = false)
  public long topLoadGrams;
}
