import type { UserInfo } from '@nebula/types';

import type { AuthApi } from '#/api';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@nebula/constants';
import { preferences } from '@nebula/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@nebula/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { loginApi, logoutApi, getMenuPermsApi } from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 登录：调后端 /auth/login，落地 token + 用 LoginResult 直接填 UserInfo
   * （后端暂未提供 /user/info、/auth/codes，未来补上后再扩展）
   */
  async function authLogin(
    params: AuthApi.LoginParams,
    onSuccess?: () => Promise<void> | void,
  ) {
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const result = await loginApi(params);

      if (result?.tokenValue) {
        accessStore.setAccessToken(result.tokenValue);

        userInfo = {
          avatar: '',
          desc: '',
          homePath: preferences.app.defaultHomePath,
          realName: result.nickname || result.username,
          roles: [],
          token: result.tokenValue,
          userId: String(result.userId),
          username: result.username,
        };

        userStore.setUserInfo(userInfo);
        const perms = await getMenuPermsApi();
        accessStore.setAccessCodes(perms ?? []);

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );
        }

        if (userInfo?.realName) {
          ElNotification({
            message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            title: $t('authentication.loginSuccess'),
            type: 'success',
          });
        }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 后端 token 失效或网络抖动都吞掉，前端继续清理本地状态
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  /**
   * 当前后端无独立 /user/info 接口，沿用登录写入 store 的信息
   * 若 store 已持久化丢失（手动清 localStorage 等），返回最小占位避免守卫崩溃
   */
  async function fetchUserInfo(): Promise<UserInfo> {
    if (userStore.userInfo) {
      return userStore.userInfo;
    }
    return {
      avatar: '',
      desc: '',
      homePath: preferences.app.defaultHomePath,
      realName: '',
      roles: [],
      token: accessStore.accessToken ?? '',
      userId: '',
      username: '',
    };
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    loginLoading,
    logout,
  };
});