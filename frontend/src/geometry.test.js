// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { scene, moves, replaceMove } from "./geometry.js";
const input = {
  space: { length: 12000, width: 2400, height: 2600, maxWeightGrams: 25000000 },
};
const box = {
  uid: "AA-001",
  code: "AA",
  name: "TEST",
  x: 0,
  y: 0,
  z: 0,
  dx: 1000,
  dy: 1000,
  dz: 1000,
  weightGrams: 10000,
  rotated: false,
};
const layout = { placements: [box] };
test("all space corners fit at each allowed view angle", () => {
  for (const angle of [15, 35, 75]) {
    const s = scene(input, layout, angle);
    for (const line of s.outline)
      for (const point of line.split(" ")) {
        const [x, y] = point.split(",").map(Number);
        assert.ok(Number.isFinite(x) && Number.isFinite(y));
        assert.ok(x >= 0 && x <= 1000 && y >= 0 && y <= 600);
      }
  }
});
test("coordinate projection uses z height", () => {
  const a = scene(input, layout).boxes[0];
  const b = scene(input, { placements: [{ ...box, z: 1000 }] }).boxes[0];
  assert.notEqual(a.faces[0], b.faces[0]);
});
test("angle changes geometry without changing stored coordinates", () => {
  const source = JSON.stringify(layout);
  assert.notDeepEqual(
    scene(input, layout, 15).boxes[0].faces,
    scene(input, layout, 75).boxes[0].faces,
  );
  assert.equal(JSON.stringify(layout), source);
});
test("moves cannot smuggle browser mass or size", () => {
  assert.deepEqual(Object.keys(moves(layout)[0]).sort(), [
    "rotated",
    "uid",
    "x",
    "y",
    "z",
  ]);
});
test("manual position update preserves other units and original snapshot", () => {
  const l = { placements: [box, { ...box, uid: "AA-002", x: 1000 }] };
  const updated = replaceMove(l, {
    uid: "AA-001",
    x: 50,
    y: 0,
    z: 0,
    rotated: false,
  });
  assert.equal(updated[0].x, 50);
  assert.equal(updated[1].x, 1000);
  assert.equal(l.placements[0].x, 0);
});
test("unplaced unit can be explicitly added once", () => {
  const l = { placements: [] };
  assert.equal(
    replaceMove(l, { uid: "AA-001", x: 0, y: 0, z: 0, rotated: false }).length,
    1,
  );
  assert.equal(l.placements.length, 0);
});
