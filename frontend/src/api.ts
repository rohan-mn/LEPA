import type { AiHealth, Analysis, BankProfile, Journey, JourneyEvent } from './types'

const API = import.meta.env.VITE_API_URL || '/api'

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(`${API}${path}`, init)
  if (!r.ok) {
    const body = await r.json().catch(() => ({}))
    throw new Error(body.error || body.message || `Request failed (${r.status})`)
  }
  return r.json()
}

export const api = {
  journeys: () => req<Journey[]>('/journeys'),
  journey: (id:number) => req<Journey>(`/journeys/${id}`),
  events: () => req<JourneyEvent[]>('/events/recent'),
  bankProfile: () => req<BankProfile>('/bank-profile'),
  aiHealth: () => req<AiHealth>('/ai/health'),
  reset: () => req<{status:string;message:string}>('/system/reset', {method:'POST'}),
  execute: (milestoneId:number) => req<Journey>(`/milestones/${milestoneId}/execute`, {method:'POST'}),
  credit: (journeyId:number, amount:number) => req<Journey>(`/journeys/${journeyId}/credit`, {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({amount})
  }),
  analyze: async (file:File, eventType:string) => {
    const fd = new FormData(); fd.append('file', file); fd.append('eventType', eventType)
    return req<Analysis>('/ai/analyze', {method:'POST', body:fd})
  },
  createJourney: (body:unknown) => req<Journey>('/journeys/from-analysis', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)
  }),
  chat: (journeyId:number, message:string) => req<{answer:string; provider:string}>('/ai/chat', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({journeyId, message})
  })
}
