import { get } from '../utils/request'

export interface TravelTripListItem {
  id: number | string
  slug: string
  title: string
  summary?: string | null
  coverUrl?: string | null
  startDate?: string | null
  endDate?: string | null
  daysCount?: number | null
  persons?: number | null
  costTotal?: number | null
  costCurrency?: string | null
  viewCount: number
  likeCount: number
  publishedAt: string
}

export interface TravelDestinationSummary {
  id: number | string
  name: string
  slug?: string | null
  type?: number | null
}

export interface TravelCheckin {
  id: number | string
  customName?: string | null
  destinationId?: number | string | null
  destinationName?: string | null
  customLongitude?: number | null
  customLatitude?: number | null
  arrivalTime?: string | null
  departureTime?: string | null
  notes?: string | null
  rating?: number | null
  photoUrls: string[]
  sortOrder?: number | null
}

export interface TravelTripDay {
  id: number | string
  dayNumber: number
  title?: string | null
  description?: string | null
  accommodation?: string | null
  mealCost?: number | null
  transportCost?: number | null
  otherCost?: number | null
  sortOrder?: number | null
  checkins: TravelCheckin[]
}

export interface TravelTripPostSummary {
  postId: number | string
  slug: string
  title: string
  summary?: string | null
  coverUrl?: string | null
  postType?: number | null
}

export interface TravelTripDetail extends TravelTripListItem {
  days: TravelTripDay[]
  destinations: TravelDestinationSummary[]
  posts?: TravelTripPostSummary[] | null
}

export interface TravelTripListResponse {
  items: TravelTripListItem[]
  nextCursor: string | null
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
