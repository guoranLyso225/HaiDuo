export const userTimezone=Intl.DateTimeFormat().resolvedOptions().timeZone;
export function formatTime(value:unknown){if(!value)return '—';const date=new Date(typeof value==='number'?value:String(value));return Number.isNaN(date.getTime())?String(value):new Intl.DateTimeFormat('zh-CN',{timeZone:userTimezone,dateStyle:'medium',timeStyle:'medium'}).format(date);}
