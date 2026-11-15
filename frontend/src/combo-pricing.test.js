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
test('unset special price falls back to base, zero is preserved, tickets/services unaffected',()=>{
 assert.equal(priceForDate({...combo,holidayPrice:null},'2027-01-02',[{date:'2027-01-02'}]),100);
 assert.equal(priceForDate({...combo,weekendPrice:null},'2027-01-09'),100);
 assert.equal(priceForDate({...combo,holidayPrice:0},'2027-01-02',[{date:'2027-01-02'}]),0);
 assert.equal(priceForDate({...combo,kind:'TICKET'},'2027-01-09'),100);
 assert.equal(priceForDate({...combo,kind:'SERVICE'},'2027-01-09'),100);
});
