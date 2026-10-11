// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import java.math.*;
import java.util.*;

/** 整数毫米、有界轴对齐箱件规划和独立几何核验。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
public final class PackingEngine {
  public static final String VERSION = "SUPPORTED-EXTREME-POINT-1";

  /** 实测柜内尺寸与允许货物总重，不包括箱体皮重。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Space(int length, int width, int height, long maxWeightGrams) {}

  /** 一种矩形包装：竖直高度固定、水平旋转许可和最大累积上部载重。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Cargo(
      String code,
      String name,
      int length,
      int width,
      int height,
      int quantity,
      long weightGrams,
      boolean rotate,
      long topLoadGrams) {}

  /** 布局输入只包含稳定箱件和柜内规格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(Space space, List<Cargo> cargo) {}

  /** 手工位置只接受编号、坐标与旋转；重量尺寸由服务端重建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Move(String uid, int x, int y, int z, boolean rotated) {}

  /** 已核验单件，坐标单位毫米，重量单位克。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Placement(
      String uid,
      String code,
      String name,
      int x,
      int y,
      int z,
      int dx,
      int dy,
      int dz,
      boolean rotated,
      long weightGrams,
      String supportUid) {}

  /** 未排入不是不可装证明：仅表示当前方案没有排入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Unplaced(String uid, String code, String name, String reason) {}

  /** 数量守恒结果、空间利用率和参考次序；不执行现场装载。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Layout(
      String algorithm,
      List<Placement> placements,
      List<Unplaced> unplaced,
      long weightGrams,
      long volumeMm3,
      BigDecimal volumeUtilization,
      List<String> referenceOrder) {}

  record Unit(String uid, Cargo cargo) {}

  record Point(int x, int y, int z) {}

  /** 约束尺寸与数量使计算、内存和整数乘积都有界；不隐式舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validateInput(Input in) {
    if (in == null || in.space == null || in.cargo == null) fail("INVALID_INPUT");
    Space s = in.space;
    dim(s.length);
    dim(s.width);
    dim(s.height);
    if (s.maxWeightGrams < 1 || s.maxWeightGrams > 100_000_000) fail("INVALID_WEIGHT");
    if (in.cargo.isEmpty() || in.cargo.size() > 20) fail("CARGO_LIMIT");
    Set<String> codes = new HashSet<>();
    int count = 0;
    for (Cargo c : in.cargo) {
      if (c == null || c.code == null || !c.code.matches("[A-Z0-9_]{2,30}") || !codes.add(c.code))
        fail("CARGO_CODE_INVALID");
      Rules.text(c.name, 120, true);
      dim(c.length);
      dim(c.width);
      dim(c.height);
      if (c.quantity < 1 || c.quantity > 120) fail("CARGO_LIMIT");
      count += c.quantity;
      if (c.weightGrams < 1
          || c.weightGrams > 100_000_000
          || c.topLoadGrams < 0
          || c.topLoadGrams > 100_000_000) fail("INVALID_WEIGHT");
    }
    if (count > 120) fail("CARGO_LIMIT");
  }

  static void dim(int n) {
    if (n < 1 || n > 20000) fail("INVALID_DIMENSION");
  }

  static void fail(String code) {
    throw new Problem(400, code);
  }

  static long volume(Cargo c) {
    return (long) c.length * c.width * c.height;
  }

  static List<Unit> units(Input in) {
    List<Unit> out = new ArrayList<>();
    for (Cargo c : in.cargo)
      for (int n = 1; n <= c.quantity; n++)
        out.add(new Unit(c.code + "-" + String.format(Locale.ROOT, "%03d", n), c));
    return out;
  }

  static Placement place(Unit u, Move m, String support) {
    Cargo c = u.cargo;
    return new Placement(
        u.uid,
        c.code,
        c.name,
        m.x,
        m.y,
        m.z,
        m.rotated ? c.width : c.length,
        m.rotated ? c.length : c.width,
        c.height,
        m.rotated,
        c.weightGrams,
        support);
  }

  static boolean overlaps(Placement a, Placement b) {
    return a.x < b.x + b.dx
        && b.x < a.x + a.dx
        && a.y < b.y + b.dy
        && b.y < a.y + a.dy
        && a.z < b.z + b.dz
        && b.z < a.z + a.dz;
  }

  static boolean supports(Placement a, Placement b) {
    return a.z + a.dz == b.z
        && a.x <= b.x
        && a.y <= b.y
        && a.x + a.dx >= b.x + b.dx
        && a.y + a.dy >= b.y + b.dy;
  }

  /** 所有手工和自动位置走同一校验：边界、旋转、唯一编号、支撑链及累积载重。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Placement> validatePositions(Input in, List<Move> moves) {
    validateInput(in);
    if (moves == null || moves.size() > 120) fail("CARGO_LIMIT");
    Map<String, Unit> known = new LinkedHashMap<>();
    for (Unit u : units(in)) known.put(u.uid, u);
    return validateKnown(in, moves, known);
  }

  static List<Placement> validateKnown(Input in, List<Move> moves, Map<String, Unit> known) {
    Set<String> ids = new HashSet<>();
    List<Placement> out = new ArrayList<>();
    long weight = 0;
    for (Move m : moves) {
      if (m == null || !known.containsKey(m.uid) || !ids.add(m.uid)) fail("BOX_ID_INVALID");
      Unit u = known.get(m.uid);
      if (m.rotated && !u.cargo.rotate) fail("ROTATION_DENIED");
      Placement p = place(u, m, null);
      if (p.x < 0
          || p.y < 0
          || p.z < 0
          || p.x > in.space.length - p.dx
          || p.y > in.space.width - p.dy
          || p.z > in.space.height - p.dz) fail("OUT_OF_BOUNDS");
      for (Placement other : out) if (overlaps(p, other)) fail("BOX_OVERLAP");
      out.add(p);
      weight += p.weightGrams;
    }
    if (weight > in.space.maxWeightGrams) fail("WEIGHT_EXCEEDED");
    Map<String, String> parents = new HashMap<>();
    for (Placement p : out)
      if (p.z > 0) {
        Placement support = out.stream().filter(b -> supports(b, p)).findFirst().orElse(null);
        if (support == null) fail("UNSUPPORTED_BOX");
        parents.put(p.uid, support.uid);
      }
    Map<String, Long> loads = new HashMap<>();
    for (Placement p : out) {
      String parent = parents.get(p.uid);
      while (parent != null) {
        loads.merge(parent, p.weightGrams, Long::sum);
        parent = parents.get(parent);
      }
    }
    for (var e : loads.entrySet())
      if (e.getValue() > known.get(e.getKey()).cargo.topLoadGrams) fail("STACK_LOAD_EXCEEDED");
    return out.stream()
        .map(
            p ->
                place(
                    known.get(p.uid),
                    new Move(p.uid, p.x, p.y, p.z, p.rotated),
                    parents.get(p.uid)))
        .toList();
  }

  /** 计算当前布局剩余箱件与确定性参考次序，数量和总体积由源输入重建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Layout manual(Input in, List<Move> moves) {
    return summarize(in, validatePositions(in, moves), "MANUAL-CHECKED-1");
  }

  static Layout summarize(Input in, List<Placement> placed, String algorithm) {
    Set<String> ids = new HashSet<>();
    long v = 0, w = 0;
    for (Placement p : placed) {
      ids.add(p.uid);
      v += (long) p.dx * p.dy * p.dz;
      w += p.weightGrams;
    }
    List<Unplaced> missing = new ArrayList<>();
    for (Unit u : units(in))
      if (!ids.contains(u.uid))
        missing.add(new Unplaced(u.uid, u.cargo.code, u.cargo.name, "NOT_PLACED_IN_THIS_PLAN"));
    var sorted =
        placed.stream()
            .sorted(
                Comparator.comparingInt(Placement::z)
                    .thenComparingInt(Placement::x)
                    .thenComparingInt(Placement::y)
                    .thenComparing(Placement::uid))
            .map(Placement::uid)
            .toList();
    return new Layout(
        algorithm,
        placed,
        missing,
        w,
        v,
        BigDecimal.valueOf(v)
            .multiply(BigDecimal.valueOf(100))
            .divide(
                BigDecimal.valueOf((long) in.space.length * in.space.width * in.space.height),
                2,
                RoundingMode.HALF_UP),
        sorted);
  }

  /** 四种排序比较的自主启发式；支撑完整才接受候选，未找到位置会明确留在未装入清单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Layout calculate(Input in) {
    validateInput(in);
    Layout best = null;
    Map<String, Unit> known = new LinkedHashMap<>();
    for (Unit u : units(in)) known.put(u.uid, u);
    for (int strategy = 0; strategy < 4; strategy++) {
      List<Unit> order = new ArrayList<>(units(in));
      Comparator<Unit> sort =
          switch (strategy) {
            case 0 -> Comparator.<Unit>comparingLong(u -> volume(u.cargo)).reversed();
            case 1 -> Comparator.<Unit>comparingLong(u -> u.cargo.weightGrams).reversed();
            case 2 ->
                Comparator.<Unit>comparingLong(u -> (long) u.cargo.length * u.cargo.width)
                    .reversed();
            default ->
                Comparator.<Unit>comparingLong(u -> u.cargo.topLoadGrams)
                    .reversed()
                    .thenComparing(Comparator.<Unit>comparingLong(u -> volume(u.cargo)).reversed());
          };
      order.sort(sort.thenComparing(Unit::uid));
      List<Move> accepted = new ArrayList<>();
      List<Placement> placed = new ArrayList<>();
      for (Unit u : order) {
        Set<Point> points = new LinkedHashSet<>();
        points.add(new Point(0, 0, 0));
        for (Placement p : placed) {
          points.add(new Point(p.x + p.dx, p.y, p.z));
          points.add(new Point(p.x, p.y + p.dy, p.z));
          points.add(new Point(p.x, p.y, p.z + p.dz));
          points.add(new Point(p.x + p.dx, 0, 0));
          points.add(new Point(0, p.y + p.dy, 0));
        }
        List<Point> candidates =
            points.stream()
                .filter(p -> p.x < in.space.length && p.y < in.space.width && p.z < in.space.height)
                .sorted(
                    Comparator.comparingInt(Point::z)
                        .thenComparingInt(Point::x)
                        .thenComparingInt(Point::y))
                .toList();
        boolean found = false;
        for (Point p : candidates) {
          for (int rotation = 0; rotation < (u.cargo.rotate ? 2 : 1); rotation++) {
            Move m = new Move(u.uid, p.x, p.y, p.z, rotation == 1);
            var candidateBox = place(u, m, null);
            if (candidateBox.x > in.space.length - candidateBox.dx
                || candidateBox.y > in.space.width - candidateBox.dy
                || candidateBox.z > in.space.height - candidateBox.dz
                || placed.stream().anyMatch(b -> overlaps(candidateBox, b))) continue;
            if (candidateBox.z > 0 && placed.stream().noneMatch(b -> supports(b, candidateBox)))
              continue;
            List<Move> attempt = new ArrayList<>(accepted);
            attempt.add(m);
            try {
              List<Placement> checked = validateKnown(in, attempt, known);
              accepted.add(m);
              placed = new ArrayList<>(checked);
              found = true;
              break;
            } catch (Problem ignored) {
              /* A rejected candidate is not a feasible placement. */
            }
          }
          if (found) break;
        }
      }
      Layout candidate = summarize(in, placed, VERSION + "/" + strategy);
      if (best == null
          || candidate.placements.size() > best.placements.size()
          || candidate.placements.size() == best.placements.size()
              && candidate.volumeMm3 > best.volumeMm3) best = candidate;
    }
    return best;
  }
}
