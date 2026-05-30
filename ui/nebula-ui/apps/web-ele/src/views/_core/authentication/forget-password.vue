<script lang="ts" setup>
import type { nebulaFormSchema } from '@nebula/common-ui';
import type { Recordable } from '@nebula/types';

import { computed, ref } from 'vue';

import { AuthenticationForgetPassword, z } from '@nebula/common-ui';
import { $t } from '@nebula/locales';

defineOptions({ name: 'ForgetPassword' });

const loading = ref(false);

const formSchema = computed((): nebulaFormSchema[] => {
  return [
    {
      component: 'nebulaInput',
      componentProps: {
        placeholder: 'example@example.com',
      },
      fieldName: 'email',
      label: $t('authentication.email'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.emailTip') })
        .email($t('authentication.emailValidErrorTip')),
    },
  ];
});

function handleSubmit(value: Recordable<any>) {
  void value;
}
</script>

<template>
  <AuthenticationForgetPassword
    :form-schema="formSchema"
    :loading="loading"
    @submit="handleSubmit"
  />
</template>






