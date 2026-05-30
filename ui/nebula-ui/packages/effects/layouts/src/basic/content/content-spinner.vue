<script lang="ts" setup>
import { nebulaSpinner } from '@nebula-core/shadcn-ui';

import { useContentSpinner } from './use-content-spinner';

defineOptions({ name: 'LayoutContentSpinner' });

const { spinning } = useContentSpinner();
</script>
<template>
  <nebulaSpinner :spinning="spinning" />
</template>






