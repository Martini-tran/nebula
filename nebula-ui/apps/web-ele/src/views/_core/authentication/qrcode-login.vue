<script lang="ts" setup>
import { AuthenticationQrCodeLogin } from '@nebula/common-ui';
import { LOGIN_PATH } from '@nebula/constants';

defineOptions({ name: 'QrCodeLogin' });
</script>

<template>
  <AuthenticationQrCodeLogin :login-path="LOGIN_PATH" />
</template>






