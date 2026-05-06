<script setup lang="ts">
import type { AuthApi } from '#/api';

import { computed, onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';

import CryptoJS from 'crypto-js';

import { checkCaptchaApi, getCaptchaApi } from '#/api';

const props = defineProps<{
  /** 是否已经验证通过（受控） */
  modelValue?: boolean;
}>();

const emit = defineEmits<{
  /** 验证通过：携带 verifyToken 与 captchaType 给登录/注册接口使用 */
  (
    event: 'success',
    payload: { captchaType: AuthApi.CaptchaType; verifyToken: string },
  ): void;
  (event: 'update:modelValue', value: boolean): void;
}>();

const captchaType: AuthApi.CaptchaType = 'blockPuzzle';
const SLIDER_AREA_WIDTH = 310;
const SLIDER_AREA_HEIGHT = 155;
const PIECE_SIZE = 50;

const loading = ref(false);
const refreshing = ref(false);
const passed = ref(Boolean(props.modelValue));

/** 后端 /captcha/get 返回的图片信息 */
const bgImage = ref('');
const sliceImage = ref('');
const captchaToken = ref('');
const captchaSecretKey = ref(''); // aj-captcha AES 密钥，对 pointJson 加密
const sliceY = ref(0); // 滑块在背景上的纵坐标（aj-captcha 不动 y，仅用于定位）

/** 滑块当前 x 偏移（0 ~ 滑动条最大值） */
const offsetX = ref(0);
const dragging = ref(false);
const startX = ref(0);
const sliderTrack = ref<HTMLElement>();

const sliderMaxOffset = computed(() => SLIDER_AREA_WIDTH - PIECE_SIZE);

const tipText = computed(() => {
  if (passed.value) return '验证成功';
  if (loading.value) return '加载中…';
  return '向右拖动滑块完成拼图';
});

async function loadCaptcha() {
  loading.value = true;
  passed.value = false;
  emit('update:modelValue', false);
  offsetX.value = 0;
  try {
    const data = await getCaptchaApi({
      captchaType,
      clientUid: getClientUid(),
    });
    if (data?.repCode !== '0000') {
      ElMessage.error(data?.repMsg || '验证码获取失败');
      return;
    }
    const repData = data.repData ?? ({} as Record<string, unknown>);
    bgImage.value = String(repData.originalImageBase64 ?? '');
    sliceImage.value = String(repData.jigsawImageBase64 ?? '');
    captchaToken.value = String(repData.token ?? '');
    captchaSecretKey.value = String(repData.secretKey ?? '');
    sliceY.value = Number((repData as { y?: number }).y ?? 0);
  } catch {
    // 由 request.ts 的 errorMessageResponseInterceptor 统一提示
  } finally {
    loading.value = false;
  }
}

async function refresh() {
  if (refreshing.value) return;
  refreshing.value = true;
  try {
    await loadCaptcha();
  } finally {
    refreshing.value = false;
  }
}

function onDragStart(event: MouseEvent | TouchEvent) {
  if (passed.value || loading.value || !captchaToken.value) return;
  dragging.value = true;
  startX.value = getEventX(event) - offsetX.value;
  document.addEventListener('mousemove', onDragMove);
  document.addEventListener('mouseup', onDragEnd);
  document.addEventListener('touchmove', onDragMove, { passive: false });
  document.addEventListener('touchend', onDragEnd);
}

function onDragMove(event: MouseEvent | TouchEvent) {
  if (!dragging.value) return;
  if (event.cancelable && 'touches' in event) {
    event.preventDefault();
  }
  const x = getEventX(event) - startX.value;
  offsetX.value = Math.max(0, Math.min(sliderMaxOffset.value, x));
}

async function onDragEnd() {
  if (!dragging.value) return;
  dragging.value = false;
  document.removeEventListener('mousemove', onDragMove);
  document.removeEventListener('mouseup', onDragEnd);
  document.removeEventListener('touchmove', onDragMove);
  document.removeEventListener('touchend', onDragEnd);

  if (!captchaToken.value) return;
  await verify();
}

async function verify() {
  loading.value = true;
  try {
    const rawPoint = JSON.stringify({ x: offsetX.value, y: sliceY.value });
    // aj-captcha 默认开启 AES：用 /captcha/get 返回的 secretKey 对 pointJson 进行
    // AES/ECB/PKCS7 加密后 base64，未开启 secretKey 则直接传明文
    const pointJson = captchaSecretKey.value
      ? aesEncrypt(rawPoint, captchaSecretKey.value)
      : rawPoint;
    const result = await checkCaptchaApi({
      captchaType,
      pointJson,
      token: captchaToken.value,
    });
    if (result?.verifyToken) {
      passed.value = true;
      emit('update:modelValue', true);
      emit('success', {
        captchaType,
        verifyToken: result.verifyToken,
      });
    } else {
      await loadCaptcha();
    }
  } catch {
    // 校验失败时刷新一张新图
    await loadCaptcha();
  } finally {
    loading.value = false;
  }
}

function getEventX(event: MouseEvent | TouchEvent): number {
  if ('touches' in event && event.touches[0]) return event.touches[0].clientX;
  if ('changedTouches' in event && event.changedTouches[0]) {
    return event.changedTouches[0].clientX;
  }
  return (event as MouseEvent).clientX;
}

/** AES/ECB/PKCS7 加密 → base64，对齐 aj-captcha 服务端解密 */
function aesEncrypt(plaintext: string, secretKey: string): string {
  const key = CryptoJS.enc.Utf8.parse(secretKey);
  return CryptoJS.AES.encrypt(plaintext, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  }).toString();
}

function getClientUid(): string {
  const KEY = 'nebula:captcha:clientUid';
  let uid = localStorage.getItem(KEY);
  if (!uid) {
    uid = `web-${Math.random().toString(36).slice(2)}-${Date.now()}`;
    localStorage.setItem(KEY, uid);
  }
  return uid;
}

onMounted(loadCaptcha);
</script>

<template>
  <div class="block-puzzle-captcha">
    <div
      class="captcha-canvas"
      :style="{
        width: `${SLIDER_AREA_WIDTH}px`,
        height: `${SLIDER_AREA_HEIGHT}px`,
      }"
    >
      <img
        v-if="bgImage"
        class="captcha-bg"
        :src="`data:image/png;base64,${bgImage}`"
        alt="captcha"
        draggable="false"
      />
      <img
        v-if="sliceImage"
        class="captcha-slice"
        :src="`data:image/png;base64,${sliceImage}`"
        :style="{ left: `${offsetX}px`, top: `${sliceY}px` }"
        alt="slice"
        draggable="false"
      />
      <button
        class="captcha-refresh"
        type="button"
        title="刷新"
        @click="refresh"
      >
        ⟳
      </button>
    </div>

    <div
      ref="sliderTrack"
      class="slider-track"
      :class="{ 'is-passed': passed }"
      :style="{ width: `${SLIDER_AREA_WIDTH}px` }"
    >
      <div
        class="slider-fill"
        :style="{ width: `${offsetX + PIECE_SIZE / 2}px` }"
      ></div>
      <div class="slider-text">{{ tipText }}</div>
      <div
        class="slider-handle"
        :style="{ left: `${offsetX}px`, width: `${PIECE_SIZE}px` }"
        @mousedown="onDragStart"
        @touchstart="onDragStart"
      >
        »
      </div>
    </div>
  </div>
</template>

<style scoped>
.block-puzzle-captcha {
  display: inline-flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.captcha-canvas {
  position: relative;
  overflow: hidden;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.captcha-bg {
  width: 100%;
  height: 100%;
  display: block;
  user-select: none;
}

.captcha-slice {
  position: absolute;
  width: 50px;
  height: 50px;
  pointer-events: none;
  user-select: none;
}

.captcha-refresh {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-primary);
  background: rgba(255, 255, 255, 0.8);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.slider-track {
  position: relative;
  height: 38px;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
  user-select: none;
}

.slider-track.is-passed {
  background-color: var(--el-color-success-light-9);
  border-color: var(--el-color-success);
}

.slider-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background-color: var(--el-color-primary-light-7);
  transition: width 0.05s linear;
}

.slider-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.slider-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--el-text-color-primary);
  background-color: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: grab;
  transition: width 0.05s linear;
}

.slider-handle:active {
  cursor: grabbing;
}
</style>