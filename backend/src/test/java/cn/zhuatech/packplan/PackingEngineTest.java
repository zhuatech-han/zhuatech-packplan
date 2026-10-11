// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** 有意义的几何、支撑载重和数量守恒回归，不只复刻实现。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class PackingEngineTest {
  PackingEngine.Cargo cargo(String c, int l, int w, int h, int q, long g, boolean r, long top) {
    return new PackingEngine.Cargo(c, "TEST " + c, l, w, h, q, g, r, top);
  }

  PackingEngine.Input input(int l, int w, int h, long g, PackingEngine.Cargo... c) {
    return new PackingEngine.Input(new PackingEngine.Space(l, w, h, g), List.of(c));
  }

  PackingEngine.Move at(String uid, int x, int y, int z, boolean r) {
    return new PackingEngine.Move(uid, x, y, z, r);
  }

  void error(String code, Runnable r) {
    assertEquals(code, assertThrows(Problem.class, r::run).getMessage());
  }

  @Test
  void fillsCubeWithSupportedLayers() {
    var in = input(100, 100, 100, 100000, cargo("AA", 50, 50, 50, 8, 100, true, 10000));
    var l = PackingEngine.calculate(in);
    assertEquals(8, l.placements().size());
    assertTrue(l.unplaced().isEmpty());
    assertEquals(100.0, l.volumeUtilization().doubleValue());
    assertEquals(800, l.weightGrams());
  }

  @Test
  void deterministicAcrossRuns() {
    var in = input(170, 110, 100, 10000, cargo("BB", 60, 40, 50, 9, 200, true, 400));
    assertEquals(PackingEngine.calculate(in), PackingEngine.calculate(in));
  }

  @Test
  void weightBoundLeavesExplicitMissing() {
    var in = input(100, 100, 100, 100, cargo("AA", 20, 20, 20, 3, 80, false, 100));
    var l = PackingEngine.calculate(in);
    assertEquals(1, l.placements().size());
    assertEquals(2, l.unplaced().size());
    assertEquals(80, l.weightGrams());
  }

  @Test
  void horizontalRotationEnablesFit() {
    var in = input(40, 70, 50, 1000, cargo("AA", 60, 30, 40, 1, 20, true, 0));
    var l = PackingEngine.calculate(in);
    assertEquals(1, l.placements().size());
    assertTrue(l.placements().getFirst().rotated());
  }

  @Test
  void deniedRotationLeavesUnplaced() {
    var in = input(40, 70, 50, 1000, cargo("AA", 60, 30, 40, 1, 20, false, 0));
    assertEquals(1, PackingEngine.calculate(in).unplaced().size());
  }

  @Test
  void neverTiltsHeightToFit() {
    var in = input(100, 100, 50, 1000, cargo("AA", 20, 20, 60, 1, 20, true, 0));
    assertTrue(PackingEngine.calculate(in).placements().isEmpty());
  }

  @Test
  void touchingFacesAreLegal() {
    var in = input(100, 50, 50, 1000, cargo("AA", 50, 50, 50, 2, 100, false, 0));
    assertEquals(
        2,
        PackingEngine.manual(
                in, List.of(at("AA-001", 0, 0, 0, false), at("AA-002", 50, 0, 0, false)))
            .placements()
            .size());
  }

  @Test
  void overlappingFacesAreRejected() {
    var in = input(100, 50, 50, 1000, cargo("AA", 50, 50, 50, 2, 100, false, 0));
    error(
        "BOX_OVERLAP",
        () ->
            PackingEngine.manual(
                in, List.of(at("AA-001", 0, 0, 0, false), at("AA-002", 49, 0, 0, false))));
  }

  @Test
  void boundsDoNotRound() {
    var in = input(50, 50, 50, 1000, cargo("AA", 50, 50, 50, 1, 100, false, 0));
    error("OUT_OF_BOUNDS", () -> PackingEngine.manual(in, List.of(at("AA-001", 1, 0, 0, false))));
  }

  @Test
  void negativePositionRejected() {
    var in = input(100, 100, 100, 1000, cargo("AA", 20, 20, 20, 1, 100, false, 0));
    error("OUT_OF_BOUNDS", () -> PackingEngine.manual(in, List.of(at("AA-001", -1, 0, 0, false))));
  }

  @Test
  void floatingBoxRejected() {
    var in = input(100, 100, 100, 1000, cargo("AA", 20, 20, 20, 1, 100, false, 100));
    error("UNSUPPORTED_BOX", () -> PackingEngine.manual(in, List.of(at("AA-001", 0, 0, 1, false))));
  }

  @Test
  void partialBaseCoverageRejected() {
    var in = input(100, 100, 100, 1000, cargo("AA", 50, 50, 20, 2, 100, false, 1000));
    error(
        "UNSUPPORTED_BOX",
        () ->
            PackingEngine.manual(
                in, List.of(at("AA-001", 0, 0, 0, false), at("AA-002", 1, 0, 20, false))));
  }

  @Test
  void cannotUseTwoBasesAsOneSupport() {
    var in =
        input(
            100,
            100,
            100,
            1000,
            cargo("AA", 50, 100, 20, 2, 100, false, 1000),
            cargo("BB", 100, 100, 20, 1, 100, false, 0));
    error(
        "UNSUPPORTED_BOX",
        () ->
            PackingEngine.manual(
                in,
                List.of(
                    at("AA-001", 0, 0, 0, false),
                    at("AA-002", 50, 0, 0, false),
                    at("BB-001", 0, 0, 20, false))));
  }

  @Test
  void stackLoadIncludesAllDescendants() {
    var in =
        input(
            50,
            50,
            100,
            1000,
            cargo("AA", 50, 50, 20, 1, 100, false, 150),
            cargo("BB", 50, 50, 20, 2, 100, false, 200));
    error(
        "STACK_LOAD_EXCEEDED",
        () ->
            PackingEngine.manual(
                in,
                List.of(
                    at("AA-001", 0, 0, 0, false),
                    at("BB-001", 0, 0, 20, false),
                    at("BB-002", 0, 0, 40, false))));
  }

  @Test
  void exactTopLoadAccepted() {
    var in = input(50, 50, 100, 1000, cargo("AA", 50, 50, 20, 2, 100, false, 100));
    var l =
        PackingEngine.manual(
            in, List.of(at("AA-001", 0, 0, 0, false), at("AA-002", 0, 0, 20, false)));
    assertEquals("AA-001", l.placements().get(1).supportUid());
  }

  @Test
  void zeroTopLoadProhibitsStack() {
    var in = input(50, 50, 100, 1000, cargo("AA", 50, 50, 20, 2, 100, false, 0));
    assertEquals(1, PackingEngine.calculate(in).placements().size());
  }

  @Test
  void duplicateUnitRejected() {
    var in = input(100, 100, 100, 1000, cargo("AA", 20, 20, 20, 2, 100, false, 0));
    error(
        "BOX_ID_INVALID",
        () ->
            PackingEngine.manual(
                in, List.of(at("AA-001", 0, 0, 0, false), at("AA-001", 50, 0, 0, false))));
  }

  @Test
  void forgedUnitRejected() {
    var in = input(100, 100, 100, 1000, cargo("AA", 20, 20, 20, 1, 100, false, 0));
    error("BOX_ID_INVALID", () -> PackingEngine.manual(in, List.of(at("AA-002", 0, 0, 0, false))));
  }

  @Test
  void manualRotationMustBeAllowed() {
    var in = input(100, 100, 100, 1000, cargo("AA", 20, 30, 20, 1, 100, false, 0));
    error("ROTATION_DENIED", () -> PackingEngine.manual(in, List.of(at("AA-001", 0, 0, 0, true))));
  }

  @Test
  void emptyManualIsExplicitAllUnplaced() {
    var in = input(100, 100, 100, 1000, cargo("AA", 20, 30, 20, 4, 100, false, 0));
    var l = PackingEngine.manual(in, List.of());
    assertEquals(4, l.unplaced().size());
    assertEquals(0, l.weightGrams());
  }

  @Test
  void dimensionOverflowRejected() {
    error(
        "INVALID_DIMENSION",
        () ->
            PackingEngine.calculate(
                input(20001, 100, 100, 1000, cargo("AA", 20, 20, 20, 1, 100, false, 0))));
  }

  @Test
  void quantityLimitRejected() {
    error(
        "CARGO_LIMIT",
        () ->
            PackingEngine.calculate(
                input(100, 100, 100, 1000, cargo("AA", 20, 20, 20, 121, 100, false, 0))));
  }

  @Test
  void volumeUsesLong() {
    var in = input(20000, 20000, 20000, 1000, cargo("AA", 20000, 20000, 20000, 1, 100, false, 0));
    assertEquals(8000000000000L, PackingEngine.calculate(in).volumeMm3());
  }

  @Test
  void independentOracleChecksGeneratedLayouts() {
    var random = new Random(20261011);
    for (int k = 0; k < 20; k++) {
      var in =
          input(
              300,
              300,
              250,
              10000,
              cargo(
                  "AA",
                  20 + random.nextInt(80),
                  20 + random.nextInt(80),
                  20 + random.nextInt(60),
                  12,
                  100,
                  true,
                  700));
      var l = PackingEngine.calculate(in);
      assertEquals(12, l.placements().size() + l.unplaced().size());
      var ids = new HashSet<String>();
      for (var a : l.placements()) {
        assertTrue(ids.add(a.uid()));
        assertTrue(
            a.x() >= 0
                && a.x() + a.dx() <= 300
                && a.y() >= 0
                && a.y() + a.dy() <= 300
                && a.z() >= 0
                && a.z() + a.dz() <= 250);
        for (var b : l.placements())
          if (a != b)
            assertFalse(
                Math.min(a.x() + a.dx(), b.x() + b.dx()) > Math.max(a.x(), b.x())
                    && Math.min(a.y() + a.dy(), b.y() + b.dy()) > Math.max(a.y(), b.y())
                    && Math.min(a.z() + a.dz(), b.z() + b.dz()) > Math.max(a.z(), b.z()));
      }
    }
  }

  @Test
  void svgEscapesInputAndFitsCanvas() {
    var in = input(100, 100, 100, 1000, cargo("AA", 50, 50, 50, 1, 100, false, 0));
    String s = SvgReport.render("TEST<&", 1L, in, PackingEngine.calculate(in));
    assertTrue(s.contains("TEST&lt;&amp;"));
    assertFalse(s.contains("<script"));
    assertTrue(s.contains("viewBox=\"0 0 1000 620\""));
  }
}
