import { dayType } from "./combo-pricing.js";
export const tiers = { SILVER: "Bạc · Silver", GOLD: "Vàng · Gold", PLATINUM: "Bạch kim · Platinum" };
export function monthDays(month) {
 if (!/^\d{4}-\d{2}$/.test(month)) return [];
 const [year,m]=month.split("-").map(Number);
 if(m<1||m>12)return [];
 return Array.from({length:new Date(Date.UTC(year,m,0)).getUTCDate()},(_,i)=>`${month}-${String(i+1).padStart(2,"0")}`);
}
export function allowedDate(tier,date,holidays,today){return date>=today&&(tier!=="SILVER"||dayType(date,holidays)==="WEEKDAY");}
export function monthlyDayPrice(plan,date,holidays){return Number(dayType(date,holidays)==="WEEKDAY"?plan.weekdayPrice:plan.weekendPrice);}

export function missingWeekendWeeks(dates){
 const weeks=new Set(),covered=new Set();
 for(const date of dates){
  const d=new Date(`${date}T00:00:00Z`),day=d.getUTCDay();
  d.setUTCDate(d.getUTCDate()-((day+6)%7));const monday=d.toISOString().slice(0,10);
  weeks.add(monday);if(day===0||day===6)covered.add(monday);
 }
 return [...weeks].filter(week=>!covered.has(week)).sort();
}
export function withinTwoMonths(selected,date){
 const months=[...selected,...(date?[date]:[])].map(d=>Number(d.slice(0,4))*12+Number(d.slice(5,7)));
 return !months.length||Math.max(...months)-Math.min(...months)<=1;
}
export function selectionError(tier,dates){
 if(!dates.length)return "Vui lòng chọn ngày sử dụng.";
 if(dates.length>30)return "Tối đa 30 ngày trong một thẻ.";
 if(!withinTwoMonths(dates))return "Chỉ chọn ngày trong tối đa 2 tháng liền kề.";
 if(dates.length<12)return `${tiers[tier]} cần tối thiểu 12 ngày (đã chọn ${dates.length}/12 ngày).`;
 if(tier!=="SILVER"){
  const missing=missingWeekendWeeks(dates);
  if(missing.length)return `Gold và Platinum cần ít nhất 1 ngày thứ Bảy hoặc Chủ nhật trong mỗi tuần đăng ký (thứ Hai–Chủ nhật). Tuần thiếu bắt đầu: ${missing.map(d=>d.split("-").reverse().join("/")).join(", ")}.`;
 }
 return "";
}
export function addMonthSelection(selected,dates){const result=[...selected];for(const date of dates)if(result.length<30&&!result.includes(date)&&withinTwoMonths(result,date))result.push(date);return result.sort();}
