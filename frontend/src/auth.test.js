import { test } from 'node:test';
import assert from 'node:assert/strict';
import { api, login } from './api.js';
import { rememberStaffPage, restoredStaffPage, forgetStaffPage } from './auth-navigation.js';

test('login sends JSON once with CSRF and subsequent refresh uses session cookies', async t => {
  const calls=[];
  t.mock.method(globalThis,'fetch',async (url,options)=>{
    calls.push({url,options});
    return {ok:true,json:async()=>url==='/api/csrf'?{headerName:'X-XSRF-TOKEN',token:'csrf'}:{username:'admin',roles:['ROLE_ADMIN']}};
  });
  await login('admin','test-password');
  await api('/me',{silentUnauthorized:true});
  assert.equal(calls[1].url,'/api/auth/login');
  assert.deepEqual(JSON.parse(calls[1].options.body),{username:'admin',password:'test-password'});
  assert.equal(calls[1].options.headers['X-XSRF-TOKEN'],'csrf');
  assert.equal(calls[2].options.credentials,'same-origin');
  assert.equal(calls[2].options.headers.Authorization,undefined);
  assert.equal(calls[2].options.body,undefined);
  assert.equal(calls[2].options.silentUnauthorized,undefined);
});
test('expired protected session signals logout; anonymous bootstrap and failed login do not', async t=>{
  let expired=0;
  t.mock.method(globalThis,'fetch',async url=>url==='/api/csrf'?{ok:true,json:async()=>({headerName:'X-CSRF',token:'test'})}:{ok:false,status:401,json:async()=>({})});
  const previous=globalThis.dispatchEvent;
  globalThis.dispatchEvent=event=>{if(event.type==='vticket-session-expired')expired++;};
  t.after(()=>{if(previous)globalThis.dispatchEvent=previous;else delete globalThis.dispatchEvent;});
  await assert.rejects(api('/me',{silentUnauthorized:true}),{status:401});
  await assert.rejects(login('admin','bad'),{status:401});
  assert.equal(expired,0);
  await assert.rejects(api('/staff/orders'),{status:401});
  assert.equal(expired,1);
});
test('logout sends CSRF and accepts an empty 204 response',async t=>{
  const calls=[];
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    calls.push({url,options});
    return url==='/api/csrf'?{ok:true,json:async()=>({headerName:'X-CSRF',token:'new-token'})}:{ok:true,status:204,json:async()=>{throw new SyntaxError();}};
  });
  await api('/auth/logout',{method:'POST'});
  assert.equal(calls[1].options.headers['X-CSRF'],'new-token');
});
test('refresh remembers staff screen and applies current role; logout clears it',()=>{
  const values=new Map();const storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  const admin={roles:['ROLE_ADMIN']},staff={roles:['ROLE_STAFF']};
  rememberStaffPage('counter',storage);assert.equal(restoredStaffPage(staff,storage),'counter');
  rememberStaffPage('shop',storage);assert.equal(restoredStaffPage(staff,storage),'counter');
  rememberStaffPage('manage',storage);assert.equal(restoredStaffPage(admin,storage),'manage');
  assert.equal(restoredStaffPage(staff,storage),'orders');
  forgetStaffPage(storage);assert.equal(values.size,0);
  assert.equal(restoredStaffPage(admin,storage),'orders');
  const blocked={getItem(){throw Error();},setItem(){throw Error();},removeItem(){throw Error();}};
  rememberStaffPage('counter',blocked);forgetStaffPage(blocked);assert.equal(restoredStaffPage(staff,blocked),'orders');
});
