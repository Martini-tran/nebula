<script lang="ts" setup>
import { Page } from '@nebula/common-ui';
import { ElEmpty } from 'element-plus';

defineOptions({ name: 'SystemRole' });

// 角色管理页面占位：后端 SysRole 服务尚未实现
// 待 SysRoleController/Service 落地、apps/web-ele/src/api/system/role.ts 创建之后再补完整页面
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full items-center justify-center p-8">
      <ElEmpty description="角色管理功能开发中">
        <template #image>
          <div class="text-6xl">🛠️</div>
        </template>
      </ElEmpty>
    </div>
  </Page>
</template>
