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

export function selectionError(tier,dates){
 if(!dates.length)return "Vui lòng chọn ngày sử dụng.";
 if(dates.length>366)return "Tối đa 366 ngày trong một thẻ.";
 if(tier==="SILVER"&&dates.length<12)return `Silver cần tối thiểu 12 ngày thường (đã chọn ${dates.length}/12 ngày).`;
 if(tier!=="SILVER"&&!dates.some(date=>[0,6].includes(new Date(`${date}T00:00:00Z`).getUTCDay())))return "Gold và Platinum cần ít nhất 1 ngày thứ Bảy hoặc Chủ nhật.";
 return "";
}
export function addMonthSelection(selected,dates){return [...new Set([...selected,...dates])].sort();}
