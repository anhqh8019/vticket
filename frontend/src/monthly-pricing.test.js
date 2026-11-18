import test from "node:test";
import assert from "node:assert/strict";
import {monthDays,allowedDate,monthlyDayPrice} from "./monthly-pricing.js";
test("monthly calendar handles leap years and month boundaries",()=>{
 assert.equal(monthDays("2028-02").length,29);assert.equal(monthDays("2027-02").length,28);
 assert.equal(monthDays("2027-01").at(-1),"2027-01-31");assert.deepEqual(monthDays("2027-13"),[]);
});
test("Silver excludes weekends and weekday holidays; Gold and Platinum permit them",()=>{
 const holidays=[{date:"2027-11-24"}];
 assert.equal(allowedDate("SILVER","2027-11-24",holidays,"2027-11-01"),false);
 assert.equal(allowedDate("SILVER","2027-11-27",holidays,"2027-11-01"),false);
 assert.equal(allowedDate("SILVER","2027-11-25",holidays,"2027-11-01"),true);
 for(const tier of ["GOLD","PLATINUM"]){assert.equal(allowedDate(tier,"2027-11-24",holidays,"2027-11-01"),true);assert.equal(allowedDate(tier,"2027-11-01",holidays,"2027-11-02"),false);}
});
test("monthly total uses weekday and shared weekend/holiday rates including zero",()=>{
 const p={weekdayPrice:100,weekendPrice:150},holidays=[{date:"2027-01-01"}];
 const total=["2027-01-01","2027-01-04","2027-01-09"].reduce((sum,date)=>sum+monthlyDayPrice(p,date,holidays),0);
 assert.equal(total,400);assert.equal(monthlyDayPrice({...p,weekendPrice:0},"2027-01-09",holidays),0);
});
