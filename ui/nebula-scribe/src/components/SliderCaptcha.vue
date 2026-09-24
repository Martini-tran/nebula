<script setup lang="ts">
/**
 * 滑块拼图验证码（对接 manager 的 aj-captcha）。
 *
 * 底图固定 310×155：aj-captcha 服务端按 310 宽的坐标系校验，
 * 底图按原尺寸显示时滑块位移即为拼图 x 坐标，无需换算。
 */
import { onMounted, ref } from 'vue'
import { Icon } from '@iconify/vue'
import CryptoJS from 'crypto-js'
import { checkCaptcha, fetchCaptcha } from '../api/auth'
import type { CaptchaType } from '../types/auth'

const emit = defineEmits<{
  success: [payload: { captchaType: CaptchaType; verifyToken: string }]
}>()

const CAPTCHA_TYPE: CaptchaType = 'blockPuzzle'
const AREA_WIDTH = 310
const AREA_HEIGHT = 155
const HANDLE_WIDTH = 44
const MAX_MOVE = AREA_WIDTH - HANDLE_WIDTH

type Phase = 'loading' | 'error' | 'idle' | 'dragging' | 'verifying' | 'passed' | 'failed'

const phase = ref<Phase>('loading')
const bgImage = ref('')
const pieceImage = ref('')
const moveX = ref(0)

let captchaToken = ''
let secretKey = ''
let startClientX = 0

const clientUid = (() => {
  const KEY = 'nebula-scribe:captcha-client-uid'
  try {
    let uid = localStorage.getItem(KEY)
    if (!uid) {
      uid = `scribe-${Math.random().toString(36).slice(2)}-${Date.now()}`
      localStorage.setItem(KEY, uid)
    }
    return uid
  } catch {
    return `scribe-${Date.now()}`
  }
})()

const load = async () => {
  phase.value = 'loading'
  moveX.value = 0
  try {
    const data = await fetchCaptcha(CAPTCHA_TYPE, clientUid)
    const rep = data?.repData
    if (data?.repCode !== '0000' || !rep?.originalImageBase64 || !rep.jigsawImageBase64) {
      phase.value = 'error'
      return
    }
    bgImage.value = `data:image/png;base64,${rep.originalImageBase64}`
    pieceImage.value = `data:image/png;base64,${rep.jigsawImageBase64}`
    captchaToken = rep.token ?? ''
    secretKey = rep.secretKey ?? ''
    phase.value = 'idle'
  } catch {
    phase.value = 'error'
  }
}

/** aj-captcha 开启加密时坐标须 AES/ECB/PKCS7 加密后 base64，与服务端解密对齐 */
const encryptPoint = (plain: string) => {
  if (!secretKey) return plain
  const key = CryptoJS.enc.Utf8.parse(secretKey)
  return CryptoJS.AES.encrypt(plain, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  }).toString()
}

const onPointerDown = (event: PointerEvent) => {
  if (phase.value !== 'idle') return
  startClientX = event.clientX
  phase.value = 'dragging'
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

const onPointerMove = (event: PointerEvent) => {
  if (phase.value !== 'dragging') return
  moveX.value = Math.min(MAX_MOVE, Math.max(0, event.clientX - startClientX))
}

const onPointerUp = async () => {
  if (phase.value !== 'dragging') return
  if (moveX.value < 2) {
    phase.value = 'idle'
    moveX.value = 0
    return
  }
  phase.value = 'verifying'
  try {
    // 服务端只严格校验 x，y 在容差内，固定传 5
    const pointJson = encryptPoint(JSON.stringify({ x: Math.trunc(moveX.value), y: 5 }))
    const result = await checkCaptcha(CAPTCHA_TYPE, captchaToken, pointJson)
    if (!result?.verifyToken) {
      throw new Error('verify failed')
    }
    phase.value = 'passed'
    emit('success', { captchaType: CAPTCHA_TYPE, verifyToken: result.verifyToken })
  } catch {
    phase.value = 'failed'
    // 停留片刻让用户看到失败反馈，再换一张新图
    setTimeout(load, 700)
  }
}

onMounted(load)
</script>

<template>
  <div class="captcha" :style="{ width: `${AREA_WIDTH}px` }">
    <div class="captcha__canvas" :style="{ height: `${AREA_HEIGHT}px` }">
      <template v-if="bgImage && phase !== 'error'">
        <img class="captcha__bg" :src="bgImage" alt="" draggable="false" />
        <img
          class="captcha__piece"
          :src="pieceImage"
          alt=""
          draggable="false"
          :style="{ transform: `translateX(${moveX}px)` }"
        />
      </template>
      <div v-if="phase === 'loading'" class="captcha__overlay">
        <Icon icon="lucide:loader-circle" class="captcha__spin" />
      </div>
      <button v-if="phase === 'error'" class="captcha__overlay captcha__retry" type="button" @click="load">
        <Icon icon="lucide:rotate-cw" />
        验证码加载失败，点此重试
      </button>
      <button
        v-if="phase === 'idle' || phase === 'failed'"
        class="captcha__refresh"
        type="button"
        aria-label="换一张"
        title="换一张"
        @click="load"
      >
        <Icon icon="lucide:refresh-cw" />
      </button>
    </div>

    <div
      class="captcha__track"
      :class="{
        'captcha__track--passed': phase === 'passed',
        'captcha__track--failed': phase === 'failed',
      }"
    >
      <div class="captcha__fill" :style="{ width: `${moveX + HANDLE_WIDTH / 2}px` }" />
      <span class="captcha__tip" aria-live="polite">
        <template v-if="phase === 'passed'">验证通过</template>
        <template v-else-if="phase === 'failed'">没对准，换一张再试</template>
        <template v-else-if="phase === 'verifying'">校验中…</template>
        <template v-else-if="phase === 'idle'">向右拖动滑块完成拼图</template>
      </span>
      <div
        class="captcha__handle"
        role="slider"
        aria-label="拖动滑块完成拼图"
        :aria-valuemin="0"
        :aria-valuemax="MAX_MOVE"
        :aria-valuenow="Math.trunc(moveX)"
        :style="{ transform: `translateX(${moveX}px)`, width: `${HANDLE_WIDTH}px` }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
      >
        <Icon
          :icon="
            phase === 'passed'
              ? 'lucide:check'
              : phase === 'failed'
                ? 'lucide:x'
                : phase === 'verifying'
                  ? 'lucide:loader-circle'
                  : 'lucide:chevrons-right'
          "
          :class="{ captcha__spin: phase === 'verifying' }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.captcha {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-width: 100%;
  user-select: none;
}

.captcha__canvas {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.captcha__bg {
  display: block;
  width: 100%;
  height: 100%;
}

/* 拼图块按原始尺寸显示，只做水平平移 */
.captcha__piece {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  pointer-events: none;
}

.captcha__overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--color-text-secondary);
}

.captcha__retry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  border: 0;
  background: none;
  cursor: pointer;
  font-size: 0.88rem;
}

.captcha__refresh {
  position: absolute;
  top: 0.4rem;
  right: 0.4rem;
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border: 0;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  cursor: pointer;
}

.captcha__track {
  position: relative;
  height: 2.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  overflow: hidden;
}

.captcha__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brand-soft);
}

.captcha__track--passed .captcha__fill {
  background: var(--color-accent-soft);
}

.captcha__track--failed .captcha__fill {
  background: rgba(220, 38, 38, 0.15);
}

.captcha__tip {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.captcha__handle {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  place-items: center;
  height: 100%;
  border-radius: var(--radius-md);
  background: var(--color-brand);
  color: var(--color-on-brand);
  cursor: grab;
  touch-action: none;
}

.captcha__handle:active {
  cursor: grabbing;
}

.captcha__track--passed .captcha__handle {
  background: var(--color-accent);
}

.captcha__track--failed .captcha__handle {
  background: #dc2626;
}

.captcha__handle svg,
.captcha__refresh svg {
  width: 1.1rem;
  height: 1.1rem;
}

.captcha__spin {
  width: 1.3rem;
  height: 1.3rem;
  animation: captcha-spin 1s linear infinite;
}

@keyframes captcha-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .captcha__spin {
    animation: none;
  }
}
</style>
