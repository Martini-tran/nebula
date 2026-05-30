<script setup lang="ts">
import type { Recordable } from '@nebula/types';

import type { nebulaFormSchema } from '@nebula-core/form-ui';

import { computed, reactive } from 'vue';

import { $t } from '@nebula/locales';

import { usenebulaForm } from '@nebula-core/form-ui';
import { nebulaButton } from '@nebula-core/shadcn-ui';

interface Props {
  formSchema?: nebulaFormSchema[];
}

const props = withDefaults(defineProps<Props>(), {
  formSchema: () => [],
});

const emit = defineEmits<{
  submit: [Recordable<any>];
}>();

const [Form, formApi] = usenebulaForm(
  reactive({
    commonConfig: {
      labelWidth: 130,
      // 所有表单项
      componentProps: {
        class: 'w-full',
      },
    },
    layout: 'horizontal',
    schema: computed(() => props.formSchema),
    showDefaultActions: false,
  }),
);

async function handleSubmit() {
  const { valid } = await formApi.validate();
  const values = await formApi.getValues();
  if (valid) {
    emit('submit', values);
  }
}

defineExpose({
  getFormApi: () => formApi,
});
</script>
<template>
  <div>
    <Form />
    <nebulaButton type="submit" class="mt-4" @click="handleSubmit">
      {{ $t('profile.updatePassword') }}
    </nebulaButton>
  </div>
</template>






