// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import java.util.*;

/** 无脚本、自有轴测投影打印图，数值来自已核验快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class SvgReport {
  static String xml(String v) {
    return Objects.toString(v, "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  /** 同比例投影X/Y/Z，图不参与几何或承重判断。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String render(
      String code, Long rev, PackingEngine.Input in, PackingEngine.Layout layout) {
    var c = in.space();
    double maxX = (c.length() + c.width()) * .866,
        maxY = (c.length() + c.width()) * .5 + c.height();
    double scale = Math.min(880 / maxX, 410 / maxY);
    StringBuilder s =
        new StringBuilder(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 620\"><rect width=\"1000\" height=\"620\" fill=\"white\"/>");
    s.append(
            "<g font-family=\"sans-serif\" fill=\"#233746\"><text x=\"35\" y=\"35\" font-size=\"21\">")
        .append(xml(code))
        .append(" · R")
        .append(rev)
        .append("</text><text x=\"35\" y=\"61\" font-size=\"13\">")
        .append(layout.placements().size())
        .append(" placed / ")
        .append(layout.unplaced().size())
        .append(" unplaced · ")
        .append(layout.volumeUtilization())
        .append("% volume · positions in mm</text></g>");
    s.append("<polygon points=\"")
        .append(
            points(
                scale,
                c.width(),
                c.height(),
                new int[][] {
                  {0, 0, 0}, {c.length(), 0, 0}, {c.length(), c.width(), 0}, {0, c.width(), 0}
                }))
        .append("\" fill=\"#eef3f5\" stroke=\"#8aa0af\"/>");
    List<PackingEngine.Placement> boxes = new ArrayList<>(layout.placements());
    boxes.sort(Comparator.comparingLong(p -> (long) p.x() + p.y() + p.z()));
    String[] colors = {"#82b0b8", "#9bb7d7", "#d3b38b", "#a8bb9a", "#b5a8ca", "#c29ea1"};
    for (var p : boxes) {
      int x = p.x(), y = p.y(), z = p.z(), X = x + p.dx(), Y = y + p.dy(), Z = z + p.dz();
      String color = colors[Math.floorMod(p.code().hashCode(), colors.length)];
      s.append("<g stroke=\"#334b59\" stroke-width=\"0.7\" fill=\"")
          .append(color)
          .append("\"><title>")
          .append(xml(p.uid() + " " + p.name()))
          .append("</title>");
      for (int[][] face :
          new int[][][] {
            {{x, y, Z}, {X, y, Z}, {X, Y, Z}, {x, Y, Z}},
            {{X, y, z}, {X, Y, z}, {X, Y, Z}, {X, y, Z}},
            {{x, Y, z}, {X, Y, z}, {X, Y, Z}, {x, Y, Z}}
          })
        s.append("<polygon points=\"")
            .append(points(scale, c.width(), c.height(), face))
            .append("\"/>");
      s.append("</g>");
    }
    s.append(
            "<text x=\"35\" y=\"570\" font-size=\"13\" font-family=\"sans-serif\">X length / Y width / Z height · ")
        .append(c.length())
        .append(" × ")
        .append(c.width())
        .append(" × ")
        .append(c.height())
        .append(
            " mm</text><text x=\"35\" y=\"595\" font-size=\"12\" font-family=\"sans-serif\">Layout reference only. Inspect physical access, restraint and cargo strength on site.</text></svg>");
    return s.toString();
  }

  static String points(double scale, int width, int height, int[][] points) {
    StringJoiner out = new StringJoiner(" ");
    for (int[] p : points)
      out.add(
          String.format(
              Locale.ROOT,
              "%.2f,%.2f",
              65 + width * .866 * scale + (p[0] - p[1]) * .866 * scale,
              105 + height * scale + (p[0] + p[1]) * .5 * scale - p[2] * scale));
    return out.toString();
  }
}
