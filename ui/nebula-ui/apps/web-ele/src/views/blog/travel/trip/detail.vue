<script lang="ts" setup>
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { BlogArticleApi, BlogTravelApi } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElImage,
  ElImageViewer,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElScrollbar,
  ElSelect,
  ElTag,
  ElTransfer,
  ElUpload,
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
  uploadBlogFileApi,
} from '#/api';

defineOptions({ name: 'BlogTravelTripDetail' });

const route = useRoute();
const router = useRouter();

const tripId = computed<number | string>(() => {
  const id = route.params.id;
  return Array.isArray(id) ? (id[0] ?? '') : (id ?? '');
});

const tripDetail = ref<BlogTravelApi.Trip | null>(null);
const tripTitle = computed(() => tripDetail.value?.title ?? '');

async function loadTripDetail() {
  if (!tripId.value) return;
  try {
    tripDetail.value = await getBlogTravelTripDetailApi(tripId.value);
  } catch {
    /* ignore */
  }
}

// -------------------- 行程日 + 打卡点 --------------------

const days = ref<BlogTravelApi.TripDay[]>([]);
const daysLoading = ref(false);
const checkinsByDay = ref<Record<string, BlogTravelApi.Checkin[]>>({});
const activeDayId = ref<null | number | string>(null);

const activeDay = computed<BlogTravelApi.TripDay | null>(() => {
  if (activeDayId.value == null) return null;
  return (
    days.value.find((d) => String(d.id) === String(activeDayId.value)) ?? null
  );
});

const activeCheckins = computed<BlogTravelApi.Checkin[]>(() => {
  if (activeDayId.value == null) return [];
  return checkinsByDay.value[String(activeDayId.value)] ?? [];
});

const tripStats = computed(() => {
  const totalCheckins = days.value.reduce(
    (sum, d) => sum + (checkinsByDay.value[String(d.id)]?.length ?? 0),
    0,
  );
  const totalCost = days.value.reduce((sum, d) => {
    const meal = Number(d.mealCost ?? 0);
    const tr = Number(d.transportCost ?? 0);
    const ot = Number(d.otherCost ?? 0);
    return sum + (Number.isFinite(meal) ? meal : 0) +
      (Number.isFinite(tr) ? tr : 0) +
      (Number.isFinite(ot) ? ot : 0);
  }, 0);
  return {
    days: days.value.length,
    checkins: totalCheckins,
    cost: totalCost,
  };
});

async function loadDays() {
  if (!tripId.value) return;
  daysLoading.value = true;
  try {
    const list = await getBlogTravelTripDayListApi(tripId.value);
    days.value = list;
    const all = await Promise.all(
      list.map((d) => getBlogTravelCheckinListApi(d.id)),
    );
    const map: Record<string, BlogTravelApi.Checkin[]> = {};
    list.forEach((d, idx) => {
      map[String(d.id)] = all[idx] ?? [];
    });
    checkinsByDay.value = map;
    if (list.length > 0) {
      const first = list[0]!;
      const stillExists =
        activeDayId.value != null &&
        list.some((d) => String(d.id) === String(activeDayId.value));
      if (!stillExists) {
        activeDayId.value = first.id;
      }
    } else {
      activeDayId.value = null;
    }
  } finally {
    daysLoading.value = false;
  }
}

async function reloadCheckinsForDay(dayId: number | string) {
  const list = await getBlogTravelCheckinListApi(dayId);
  checkinsByDay.value = { ...checkinsByDay.value, [String(dayId)]: list };
}

// -------------------- 目的地 --------------------

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

// -------------------- 行程日 弹窗 --------------------

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
  dayForm.sortOrder = days.value.length;
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

// -------------------- 打卡点 弹窗 --------------------

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
  photos: { id: number | string; url: string }[];
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
  photos: [],
  sortOrder: 0,
});

const checkinPhotoUploading = ref(false);
const MAX_CHECKIN_PHOTOS = 12;

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
  checkinForm.photos = [];
  checkinForm.sortOrder = 0;
  checkinFormRef.value?.clearValidate();
}

function openCreateCheckin(day?: BlogTravelApi.TripDay | null) {
  const target = day ?? activeDay.value;
  if (!target) {
    ElMessage.warning('请先选择一个行程日');
    return;
  }
  checkinMode.value = 'create';
  editingCheckinId.value = null;
  resetCheckinForm();
  checkinForm.tripDayId = target.id;
  checkinForm.sortOrder = (checkinsByDay.value[String(target.id)]?.length ?? 0);
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
  checkinForm.photos = zipPhotos(c.photoIds, c.photoUrls);
  checkinForm.sortOrder = c.sortOrder ?? 0;
  checkinDialogVisible.value = true;
}

function zipPhotos(
  ids: Array<number | string> | undefined,
  urls: string[] | undefined,
): { id: number | string; url: string }[] {
  const safeIds = ids ?? [];
  const safeUrls = urls ?? [];
  const len = Math.max(safeIds.length, safeUrls.length);
  const out: { id: number | string; url: string }[] = [];
  for (let i = 0; i < len; i++) {
    const id = safeIds[i];
    if (id == null) continue;
    out.push({ id, url: safeUrls[i] ?? '' });
  }
  return out;
}

async function handleCheckinPhotoChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) return;
  if (checkinForm.photos.length >= MAX_CHECKIN_PHOTOS) {
    ElMessage.warning(`最多上传 ${MAX_CHECKIN_PHOTOS} 张`);
    return;
  }
  checkinPhotoUploading.value = true;
  try {
    const result = await uploadBlogFileApi(uploadFile.raw, 'image');
    checkinForm.photos.push({ id: result.id, url: result.url });
  } finally {
    checkinPhotoUploading.value = false;
  }
}

function removeCheckinPhoto(idx: number) {
  checkinForm.photos.splice(idx, 1);
}

async function submitCheckin() {
  if (!checkinFormRef.value) return;
  const valid = await checkinFormRef.value.validate().catch(() => false);
  if (!valid) return;

  if (checkinForm.destinationId == null && !checkinForm.customName.trim()) {
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
    const photoIds = checkinForm.photos
      .map((p) => Number(p.id))
      .filter((n) => Number.isFinite(n) && n > 0);
    const photosJson = photoIds.length ? JSON.stringify(photoIds) : '';
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
        photos: photosJson || undefined,
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
        photos: photosJson,
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

// -------------------- 关联文章 --------------------

const postDialogVisible = ref(false);
const postLoading = ref(false);
const selectedPostIds = ref<Array<number | string>>([]);
const primaryPostId = ref<null | number | string>(null);
const postCandidates = ref<BlogArticleApi.ArticleListItem[]>([]);
const postKeyword = ref<string>('');
const boundPosts = ref<BlogTravelApi.TripPost[]>([]);

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
    boundPosts.value = posts;
    selectedPostIds.value = posts.map((p) => p.postId);
    primaryPostId.value =
      posts.find((p) => p.postType === 0)?.postId ?? null;

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
    postDialogVisible.value = false;
    await loadBoundPosts();
  } finally {
    postLoading.value = false;
  }
}

function openPostDialog() {
  postDialogVisible.value = true;
  if (postCandidates.value.length === 0) {
    loadBoundPosts();
  }
}

// -------------------- 图片预览 --------------------

const previewVisible = ref(false);
const previewIndex = ref(0);
const previewUrls = ref<string[]>([]);

function openPreview(urls: string[], idx: number) {
  if (!urls?.length) return;
  previewUrls.value = urls;
  previewIndex.value = idx;
  previewVisible.value = true;
}

const galleryPhotos = computed(() => {
  const all: { url: string; checkin: BlogTravelApi.Checkin }[] = [];
  for (const c of activeCheckins.value) {
    for (const url of c.photoUrls ?? []) {
      all.push({ url, checkin: c });
    }
  }
  return all;
});

// -------------------- 工具 --------------------

function formatTime(value?: string) {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 16);
}

function formatTimeRange(arrival?: string, departure?: string) {
  const a = formatTime(arrival);
  const d = formatTime(departure);
  if (!a && !d) return '';
  return `${a || '—'} → ${d || '—'}`;
}

function back() {
  router.push('/blog/travel/trip');
}

function statusLabel(status?: string) {
  switch (status) {
    case 'archived':
      return '已归档';
    case 'draft':
      return '草稿';
    case 'published':
      return '已发布';
    default:
      return status ?? '-';
  }
}

function statusTagType(
  status?: string,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  switch (status) {
    case 'archived':
      return 'warning';
    case 'draft':
      return 'info';
    case 'published':
      return 'success';
    default:
      return 'info';
  }
}

onMounted(() => {
  loadTripDetail();
  loadDays();
  loadDestinations();
  loadBoundPosts();
});

watch(tripId, () => {
  loadTripDetail();
  loadDays();
  loadBoundPosts();
});
</script>

<template>
  <Page>
    <div class="trip-detail" v-loading="daysLoading">
      <!-- ============ Hero ============ -->
      <div
        class="hero"
        :style="
          tripDetail?.coverUrl
            ? `background-image: linear-gradient(135deg, rgba(15,23,42,0.7), rgba(15,23,42,0.45)), url(${tripDetail.coverUrl})`
            : ''
        "
        :class="{ 'hero--no-cover': !tripDetail?.coverUrl }"
      >
        <div class="hero__top">
          <ElButton link class="hero__back" @click="back">
            ← 返回游记列表
          </ElButton>
          <ElTag
            v-if="tripDetail?.status"
            :type="statusTagType(tripDetail.status)"
            effect="dark"
            round
          >
            {{ statusLabel(tripDetail.status) }}
          </ElTag>
        </div>

        <div class="hero__main">
          <h1 class="hero__title">
            {{ tripTitle || '加载中…' }}
          </h1>
          <p v-if="tripDetail?.summary" class="hero__summary">
            {{ tripDetail.summary }}
          </p>
          <div v-if="tripDetail" class="hero__meta">
            <span v-if="tripDetail.startDate || tripDetail.endDate">
              <span class="hero__meta-icon">📅</span>
              {{ tripDetail.startDate || '?' }} ~ {{ tripDetail.endDate || '?' }}
              <span v-if="tripDetail.daysCount">
                · {{ tripDetail.daysCount }} 天
              </span>
            </span>
            <span v-if="tripDetail.persons">
              <span class="hero__meta-icon">👥</span>
              {{ tripDetail.persons }} 人
            </span>
            <span v-if="tripDetail.costTotal != null">
              <span class="hero__meta-icon">💰</span>
              {{ tripDetail.costTotal }} {{ tripDetail.costCurrency || 'CNY' }}
            </span>
          </div>
        </div>

        <div class="hero__stats">
          <div class="stat-card">
            <span class="stat-card__value">{{ tripStats.days }}</span>
            <span class="stat-card__label">行程日</span>
          </div>
          <div class="stat-card">
            <span class="stat-card__value">{{ tripStats.checkins }}</span>
            <span class="stat-card__label">打卡点</span>
          </div>
          <div class="stat-card">
            <span class="stat-card__value">{{ boundPosts.length }}</span>
            <span class="stat-card__label">关联文章</span>
          </div>
        </div>
      </div>

      <!-- ============ 主体两栏布局 ============ -->
      <div class="layout">
        <!-- 左侧：行程日时间线 + 关联文章 -->
        <aside class="sidebar">
          <ElCard class="panel" shadow="never">
            <template #header>
              <div class="panel__header">
                <span class="panel__title">行程日</span>
                <ElButton
                  v-access:code="'blog:travel:add'"
                  size="small"
                  type="primary"
                  @click="openCreateDay"
                >
                  + 新增
                </ElButton>
              </div>
            </template>

            <ElEmpty
              v-if="!daysLoading && days.length === 0"
              :image-size="80"
              description="还没有行程日"
            />
            <ElScrollbar v-else max-height="420px">
              <ul class="timeline">
                <li
                  v-for="day in days"
                  :key="day.id"
                  class="timeline__item"
                  :class="{
                    'timeline__item--active':
                      String(day.id) === String(activeDayId),
                  }"
                  @click="activeDayId = day.id"
                >
                  <div class="timeline__node">
                    <span class="timeline__day">D{{ day.dayNumber }}</span>
                  </div>
                  <div class="timeline__body">
                    <div class="timeline__title">
                      {{ day.title || '(未命名)' }}
                    </div>
                    <div class="timeline__meta">
                      <span>
                        {{ checkinsByDay[String(day.id)]?.length ?? 0 }} 个打卡点
                      </span>
                      <span v-if="day.accommodation" class="timeline__acc">
                        🏨 {{ day.accommodation }}
                      </span>
                    </div>
                  </div>
                </li>
              </ul>
            </ElScrollbar>
          </ElCard>

          <ElCard class="panel" shadow="never">
            <template #header>
              <div class="panel__header">
                <span class="panel__title">关联文章</span>
                <ElButton
                  v-access:code="'blog:travel:edit'"
                  size="small"
                  @click="openPostDialog"
                >
                  管理
                </ElButton>
              </div>
            </template>

            <ElEmpty
              v-if="!postLoading && boundPosts.length === 0"
              :image-size="60"
              description="暂未关联文章"
            />
            <ul v-else class="post-list">
              <li
                v-for="p in boundPosts"
                :key="p.postId"
                class="post-list__item"
              >
                <div class="post-list__main">
                  <span class="post-list__title">
                    {{ p.postTitle || `#${p.postId}` }}
                  </span>
                  <span v-if="p.postSlug" class="post-list__slug">
                    /{{ p.postSlug }}
                  </span>
                </div>
                <ElTag
                  v-if="p.postType === 0"
                  type="primary"
                  effect="plain"
                  size="small"
                >
                  主推
                </ElTag>
              </li>
            </ul>
          </ElCard>
        </aside>

        <!-- 右侧：当日详情 + 打卡点 + 图片墙 -->
        <main class="main">
          <ElEmpty
            v-if="!activeDay"
            description="选择左侧的行程日查看详情"
            class="main__empty"
          />
          <template v-else>
            <ElCard class="day-card" shadow="never">
              <div class="day-card__header">
                <div class="day-card__title-wrap">
                  <ElTag type="primary" effect="dark" round size="default">
                    Day {{ activeDay.dayNumber }}
                  </ElTag>
                  <h2 class="day-card__title">
                    {{ activeDay.title || '(未命名)' }}
                  </h2>
                </div>
                <div class="day-card__actions">
                  <ElButton
                    v-access:code="'blog:travel:add'"
                    type="primary"
                    @click="openCreateCheckin()"
                  >
                    + 打卡点
                  </ElButton>
                  <ElButton
                    v-access:code="'blog:travel:edit'"
                    @click="openEditDay(activeDay)"
                  >
                    编辑
                  </ElButton>
                  <ElButton
                    v-access:code="'blog:travel:delete'"
                    type="danger"
                    plain
                    @click="deleteDay(activeDay)"
                  >
                    删除
                  </ElButton>
                </div>
              </div>

              <p v-if="activeDay.description" class="day-card__desc">
                {{ activeDay.description }}
              </p>

              <div class="day-card__metrics">
                <div class="metric">
                  <span class="metric__label">🏨 住宿</span>
                  <span class="metric__value">
                    {{ activeDay.accommodation || '—' }}
                  </span>
                </div>
                <div class="metric">
                  <span class="metric__label">🍜 餐饮</span>
                  <span class="metric__value">
                    {{ activeDay.mealCost ?? '—' }}
                  </span>
                </div>
                <div class="metric">
                  <span class="metric__label">🚆 交通</span>
                  <span class="metric__value">
                    {{ activeDay.transportCost ?? '—' }}
                  </span>
                </div>
                <div class="metric">
                  <span class="metric__label">💸 其他</span>
                  <span class="metric__value">
                    {{ activeDay.otherCost ?? '—' }}
                  </span>
                </div>
              </div>
            </ElCard>

            <ElCard class="panel" shadow="never">
              <template #header>
                <div class="panel__header">
                  <span class="panel__title">
                    打卡点
                    <span class="panel__count">{{ activeCheckins.length }}</span>
                  </span>
                </div>
              </template>

              <ElEmpty
                v-if="activeCheckins.length === 0"
                :image-size="80"
                description="该日还没有打卡点"
              />
              <div v-else class="checkin-list">
                <div
                  v-for="(c, idx) in activeCheckins"
                  :key="c.id"
                  class="checkin"
                >
                  <div class="checkin__rail">
                    <span class="checkin__seq">{{ idx + 1 }}</span>
                    <span
                      v-if="idx !== activeCheckins.length - 1"
                      class="checkin__line"
                    />
                  </div>
                  <div class="checkin__body">
                    <div class="checkin__head">
                      <div class="checkin__title-wrap">
                        <span class="checkin__title">
                          {{ c.destinationName || c.customName || `#${c.id}` }}
                        </span>
                        <ElTag
                          v-if="c.rating != null"
                          type="warning"
                          effect="plain"
                          size="small"
                        >
                          ★ {{ c.rating }}
                        </ElTag>
                      </div>
                      <div class="checkin__actions">
                        <ElButton
                          v-access:code="'blog:travel:edit'"
                          link
                          type="primary"
                          @click="openEditCheckin(c)"
                        >
                          编辑
                        </ElButton>
                        <ElButton
                          v-access:code="'blog:travel:delete'"
                          link
                          type="danger"
                          @click="deleteCheckin(c)"
                        >
                          删除
                        </ElButton>
                      </div>
                    </div>
                    <div
                      v-if="formatTimeRange(c.arrivalTime, c.departureTime)"
                      class="checkin__time"
                    >
                      🕒 {{ formatTimeRange(c.arrivalTime, c.departureTime) }}
                    </div>
                    <p v-if="c.notes" class="checkin__notes">{{ c.notes }}</p>
                    <div v-if="c.photoUrls?.length" class="checkin__photos">
                      <ElImage
                        v-for="(url, pIdx) in c.photoUrls"
                        :key="pIdx"
                        :src="url"
                        fit="cover"
                        class="checkin__thumb"
                        @click="openPreview(c.photoUrls!, pIdx)"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </ElCard>

            <ElCard
              v-if="galleryPhotos.length > 0"
              class="panel"
              shadow="never"
            >
              <template #header>
                <div class="panel__header">
                  <span class="panel__title">
                    📷 当日相册
                    <span class="panel__count">{{ galleryPhotos.length }}</span>
                  </span>
                </div>
              </template>
              <div class="gallery">
                <ElImage
                  v-for="(item, idx) in galleryPhotos"
                  :key="idx"
                  :src="item.url"
                  fit="cover"
                  class="gallery__item"
                  @click="
                    openPreview(
                      galleryPhotos.map((g) => g.url),
                      idx,
                    )
                  "
                />
              </div>
            </ElCard>
          </template>
        </main>
      </div>
    </div>

    <ElImageViewer
      v-if="previewVisible"
      :url-list="previewUrls"
      :initial-index="previewIndex"
      @close="previewVisible = false"
    />

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
      width="640"
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
          <div class="coord-row">
            <ElFormItem prop="customLongitude" class="coord-row__item">
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
            <ElFormItem prop="customLatitude" class="coord-row__item">
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
            value-format="YYYY-MM-DD HH:mm:ss"
            type="datetime"
            placeholder="可选"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="离开时间">
          <ElDatePicker
            v-model="checkinForm.departureTime"
            value-format="YYYY-MM-DD HH:mm:ss"
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
        <ElFormItem label="照片">
          <div class="photo-wrap">
            <div v-if="checkinForm.photos.length" class="photo-grid">
              <div
                v-for="(p, idx) in checkinForm.photos"
                :key="`${p.id}-${idx}`"
                class="photo-item"
              >
                <img v-if="p.url" :src="p.url" alt="photo" class="photo-img" />
                <div v-else class="photo-fallback">#{{ p.id }}</div>
                <ElButton
                  class="photo-remove"
                  size="small"
                  type="danger"
                  link
                  @click="removeCheckinPhoto(idx)"
                >
                  移除
                </ElButton>
              </div>
            </div>
            <ElUpload
              v-if="checkinForm.photos.length < MAX_CHECKIN_PHOTOS"
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              @change="handleCheckinPhotoChange"
            >
              <ElButton
                :loading="checkinPhotoUploading"
                size="small"
                type="primary"
                plain
              >
                上传照片
              </ElButton>
            </ElUpload>
            <span class="photo-hint">
              最多 {{ MAX_CHECKIN_PHOTOS }} 张，支持 JPG、PNG、WebP、GIF
            </span>
          </div>
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

    <!-- 关联文章 弹窗 -->
    <ElDialog
      v-model="postDialogVisible"
      :close-on-click-modal="false"
      title="管理关联文章"
      width="720"
    >
      <div v-loading="postLoading" class="bind-wrap">
        <div class="bind-toolbar">
          <ElInput
            v-model="postKeyword"
            placeholder="按标题/Slug 搜索文章"
            clearable
            class="bind-toolbar__search"
            @keyup.enter="searchPosts"
          />
          <ElButton type="primary" @click="searchPosts">搜索</ElButton>
        </div>
        <p class="bind-hint">
          穿梭至右侧的文章会保存为该游记的关联文章；可设置一篇主要文章。
        </p>

        <ElTransfer
          v-model="selectedPostIds"
          :data="transferData"
          :titles="['可选文章', '已选文章']"
          filterable
          target-order="push"
        />

        <div class="primary-row">
          <span class="primary-row__label">主要文章</span>
          <ElSelect
            v-model="primaryPostId"
            placeholder="可选，标记主推文章"
            clearable
            class="primary-row__select"
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
        </div>
      </div>
      <template #footer>
        <ElButton @click="postDialogVisible = false">取消</ElButton>
        <ElButton :loading="postLoading" type="primary" @click="submitBindPosts">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.trip-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ==================== Hero ==================== */
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 28px 22px;
  overflow: hidden;
  color: #fff;
  background-color: var(--el-color-primary);
  background-position: center;
  background-size: cover;
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
}

.hero--no-cover {
  background-image: linear-gradient(
    135deg,
    var(--el-color-primary) 0%,
    var(--el-color-primary-light-3) 100%
  );
}

.hero__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero__back {
  color: rgba(255, 255, 255, 0.9) !important;
  font-size: 13px;
}

.hero__back:hover {
  color: #fff !important;
}

.hero__main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 720px;
}

.hero__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.hero__summary {
  margin: 0;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  line-height: 1.6;
}

.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 13px;
}

.hero__meta-icon {
  margin-right: 4px;
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 96px;
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  backdrop-filter: blur(6px);
}

.stat-card__value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-card__label {
  color: rgba(255, 255, 255, 0.85);
  font-size: 12px;
}

/* ==================== Layout ==================== */
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 1024px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.main__empty {
  padding: 60px 0;
  background: var(--el-bg-color);
  border-radius: 12px;
}

/* ==================== 通用面板 ==================== */
.panel :deep(.el-card__header) {
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel :deep(.el-card__body) {
  padding: 14px 16px;
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.panel__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.panel__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  background: var(--el-color-primary-light-9);
  border-radius: 10px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

/* ==================== 时间线 ==================== */
.timeline {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline__item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.2s ease;
}

.timeline__item:hover {
  background: var(--el-fill-color-light);
}

.timeline__item--active {
  background: var(--el-color-primary-light-9);
}

.timeline__node {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--el-fill-color);
  border: 2px solid var(--el-border-color);
  border-radius: 50%;
  transition: all 0.2s ease;
}

.timeline__item--active .timeline__node {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.timeline__day {
  color: var(--el-text-color-regular);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.timeline__item--active .timeline__day {
  color: #fff;
}

.timeline__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.timeline__title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.timeline__acc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}

/* ==================== 关联文章列表 ==================== */
.post-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.post-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  transition: background 0.2s ease;
}

.post-list__item:hover {
  background: var(--el-fill-color);
}

.post-list__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.post-list__title {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-list__slug {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  font-family: var(--el-font-family-monospace, monospace);
}

/* ==================== 当日详情卡片 ==================== */
.day-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 22px;
}

.day-card__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.day-card__title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.day-card__title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.3;
}

.day-card__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}

.day-card__desc {
  margin: 0;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.day-card__metrics {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.metric__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.metric__value {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ==================== 打卡点（时间轴风格） ==================== */
.checkin-list {
  display: flex;
  flex-direction: column;
}

.checkin {
  display: flex;
  gap: 14px;
}

.checkin__rail {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 28px;
}

.checkin__seq {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-top: 4px;
  background: var(--el-color-primary);
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.checkin__line {
  flex: 1;
  width: 2px;
  margin: 4px 0;
  background: var(--el-border-color-lighter);
}

.checkin__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.checkin__body:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.checkin__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.checkin__title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.checkin__title {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.checkin__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
}

.checkin__time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.checkin__notes {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.checkin__photos {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.checkin__thumb {
  width: 72px;
  height: 72px;
  cursor: pointer;
  border-radius: 6px;
  overflow: hidden;
  transition: transform 0.2s ease;
}

.checkin__thumb:hover {
  transform: scale(1.04);
}

/* ==================== 当日相册 ==================== */
.gallery {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
}

.gallery__item {
  width: 100%;
  aspect-ratio: 1 / 1;
  cursor: pointer;
  border-radius: 6px;
  overflow: hidden;
  transition: transform 0.2s ease;
}

.gallery__item:hover {
  transform: scale(1.03);
}

/* ==================== 弹窗 — 关联文章 ==================== */
.bind-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bind-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.bind-toolbar__search {
  width: 280px;
}

.bind-hint {
  margin: 0;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.primary-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.primary-row__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 500;
}

.primary-row__select {
  width: 320px;
}

/* ==================== 弹窗 — 经纬度行 ==================== */
.coord-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.coord-row__item {
  flex: 1;
  margin-bottom: 0;
}

/* ==================== 弹窗 — 照片网格 ==================== */
.photo-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.photo-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
}

.photo-item {
  position: relative;
  overflow: hidden;
  aspect-ratio: 1 / 1;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.photo-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.photo-remove {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 2px 8px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 4px;
  color: #fff !important;
  font-size: 12px;
}

.photo-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
