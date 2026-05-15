<script lang="ts" setup>
import { Settings } from '@nebula/icons';

import { nebulaIconButton } from '@nebula-core/shadcn-ui';

import Preferences from './preferences.vue';

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();

function clearPreferencesAndLogout() {
  emit('clearPreferencesAndLogout');
}
</script>
<template>
  <Preferences @clear-preferences-and-logout="clearPreferencesAndLogout">
    <nebulaIconButton class="hover:animate-[shrink_0.3s_ease-in-out]">
      <Settings class="size-4 text-foreground" />
    </nebulaIconButton>
  </Preferences>
</template>






