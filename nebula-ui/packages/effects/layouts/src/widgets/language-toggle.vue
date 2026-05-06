<script setup lang="ts">
import type { SupportedLanguagesType } from '@nebula/locales';

import { SUPPORT_LANGUAGES } from '@nebula/constants';
import { Languages } from '@nebula/icons';
import { loadLocaleMessages } from '@nebula/locales';
import { preferences, updatePreferences } from '@nebula/preferences';

import { nebulaDropdownRadioMenu, nebulaIconButton } from '@nebula-core/shadcn-ui';

defineOptions({
  name: 'LanguageToggle',
});

async function handleUpdate(value: string | undefined) {
  if (!value) return;
  const locale = value as SupportedLanguagesType;
  updatePreferences({
    app: {
      locale,
    },
  });
  await loadLocaleMessages(locale);
}
</script>

<template>
  <div>
    <nebulaDropdownRadioMenu
      :menus="SUPPORT_LANGUAGES"
      :model-value="preferences.app.locale"
      @update:model-value="handleUpdate"
    >
      <nebulaIconButton class="hover:animate-[shrink_0.3s_ease-in-out]">
        <Languages class="size-4 text-foreground" />
      </nebulaIconButton>
    </nebulaDropdownRadioMenu>
  </div>
</template>






