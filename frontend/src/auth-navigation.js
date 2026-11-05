// Remember navigation only, never credentials or authentication state.
const key = "vticket-staff-page";
const pages = new Set(["orders", "counter", "checkin", "manage"]);
export function rememberStaffPage(page, storage) {
  if (!pages.has(page)) return;
  try { (storage ?? globalThis.sessionStorage)?.setItem(key, page); } catch { /* Storage may be disabled. */ }
}
export function restoredStaffPage(user, storage) {
  let page;
  try { page = (storage ?? globalThis.sessionStorage)?.getItem(key); } catch { /* Use default page. */ }
  if (!pages.has(page) || (page === "manage" && !user.roles.includes("ROLE_ADMIN"))) return "orders";
  return page;
}
export function forgetStaffPage(storage) {
  try { (storage ?? globalThis.sessionStorage)?.removeItem(key); } catch { /* Storage may be disabled. */ }
}
