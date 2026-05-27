import { get } from '../utils/request'

export interface TravelTripListItem {
  id: number | string
  slug: string
  title: string
  summary?: string | null
  cover_url?: string | null
  start_date?: string | null
  end_date?: string | null
  days_count?: number | null
  persons?: number | null
  cost_total?: number | null
  cost_currency?: string | null
  view_count: number
  like_count: number
  published_at: string
}

export interface TravelDestinationSummary {
  id: number | string
  name: string
  slug?: string | null
  type?: number | null
}

export interface TravelCheckin {
  id: number | string
  custom_name?: string | null
  destination_id?: number | string | null
  destination_name?: string | null
  custom_location?: string | null
  arrival_time?: string | null
  departure_time?: string | null
  notes?: string | null
  rating?: number | null
  photo_urls: string[]
  sort_order?: number | null
}

export interface TravelTripDay {
  id: number | string
  day_number: number
  title?: string | null
  description?: string | null
  accommodation?: string | null
  meal_cost?: number | null
  transport_cost?: number | null
  other_cost?: number | null
  sort_order?: number | null
  checkins: TravelCheckin[]
}

export interface TravelTripDetail extends TravelTripListItem {
  days: TravelTripDay[]
  destinations: TravelDestinationSummary[]
}

export interface TravelTripListResponse {
  items: TravelTripListItem[]
  next_cursor: string | null
}

export interface FetchTripsParams {
  keyword?: string
  destinationId?: number | string | null
  cursor?: string | null
  limit?: number
}

const omitEmpty = <T extends Record<string, unknown>>(obj: T) =>
  Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  )

export const fetchTrips = (params: FetchTripsParams = {}) =>
  get<TravelTripListResponse>('/blog/front/travel/trips', {
    params: omitEmpty({
      keyword: params.keyword,
      destinationId: params.destinationId,
      cursor: params.cursor,
      limit: params.limit,
    }),
  })

export const searchTrips = (params: FetchTripsParams = {}) =>
  get<TravelTripListResponse>('/blog/front/travel/trips/search', {
    params: omitEmpty({
      keyword: params.keyword,
      destinationId: params.destinationId,
      cursor: params.cursor,
      limit: params.limit,
    }),
  })

export const fetchHotTrips = (limit = 5) =>
  get<TravelTripListItem[]>('/blog/front/travel/trips/hot', { params: { limit } })

export const fetchTripDetail = (slug: string) =>
  get<TravelTripDetail>(`/blog/front/travel/trips/${encodeURIComponent(slug)}`)

export const fetchTravelDestinations = (limit = 30) =>
  get<TravelDestinationSummary[]>('/blog/front/travel/destinations', {
    params: { limit },
  })
