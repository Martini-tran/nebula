import { requestClient } from '#/api/request';

/**
 * 博客-旅行 API
 * blog 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace BlogTravelApi {
  // -------------------- Destination --------------------

  export interface Destination {
    id: number | string;
    parentId?: number | string | null;
    name: string;
    slug: string;
    /** 0 国家 / 1 省份 / 2 城市 / 3 景点 */
    type: number;
    description?: string;
    coverFileId?: number | string;
    coverUrl?: string;
    longitude?: number | string;
    latitude?: number | string;
    address?: string;
    visitCount?: number;
    rating?: number | string;
    status?: number;
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
    children?: Destination[];
  }

  export interface DestinationCreateParams {
    parentId?: number | string | null;
    name: string;
    slug: string;
    type: number;
    description?: string;
    coverFileId?: number | string;
    longitude?: number | string;
    latitude?: number | string;
    address?: string;
    status?: number;
    sortOrder?: number;
  }

  export interface DestinationUpdateParams {
    parentId?: number | string | null;
    name?: string;
    slug?: string;
    type?: number;
    description?: string;
    coverFileId?: number | string;
    clearCoverFileId?: boolean;
    longitude?: number | string;
    latitude?: number | string;
    address?: string;
    status?: number;
    sortOrder?: number;
  }

  // -------------------- Trip --------------------

  export interface TripPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: string;
    visibility?: string;
    userId?: number | string;
    startDateFrom?: string;
    startDateTo?: string;
  }

  export interface Trip {
    id: number | string;
    userId?: number | string;
    title: string;
    slug: string;
    summary?: string;
    coverFileId?: number | string;
    coverUrl?: string;
    status: string;
    visibility: string;
    startDate?: string;
    endDate?: string;
    daysCount?: number;
    persons?: number;
    costTotal?: number | string;
    costCurrency?: string;
    viewCount?: number;
    likeCount?: number;
    publishedAt?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface TripPageResult {
    records: Trip[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }

  export interface TripCreateParams {
    title: string;
    slug: string;
    summary?: string;
    coverFileId?: number | string;
    status?: string;
    visibility?: string;
    startDate?: string;
    endDate?: string;
    persons?: number;
    costTotal?: number | string;
    costCurrency?: string;
  }

  export interface TripUpdateParams {
    title?: string;
    slug?: string;
    summary?: string;
    coverFileId?: number | string;
    clearCoverFileId?: boolean;
    status?: string;
    visibility?: string;
    startDate?: string;
    endDate?: string;
    persons?: number;
    costTotal?: number | string;
    costCurrency?: string;
  }

  export interface TripStatusUpdateParams {
    status?: string;
    visibility?: string;
    publishedAt?: string;
  }

  // -------------------- Trip Day --------------------

  export interface TripDay {
    id: number | string;
    tripId: number | string;
    dayNumber: number;
    title?: string;
    description?: string;
    accommodation?: string;
    mealCost?: number | string;
    transportCost?: number | string;
    otherCost?: number | string;
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface TripDayCreateParams {
    tripId: number | string;
    dayNumber: number;
    title?: string;
    description?: string;
    accommodation?: string;
    mealCost?: number | string;
    transportCost?: number | string;
    otherCost?: number | string;
    sortOrder?: number;
  }

  export interface TripDayUpdateParams {
    dayNumber?: number;
    title?: string;
    description?: string;
    accommodation?: string;
    mealCost?: number | string;
    transportCost?: number | string;
    otherCost?: number | string;
    sortOrder?: number;
  }

  // -------------------- Checkin --------------------

  export interface Checkin {
    id: number | string;
    tripDayId: number | string;
    destinationId?: number | string | null;
    destinationName?: string;
    customName?: string;
    customLongitude?: number | string | null;
    customLatitude?: number | string | null;
    arrivalTime?: string;
    departureTime?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    photoIds?: Array<number | string>;
    photoUrls?: string[];
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface CheckinCreateParams {
    tripDayId: number | string;
    destinationId?: number | string | null;
    customName?: string;
    customLongitude?: number | string | null;
    customLatitude?: number | string | null;
    arrivalTime?: string;
    departureTime?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    sortOrder?: number;
  }

  export interface CheckinUpdateParams {
    destinationId?: number | string | null;
    clearDestinationId?: boolean;
    customName?: string;
    customLongitude?: number | string | null;
    customLatitude?: number | string | null;
    arrivalTime?: string;
    departureTime?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    sortOrder?: number;
  }

  // -------------------- Trip ↔ Post --------------------

  export interface TripPost {
    tripId: number | string;
    postId: number | string;
    postType?: number;
    postTitle?: string;
    postSlug?: string;
    postStatus?: string;
    createTime?: string;
  }

  export interface TripPostBindParams {
    postIds: Array<number | string>;
    primaryPostId?: number | string | null;
  }
}

// -------------------- Destination APIs --------------------

export async function getBlogTravelDestinationTreeApi() {
  const data = await requestClient.get<BlogTravelApi.Destination[]>(
    '/blog/admin/travel/destinations/tree',
  );
  return data ?? [];
}

export async function getBlogTravelDestinationDetailApi(id: number | string) {
  return requestClient.get<BlogTravelApi.Destination>(
    `/blog/admin/travel/destinations/${id}`,
  );
}

export async function createBlogTravelDestinationApi(
  data: BlogTravelApi.DestinationCreateParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/travel/destinations',
    data,
  );
}

export async function updateBlogTravelDestinationApi(
  id: number | string,
  data: BlogTravelApi.DestinationUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/destinations/${id}`, data);
}

export async function deleteBlogTravelDestinationApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/travel/destinations/${id}`);
}

// -------------------- Trip APIs --------------------

export async function getBlogTravelTripPageApi(
  params: BlogTravelApi.TripPageQuery,
) {
  return requestClient.get<BlogTravelApi.TripPageResult>(
    '/blog/admin/travel/trips/page',
    { params },
  );
}

export async function getBlogTravelTripDetailApi(id: number | string) {
  return requestClient.get<BlogTravelApi.Trip>(
    `/blog/admin/travel/trips/${id}`,
  );
}

export async function createBlogTravelTripApi(
  data: BlogTravelApi.TripCreateParams,
) {
  return requestClient.post<number | string>('/blog/admin/travel/trips', data);
}

export async function updateBlogTravelTripApi(
  id: number | string,
  data: BlogTravelApi.TripUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/trips/${id}`, data);
}

export async function updateBlogTravelTripStatusApi(
  id: number | string,
  data: BlogTravelApi.TripStatusUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/trips/${id}/status`, data);
}

export async function deleteBlogTravelTripApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/travel/trips/${id}`);
}

export async function getBlogTravelTripPostsApi(id: number | string) {
  const data = await requestClient.get<BlogTravelApi.TripPost[]>(
    `/blog/admin/travel/trips/${id}/posts`,
  );
  return data ?? [];
}

export async function bindBlogTravelTripPostsApi(
  id: number | string,
  data: BlogTravelApi.TripPostBindParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/trips/${id}/posts`, data);
}

// -------------------- Trip Day APIs --------------------

export async function getBlogTravelTripDayListApi(tripId: number | string) {
  const data = await requestClient.get<BlogTravelApi.TripDay[]>(
    '/blog/admin/travel/trip-days',
    { params: { tripId } },
  );
  return data ?? [];
}

export async function getBlogTravelTripDayDetailApi(id: number | string) {
  return requestClient.get<BlogTravelApi.TripDay>(
    `/blog/admin/travel/trip-days/${id}`,
  );
}

export async function createBlogTravelTripDayApi(
  data: BlogTravelApi.TripDayCreateParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/travel/trip-days',
    data,
  );
}

export async function updateBlogTravelTripDayApi(
  id: number | string,
  data: BlogTravelApi.TripDayUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/trip-days/${id}`, data);
}

export async function deleteBlogTravelTripDayApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/travel/trip-days/${id}`);
}

// -------------------- Checkin APIs --------------------

export async function getBlogTravelCheckinListApi(tripDayId: number | string) {
  const data = await requestClient.get<BlogTravelApi.Checkin[]>(
    '/blog/admin/travel/checkins',
    { params: { tripDayId } },
  );
  return data ?? [];
}

export async function getBlogTravelCheckinDetailApi(id: number | string) {
  return requestClient.get<BlogTravelApi.Checkin>(
    `/blog/admin/travel/checkins/${id}`,
  );
}

export async function createBlogTravelCheckinApi(
  data: BlogTravelApi.CheckinCreateParams,
) {
  return requestClient.post<number | string>(
    '/blog/admin/travel/checkins',
    data,
  );
}

export async function updateBlogTravelCheckinApi(
  id: number | string,
  data: BlogTravelApi.CheckinUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/checkins/${id}`, data);
}

export async function deleteBlogTravelCheckinApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/travel/checkins/${id}`);
}
