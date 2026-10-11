<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, ref } from "vue";
import { scene } from "../geometry.js";
import { t } from "../ui.js";
const props = defineProps({
  input: { type: Object, required: true },
  layout: { type: Object, required: true },
  selected: { type: String, default: "" },
});
const emit = defineEmits(["select"]);
const angle = ref(35),
  zoom = ref(1),
  single = ref(false);
const picture = computed(() =>
  scene(props.input, props.layout, Number(angle.value)),
);
const boxes = computed(() =>
  single.value && props.selected
    ? picture.value.boxes.filter((b) => b.uid === props.selected)
    : picture.value.boxes,
);
</script>
<template>
  <div class="box-view">
    <div class="view-tools">
      <label
        >{{ t("视角", "Angle")
        }}<input
          v-model="angle"
          type="range"
          min="15"
          max="75"
          aria-label="view angle" /></label
      ><label
        >{{ t("缩放", "Zoom")
        }}<input
          v-model="zoom"
          type="range"
          min="0.7"
          max="1.8"
          step="0.1"
          aria-label="view zoom" /></label
      ><label class="check"
        ><input v-model="single" type="checkbox" />{{
          t("仅看选中箱件", "Selected box only")
        }}</label
      ><button
        @click="
          angle = 35;
          zoom = 1;
          single = false;
        "
      >
        {{ t("复位", "Reset view") }}
      </button>
    </div>
    <svg
      viewBox="0 0 1000 600"
      role="img"
      :aria-label="t('装柜三维布局', '3D cargo layout')"
    >
      <g
        :transform="`translate(${500 * (1 - zoom)} ${285 * (1 - zoom)}) scale(${zoom})`"
      >
        <polyline
          v-for="(line, i) in picture.outline.slice(0, 1)"
          :key="'floor' + i"
          :points="line"
          fill="#f0f4f6"
          stroke="#a5b4bd"
        />
        <g
          v-for="box in boxes"
          :key="box.uid"
          tabindex="0"
          role="button"
          :aria-label="box.uid"
          class="cargo-box"
          :class="{ selected: selected === box.uid }"
          :fill="box.color"
          @click="emit('select', box.uid)"
          @keydown.enter="emit('select', box.uid)"
          @keydown.space.prevent="emit('select', box.uid)"
        >
          <title>
            {{ box.uid }} · {{ box.name }} · {{ box.dx }}×{{ box.dy }}×{{
              box.dz
            }}
            mm
          </title>
          <polygon
            v-for="(face, j) in box.faces"
            :key="j"
            :points="face"
            :class="'face-' + j"
          />
        </g>
        <polyline
          v-for="(line, i) in picture.outline.slice(1)"
          :key="'edge' + i"
          :points="line"
          fill="none"
          stroke="#8c9fae"
          stroke-dasharray="5 4"
          pointer-events="none"
        />
        <text
          v-for="axis in picture.axes"
          :key="axis.label"
          :x="axis.point[0] + 8"
          :y="axis.point[1] - 8"
          font-size="15"
          fill="#718796"
        >
          {{ axis.label }}
        </text>
      </g>
      <text x="32" y="578" font-size="14" fill="#657b89">
        {{ input.space.length }} × {{ input.space.width }} ×
        {{ input.space.height }} mm ·
        {{ t("X 长度 / Y 宽度 / Z 高度", "X length / Y width / Z height") }}
      </text>
    </svg>
  </div>
</template>
