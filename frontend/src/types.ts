export type Milestone = {
  id: number
  name: string
  category: 'CONFIRMED' | 'RECOMMENDED'
  amount: number | null
  dueDate: string | null
  status: 'PENDING' | 'COMPLETED' | 'SCHEDULED'
  source: string
  paymentRail: string | null
  paymentId: string | null
}

export type Funding = {
  availableFunds: number
  expectedIncome: number
  upcomingConfirmed: number
  projectedBalance: number
  fundingGap: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
}

export type ActionStep = {
  sequence: number
  type: 'COMPLETED' | 'REVIEW_DATE' | 'ARRANGE_FUNDS' | 'RESERVE_FUNDS' | 'PAY' | string
  title: string
  rationale: string
  actionDate: string | null
  paymentDueDate: string | null
  amount: number | null
  amountToArrange: number | null
  projectedBalanceAfter: number | null
  status: string
  priority: string
  milestoneId: number | null
}

export type CashFlowPoint = {
  date: string
  label: string
  inflow: number
  outflow: number
  projectedBalance: number
  kind: string
}

export type Planning = {
  startingLiquidity: number
  protectedReserve: number
  activeMonthlyEmis: number
  averageMonthlySpend: number
  expectedMonthlyIncome: number
  nextIncomeDate: string | null
  spendableNow: number
  totalToArrange: number
  projectedEndBalance: number
  nextCriticalDate: string | null
  summary: string
  actions: ActionStep[]
  cashFlow: CashFlowPoint[]
}

export type Journey = {
  id: number
  eventType: string
  title: string
  status: string
  customerName: string
  createdAt: string
  updatedAt: string
  milestones: Milestone[]
  funding: Funding
  plan: Planning
  progressPercent: number
}

export type JourneyEvent = {
  id: number
  journeyId: number
  type: string
  message: string
  occurredAt: string
}

export type Analysis = {
  eventType: string
  title: string
  summary: string
  obligations: Array<{name:string; amount:number|null; dueDate:string|null; source:string; paymentRail:string|null; alreadyPaid?:boolean}>
  recommendations: Array<{name:string; amount:number|null; dueDate:string|null; source:string}>
  confidence: number
  provider: string
  documentPreview?: string
}

export type BankAccount = {
  name: string
  type: string
  maskedNumber: string
  balance: number
}

export type BankLoan = {
  name: string
  maskedNumber: string
  outstanding: number
  emi: number
  interestRate: number
  nextDueDate: string
  remainingMonths: number
}

export type BankTransaction = {
  date: string
  description: string
  category: string
  amount: number
  direction: 'DEBIT' | 'CREDIT'
}

export type BankProfile = {
  customerName: string
  totalLiquidBalance: number
  accounts: BankAccount[]
  loans: BankLoan[]
  expectedMonthlyIncome: number
  nextIncomeDate: string
  averageMonthlySpend: number
  planningReserve: number
  recentTransactions: BankTransaction[]
}

export type AiHealth = {
  status?: string
  openrouterConfigured?: boolean
  openrouterModel?: string
  ollamaModel?: string
  priority?: 'openrouter-first' | 'ollama-first' | string
  providerOrder?: string[]
}
