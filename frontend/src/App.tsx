import { useEffect, useMemo, useState } from 'react'
import type { ChangeEvent, KeyboardEvent } from 'react'
import {
  Activity, AlertTriangle, ArrowRight, BadgeCheck, Banknote, Bot, CalendarDays,
  CheckCircle2, ChevronRight, Clock3, FileText, Home, Landmark, Loader2,
  MessageSquareText, Plus, ReceiptText, RefreshCw, ShieldCheck, Trash2, Upload,
  WalletCards, X, TrendingDown, CircleDollarSign, CalendarClock, Target, ArrowDownRight,
  ArrowUpRight, Sparkles
} from 'lucide-react'
import { Area, AreaChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from './api'
import type { ActionStep, AiHealth, Analysis, BankProfile, Journey, JourneyEvent, Milestone } from './types'

const money = (v:number|null|undefined) => v == null ? '—' : new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(v)
const shortMoney = (v:number|null|undefined) => {
  if(v == null) return '—'
  if(Math.abs(v)>=10000000) return `₹${(v/10000000).toFixed(1)}Cr`
  if(Math.abs(v)>=100000) return `₹${(v/100000).toFixed(1)}L`
  if(Math.abs(v)>=1000) return `₹${(v/1000).toFixed(0)}K`
  return money(v)
}
const date = (v:string|null|undefined) => v ? new Intl.DateTimeFormat('en-IN',{day:'2-digit',month:'short',year:'numeric'}).format(new Date(v)) : 'Date to confirm'
const when = (v:string) => new Intl.DateTimeFormat('en-IN',{day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}).format(new Date(v))

function syncBankBalance(bank:BankProfile,total:number):BankProfile{
  if(!bank.accounts.length)return {...bank,totalLiquidBalance:total}
  const delta=total-bank.totalLiquidBalance
  const accounts=bank.accounts.map((a,i)=>i===0?{...a,balance:Math.max(0,a.balance+delta)}:a)
  return {...bank,totalLiquidBalance:total,accounts}
}

function Logo(){
  return <div className="brand"><div className="brandMark">L</div><div><strong>LEPA</strong><span>Life Event Payment Agent</span></div></div>
}

function Sidebar({page,setPage,hasJourney}:{page:string,setPage:(p:string)=>void,hasJourney:boolean}){
  const nav = [
    ['overview','Overview',Home,false],
    ['journey','Action plan',CalendarDays,true],
    ['payments','Payments',WalletCards,true],
    ['assistant','AI Copilot',Bot,true],
  ] as const
  return <aside className="sidebar">
    <Logo/>
    <div className="navLabel">Workspace</div>
    <nav>{nav.map(([id,label,Icon,needsJourney])=><button key={id} disabled={needsJourney&&!hasJourney} className={page===id?'navItem active':'navItem'} onClick={()=>setPage(id)}><Icon size={17}/><span>{label}</span></button>)}</nav>
    <div className="sidebarSpacer"/>
    <div className="controlNote"><ShieldCheck size={17}/><div><strong>Planner, not autopilot</strong><span>LEPA plans and explains. Rules calculate. You authorize every payment.</span></div></div>
    <div className="profile"><div className="avatar">DC</div><div><strong>Demo Customer</strong><span>Local sandbox</span></div><ChevronRight size={15}/></div>
  </aside>
}

function AiRoute({health}:{health:AiHealth|null}){
  const order = health?.providerOrder?.length ? health.providerOrder : ['openrouter','ollama','deterministic']
  return <div className="aiRoute"><span>AI route</span><strong>{order.map(x=>x==='deterministic'?'Safe parser':x==='openrouter'?'OpenRouter':'Ollama').join(' → ')}</strong></div>
}

function Header({onNew,onRefresh,onReset,loading,health}:{onNew:()=>void,onRefresh:()=>void,onReset:()=>void,loading:boolean,health:AiHealth|null}){
  return <header className="topbar">
    <div><div className="kicker">FINANCIAL LIFE-EVENT WORKSPACE</div><h1>Life Event Payment Agent</h1></div>
    <div className="topActions"><AiRoute health={health}/><button className="iconBtn" onClick={onRefresh} title="Refresh"><RefreshCw size={16} className={loading?'spin':''}/></button><button className="ghostBtn" onClick={onReset}><Trash2 size={15}/>Reset</button><button className="primaryBtn" onClick={onNew}><Plus size={16}/>Start life event</button></div>
  </header>
}

function BankSnapshot({bank,compact=false}:{bank:BankProfile,compact?:boolean}){
  return <section className={compact?'bankSnapshot compact':'bankSnapshot'}>
    <div className="bankLead">
      <div className="kicker">BANK POSITION</div>
      <span>Liquid balance</span>
      <strong>{money(bank.totalLiquidBalance)}</strong>
      {!compact&&<small>Across {bank.accounts.length} linked deposit accounts</small>}
    </div>
    <div className="accountGrid">
      {bank.accounts.map(a=><div className="accountRow" key={a.maskedNumber}><div><span>{a.name}</span><small>{a.maskedNumber}</small></div><strong>{money(a.balance)}</strong></div>)}
    </div>
    <div className="loanBlock">
      <div className="loanHead"><div><span>Active loans</span><strong>{bank.loans.length}</strong></div><Landmark size={18}/></div>
      {bank.loans.length ? bank.loans.map(l=><div className="loanRow" key={l.maskedNumber}><div><strong>{l.name}</strong><span>{l.maskedNumber} · {l.interestRate}% · {l.remainingMonths} months</span></div><div><strong>{money(l.outstanding)}</strong><span>EMI {money(l.emi)}</span></div></div>) : <div className="noLoan">No active loans</div>}
    </div>
  </section>
}

function EmptyWorkspace({onNew}:{onNew:()=>void}){
  return <div className="emptyGrid">
    <section className="emptyHero">
      <div className="redRule"/>
      <div className="emptyCopy"><div className="kicker">NO ACTIVE LIFE EVENT</div><h2>Your bank position is ready.<br/>Add context when life changes.</h2><p>Upload a financial document. LEPA will combine its explicit obligations with your balance, active loans and spending pattern, then create a dated plan for what to reserve, arrange and pay.</p><button className="primaryBtn large" onClick={onNew}><Upload size={17}/>Upload a document</button></div>
      <div className="emptySteps"><div><span>01</span><strong>Understand</strong><small>Extract verified amounts, dates and conditions from the document.</small></div><div><span>02</span><strong>Plan</strong><small>Combine the event with liquidity, income, EMIs and money activity.</small></div><div><span>03</span><strong>Act</strong><small>Show what to reserve, arrange and pay — with dates and amounts.</small></div></div>
    </section>
  </div>
}

function Metric({label,value,sub,tone}:{label:string,value:string,sub:string,tone?:'danger'|'good'|'dark'}){
  return <div className={`metric ${tone||''}`}><span>{label}</span><strong>{value}</strong><small>{sub}</small></div>
}

function PlanningContext({journey}:{journey:Journey}){
  const p=journey.plan
  return <section className="planningContext panel">
    <div className="sectionHead"><div><div className="kicker">FINANCIAL CONTEXT USED</div><h3>What LEPA protected before planning the event</h3></div><ShieldCheck size={17}/></div>
    <div className="contextGrid">
      <div><span>Protected reserve</span><strong>{money(p.protectedReserve)}</strong><small>Normal spending + existing commitments + buffer</small></div>
      <div><span>Existing monthly EMIs</span><strong>{money(p.activeMonthlyEmis)}</strong><small>Kept outside the life-event budget</small></div>
      <div><span>Observed monthly spend</span><strong>{money(p.averageMonthlySpend)}</strong><small>Used for forward cash-flow estimates</small></div>
      <div><span>Next expected income</span><strong>{money(p.expectedMonthlyIncome)}</strong><small>{date(p.nextIncomeDate)}</small></div>
    </div>
  </section>
}

function ActionPlan({journey,onArrange}:{journey:Journey,onArrange:(amount:number)=>Promise<void>}){
  const [busy,setBusy]=useState(false)
  const active=journey.plan.actions.filter(a=>a.type!=='COMPLETED').slice(0,8)
  const arrange=async()=>{if(journey.plan.totalToArrange<=0)return;setBusy(true);try{await onArrange(journey.plan.totalToArrange)}finally{setBusy(false)}}
  return <section className="panel actionPlanPanel">
    <div className="sectionHead actionHead">
      <div><div className="kicker">YOUR ACTION PLAN</div><h3>{journey.plan.summary}</h3></div>
      {journey.plan.totalToArrange>0&&<button className="blackBtn" onClick={arrange} disabled={busy}>{busy?<Loader2 size={15} className="spin"/>:<Banknote size={15}/>}Simulate arranged funds</button>}
    </div>
    <div className="actionList">
      {active.map((a,i)=><ActionRow action={a} key={`${a.sequence}-${i}`}/>) }
      {!active.length&&<div className="emptyAction"><CheckCircle2 size={18}/><span>No pending actions.</span></div>}
    </div>
  </section>
}

function ActionRow({action}:{action:ActionStep}){
  const danger=action.status==='ACTION_REQUIRED'||action.priority==='CRITICAL'
  const icon=action.type==='ARRANGE_FUNDS'?<CircleDollarSign size={16}/>:action.type==='PAY'?<ArrowRight size={16}/>:action.type==='REVIEW_DATE'?<AlertTriangle size={16}/>:<ShieldCheck size={16}/>
  return <div className={`actionRow ${danger?'urgent':''}`}>
    <div className="actionSeq">{String(action.sequence).padStart(2,'0')}</div>
    <div className="actionIcon">{icon}</div>
    <div className="actionMain"><div className="actionMeta"><span>{action.type.replaceAll('_',' ')}</span><span>{action.priority}</span></div><strong>{action.title}</strong><p>{action.rationale}</p></div>
    <div className="actionDate"><span>{action.actionDate?'By':'Needs'}</span><strong>{action.actionDate?date(action.actionDate):'date'}</strong>{action.paymentDueDate&&action.actionDate!==action.paymentDueDate&&<small>Payment due {date(action.paymentDueDate)}</small>}</div>
    <div className="actionAmount"><span>{action.amountToArrange&&action.amountToArrange>0?'Arrange':'Amount'}</span><strong>{money(action.amountToArrange&&action.amountToArrange>0?action.amountToArrange:action.amount)}</strong>{action.projectedBalanceAfter!=null&&<small>Plan balance {money(action.projectedBalanceAfter)}</small>}</div>
  </div>
}

function NextTimeline({journey}:{journey:Journey}){
  const steps=journey.plan.actions.filter(a=>a.type!=='COMPLETED').slice(0,5)
  return <section className="panel nextPanel">
    <div className="sectionHead"><div><div className="kicker">WHAT HAPPENS NEXT</div><h3>Dated financial timeline</h3></div><CalendarClock size={17}/></div>
    <div className="nextList">{steps.map((a,i)=><div className="nextItem" key={`${a.sequence}-${i}`}><div className={`nextMarker ${a.status==='ACTION_REQUIRED'?'danger':''}`}>{i+1}</div><div><span>{a.actionDate?date(a.actionDate):'Date to confirm'}</span><strong>{a.title}</strong><small>{a.amount?money(a.amount):a.rationale}</small></div></div>)}</div>
    <div className="nextFooter"><span>Next critical date</span><strong>{date(journey.plan.nextCriticalDate)}</strong></div>
  </section>
}

function CashFlowPanel({journey}:{journey:Journey}){
  const data=journey.plan.cashFlow.map(x=>({date:new Intl.DateTimeFormat('en-IN',{day:'2-digit',month:'short'}).format(new Date(x.date)),balance:x.projectedBalance,label:x.label}))
  return <section className="panel cashPanel">
    <div className="sectionHead"><div><div className="kicker">PROJECTED CASH FLOW</div><h3>How the plan changes liquidity over time</h3></div><TrendingDown size={17}/></div>
    <div className="cashChart"><ResponsiveContainer width="100%" height="100%"><AreaChart data={data} margin={{top:12,right:12,left:0,bottom:0}}><defs><linearGradient id="cashFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#101114" stopOpacity={0.16}/><stop offset="100%" stopColor="#101114" stopOpacity={0.01}/></linearGradient></defs><XAxis dataKey="date" tickLine={false} axisLine={false} tick={{fontSize:9,fill:'#777'}}/><YAxis hide/><Tooltip formatter={(v:any)=>money(Number(v))} labelStyle={{fontSize:10}} contentStyle={{border:'1px solid #ddd',borderRadius:2,fontSize:10}}/><ReferenceLine y={journey.plan.protectedReserve} stroke="#c8102e" strokeDasharray="4 4"/><Area type="monotone" dataKey="balance" stroke="#101114" strokeWidth={2} fill="url(#cashFill)"/></AreaChart></ResponsiveContainer></div>
    <div className="cashLegend"><span><i className="legendLine black"/>Projected balance</span><span><i className="legendLine red"/>Protected reserve {money(journey.plan.protectedReserve)}</span></div>
  </section>
}

function MoneyActivity({bank}:{bank:BankProfile}){
  return <section className="panel activityMoneyPanel">
    <div className="sectionHead"><div><div className="kicker">MONEY ACTIVITY CONSIDERED</div><h3>Recent signals behind the plan</h3></div><Activity size={17}/></div>
    <div className="moneyRows">{bank.recentTransactions.slice(0,6).map((t,i)=><div className="moneyRow" key={`${t.date}-${i}`}><div className={`moneyDir ${t.direction.toLowerCase()}`}>{t.direction==='CREDIT'?<ArrowDownRight size={14}/>:<ArrowUpRight size={14}/>}</div><div><strong>{t.description}</strong><span>{date(t.date)} · {t.category.replaceAll('_',' ')}</span></div><strong className={t.direction==='CREDIT'?'creditAmt':''}>{t.direction==='CREDIT'?'+':'-'}{money(t.amount)}</strong></div>)}</div>
  </section>
}

function MilestoneTable({journey,onExecute}:{journey:Journey,onExecute:(id:number)=>Promise<void>}){
  const [busy,setBusy]=useState<number|null>(null)
  const pay=async(id:number)=>{setBusy(id);try{await onExecute(id)}finally{setBusy(null)}}
  return <section className="panel">
    <div className="sectionHead"><div><div className="kicker">SOURCE OBLIGATIONS</div><h3>What the document actually says</h3></div><span className="quiet">Recommendations stay separate and never become obligations automatically.</span></div>
    <div className="milestoneHeader"><span>Item</span><span>Due</span><span>Amount</span><span>Rail</span><span>Status</span><span></span></div>
    {journey.milestones.map(m=><div className="milestoneRow" key={m.id}>
      <div className="milestoneName"><div className={m.category==='CONFIRMED'?'factDot':'recDot'}></div><div><strong>{m.name}</strong><small>{m.category==='CONFIRMED'?'Document fact':'AI recommendation'} · {m.source}</small></div></div>
      <span>{date(m.dueDate)}</span><strong>{money(m.amount)}</strong><span>{m.paymentRail?.replaceAll('_',' ')||'Planning only'}</span>
      <span className={`status ${m.status.toLowerCase()}`}>{m.status}</span>
      <div className="rowAction">{m.category==='CONFIRMED'&&m.status!=='COMPLETED'&&m.amount?<button className="textBtn" onClick={()=>pay(m.id)} disabled={busy===m.id}>{busy===m.id?<Loader2 size={14} className="spin"/>:<ArrowRight size={14}/>}Mock pay</button>:m.status==='COMPLETED'?<BadgeCheck size={17} className="okIcon"/>:null}</div>
    </div>)}
  </section>
}

function ActiveOverview({journey,bank,events,onExecute,onArrange}:{journey:Journey,bank:BankProfile,events:JourneyEvent[],onExecute:(id:number)=>Promise<void>,onArrange:(amount:number)=>Promise<void>}){
  const confirmed=journey.milestones.filter(m=>m.category==='CONFIRMED')
  const nextPay=journey.plan.actions.find(a=>a.type==='PAY'&&a.status!=='COMPLETED')
  return <div className="pageStack">
    <BankSnapshot bank={bank} compact/>
    <section className="journeyBanner"><div><div className="kicker">ACTIVE LIFE EVENT</div><h2>{journey.title}</h2><p>{journey.eventType.replaceAll('_',' ')} · {confirmed.length} confirmed obligations · planner recalculated from current financial context</p></div><div className={`riskFlag ${journey.funding.riskLevel.toLowerCase()}`}>{journey.funding.riskLevel} RISK</div></section>
    <div className="metricsGrid"><Metric label="Next payment" value={money(nextPay?.amount)} sub={nextPay?.paymentDueDate?`Due ${date(nextPay.paymentDueDate)}`:'No dated payment'}/><Metric label="Money to arrange" value={money(journey.plan.totalToArrange)} sub="To preserve normal commitments" tone={journey.plan.totalToArrange>0?'danger':'good'}/><Metric label="Protected reserve" value={money(journey.plan.protectedReserve)} sub="Not treated as event spending"/><Metric label="Projected end balance" value={money(journey.plan.projectedEndBalance)} sub="After following the action plan" tone={journey.plan.projectedEndBalance>=journey.plan.protectedReserve?'good':'danger'}/></div>
    <div className="plannerGrid"><ActionPlan journey={journey} onArrange={onArrange}/><NextTimeline journey={journey}/></div>
    <PlanningContext journey={journey}/>
    <CashFlowPanel journey={journey}/>
    <div className="activeGrid"><MoneyActivity bank={bank}/><section className="panel sidePanel"><div className="sectionHead"><div><div className="kicker">LIVE ACTIVITY</div><h3>What LEPA has done</h3></div><Activity size={17}/></div><div className="activityList">{events.length?events.slice(0,8).map(e=><div className="activityRow" key={e.id}><div className="eventMark"/><div><strong>{e.type.replaceAll('_',' ')}</strong><span>{e.message}</span><small>{when(e.occurredAt)}</small></div></div>):<p className="quiet pad">No events yet.</p>}</div></section></div>
  </div>
}

function JourneyPage({journey,onExecute,onArrange}:{journey:Journey,onExecute:(id:number)=>Promise<void>,onArrange:(amount:number)=>Promise<void>}){
  return <div className="pageStack"><section className="pageIntro"><div className="kicker">PERSONAL FINANCIAL PLAN</div><h2>{journey.title}</h2><p>LEPA combines document obligations with current liquidity, income timing, active loan commitments and observed spending before proposing what to do next.</p></section><ActionPlan journey={journey} onArrange={onArrange}/><NextTimeline journey={journey}/><MilestoneTable journey={journey} onExecute={onExecute}/></div>
}

function PaymentsPage({journey,onExecute}:{journey:Journey,onExecute:(id:number)=>Promise<void>}){
  const items=journey.milestones.filter(m=>m.category==='CONFIRMED')
  return <div className="pageStack"><section className="pageIntro"><div className="kicker">CONTROLLED EXECUTION</div><h2>Payments</h2><p>LEPA plans the sequence; you remain in control of execution. The LLM never moves money.</p></section><section className="panel"><div className="paymentRows">{items.map(m=><PaymentRow m={m} key={m.id} onExecute={onExecute}/>)}</div></section></div>
}

function PaymentRow({m,onExecute}:{m:Milestone,onExecute:(id:number)=>Promise<void>}){
  const [busy,setBusy]=useState(false)
  const go=async()=>{setBusy(true);try{await onExecute(m.id)}finally{setBusy(false)}}
  return <div className="paymentRow"><div><span className="payIcon"><ReceiptText size={17}/></span><div><strong>{m.name}</strong><small>{m.source} · due {date(m.dueDate)}</small></div></div><strong>{money(m.amount)}</strong><span>{m.paymentRail?.replaceAll('_',' ')}</span><span className={`status ${m.status.toLowerCase()}`}>{m.status}</span><div>{m.status!=='COMPLETED'&&m.amount?<button className="blackBtn" onClick={go} disabled={busy}>{busy?<Loader2 className="spin" size={15}/>:<ArrowRight size={15}/>}Authorize mock payment</button>:<span className="paidRef"><CheckCircle2 size={15}/> {m.paymentId||'Completed'}</span>}</div></div>
}

function AssistantPage({journey,health}:{journey:Journey,health:AiHealth|null}){
  const [message,setMessage]=useState(''); const [busy,setBusy]=useState(false); const [messages,setMessages]=useState<Array<{role:'user'|'assistant';text:string;provider?:string}>>([{role:'assistant',text:'I have the document obligations, your current bank position and LEPA’s deterministic action plan. Ask me what to do next, why funds need to be arranged, or which payment should be prioritized.'}])
  const send=async()=>{if(!message.trim()||busy)return;const q=message.trim();setMessage('');setMessages(x=>[...x,{role:'user',text:q}]);setBusy(true);try{const r=await api.chat(journey.id,q);setMessages(x=>[...x,{role:'assistant',text:r.answer,provider:r.provider}])}catch(e:any){setMessages(x=>[...x,{role:'assistant',text:e.message}])}finally{setBusy(false)}}
  return <div className="assistantGrid"><section className="assistantContext"><div><div className="kicker light">AI PLANNER CONTEXT</div><h2>{journey.title}</h2><p>The assistant sees the verified obligations and LEPA plan. It can reason about sequencing and explain trade-offs, while monetary calculations and payment authorization remain deterministic.</p></div><div className="routeBox"><span>Configured provider order</span><strong>{health?.providerOrder?.join(' → ')||'OpenRouter → Ollama → deterministic'}</strong></div><div className="guardrails"><div><Target size={16}/><span>Action plan is grounded in bank context</span></div><div><FileText size={16}/><span>Document facts remain separate from AI suggestions</span></div><div><Landmark size={16}/><span>Balances, shortfalls and reserves are rule-calculated</span></div></div></section><section className="chatPanel"><div className="chatHead"><div><MessageSquareText size={17}/><strong>LEPA Copilot</strong></div><span>Planner-aware</span></div><div className="messages">{messages.map((m,i)=><div key={i} className={`message ${m.role}`}><span>{m.text}</span>{m.provider&&<small>{m.provider}</small>}</div>)}{busy&&<div className="message assistant"><Loader2 className="spin" size={16}/></div>}</div><div className="suggestedQuestions"><button onClick={()=>setMessage('What should I do next and why?')}>What should I do next?</button><button onClick={()=>setMessage('How much money must I arrange and by when?')}>How much must I arrange?</button><button onClick={()=>setMessage('Which payment should I prioritize first?')}>What should I prioritize?</button></div><div className="chatInput"><input value={message} onChange={(e:ChangeEvent<HTMLInputElement>)=>setMessage(e.target.value)} onKeyDown={(e:KeyboardEvent<HTMLInputElement>)=>e.key==='Enter'&&send()} placeholder="Ask about the plan, deadlines or affordability"/><button onClick={send}><ArrowRight size={17}/></button></div></section></div>
}

function NewJourneyModal({bank,onClose,onCreated}:{bank:BankProfile,onClose:()=>void,onCreated:(j:Journey)=>void}){
  const [file,setFile]=useState<File|null>(null); const [analysis,setAnalysis]=useState<Analysis|null>(null); const [busy,setBusy]=useState(false); const [error,setError]=useState('')
  const analyze=async()=>{if(!file)return;setBusy(true);setError('');try{setAnalysis(await api.analyze(file,'AUTO'))}catch(e:any){setError(e.message)}finally{setBusy(false)}}
  const create=async()=>{if(!analysis)return;setBusy(true);setError('');try{const j=await api.createJourney({eventType:analysis.eventType,title:analysis.title,customerName:bank.customerName,availableFunds:bank.totalLiquidBalance,expectedIncome:bank.expectedMonthlyIncome,obligations:analysis.obligations,recommendations:analysis.recommendations});onCreated(j)}catch(e:any){setError(e.message)}finally{setBusy(false)}}
  return <div className="modalBackdrop"><div className="modal"><div className="modalTop"><div><div className="kicker">NEW LIFE EVENT</div><h2>{analysis?'Review what LEPA found':'Upload source document'}</h2></div><button className="iconBtn" onClick={onClose}><X size={17}/></button></div>
    {!analysis?<><label className="dropzone"><Upload size={24}/><strong>{file?file.name:'Choose a PDF, image or text file'}</strong><span>LEPA extracts explicit financial facts first. The action plan is created only after those facts are combined with the customer’s bank position.</span><input type="file" accept=".pdf,.png,.jpg,.jpeg,.webp,.txt" onChange={(e:ChangeEvent<HTMLInputElement>)=>setFile(e.target.files?.[0]||null)}/></label><button className="primaryBtn full" disabled={!file||busy} onClick={analyze}>{busy?<Loader2 className="spin" size={16}/>:<ArrowRight size={16}/>}Analyze document</button></>:<><div className="analysisSummary"><div><span>Detected event</span><strong>{analysis.eventType.replaceAll('_',' ')}</strong></div><div><span>AI provider</span><strong>{analysis.provider}</strong></div><div><span>Confidence</span><strong>{Math.round((analysis.confidence||0)*100)}%</strong></div></div><p className="analysisText">{analysis.summary}</p><div className="analysisGrid"><div><div className="analysisTitle"><span>Document facts</span><strong>{analysis.obligations.length}</strong></div>{analysis.obligations.map((o,i)=><div className="analysisLine" key={i}><div><strong>{o.name}</strong><span>{o.alreadyPaid?'Already paid · ':''}{date(o.dueDate)}</span></div><strong>{money(o.amount)}</strong></div>)}</div><div><div className="analysisTitle"><span>AI considerations</span><strong>{analysis.recommendations.length}</strong></div>{analysis.recommendations.map((o,i)=><div className="analysisLine rec" key={i}><div><strong>{o.name}</strong><span>Planning suggestion only</span></div></div>)}</div></div><div className="plannerPreview"><div><Banknote size={17}/><span>Current liquidity</span><strong>{money(bank.totalLiquidBalance)}</strong></div><div><Landmark size={17}/><span>Active monthly EMIs</span><strong>{money(bank.loans.reduce((s,l)=>s+l.emi,0))}</strong></div><div><Activity size={17}/><span>Observed monthly spend</span><strong>{money(bank.averageMonthlySpend)}</strong></div><div><ShieldCheck size={17}/><span>Protected reserve</span><strong>{money(bank.planningReserve)}</strong></div></div><div className="modalPlannerNote"><Sparkles size={16}/><span>After you create the journey, LEPA will calculate what to reserve, what to arrange, what to pay, and by when.</span></div><div className="modalActions"><button className="ghostBtn" onClick={()=>setAnalysis(null)}>Back</button><button className="primaryBtn" disabled={busy} onClick={create}>{busy?<Loader2 className="spin" size={16}/>:<ArrowRight size={16}/>}Build action plan</button></div></>}
    {error&&<div className="errorBox"><AlertTriangle size={15}/>{error}</div>}
  </div></div>
}

export default function App(){
  const [page,setPage]=useState('overview'); const [journeys,setJourneys]=useState<Journey[]>([]); const [events,setEvents]=useState<JourneyEvent[]>([]); const [bank,setBank]=useState<BankProfile|null>(null); const [health,setHealth]=useState<AiHealth|null>(null); const [loading,setLoading]=useState(true); const [modal,setModal]=useState(false); const [error,setError]=useState('')
  const current=journeys[0]
  const activeBank=useMemo(()=>bank&&current?syncBankBalance(bank,current.funding.availableFunds):bank,[bank,current])
  const load=async()=>{setLoading(true);setError('');try{const [js,ev,b,h]=await Promise.all([api.journeys(),api.events(),api.bankProfile(),api.aiHealth()]);setJourneys(js);setEvents(ev);setBank(b);setHealth(h)}catch(e:any){setError(e.message)}finally{setLoading(false)}}
  useEffect(()=>{load()},[])
  useEffect(()=>{if(!current&&page!=='overview')setPage('overview')},[current,page])
  const execute=async(id:number)=>{try{await api.execute(id);await load()}catch(e:any){setError(e.message)}}
  const arrange=async(amount:number)=>{if(!current||amount<=0)return;try{await api.credit(current.id,amount);await load()}catch(e:any){setError(e.message)}}
  const reset=async()=>{if(!confirm('Clear all life-event journeys and return to the clean banking dashboard?'))return;try{await api.reset();setPage('overview');await load()}catch(e:any){setError(e.message)}}
  const created=(j:Journey)=>{setModal(false);setJourneys(x=>[j,...x]);setPage('overview');load()}
  return <div className="appShell"><Sidebar page={page} setPage={setPage} hasJourney={!!current}/><main className="main"><Header onNew={()=>setModal(true)} onRefresh={load} onReset={reset} loading={loading} health={health}/>{error&&<div className="globalError"><AlertTriangle size={15}/>{error}<button onClick={()=>setError('')}><X size={14}/></button></div>}{loading&&!bank?<div className="loading"><Loader2 className="spin"/><span>Loading LEPA workspace</span></div>:bank?<> {!current&&page==='overview'&&<><BankSnapshot bank={bank}/><EmptyWorkspace onNew={()=>setModal(true)}/></>} {current&&page==='overview'&&activeBank&&<ActiveOverview journey={current} bank={activeBank} events={events} onExecute={execute} onArrange={arrange}/>} {current&&page==='journey'&&<JourneyPage journey={current} onExecute={execute} onArrange={arrange}/>} {current&&page==='payments'&&<PaymentsPage journey={current} onExecute={execute}/>} {current&&page==='assistant'&&<AssistantPage journey={current} health={health}/>} </>:null}</main>{modal&&bank&&<NewJourneyModal bank={bank} onClose={()=>setModal(false)} onCreated={created}/>}</div>
}
