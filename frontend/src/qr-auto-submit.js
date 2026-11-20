// QR tickets and monthly cards contain a UUID. Wait briefly for scanner suffixes
// so incomplete keyboard input is never sent as a ticket scan.
export function isCompleteTicketQr(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.trim());
}
export function createQrAutoSubmit(submit, {setTimer=setTimeout, clearTimer=clearTimeout, delay=180}={}) {
  let timer=null;
  function cancel(){if(timer!==null){clearTimer(timer);timer=null;}}
  return {
    cancel,
    update(value){
      cancel();
      if(isCompleteTicketQr(value))timer=setTimer(()=>{timer=null;submit(value.trim());},delay);
    },
  };
}
