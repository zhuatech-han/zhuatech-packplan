// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 轴测三维示意；位置和重量校验只由服务端执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function scene(input, layout, angle = 35) {
  const space = input.space;
  const a = (angle * Math.PI) / 180;
  const project = ([x, y, z]) => [
    x * Math.cos(a) - y * Math.sin(a),
    (x * Math.sin(a) + y * Math.cos(a)) * 0.5 - z,
  ];
  const corners = [];
  for (const x of [0, space.length])
    for (const y of [0, space.width])
      for (const z of [0, space.height]) corners.push(project([x, y, z]));
  const minX = Math.min(...corners.map((p) => p[0])),
    maxX = Math.max(...corners.map((p) => p[0]));
  const minY = Math.min(...corners.map((p) => p[1])),
    maxY = Math.max(...corners.map((p) => p[1]));
  const scale = Math.min(850 / (maxX - minX), 425 / (maxY - minY));
  const point = (p) => {
    const [x, y] = project(p);
    return [
      500 + (x - (minX + maxX) / 2) * scale,
      285 + (y - (minY + maxY) / 2) * scale,
    ];
  };
  const polygon = (points) =>
    points
      .map((p) =>
        point(p)
          .map((n) => n.toFixed(2))
          .join(","),
      )
      .join(" ");
  const outline = [
    [
      [0, 0, 0],
      [space.length, 0, 0],
      [space.length, space.width, 0],
      [0, space.width, 0],
    ],
    [
      [0, 0, space.height],
      [space.length, 0, space.height],
      [space.length, space.width, space.height],
      [0, space.width, space.height],
    ],
    ...[
      [0, 0],
      [space.length, 0],
      [space.length, space.width],
      [0, space.width],
    ].map(([x, y]) => [
      [x, y, 0],
      [x, y, space.height],
    ]),
  ].map(polygon);
  const colors = [
    "#87b6bf",
    "#9ab8d6",
    "#d3b891",
    "#aabf9a",
    "#b6a9cd",
    "#c99fa3",
  ];
  const color = (code) => {
    let hash = 0;
    for (const char of code) hash = (hash * 31 + char.charCodeAt(0)) | 0;
    return colors[Math.abs(hash) % colors.length];
  };
  const boxes = layout.placements
    .map((b) => {
      const x = b.x,
        y = b.y,
        z = b.z,
        X = x + b.dx,
        Y = y + b.dy,
        Z = z + b.dz;
      return {
        ...b,
        color: color(b.code),
        depth: (x + X) * Math.sin(a) + (y + Y) * Math.cos(a) + (z + Z) * 0.02,
        faces: [
          polygon([
            [x, y, Z],
            [X, y, Z],
            [X, Y, Z],
            [x, Y, Z],
          ]),
          polygon([
            [X, y, z],
            [X, Y, z],
            [X, Y, Z],
            [X, y, Z],
          ]),
          polygon([
            [x, Y, z],
            [X, Y, z],
            [X, Y, Z],
            [x, Y, Z],
          ]),
        ],
      };
    })
    .sort((a, b) => a.depth - b.depth || a.uid.localeCompare(b.uid));
  return {
    outline,
    boxes,
    axes: [
      { label: "X", point: point([space.length, 0, 0]) },
      { label: "Y", point: point([0, space.width, 0]) },
      { label: "Z", point: point([0, 0, space.height]) },
    ],
  };
}
/** 发送最小位置字段，尺寸和重量不从浏览器上传。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function moves(layout) {
  return layout.placements.map((b) => ({
    uid: b.uid,
    x: b.x,
    y: b.y,
    z: b.z,
    rotated: b.rotated,
  }));
}
/** 只改指定单件，其他坐标保留；可以显式从未装入清单加入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function replaceMove(layout, move) {
  const out = moves(layout);
  const index = out.findIndex((b) => b.uid === move.uid);
  if (index < 0) out.push(move);
  else out[index] = move;
  return out;
}
