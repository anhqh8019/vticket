import test from "node:test";
import assert from "node:assert/strict";
import {createQrAutoSubmit,isCompleteTicketQr} from "./qr-auto-submit.js";
const qr="c1ba0f07-efb8-4ea1-87af-06dc4352c22a";
function setup(){let pending=null;const sent=[];return {sent,controller:createQrAutoSubmit(value=>sent.push(value),{setTimer:callback=>{pending=callback;return 1;},clearTimer:()=>{pending=null;}}),flush:()=>{const callback=pending;pending=null;callback?.();}};}
test("only a complete QR triggers automatic submission, not partial typing",()=>{
 const s=setup();for(let i=1;i<qr.length;i++){s.controller.update(qr.slice(0,i));s.flush();}assert.deepEqual(s.sent,[]);
 s.controller.update(qr);s.flush();assert.deepEqual(s.sent,[qr]);
 assert.equal(isCompleteTicketQr(qr.toUpperCase()),true);assert.equal(isCompleteTicketQr("1131000001"),false);
});
test("scanner suffixes reset debounce and send only the final complete value once",()=>{
 const s=setup();s.controller.update(qr);s.controller.update(qr+"\r\n");s.flush();s.flush();assert.deepEqual(s.sent,[qr]);
});
test("Enter/manual submit or unmount cancels the pending automatic request",()=>{
 const s=setup();s.controller.update(qr);s.controller.cancel();s.flush();assert.deepEqual(s.sent,[]);
 s.controller.update(qr);s.controller.update("");s.flush();assert.deepEqual(s.sent,[]);
});
