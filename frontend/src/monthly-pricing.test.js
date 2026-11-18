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

test("selections accumulate across months and years without duplicates",async()=>{
 const {addMonthSelection,selectionError}=await import("./monthly-pricing.js");
 const selected=addMonthSelection(["2026-12-31"],["2027-01-01","2026-12-31"]);
 assert.deepEqual(selected,["2026-12-31","2027-01-01"]);
 assert.match(selectionError("SILVER",Array(11).fill("2027-01-04")),/12/);
 assert.equal(selectionError("SILVER",Array(12).fill("2027-01-04")),"");
 for(const tier of ["GOLD","PLATINUM"]){assert.match(selectionError(tier,["2027-11-24"]),/thứ Bảy/);assert.equal(selectionError(tier,["2027-01-09"]),"");}
});

test("one pass allows at most 30 dates and adding a month preserves earlier choices",async()=>{
 const {addMonthSelection,selectionError}=await import("./monthly-pricing.js");
 const january=monthDays("2027-01");
 const chosen=addMonthSelection(["2027-02-01"],january);
 assert.equal(chosen.length,30);assert.ok(chosen.includes("2027-02-01"));
 assert.match(selectionError("GOLD",january),/30/);
 assert.equal(selectionError("GOLD",january.slice(0,30)),"");
});

test("every selected Monday-Sunday week needs a weekend, including cross-month and cross-year weeks",async()=>{
 const {selectionError,missingWeekendWeeks}=await import("./monthly-pricing.js");
 for(const tier of ["GOLD","PLATINUM"]){
  assert.match(selectionError(tier,["2027-01-04","2027-01-09","2027-01-11"]),/11\/01\/2027/);
  assert.equal(selectionError(tier,["2027-01-04","2027-01-09","2027-01-11","2027-01-17"]),"");
  assert.match(selectionError(tier,["2027-01-30","2027-02-01"]),/01\/02\/2027/);
  assert.equal(selectionError(tier,["2027-12-27","2028-01-01"]),"");
  assert.match(selectionError(tier,["2027-12-27","2028-01-01","2028-01-03"]),/03\/01\/2028/);
  assert.equal(selectionError(tier,["2027-01-09","2027-01-24"]),"");
 }
 assert.deepEqual(missingWeekendWeeks(["2027-01-10","2027-01-11"]),["2027-01-11"]);
});
