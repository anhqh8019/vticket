let credentials = "";
export function setCredentials(username, password) {
  credentials = username
    ? btoa(unescape(encodeURIComponent(`${username}:${password}`)))
    : "";
}
export async function api(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(credentials ? { Authorization: `Basic ${credentials}` } : {}),
  };
  if (options.method && options.method !== "GET") {
    const r = await fetch("/api/csrf", { credentials: "same-origin" });
    if (!r.ok) throw new Error("Không khởi tạo được phiên bảo vệ");
    const csrf = await r.json();
    headers[csrf.headerName] = csrf.token;
  }
  const r = await fetch("/api" + path, {
    ...options,
    credentials: "same-origin",
    headers: { ...headers, ...options.headers },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok)
    throw new Error(
      data.message ||
        (r.status === 401
          ? "Tài khoản hoặc mật khẩu không đúng"
          : r.status === 403
            ? "Không có quyền thực hiện"
            : "Không xử lý được yêu cầu"),
    );
  return data;
}
export const money = (n) => Number(n || 0).toLocaleString("vi-VN") + " ₫";
export const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const labels = {
  PENDING: "Chờ thanh toán",
  PAID: "Đã thanh toán",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
  USED: "Đã dùng",
  REVOKED: "Đã thu hồi",
  SCHEDULED: "Chưa đến hạn",
  RESERVED: "Đang giữ",
  AVAILABLE: "Chưa dùng",
  TICKET: "Vé",
  SERVICE: "Dịch vụ",
  COMBO: "Combo",
};
