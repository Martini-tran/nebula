<script setup lang="ts">
import type {
  CaptchaVerifyPassingData,
  SliderCaptchaActionType,
  SliderRotateVerifyPassingData,
  SliderTranslateCaptchaProps,
} from '../types';

import {
  computed,
  onMounted,
  reactive,
  ref,
  unref,
  useTemplateRef,
  watch,
} from 'vue';

import { $t } from '@nebula/locales';

import SliderCaptcha from '../slider-captcha/index.vue';

const props = withDefaults(defineProps<SliderTranslateCaptchaProps>(), {
  defaultTip: '',
  canvasWidth: 420,
  canvasHeight: 280,
  squareLength: 42,
  circleRadius: 10,
  src: '',
  diffDistance: 3,
  bgImage: '',
  sliceImage: '',
});

const emit = defineEmits<{
  fail: [];
  refresh: [];
  success: [CaptchaVerifyPassingData];
}>();

const PI: number = Math.PI;
const canvasOpr = {
  clip: 'clip',
  fill: 'fill',
} as const;

type CanvasOpr = (typeof canvasOpr)[keyof typeof canvasOpr];

const modalValue = defineModel<boolean>({ default: false });

const slideBarRef = useTemplateRef<SliderCaptchaActionType>('slideBarRef');
const puzzleCanvasRef = useTemplateRef<HTMLCanvasElement>('puzzleCanvasRef');
const pieceCanvasRef = useTemplateRef<HTMLCanvasElement>('pieceCanvasRef');

const state = reactive({
  captchaMoveX: 0,
  dragging: false,
  startTime: 0,
  endTime: 0,
  pieceX: 0,
  pieceY: 0,
  moveDistance: 0,
  isPassing: false,
  showTip: false,
});

const left = ref('0');

const isControlled = computed(() => typeof props.verify === 'function');

const canvasBoxStyle = computed(() =>
  isControlled.value
    ? { width: `${props.canvasWidth}px`, height: `${props.canvasHeight}px` }
    : {},
);

const wrapperStyle = computed(() => ({
  width: `${props.canvasWidth}px`,
}));

const pieceStyle = computed(() => {
  return {
    left: left.value,
  };
});

// 受控模式下 slice 用自身原始尺寸显示（aj-captcha 的 jigsawImageBase64
// 通常就是拼图块本身，不是与背景同尺寸的画布；强制 width/height 会被拉大）
const sliceStyle = computed(() => ({
  ...pieceStyle.value,
}));

function setLeft(val: string) {
  left.value = val;
}

const verifyTip = computed(() => {
  return state.isPassing
    ? $t('ui.captcha.sliderTranslateSuccessTip', [
        ((state.endTime - state.startTime) / 1000).toFixed(1),
      ])
    : $t('ui.captcha.sliderTranslateFailTip');
});
function handleStart() {
  state.startTime = Date.now();
}

function handleDragBarMove(data: SliderRotateVerifyPassingData) {
  state.dragging = true;
  const { moveX } = data;
  state.moveDistance = moveX;
  state.captchaMoveX = Math.trunc((moveX * 310) / props.canvasWidth);
  setLeft(`${moveX}px`);
}

async function handleDragEnd() {
  if (isControlled.value && props.verify) {
    const moveX = state.captchaMoveX;
    state.endTime = Date.now();
    // 乐观锁定滑块成功态：SliderCaptcha 在 is-slot 模式下 end 后会 setTimeout(0)
    // 检查 modelValue，若不为 true 则自动 reset；这里同步置为 true，再异步等
    // verify 回调结果，失败时回滚。
    modalValue.value = true;
    try {
      const ok = await props.verify(moveX);
      if (ok) {
        // 主动派发 success（state.isPassing 的 watch 在受控模式下已被短路，
        // 避免重复派发）
        state.isPassing = true;
        const time = (state.endTime - state.startTime) / 1000;
        emit('success', { isPassing: true, time: time.toFixed(1) });
      } else {
        modalValue.value = false;
        state.isPassing = false;
        slideBarRef.value?.resume();
        setLeft('0');
        state.captchaMoveX = 0;
        state.moveDistance = 0;
        emit('fail');
      }
    } catch {
      modalValue.value = false;
      state.isPassing = false;
      slideBarRef.value?.resume();
      setLeft('0');
      state.captchaMoveX = 0;
      state.moveDistance = 0;
      emit('fail');
    } finally {
      state.showTip = true;
      state.dragging = false;
    }
    return;
  }

  const { pieceX } = state;
  const { diffDistance } = props;

  if (Math.abs(pieceX - state.moveDistance) >= (diffDistance || 3)) {
    setLeft('0');
    state.moveDistance = 0;
  } else {
    checkPass();
  }
  state.showTip = true;
  state.dragging = false;
}

function checkPass() {
  state.isPassing = true;
  state.endTime = Date.now();
}

watch(
  () => state.isPassing,
  (isPassing) => {
    // 受控模式下 success/modalValue 由 handleDragEnd 直接管理，避免重复派发
    if (isControlled.value) return;
    if (isPassing) {
      const { endTime, startTime } = state;
      const time = (endTime - startTime) / 1000;
      emit('success', { isPassing, time: time.toFixed(1) });
    }
    modalValue.value = isPassing;
  },
);

// 受控模式下：父组件刷新拿到新图（bgImage / sliceImage 变化）时，
// 重置滑块视觉状态。不调用 resume()，避免再次 emit('refresh') 触发循环刷新。
watch(
  () => [props.bgImage, props.sliceImage],
  () => {
    if (!isControlled.value) return;
    state.dragging = false;
    state.isPassing = false;
    state.showTip = false;
    state.captchaMoveX = 0;
    state.moveDistance = 0;
    setLeft('0');
    modalValue.value = false;
    slideBarRef.value?.resume();
  },
);

function resetCanvas() {
  if (isControlled.value) return;
  const { canvasWidth, canvasHeight } = props;
  const puzzleCanvas = unref(puzzleCanvasRef);
  const pieceCanvas = unref(pieceCanvasRef);
  if (!puzzleCanvas || !pieceCanvas) return;
  pieceCanvas.width = canvasWidth;
  const puzzleCanvasCtx = puzzleCanvas.getContext('2d');
  // Canvas2D: Multiple readback operations using getImageData
  // are faster with the willReadFrequently attribute set to true.
  // See: https://html.spec.whatwg.org/multipage/canvas.html#concept-canvas-will-read-frequently (anonymous)
  const pieceCanvasCtx = pieceCanvas.getContext('2d', {
    willReadFrequently: true,
  });
  if (!puzzleCanvasCtx || !pieceCanvasCtx) return;
  puzzleCanvasCtx.clearRect(0, 0, canvasWidth, canvasHeight);
  pieceCanvasCtx.clearRect(0, 0, canvasWidth, canvasHeight);
}

function initCanvas() {
  if (isControlled.value) return;
  const { canvasWidth, canvasHeight, squareLength, circleRadius, src } = props;
  const puzzleCanvas = unref(puzzleCanvasRef);
  const pieceCanvas = unref(pieceCanvasRef);
  if (!puzzleCanvas || !pieceCanvas) return;
  const puzzleCanvasCtx = puzzleCanvas.getContext('2d');
  // Canvas2D: Multiple readback operations using getImageData
  // are faster with the willReadFrequently attribute set to true.
  // See: https://html.spec.whatwg.org/multipage/canvas.html#concept-canvas-will-read-frequently (anonymous)
  const pieceCanvasCtx = pieceCanvas.getContext('2d', {
    willReadFrequently: true,
  });
  if (!puzzleCanvasCtx || !pieceCanvasCtx) return;
  const img = new Image();
  // 解决跨域
  img.crossOrigin = 'Anonymous';
  img.src = src;
  img.addEventListener('load', () => {
    draw(puzzleCanvasCtx, pieceCanvasCtx);
    puzzleCanvasCtx.drawImage(img, 0, 0, canvasWidth, canvasHeight);
    pieceCanvasCtx.drawImage(img, 0, 0, canvasWidth, canvasHeight);
    const pieceLength = squareLength + 2 * circleRadius + 3;
    const sx = state.pieceX;
    const sy = state.pieceY - 2 * circleRadius - 1;
    const imageData = pieceCanvasCtx.getImageData(
      sx,
      sy,
      pieceLength,
      pieceLength,
    );
    pieceCanvas.width = pieceLength;
    pieceCanvasCtx.putImageData(imageData, 0, sy);
    setLeft('0');
  });
}

function getRandomNumberByRange(start: number, end: number) {
  return Math.round(Math.random() * (end - start) + start);
}

// 绘制拼图
function draw(ctx1: CanvasRenderingContext2D, ctx2: CanvasRenderingContext2D) {
  const { canvasWidth, canvasHeight, squareLength, circleRadius } = props;
  state.pieceX = getRandomNumberByRange(
    squareLength + 2 * circleRadius,
    canvasWidth - (squareLength + 2 * circleRadius),
  );
  state.pieceY = getRandomNumberByRange(
    3 * circleRadius,
    canvasHeight - (squareLength + 2 * circleRadius),
  );
  drawPiece(ctx1, state.pieceX, state.pieceY, canvasOpr.fill);
  drawPiece(ctx2, state.pieceX, state.pieceY, canvasOpr.clip);
}

// 绘制拼图切块
function drawPiece(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  opr: CanvasOpr,
) {
  const { squareLength, circleRadius } = props;
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.arc(
    x + squareLength / 2,
    y - circleRadius + 2,
    circleRadius,
    0.72 * PI,
    2.26 * PI,
  );
  ctx.lineTo(x + squareLength, y);
  ctx.arc(
    x + squareLength + circleRadius - 2,
    y + squareLength / 2,
    circleRadius,
    1.21 * PI,
    2.78 * PI,
  );
  ctx.lineTo(x + squareLength, y + squareLength);
  ctx.lineTo(x, y + squareLength);
  ctx.arc(
    x + circleRadius - 2,
    y + squareLength / 2,
    circleRadius + 0.4,
    2.76 * PI,
    1.24 * PI,
    true,
  );
  ctx.lineTo(x, y);
  ctx.lineWidth = 2;
  ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.7)';
  ctx.stroke();
  opr === canvasOpr.clip ? ctx.clip() : ctx.fill();
  ctx.globalCompositeOperation = 'destination-over';
}

function resume() {
  state.showTip = false;
  const basicEl = unref(slideBarRef);
  if (!basicEl) {
    return;
  }
  state.dragging = false;
  state.isPassing = false;
  state.pieceX = 0;
  state.pieceY = 0;
  state.captchaMoveX = 0;
  state.moveDistance = 0;
  setLeft('0');
  modalValue.value = false;

  basicEl.resume();
  if (isControlled.value) {
    // 受控模式下由父组件去刷新图片（拉新的 bg/slice/token）
    emit('refresh');
  } else {
    resetCanvas();
    initCanvas();
  }
}

defineExpose({ resume });

onMounted(() => {
  initCanvas();
});
</script>

<template>
  <div class="relative flex flex-col items-center">
    <div
      :style="canvasBoxStyle"
      class="relative flex cursor-pointer overflow-hidden border border-border shadow-md"
    >
      <template v-if="isControlled">
        <img
          v-if="bgImage"
          :src="bgImage"
          alt="captcha"
          class="block h-full w-full max-w-none select-none"
          draggable="false"
          @click="resume"
        />
        <img
          v-if="sliceImage"
          :src="sliceImage"
          :style="sliceStyle"
          alt="slice"
          class="absolute top-0 left-0 max-w-none select-none"
          draggable="false"
        />
      </template>
      <template v-else>
        <canvas
          ref="puzzleCanvasRef"
          :width="canvasWidth"
          :height="canvasHeight"
          @click="resume"
        ></canvas>
        <canvas
          ref="pieceCanvasRef"
          :width="canvasWidth"
          :height="canvasHeight"
          :style="pieceStyle"
          class="absolute"
          @click="resume"
        ></canvas>
      </template>
      <div
        class="absolute bottom-3 left-0 z-10 block h-15 w-full text-center text-xs leading-[30px] text-white"
      >
        <div
          v-if="state.showTip"
          :class="{
            'bg-success/80': state.isPassing,
            'bg-destructive/80': !state.isPassing,
          }"
        >
          {{ verifyTip }}
        </div>
        <div v-if="!state.dragging" class="bg-black/30">
          {{ defaultTip || $t('ui.captcha.sliderTranslateDefaultTip') }}
        </div>
      </div>
    </div>
    <SliderCaptcha
      ref="slideBarRef"
      v-model="modalValue"
      class="mt-5"
      :wrapper-style="wrapperStyle"
      is-slot
      @end="handleDragEnd"
      @move="handleDragBarMove"
      @start="handleStart"
    >
      <template v-for="(_, key) in $slots" :key="key" #[key]="slotProps">
        <slot :name="key" v-bind="slotProps"></slot>
      </template>
    </SliderCaptcha>
  </div>
</template>
