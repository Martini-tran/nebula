import { requestClient } from '#/api/request';

export namespace BlogTravelApi {
  // -------------------- Destination --------------------

  export interface DestinationRaw {
    id: number | string;
    parent_id?: number | string | null;
    name: string;
    slug: string;
    type: number;
    description?: string;
    cover_file_id?: number | string;
    cover_url?: string;
    location?: string;
    address?: string;
    visit_count?: number;
    rating?: number | string;
    status?: number;
    sort_order?: number;
    create_time?: string;
    update_time?: string;
    children?: DestinationRaw[];
  }

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
    location?: string;
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
    parent_id?: number | string | null;
    name: string;
    slug: string;
    type: number;
    description?: string;
    cover_file_id?: number | string;
    location?: string;
    address?: string;
    status?: number;
    sort_order?: number;
  }

  export interface DestinationUpdateParams {
    parent_id?: number | string | null;
    name?: string;
    slug?: string;
    type?: number;
    description?: string;
    cover_file_id?: number | string;
    clear_cover_file_id?: boolean;
    location?: string;
    address?: string;
    status?: number;
    sort_order?: number;
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

  export interface TripRaw {
    id: number | string;
    user_id?: number | string;
    title: string;
    slug: string;
    summary?: string;
    cover_file_id?: number | string;
    cover_url?: string;
    status: string;
    visibility: string;
    start_date?: string;
    end_date?: string;
    days_count?: number;
    persons?: number;
    cost_total?: number | string;
    cost_currency?: string;
    view_count?: number;
    like_count?: number;
    published_at?: string;
    create_time?: string;
    update_time?: string;
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
    cover_file_id?: number | string;
    status?: string;
    visibility?: string;
    start_date?: string;
    end_date?: string;
    persons?: number;
    cost_total?: number | string;
    cost_currency?: string;
  }

  export interface TripUpdateParams {
    title?: string;
    slug?: string;
    summary?: string;
    cover_file_id?: number | string;
    clear_cover_file_id?: boolean;
    status?: string;
    visibility?: string;
    start_date?: string;
    end_date?: string;
    persons?: number;
    cost_total?: number | string;
    cost_currency?: string;
  }

  export interface TripStatusUpdateParams {
    status?: string;
    visibility?: string;
    published_at?: string;
  }

  // -------------------- Trip Day --------------------

  export interface TripDayRaw {
    id: number | string;
    trip_id: number | string;
    day_number: number;
    title?: string;
    description?: string;
    accommodation?: string;
    meal_cost?: number | string;
    transport_cost?: number | string;
    other_cost?: number | string;
    sort_order?: number;
    create_time?: string;
    update_time?: string;
  }

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
    trip_id: number | string;
    day_number: number;
    title?: string;
    description?: string;
    accommodation?: string;
    meal_cost?: number | string;
    transport_cost?: number | string;
    other_cost?: number | string;
    sort_order?: number;
  }

  export interface TripDayUpdateParams {
    day_number?: number;
    title?: string;
    description?: string;
    accommodation?: string;
    meal_cost?: number | string;
    transport_cost?: number | string;
    other_cost?: number | string;
    sort_order?: number;
  }

  // -------------------- Checkin --------------------

  export interface CheckinRaw {
    id: number | string;
    trip_day_id: number | string;
    destination_id?: number | string | null;
    destination_name?: string;
    custom_name?: string;
    custom_location?: string;
    arrival_time?: string;
    departure_time?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    sort_order?: number;
    create_time?: string;
    update_time?: string;
  }

  export interface Checkin {
    id: number | string;
    tripDayId: number | string;
    destinationId?: number | string | null;
    destinationName?: string;
    customName?: string;
    customLocation?: string;
    arrivalTime?: string;
    departureTime?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    sortOrder?: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface CheckinCreateParams {
    trip_day_id: number | string;
    destination_id?: number | string | null;
    custom_name?: string;
    custom_location?: string;
    arrival_time?: string;
    departure_time?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    sort_order?: number;
  }

  export interface CheckinUpdateParams {
    destination_id?: number | string | null;
    clear_destination_id?: boolean;
    custom_name?: string;
    custom_location?: string;
    arrival_time?: string;
    departure_time?: string;
    notes?: string;
    rating?: number | string;
    photos?: string;
    sort_order?: number;
  }

  // -------------------- Trip ↔ Post --------------------

  export interface TripPostRaw {
    trip_id: number | string;
    post_id: number | string;
    post_type?: number;
    post_title?: string;
    post_slug?: string;
    post_status?: string;
    create_time?: string;
  }

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
    post_ids: Array<number | string>;
    primary_post_id?: number | string | null;
  }
}

// -------------------- Normalizers --------------------

function normalizeDestination(
  raw: BlogTravelApi.DestinationRaw,
): BlogTravelApi.Destination {
  return {
    id: raw.id,
    parentId: raw.parent_id ?? null,
    name: raw.name,
    slug: raw.slug,
    type: raw.type,
    description: raw.description,
    coverFileId: raw.cover_file_id,
    coverUrl: raw.cover_url,
    location: raw.location,
    address: raw.address,
    visitCount: raw.visit_count,
    rating: raw.rating,
    status: raw.status,
    sortOrder: raw.sort_order,
    createTime: raw.create_time,
    updateTime: raw.update_time,
    children: raw.children?.map(normalizeDestination) ?? [],
  };
}

function normalizeTrip(raw: BlogTravelApi.TripRaw): BlogTravelApi.Trip {
  return {
    id: raw.id,
    userId: raw.user_id,
    title: raw.title,
    slug: raw.slug,
    summary: raw.summary,
    coverFileId: raw.cover_file_id,
    coverUrl: raw.cover_url,
    status: raw.status,
    visibility: raw.visibility,
    startDate: raw.start_date,
    endDate: raw.end_date,
    daysCount: raw.days_count,
    persons: raw.persons,
    costTotal: raw.cost_total,
    costCurrency: raw.cost_currency,
    viewCount: raw.view_count,
    likeCount: raw.like_count,
    publishedAt: raw.published_at,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeTripDay(
  raw: BlogTravelApi.TripDayRaw,
): BlogTravelApi.TripDay {
  return {
    id: raw.id,
    tripId: raw.trip_id,
    dayNumber: raw.day_number,
    title: raw.title,
    description: raw.description,
    accommodation: raw.accommodation,
    mealCost: raw.meal_cost,
    transportCost: raw.transport_cost,
    otherCost: raw.other_cost,
    sortOrder: raw.sort_order,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeCheckin(
  raw: BlogTravelApi.CheckinRaw,
): BlogTravelApi.Checkin {
  return {
    id: raw.id,
    tripDayId: raw.trip_day_id,
    destinationId: raw.destination_id ?? null,
    destinationName: raw.destination_name,
    customName: raw.custom_name,
    customLocation: raw.custom_location,
    arrivalTime: raw.arrival_time,
    departureTime: raw.departure_time,
    notes: raw.notes,
    rating: raw.rating,
    photos: raw.photos,
    sortOrder: raw.sort_order,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizeTripPost(
  raw: BlogTravelApi.TripPostRaw,
): BlogTravelApi.TripPost {
  return {
    tripId: raw.trip_id,
    postId: raw.post_id,
    postType: raw.post_type,
    postTitle: raw.post_title,
    postSlug: raw.post_slug,
    postStatus: raw.post_status,
    createTime: raw.create_time,
  };
}

// -------------------- Destination APIs --------------------

export async function getBlogTravelDestinationTreeApi() {
  const data = await requestClient.get<BlogTravelApi.DestinationRaw[]>(
    '/blog/admin/travel/destinations/tree',
  );
  return (data ?? []).map(normalizeDestination);
}

export async function getBlogTravelDestinationDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogTravelApi.DestinationRaw>(
    `/blog/admin/travel/destinations/${id}`,
  );
  return normalizeDestination(raw);
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
  const result = await requestClient.get<{
    records: BlogTravelApi.TripRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/blog/admin/travel/trips/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeTrip),
  } as BlogTravelApi.TripPageResult;
}

export async function getBlogTravelTripDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogTravelApi.TripRaw>(
    `/blog/admin/travel/trips/${id}`,
  );
  return normalizeTrip(raw);
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
  const data = await requestClient.get<BlogTravelApi.TripPostRaw[]>(
    `/blog/admin/travel/trips/${id}/posts`,
  );
  return (data ?? []).map(normalizeTripPost);
}

export async function bindBlogTravelTripPostsApi(
  id: number | string,
  data: BlogTravelApi.TripPostBindParams,
) {
  return requestClient.put<void>(`/blog/admin/travel/trips/${id}/posts`, data);
}

// -------------------- Trip Day APIs --------------------

export async function getBlogTravelTripDayListApi(tripId: number | string) {
  const data = await requestClient.get<BlogTravelApi.TripDayRaw[]>(
    '/blog/admin/travel/trip-days',
    { params: { tripId } },
  );
  return (data ?? []).map(normalizeTripDay);
}

export async function getBlogTravelTripDayDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogTravelApi.TripDayRaw>(
    `/blog/admin/travel/trip-days/${id}`,
  );
  return normalizeTripDay(raw);
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
  const data = await requestClient.get<BlogTravelApi.CheckinRaw[]>(
    '/blog/admin/travel/checkins',
    { params: { tripDayId } },
  );
  return (data ?? []).map(normalizeCheckin);
}

export async function getBlogTravelCheckinDetailApi(id: number | string) {
  const raw = await requestClient.get<BlogTravelApi.CheckinRaw>(
    `/blog/admin/travel/checkins/${id}`,
  );
  return normalizeCheckin(raw);
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
