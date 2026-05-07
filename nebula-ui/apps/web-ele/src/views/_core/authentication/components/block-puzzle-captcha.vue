<script setup lang="ts">
import type { AuthApi } from '#/api';

import { onMounted, ref } from 'vue';

import { SliderTranslateCaptcha } from '@nebula/common-ui';

import CryptoJS from 'crypto-js';
import { ElMessage } from 'element-plus';

import { checkCaptchaApi, getCaptchaApi } from '#/api';

defineProps<{
  /** 是否已经验证通过（受控） */
  modelValue?: boolean;
}>();

const emit = defineEmits<{
  /** 验证通过：携带 verifyToken 与 captchaType 给登录/注册接口使用 */
  (
    event: 'success',
    payload: { captchaType: AuthApi.CaptchaType; verifyToken: string },
  ): void;
}>();

const passed = defineModel<boolean>({ default: false });

const captchaType: AuthApi.CaptchaType = 'blockPuzzle';
const SLIDER_AREA_WIDTH = 310;
const SLIDER_AREA_HEIGHT = 155;

/** 后端 /captcha/get 返回的图片信息（已拼成 data URL，直接喂给 SliderTranslateCaptcha） */
const bgImage = ref('');
const sliceImage = ref('');
const captchaToken = ref('');
const captchaSecretKey = ref(''); // aj-captcha AES 密钥，对 pointJson 加密
let loadingCaptchaPromise: null | Promise<void> = null;

async function loadCaptcha() {
  if (loadingCaptchaPromise) {
    return loadingCaptchaPromise;
  }

  passed.value = false;
  loadingCaptchaPromise = (async () => {
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
      const original = String(repData.originalImageBase64 ?? '');
      const jigsaw = String(repData.jigsawImageBase64 ?? '');
      bgImage.value = original ? `data:image/png;base64,${original}` : '';
      sliceImage.value = jigsaw ? `data:image/png;base64,${jigsaw}` : '';
      captchaToken.value = String(repData.token ?? '');
      captchaSecretKey.value = String(repData.secretKey ?? '');
    } catch {
      // 由 request.ts 的 errorMessageResponseInterceptor 统一提示
    } finally {
      loadingCaptchaPromise = null;
    }
  })();

  return loadingCaptchaPromise;
}

/**
 * 由 SliderTranslateCaptcha 受控模式回调：拿到滑块停下时的 x 偏移，
 * 走 aj-captcha 服务端校验，通过则向上派发 success 事件
 */
async function handleVerify(moveX: number): Promise<boolean> {
  if (!captchaToken.value) return false;
  try {
    // aj-captcha 服务端只比较 x，y 在容差范围内不严格校验，传 5 即可
    const rawPoint = JSON.stringify({ x: moveX, y: 5 });
    // aj-captcha 默认开启 AES：用 /captcha/get 返回的 secretKey 对 pointJson
    // 进行 AES/ECB/PKCS7 加密后 base64，未开启 secretKey 则直接传明文
    const pointJson = captchaSecretKey.value
      ? aesEncrypt(rawPoint, captchaSecretKey.value)
      : rawPoint;
    console.log('[captcha] verify request', {
      encryptedPointJson: pointJson,
      moveX,
      rawPoint,
      secretKey: captchaSecretKey.value,
      token: captchaToken.value,
    });
    const result = await checkCaptchaApi({
      captchaType,
      pointJson,
      token: captchaToken.value,
    });
    if (result?.verifyToken) {
      emit('success', {
        captchaType,
        verifyToken: result.verifyToken,
      });
      return true;
    }
    return false;
  } catch (error) {
    if (
      typeof error === 'object' &&
      error !== null &&
      'response' in error
    ) {
      const response = (error as {
        response?: { data?: unknown; status?: number };
      }).response;
      console.log('[captcha] verify fail response', {
        data: response?.data,
        status: response?.status,
      });
    } else {
      console.log('[captcha] verify fail error', error);
    }
    return false;
  }
}

/** 校验失败：刷新一张新图，让用户重试 */
async function handleFail() {
  await loadCaptcha();
}

/** 用户点击图片要求换一张 */
async function handleRefresh() {
  await loadCaptcha();
}

/** AES/ECB/PKCS7 加密 -> base64，对齐 aj-captcha 服务端解密 */
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
  <SliderTranslateCaptcha
    v-model="passed"
    :bg-image="bgImage"
    :slice-image="sliceImage"
    :canvas-width="SLIDER_AREA_WIDTH"
    :canvas-height="SLIDER_AREA_HEIGHT"
    :verify="handleVerify"
    default-tip="向右拖动滑块完成拼图"
    @fail="handleFail"
    @refresh="handleRefresh"
  />
</template>
