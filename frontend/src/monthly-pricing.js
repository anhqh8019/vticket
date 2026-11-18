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
