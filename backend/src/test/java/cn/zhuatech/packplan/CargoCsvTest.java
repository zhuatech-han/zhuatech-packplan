// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.packplan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** CSV边界、字符和重量精度，不接受静默导入丢行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class CargoCsvTest {
  String csv(String row) {
    return CargoCsv.HEADER + "\r\n" + row + "\r\n";
  }

  @Test
  void quotedCommaAndQuotes() {
    assertEquals(
        "TEST, \"box\"",
        CargoCsv.parse(csv("AA,\"TEST, \"\"box\"\"\",50,50,20,2,1.125,true,0")).getFirst().name());
  }

  @Test
  void bomSupported() {
    assertEquals(1, CargoCsv.parse("\uFEFF" + csv("AA,TEST,50,50,20,2,1,false,0")).size());
  }

  @Test
  void exactGramConversion() {
    assertEquals(1125, CargoCsv.grams("1.125", false));
    assertEquals(0, CargoCsv.grams("0", true));
  }

  @Test
  void precisionNotRounded() {
    assertThrows(Problem.class, () -> CargoCsv.grams("1.0001", false));
  }

  @Test
  void floatDimensionsRejected() {
    assertThrows(Problem.class, () -> CargoCsv.parse(csv("AA,TEST,50.5,50,20,2,1,false,0")));
  }

  @Test
  void unclosedQuoteRejected() {
    assertThrows(Problem.class, () -> CargoCsv.parse(csv("AA,\"TEST,50,50,20,2,1,false,0")));
  }

  @Test
  void extraColumnRejected() {
    assertThrows(Problem.class, () -> CargoCsv.parse(csv("AA,TEST,50,50,20,2,1,false,0,extra")));
  }

  @Test
  void nonBooleanRejected() {
    assertThrows(Problem.class, () -> CargoCsv.parse(csv("AA,TEST,50,50,20,2,1,yes,0")));
  }

  @Test
  void blankRowsNotSilentlyDropped() {
    assertThrows(Problem.class, () -> CargoCsv.parse(csv("AA,TEST,50,50,20,2,1,false,0") + "\n"));
  }

  @Test
  void controlCharactersRejected() {
    assertThrows(Problem.class, () -> CargoCsv.parse(csv("AA,\"TEST\nbox\",50,50,20,2,1,false,0")));
  }

  @Test
  void spreadsheetFormulaNeutralizedOnExport() {
    assertEquals("\"'=SUM(A1)\"", Rules.csv("=SUM(A1)"));
  }
}
