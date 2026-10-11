// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 有界UTF-8 CSV解析，逐行核验后才允许原子替换箱件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class CargoCsv {
  public static final String HEADER =
      "code,name,length_mm,width_mm,height_mm,quantity,weight_kg,rotate,top_load_kg";

  /** 公斤精确转换为克；不接受负数或四位小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long grams(Object n, boolean zero) {
    try {
      long g = new BigDecimal(Objects.toString(n, "")).movePointRight(3).longValueExact();
      if (g < (zero ? 0 : 1) || g > 100_000_000) throw new IllegalArgumentException();
      return g;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_WEIGHT");
    }
  }

  /** 表头固定，支持引号逗号和双引号转义，拒绝未闭合引号、额外列和控制字符。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<PackingEngine.Cargo> parse(String csv) {
    if (csv == null || csv.getBytes(StandardCharsets.UTF_8).length > 65536)
      throw new Problem(413, "CSV_LIMIT");
    if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false, ended = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
            ended = true;
          }
        } else cell.append(c);
        continue;
      }
      if (c == '"') {
        if (cell.length() != 0 || ended) throw new Problem(400, "CSV_INVALID");
        quoted = true;
      } else if (c == ',' || c == '\n' || c == '\r') {
        row.add(cell.toString());
        cell.setLength(0);
        ended = false;
        if (c != ',') {
          if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
          rows.add(row);
          row = new ArrayList<>();
        }
      } else {
        if (ended) throw new Problem(400, "CSV_INVALID");
        cell.append(c);
      }
    }
    if (quoted) throw new Problem(400, "CSV_INVALID");
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    if (rows.isEmpty() || !String.join(",", rows.removeFirst()).equals(HEADER) || rows.size() > 20)
      throw new Problem(400, "CSV_INVALID");
    List<PackingEngine.Cargo> out = new ArrayList<>();
    for (List<String> r : rows) {
      if (r.size() != 9) throw new Problem(400, "CSV_INVALID");
      if (!Set.of("true", "false").contains(r.get(7))) throw new Problem(400, "CSV_INVALID");
      out.add(
          new PackingEngine.Cargo(
              Rules.text(r.get(0), 30, true),
              Rules.text(r.get(1), 120, true),
              Rules.integer(r.get(2), 1, 20000),
              Rules.integer(r.get(3), 1, 20000),
              Rules.integer(r.get(4), 1, 20000),
              Rules.integer(r.get(5), 1, 120),
              grams(r.get(6), false),
              Boolean.parseBoolean(r.get(7)),
              grams(r.get(8), true)));
    }
    if (out.isEmpty()) throw new Problem(400, "CSV_INVALID");
    return out;
  }
}
