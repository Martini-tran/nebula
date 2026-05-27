<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { BlogArticleApi, BlogTravelApi } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCard,
  ElCollapse,
  ElCollapseItem,
  ElDatePicker,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabs,
  ElTabPane,
  ElTag,
  ElTransfer,
} from 'element-plus';

import {
  bindBlogTravelTripPostsApi,
  createBlogTravelCheckinApi,
  createBlogTravelTripDayApi,
  deleteBlogTravelCheckinApi,
  deleteBlogTravelTripDayApi,
  getBlogArticlePageApi,
  getBlogTravelCheckinListApi,
  getBlogTravelDestinationTreeApi,
  getBlogTravelTripDayListApi,
  getBlogTravelTripDetailApi,
  getBlogTravelTripPostsApi,
  updateBlogTravelCheckinApi,
  updateBlogTravelTripDayApi,
} from '#/api';

defineOptions({ name: 'BlogTravelTripDetail' });

const route = useRoute();
const router = useRouter();

const tripId = computed<number | string>(() => {
  const id = route.params.id;
  return Array.isArray(id) ? (id[0] ?? '') : (id ?? '');
});

const tripTitle = ref<string>('');
const activeTab = ref<'days' | 'posts'>('days');

async function loadTripTitle() {
  if (route.query.title) {
    tripTitle.value = String(route.query.title);
    return;
  }
  if (!tripId.value) return;
  try {
    const detail = await getBlogTravelTripDetailApi(tripId.value);
    tripTitle.value = detail.title;
  } catch {
    /* ignore */
  }
}

// -------------------- 行程日 + 打卡点 --------------------

const days = ref<BlogTravelApi.TripDay[]>([]);
const daysLoading = ref(false);
const checkinsByDay = ref<Record<string, BlogTravelApi.Checkin[]>>({});
const expandedDayIds = ref<Array<number | string>>([]);

async function loadDays() {
  if (!tripId.value) return;
  daysLoading.value = true;
  try {
    const list = await getBlogTravelTripDayListApi(tripId.value);
    days.value = list;
    // 默认展开所有日
    expandedDayIds.value = list.map((d) => String(d.id));
    // 并发拉取每日的打卡点
    const all = await Promise.all(
      list.map((d) => getBlogTravelCheckinListApi(d.id)),
    );
    const map: Record<string, BlogTravelApi.Checkin[]> = {};
    list.forEach((d, idx) => {
      map[String(d.id)] = all[idx] ?? [];
    });
    checkinsByDay.value = map;
  } finally {
    daysLoading.value = false;
  }
}

async function reloadCheckinsForDay(dayId: number | string) {
  const list = await getBlogTravelCheckinListApi(dayId);
  checkinsByDay.value = { ...checkinsByDay.value, [String(dayId)]: list };
}

// -------------------- 目的地（用于打卡点选择） --------------------

interface FlatDest {
  id: number | string;
  label: string;
  type?: number;
}

const destinations = ref<BlogTravelApi.Destination[]>([]);
const flatDestinations = computed<FlatDest[]>(() => {
  const result: FlatDest[] = [];
  function walk(list: BlogTravelApi.Destination[], depth = 0) {
    for (const n of list) {
      result.push({
        id: n.id,
        label: `${'--'.repeat(depth)}${depth > 0 ? ' ' : ''}${n.name}`,
        type: n.type,
      });
      if (n.children?.length) walk(n.children, depth + 1);
    }
  }
  walk(destinations.value);
  return result;
});

async function loadDestinations() {
  try {
    destinations.value = await getBlogTravelDestinationTreeApi();
  } catch {
    destinations.value = [];
  }
}

// -------------------- 行程日编辑弹窗 --------------------

type DayEditMode = 'create' | 'edit';

const dayDialogVisible = ref(false);
const dayMode = ref<DayEditMode>('create');
const editingDayId = ref<null | number | string>(null);
const dayLoading = ref(false);
const dayFormRef = ref<FormInstance>();

const dayForm = reactive<{
  dayNumber: number;
  title: string;
  description: string;
  accommodation: string;
  mealCost: null | number;
  transportCost: null | number;
  otherCost: null | number;
  sortOrder: number;
}>({
  dayNumber: 1,
  title: '',
  description: '',
  accommodation: '',
  mealCost: null,
  transportCost: null,
  otherCost: null,
  sortOrder: 0,
});

const dayRules: FormRules = {
  dayNumber: [
    { required: true, message: '请输入第几天', trigger: 'blur' },
    { type: 'number', min: 1, message: '不能小于 1', trigger: 'blur' },
  ],
  title: [{ max: 200, message: '最多 200 个字符', trigger: 'blur' }],
};

function resetDayForm() {
  dayForm.dayNumber = 1;
  dayForm.title = '';
  dayForm.description = '';
  dayForm.accommodation = '';
  dayForm.mealCost = null;
  dayForm.transportCost = null;
  dayForm.otherCost = null;
  dayForm.sortOrder = 0;
  dayFormRef.value?.clearValidate();
}

function openCreateDay() {
  dayMode.value = 'create';
  editingDayId.value = null;
  resetDayForm();
  dayForm.dayNumber = (days.value[days.value.length - 1]?.dayNumber ?? 0) + 1;
  dayDialogVisible.value = true;
}

function openEditDay(day: BlogTravelApi.TripDay) {
  dayMode.value = 'edit';
  editingDayId.value = day.id;
  resetDayForm();
  dayForm.dayNumber = day.dayNumber ?? 1;
  dayForm.title = day.title ?? '';
  dayForm.description = day.description ?? '';
  dayForm.accommodation = day.accommodation ?? '';
  dayForm.mealCost = day.mealCost == null ? null : Number(day.mealCost);
  dayForm.transportCost =
    day.transportCost == null ? null : Number(day.transportCost);
  dayForm.otherCost = day.otherCost == null ? null : Number(day.otherCost);
  dayForm.sortOrder = day.sortOrder ?? 0;
  dayDialogVisible.value = true;
}

async function submitDay() {
  if (!dayFormRef.value) return;
  const valid = await dayFormRef.value.validate().catch(() => false);
  if (!valid) return;

  dayLoading.value = true;
  try {
    if (dayMode.value === 'create') {
      await createBlogTravelTripDayApi({
        trip_id: tripId.value,
        day_number: dayForm.dayNumber,
        title: dayForm.title || undefined,
        description: dayForm.description || undefined,
        accommodation: dayForm.accommodation || undefined,
        meal_cost: dayForm.mealCost ?? undefined,
        transport_cost: dayForm.transportCost ?? undefined,
        other_cost: dayForm.otherCost ?? undefined,
        sort_order: dayForm.sortOrder,
      });
      ElMessage.success('已添加');
    } else if (editingDayId.value != null) {
      await updateBlogTravelTripDayApi(editingDayId.value, {
        day_number: dayForm.dayNumber,
        title: dayForm.title || undefined,
        description: dayForm.description || undefined,
        accommodation: dayForm.accommodation || undefined,
        meal_cost: dayForm.mealCost ?? undefined,
        transport_cost: dayForm.transportCost ?? undefined,
        other_cost: dayForm.otherCost ?? undefined,
        sort_order: dayForm.sortOrder,
      });
      ElMessage.success('已保存');
    }
    dayDialogVisible.value = false;
    await loadDays();
  } finally {
    dayLoading.value = false;
  }
}

async function deleteDay(day: BlogTravelApi.TripDay) {
  try {
    await ElMessageBox.confirm(
      `确认删除「Day ${day.dayNumber}${day.title ? ' · ' + day.title : ''}」？该日下的所有打卡点将一并清除。`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogTravelTripDayApi(day.id);
  ElMessage.success('已删除');
  await loadDays();
}

// -------------------- 打卡点编辑弹窗 --------------------

type CheckinEditMode = 'create' | 'edit';

const checkinDialogVisible = ref(false);
const checkinMode = ref<CheckinEditMode>('create');
const editingCheckinId = ref<null | number | string>(null);
const checkinLoading = ref(false);
const checkinFormRef = ref<FormInstance>();

const checkinForm = reactive<{
  tripDayId: null | number | string;
  destinationId: null | number | string;
  customName: string;
  customLongitude: null | number;
  customLatitude: null | number;
  arrivalTime: string;
  departureTime: string;
  notes: string;
  rating: null | number;
  sortOrder: number;
}>({
  tripDayId: null,
  destinationId: null,
  customName: '',
  customLongitude: null,
  customLatitude: null,
  arrivalTime: '',
  departureTime: '',
  notes: '',
  rating: null,
  sortOrder: 0,
});

const checkinRules: FormRules = {
  customName: [{ max: 200, message: '最多 200 个字符', trigger: 'blur' }],
  customLongitude: [
    { type: 'number', min: -180, max: 180, message: '经度范围 -180 ~ 180', trigger: 'blur' },
  ],
  customLatitude: [
    { type: 'number', min: -90, max: 90, message: '纬度范围 -90 ~ 90', trigger: 'blur' },
  ],
  notes: [{ max: 2000, message: '最多 2000 个字符', trigger: 'blur' }],
};

function resetCheckinForm() {
  checkinForm.tripDayId = null;
  checkinForm.destinationId = null;
  checkinForm.customName = '';
  checkinForm.customLongitude = null;
  checkinForm.customLatitude = null;
  checkinForm.arrivalTime = '';
  checkinForm.departureTime = '';
  checkinForm.notes = '';
  checkinForm.rating = null;
  checkinForm.sortOrder = 0;
  checkinFormRef.value?.clearValidate();
}

function openCreateCheckin(day: BlogTravelApi.TripDay) {
  checkinMode.value = 'create';
  editingCheckinId.value = null;
  resetCheckinForm();
  checkinForm.tripDayId = day.id;
  checkinForm.sortOrder = (checkinsByDay.value[String(day.id)]?.length ?? 0);
  checkinDialogVisible.value = true;
}

function openEditCheckin(c: BlogTravelApi.Checkin) {
  checkinMode.value = 'edit';
  editingCheckinId.value = c.id;
  resetCheckinForm();
  checkinForm.tripDayId = c.tripDayId;
  checkinForm.destinationId = c.destinationId ?? null;
  checkinForm.customName = c.customName ?? '';
  checkinForm.customLongitude = c.customLongitude == null ? null : Number(c.customLongitude);
  checkinForm.customLatitude = c.customLatitude == null ? null : Number(c.customLatitude);
  checkinForm.arrivalTime = c.arrivalTime ?? '';
  checkinForm.departureTime = c.departureTime ?? '';
  checkinForm.notes = c.notes ?? '';
  checkinForm.rating = c.rating == null ? null : Number(c.rating);
  checkinForm.sortOrder = c.sortOrder ?? 0;
  checkinDialogVisible.value = true;
}

async function submitCheckin() {
  if (!checkinFormRef.value) return;
  const valid = await checkinFormRef.value.validate().catch(() => false);
  if (!valid) return;

  if (
    checkinForm.destinationId == null &&
    !checkinForm.customName.trim()
  ) {
    ElMessage.error('请选择关联目的地或填写自定义地点名称');
    return;
  }
  if (
    checkinForm.arrivalTime &&
    checkinForm.departureTime &&
    checkinForm.departureTime < checkinForm.arrivalTime
  ) {
    ElMessage.error('离开时间不能早于到达时间');
    return;
  }

  checkinLoading.value = true;
  try {
    if (checkinMode.value === 'create' && checkinForm.tripDayId != null) {
      await createBlogTravelCheckinApi({
        trip_day_id: checkinForm.tripDayId,
        destination_id: checkinForm.destinationId ?? null,
        custom_name: checkinForm.customName || undefined,
        custom_longitude: checkinForm.customLongitude ?? undefined,
        custom_latitude: checkinForm.customLatitude ?? undefined,
        arrival_time: checkinForm.arrivalTime || undefined,
        departure_time: checkinForm.departureTime || undefined,
        notes: checkinForm.notes || undefined,
        rating: checkinForm.rating ?? undefined,
        sort_order: checkinForm.sortOrder,
      });
      ElMessage.success('已添加');
    } else if (editingCheckinId.value != null) {
      await updateBlogTravelCheckinApi(editingCheckinId.value, {
        destination_id: checkinForm.destinationId ?? undefined,
        clear_destination_id:
          checkinForm.destinationId == null ? true : undefined,
        custom_name: checkinForm.customName || undefined,
        custom_longitude: checkinForm.customLongitude ?? undefined,
        custom_latitude: checkinForm.customLatitude ?? undefined,
        arrival_time: checkinForm.arrivalTime || undefined,
        departure_time: checkinForm.departureTime || undefined,
        notes: checkinForm.notes || undefined,
        rating: checkinForm.rating ?? undefined,
        sort_order: checkinForm.sortOrder,
      });
      ElMessage.success('已保存');
    }
    checkinDialogVisible.value = false;
    if (checkinForm.tripDayId != null) {
      await reloadCheckinsForDay(checkinForm.tripDayId);
    }
  } finally {
    checkinLoading.value = false;
  }
}

async function deleteCheckin(c: BlogTravelApi.Checkin) {
  try {
    await ElMessageBox.confirm(
      `确认删除打卡点「${c.customName || c.destinationName || c.id}」？`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteBlogTravelCheckinApi(c.id);
  ElMessage.success('已删除');
  await reloadCheckinsForDay(c.tripDayId);
}

// -------------------- 文章绑定 --------------------

const postLoading = ref(false);
const selectedPostIds = ref<Array<number | string>>([]);
const primaryPostId = ref<null | number | string>(null);
const postCandidates = ref<BlogArticleApi.ArticleListItem[]>([]);
const postKeyword = ref<string>('');

const transferData = computed(() =>
  postCandidates.value.map((p) => ({
    key: p.id,
    label: `${p.title} (${p.slug})`,
    disabled: false,
  })),
);

async function loadPostCandidates(keyword: string = '') {
  const result = await getBlogArticlePageApi({
    pageNum: 1,
    pageSize: 200,
    keyword: keyword || undefined,
    status: 'published',
  });
  postCandidates.value = result.records ?? [];
}

async function loadBoundPosts() {
  if (!tripId.value) return;
  postLoading.value = true;
  try {
    const [, posts] = await Promise.all([
      loadPostCandidates(),
      getBlogTravelTripPostsApi(tripId.value),
    ]);
    selectedPostIds.value = posts.map((p) => p.postId);
    primaryPostId.value =
      posts.find((p) => p.postType === 0)?.postId ?? null;

    // 把已绑定但不在候选列表中的文章补进来
    const existingIds = new Set(postCandidates.value.map((p) => String(p.id)));
    for (const p of posts) {
      if (!existingIds.has(String(p.postId))) {
        postCandidates.value.push({
          id: p.postId,
          title: p.postTitle ?? '(已删除或不可见)',
          slug: p.postSlug ?? '',
          status: p.postStatus ?? '',
          visibility: 'public',
        });
      }
    }
  } finally {
    postLoading.value = false;
  }
}

async function searchPosts() {
  postLoading.value = true;
  try {
    await loadPostCandidates(postKeyword.value);
  } finally {
    postLoading.value = false;
  }
}

async function submitBindPosts() {
  if (
    primaryPostId.value != null &&
    !selectedPostIds.value.includes(primaryPostId.value)
  ) {
    ElMessage.error('主要文章必须在已选文章中');
    return;
  }
  postLoading.value = true;
  try {
    await bindBlogTravelTripPostsApi(tripId.value, {
      post_ids: selectedPostIds.value,
      primary_post_id: primaryPostId.value ?? undefined,
    });
    ElMessage.success('已保存');
  } finally {
    postLoading.value = false;
  }
}

// -------------------- 工具 --------------------

function formatTime(value?: string) {
  if (!value) return '';
  // 后端返回 ISO 或 yyyy-MM-dd HH:mm:ss，统一只显示到分钟
  return value.replace('T', ' ').slice(0, 16);
}

function getDestName(id?: null | number | string) {
  if (id == null) return '';
  return flatDestinations.value.find((d) => String(d.id) === String(id))?.label;
}

function back() {
  router.push('/blog/travel/trip');
}

onMounted(() => {
  loadTripTitle();
  loadDays();
  loadDestinations();
});

watch(tripId, () => {
  loadTripTitle();
  loadDays();
});

watch(activeTab, (val) => {
  if (val === 'posts' && postCandidates.value.length === 0) {
    loadBoundPosts();
  }
});
</script>

<template>
  <Page auto-content-height>
    <ElCard shadow="never">
      <template #header>
        <div class="detail-header">
          <div class="detail-header__left">
            <ElButton link @click="back">← 返回游记列表</ElButton>
            <span class="detail-title">游记：{{ tripTitle || '加载中...' }}</span>
          </div>
        </div>
      </template>

      <ElTabs v-model="activeTab">
        <ElTabPane label="行程日 & 打卡点" name="days">
          <div class="days-toolbar">
            <ElButton
              v-access:code="'blog:travel:add'"
              type="primary"
              @click="openCreateDay"
            >
              新增行程日
            </ElButton>
          </div>

          <div v-loading="daysLoading" class="days-wrap">
            <ElEmpty
              v-if="!daysLoading && days.length === 0"
              description="暂无行程日，点击「新增行程日」开始规划"
            />
            <ElCollapse v-else v-model="expandedDayIds">
              <ElCollapseItem
                v-for="day in days"
                :key="day.id"
                :name="String(day.id)"
              >
                <template #title>
                  <div class="day-header">
                    <ElTag type="primary" size="small">
                      Day {{ day.dayNumber }}
                    </ElTag>
                    <span class="day-title">{{ day.title || '(未命名)' }}</span>
                    <span class="day-meta">
                      <span v-if="day.accommodation">
                        住宿：{{ day.accommodation }}
                      </span>
                      <span class="day-meta__sep">·</span>
                      <span>
                        打卡点 {{ checkinsByDay[String(day.id)]?.length ?? 0 }}
                      </span>
                    </span>
                  </div>
                </template>

                <div class="day-body">
                  <div class="day-actions">
                    <ElButton
                      v-access:code="'blog:travel:add'"
                      size="small"
                      type="primary"
                      @click="openCreateCheckin(day)"
                    >
                      新增打卡点
                    </ElButton>
                    <ElButton
                      v-access:code="'blog:travel:edit'"
                      size="small"
                      @click="openEditDay(day)"
                    >
                      编辑当日
                    </ElButton>
                    <ElButton
                      v-access:code="'blog:travel:delete'"
                      size="small"
                      type="danger"
                      @click="deleteDay(day)"
                    >
                      删除当日
                    </ElButton>
                  </div>

                  <p v-if="day.description" class="day-desc">
                    {{ day.description }}
                  </p>

                  <div
                    v-if="checkinsByDay[String(day.id)]?.length"
                    class="checkin-list"
                  >
                    <div
                      v-for="c in checkinsByDay[String(day.id)]"
                      :key="c.id"
                      class="checkin-item"
                    >
                      <div class="checkin-item__main">
                        <span class="checkin-name">
                          {{ c.destinationName || c.customName || `#${c.id}` }}
                        </span>
                        <span v-if="c.arrivalTime || c.departureTime" class="checkin-time">
                          {{ formatTime(c.arrivalTime) }}
                          <span v-if="c.arrivalTime && c.departureTime"> ~ </span>
                          {{ formatTime(c.departureTime) }}
                        </span>
                        <span v-if="c.rating != null" class="checkin-rating">
                          ★ {{ c.rating }}
                        </span>
                      </div>
                      <div class="checkin-item__notes" v-if="c.notes">
                        {{ c.notes }}
                      </div>
                      <div class="checkin-item__actions">
                        <ElButton
                          v-access:code="'blog:travel:edit'"
                          link
                          size="small"
                          type="primary"
                          @click="openEditCheckin(c)"
                        >
                          编辑
                        </ElButton>
                        <ElButton
                          v-access:code="'blog:travel:delete'"
                          link
                          size="small"
                          type="danger"
                          @click="deleteCheckin(c)"
                        >
                          删除
                        </ElButton>
                      </div>
                    </div>
                  </div>
                  <p v-else class="checkin-empty">该日还没有打卡点</p>
                </div>
              </ElCollapseItem>
            </ElCollapse>
          </div>
        </ElTabPane>

        <ElTabPane label="关联文章" name="posts">
          <div v-loading="postLoading" class="bind-wrap">
            <div class="bind-toolbar">
              <ElInput
                v-model="postKeyword"
                placeholder="按标题/Slug 搜索文章"
                clearable
                style="width: 280px"
                @keyup.enter="searchPosts"
              />
              <ElButton type="primary" @click="searchPosts">搜索</ElButton>
              <span class="bind-hint">
                穿梭至右侧的文章会保存为该游记的关联文章
              </span>
            </div>

            <ElTransfer
              v-model="selectedPostIds"
              :data="transferData"
              :titles="['可选文章', '已选文章']"
              filterable
              target-order="push"
            />

            <div class="primary-row">
              <span class="primary-label">主要文章：</span>
              <ElSelect
                v-model="primaryPostId"
                placeholder="可选，标记主推文章（post_type=0）"
                clearable
                style="width: 320px"
              >
                <ElOption
                  v-for="id in selectedPostIds"
                  :key="id"
                  :label="
                    postCandidates.find((p) => String(p.id) === String(id))
                      ?.title ?? String(id)
                  "
                  :value="id"
                />
              </ElSelect>
              <ElButton
                v-access:code="'blog:travel:edit'"
                type="primary"
                @click="submitBindPosts"
              >
                保存
              </ElButton>
            </div>
          </div>
        </ElTabPane>
      </ElTabs>
    </ElCard>

    <!-- 行程日 创建/编辑 -->
    <ElDialog
      v-model="dayDialogVisible"
      :close-on-click-modal="false"
      :title="dayMode === 'create' ? '新增行程日' : '编辑行程日'"
      width="560"
    >
      <ElForm
        ref="dayFormRef"
        :model="dayForm"
        :rules="dayRules"
        label-width="90px"
      >
        <ElFormItem label="第几天" prop="dayNumber">
          <ElInputNumber
            v-model="dayForm.dayNumber"
            :min="1"
            :max="999"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="当日标题" prop="title">
          <ElInput
            v-model="dayForm.title"
            placeholder="如：抵达大理，漫步古城"
          />
        </ElFormItem>
        <ElFormItem label="当日描述">
          <ElInput
            v-model="dayForm.description"
            :rows="3"
            placeholder="可选"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="住宿">
          <ElInput v-model="dayForm.accommodation" placeholder="如：洱海客栈" />
        </ElFormItem>
        <ElFormItem label="餐饮花费">
          <ElInputNumber
            v-model="dayForm.mealCost"
            :min="0"
            :precision="2"
            controls-position="right"
            placeholder="可选"
          />
        </ElFormItem>
        <ElFormItem label="交通花费">
          <ElInputNumber
            v-model="dayForm.transportCost"
            :min="0"
            :precision="2"
            controls-position="right"
            placeholder="可选"
          />
        </ElFormItem>
        <ElFormItem label="其他花费">
          <ElInputNumber
            v-model="dayForm.otherCost"
            :min="0"
            :precision="2"
            controls-position="right"
            placeholder="可选"
          />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="dayForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dayDialogVisible = false">取消</ElButton>
        <ElButton :loading="dayLoading" type="primary" @click="submitDay">
          确认
        </ElButton>
      </template>
    </ElDialog>

    <!-- 打卡点 创建/编辑 -->
    <ElDialog
      v-model="checkinDialogVisible"
      :close-on-click-modal="false"
      :title="checkinMode === 'create' ? '新增打卡点' : '编辑打卡点'"
      width="560"
    >
      <ElForm
        ref="checkinFormRef"
        :model="checkinForm"
        :rules="checkinRules"
        label-width="100px"
      >
        <ElFormItem label="关联目的地">
          <ElSelect
            v-model="checkinForm.destinationId"
            placeholder="可选；与自定义地点二选一"
            clearable
            filterable
            style="width: 100%"
          >
            <ElOption
              v-for="item in flatDestinations"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="自定义地点" prop="customName">
          <ElInput
            v-model="checkinForm.customName"
            placeholder="未关联目的地时填写"
          />
        </ElFormItem>
        <ElFormItem label="自定义经纬度">
          <div style="display: flex; gap: 8px; width: 100%">
            <ElFormItem prop="customLongitude" style="flex: 1; margin-bottom: 0">
              <ElInputNumber
                v-model="checkinForm.customLongitude"
                :min="-180"
                :max="180"
                :precision="6"
                :step="0.01"
                :controls="false"
                placeholder="经度"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem prop="customLatitude" style="flex: 1; margin-bottom: 0">
              <ElInputNumber
                v-model="checkinForm.customLatitude"
                :min="-90"
                :max="90"
                :precision="6"
                :step="0.01"
                :controls="false"
                placeholder="纬度"
                style="width: 100%"
              />
            </ElFormItem>
          </div>
        </ElFormItem>
        <ElFormItem label="到达时间">
          <ElDatePicker
            v-model="checkinForm.arrivalTime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            type="datetime"
            placeholder="可选"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="离开时间">
          <ElDatePicker
            v-model="checkinForm.departureTime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            type="datetime"
            placeholder="可选"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="评分">
          <ElInputNumber
            v-model="checkinForm.rating"
            :min="0"
            :max="5"
            :step="0.5"
            :precision="1"
            controls-position="right"
            placeholder="0~5"
          />
        </ElFormItem>
        <ElFormItem label="游玩笔记" prop="notes">
          <ElInput
            v-model="checkinForm.notes"
            :rows="3"
            maxlength="2000"
            placeholder="可选"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="checkinForm.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="checkinDialogVisible = false">取消</ElButton>
        <ElButton
          :loading="checkinLoading"
          type="primary"
          @click="submitCheckin"
        >
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
}

.days-toolbar {
  margin-bottom: 12px;
}

.days-wrap {
  min-height: 320px;
}

.day-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.day-title {
  font-weight: 500;
}

.day-meta {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.day-meta__sep {
  margin: 0 6px;
  color: var(--el-text-color-placeholder);
}

.day-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}

.day-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.day-desc {
  margin: 0;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: pre-wrap;
}

.checkin-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.checkin-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.checkin-item__main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.checkin-name {
  font-weight: 500;
}

.checkin-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.checkin-rating {
  color: #f59e0b;
  font-size: 13px;
}

.checkin-item__notes {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: pre-wrap;
}

.checkin-item__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  align-self: flex-end;
}

.checkin-empty {
  margin: 0;
  padding: 12px;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  text-align: center;
}

.bind-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bind-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bind-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.primary-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.primary-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
