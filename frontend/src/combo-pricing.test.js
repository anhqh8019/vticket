import {test} from 'node:test';
import assert from 'node:assert/strict';
import {dayType,priceForDate} from './combo-pricing.js';
const combo={kind:'COMBO',price:100,weekendPrice:150,holidayPrice:200};
test('weekday, Saturday and Sunday prices follow visit date',()=>{
 assert.equal(priceForDate(combo,'2027-01-04'),100);
 assert.equal(priceForDate(combo,'2027-01-09'),150);
 assert.equal(priceForDate(combo,'2027-01-10'),150);
});
test('holiday takes precedence over weekend and is year-specific',()=>{
 const days=[{date:'2027-01-02'}];
 assert.equal(dayType('2027-01-02',days),'HOLIDAY');assert.equal(priceForDate(combo,'2027-01-02',days),200);
 assert.equal(dayType('2028-01-02',days),'WEEKEND');
});
test('unset special price falls back to base, zero is preserved, services unaffected',()=>{
 assert.equal(priceForDate({...combo,holidayPrice:null},'2027-01-02',[{date:'2027-01-02'}]),100);
 assert.equal(priceForDate({...combo,weekendPrice:null},'2027-01-09'),100);
 assert.equal(priceForDate({...combo,holidayPrice:0},'2027-01-02',[{date:'2027-01-02'}]),0);
 assert.equal(priceForDate({...combo,kind:'TICKET'},'2027-01-09'),150);
 assert.equal(priceForDate({...combo,kind:'SERVICE'},'2027-01-09'),100);
});

test('ticket prices follow calendar and remain independent from combo prices',()=>{
 const ticket={kind:'TICKET',price:250,weekendPrice:300,holidayPrice:400};
 assert.equal(priceForDate(ticket,'2027-01-04'),250);
 assert.equal(priceForDate(ticket,'2027-01-09'),300);
 assert.equal(priceForDate(ticket,'2027-01-10'),300);
 assert.equal(priceForDate(ticket,'2027-01-02',[{date:'2027-01-02'}]),400);
 assert.equal(priceForDate({...ticket,weekendPrice:null},'2027-01-09'),250);
 assert.equal(priceForDate({...ticket,holidayPrice:null},'2027-01-02',[{date:'2027-01-02'}]),250);
 assert.equal(priceForDate({...ticket,holidayPrice:0},'2027-01-02',[{date:'2027-01-02'}]),0);
});
test('child price uses separate base/weekend/holiday and never falls back to adult price',()=>{
 const ticket={kind:'TICKET',price:250,weekendPrice:300,holidayPrice:400,child:true,childPrice:100,childWeekendPrice:120,childHolidayPrice:150};
 for(const kind of ['TICKET','COMBO']){
  const p={...ticket,kind};assert.equal(priceForDate(p,'2027-01-04'),100);assert.equal(priceForDate(p,'2027-01-09'),120);assert.equal(priceForDate(p,'2027-01-02',[{date:'2027-01-02'}]),150);
  assert.equal(priceForDate({...p,childWeekendPrice:null},'2027-01-09'),100);
  assert.equal(priceForDate({...p,childHolidayPrice:null},'2027-01-02',[{date:'2027-01-02'}]),100);
  assert.equal(priceForDate({...p,childHolidayPrice:0},'2027-01-02',[{date:'2027-01-02'}]),0);
  assert.equal(priceForDate({...p,childPrice:null},'2027-01-04'),null);
 }
});
