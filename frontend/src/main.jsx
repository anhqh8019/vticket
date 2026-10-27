import { parseSmileCodes } from "./smile-import.js";
import React, { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";
import { api, setCredentials, money, today, labels } from "./api";
import "./style.css";
function Field({ label, ...props }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}
function App() {
  const [page, P] = useState("shop"),
    [user, U] = useState(null),
    [products, C] = useState([]),
    [config, G] = useState({}),
    [error, E] = useState(""),
    [busy, B] = useState(false),
    [detail, D] = useState(null);
  const admin = user?.roles.includes("ROLE_ADMIN");
  async function run(f) {
    E("");
    B(true);
    try {
      return await f();
    } catch (e) {
      E(e.message);
      return null;
    } finally {
      B(false);
    }
  }
  async function loadCatalog() {
    C(await api("/public/products"));
  }
  useEffect(() => {
    run(async () => {
      G(await api("/public/config"));
      await loadCatalog();
    });
  }, []);
  function go(p) {
    P(p);
    E("");
    window.scrollTo(0, 0);
  }
  async function open(token) {
    await run(async () => {
      D(await api("/public/orders/" + encodeURIComponent(token)));
      localStorage.setItem("vticket-last-token", token);
      P("ticket");
    });
  }
  return (
    <>
      <div className="notice">
        {config.vietqrEnabled && config.vietqrEnvironment === "sandbox"
          ? "VIETQR SANDBOX · Môi trường kiểm thử kết nối"
          : "VENUSGITI · VÉ ĐIỆN TỬ & TRẢI NGHIỆM NGHỈ DƯỠNG"}
      </div>
      <header>
        <button className="brand" onClick={() => go("shop")}>
          ♨ Venusgiti<small>MINERAL SPRINGS & WELLNESS</small>
        </button>
        <nav>
          <button onClick={() => go("shop")}>Đặt vé</button>
          <button onClick={() => go("lookup")}>Tra cứu vé</button>
          {user ? (
            <>
              <button onClick={() => go("counter")}>Bán tại quầy</button>
              <button onClick={() => go("checkin")}>Kiểm soát vé</button>
              <button onClick={() => go("orders")}>Đơn hàng</button>
              {admin && <button onClick={() => go("manage")}>Quản trị</button>}
              <button
                onClick={() => {
                  setCredentials("", "");
                  U(null);
                  go("shop");
                }}
              >
                Đăng xuất
              </button>
            </>
          ) : (
            <button onClick={() => go("login")}>Nhân viên</button>
          )}
        </nav>
      </header>
      <main>
        {error && (
          <div className="alert" role="alert">
            {error}
            <button onClick={() => E("")}>×</button>
          </div>
        )}
        {busy && (
          <div className="progress" role="status">
            Đang xử lý…
          </div>
        )}
        {page === "login" && (
          <div className="panel narrow">
            <h1>Đăng nhập vận hành</h1>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                run(async () => {
                  setCredentials(f.get("username"), f.get("password"));
                  try {
                    U(await api("/me"));
                    P("orders");
                  } catch (e) {
                    setCredentials("", "");
                    throw e;
                  }
                });
              }}
            >
              <Field
                label="Tài khoản"
                name="username"
                required
                autoComplete="username"
              />
              <Field
                label="Mật khẩu"
                name="password"
                type="password"
                required
                autoComplete="current-password"
              />
              <button disabled={busy} className="full">
                Đăng nhập
              </button>
            </form>
          </div>
        )}
        {(page === "shop" || page === "counter") && (
          <Shop
            key={page}
            counter={page === "counter"}
            products={products}
            run={run}
            busy={busy}
            placed={(b) => open(b.accessToken)}
          />
        )}
        {page === "lookup" && <Lookup open={open} busy={busy} />}
        {page === "ticket" && detail && (
          <OrderView
            d={detail}
            config={config}
            run={run}
            busy={busy}
            refresh={() => open(detail.order.accessToken)}
            staff={!!user}
            onUpdated={(next) =>
              D((previous) =>
                previous?.order.id === next.order.id &&
                previous.order.status === "PAID" &&
                next.order.status !== "PAID"
                  ? previous
                  : next,
              )
            }
          />
        )}
        {page === "checkin" && user && <CheckIn run={run} busy={busy} />}
        {page === "orders" && user && (
          <Orders run={run} busy={busy} open={open} />
        )}
        {page === "manage" && admin && (
          <Manage run={run} busy={busy} refreshCatalog={loadCatalog} />
        )}
      </main>
      <footer>Venusgiti · Bán vé online & tại quầy · v0.1</footer>
    </>
  );
}
function Shop({ counter, products, run, busy, placed }) {
  const [qty, Q] = useState({}),
    [date, T] = useState(today()),
    [customer, S] = useState({
      customerName: "",
      phone: "",
      email: "",
      voucherCode: "",
    });
  const selected = products.filter((p) => qty[p.id]),
    subtotal = selected.reduce((s, p) => s + p.price * qty[p.id], 0);
  const change = (id, n) => Q({ ...qty, [id]: Math.max(0, Math.min(100, n)) });
  const quantity = (p) => (
    <div className="qty">
      <button
        type="button"
        className="secondary"
        aria-label={"Giảm " + p.name}
        onClick={() => change(p.id, (qty[p.id] || 0) - 1)}
      >
        −
      </button>
      <b>{qty[p.id] || 0}</b>
      <button
        type="button"
        className="secondary"
        aria-label={"Tăng " + p.name}
        onClick={() => change(p.id, (qty[p.id] || 0) + 1)}
      >
        +
      </button>
    </div>
  );
  return (
    <>
      {!counter ? (
        <section className="hero">
          <div>
            <span className="eyebrow">THIÊN NHIÊN · THƯ GIÃN · TÁI TẠO</span>
            <h1>
              Một ngày an nhiên.
              <br />
              Tận hưởng từng khoảnh khắc.
            </h1>
            <p>Chọn trải nghiệm khoáng nóng và dịch vụ dành riêng cho bạn.</p>
            <span className="badge">Đặt vé trực tuyến · Nhận vé điện tử</span>
          </div>
          <div className="landscape">
            <div className="sun" />
            <div className="hill" />
            <div className="hill two" />
            <div className="pool" />
            <span>Trở về với sự cân bằng</span>
          </div>
        </section>
      ) : (
        <>
          <span className="eyebrow">QUẦY LỄ TÂN</span>
          <h1>Bán vé tại quầy</h1>
        </>
      )}
      <form
        className="layout"
        onSubmit={(e) => {
          e.preventDefault();
          run(async () => {
            if (!selected.some((p) => p.kind !== "SERVICE"))
              throw new Error("Chọn ít nhất một vé hoặc combo");
            placed(
              await api(counter ? "/staff/orders" : "/public/orders", {
                method: "POST",
                body: {
                  ...customer,
                  visitDate: date,
                  lines: selected.map((p) => ({
                    productId: p.id,
                    quantity: qty[p.id],
                  })),
                },
              }),
            );
          });
        }}
      >
        <div>
          <div className="panel">
            <h2>Lên lịch trải nghiệm</h2>
            <Field
              label="Ngày sử dụng"
              type="date"
              value={date}
              min={today()}
              required
              onChange={(e) => T(e.target.value)}
            />
            <p className="muted">
              Vé có hiệu lực trong ngày đã chọn. Chưa áp dụng hạn mức và giá trẻ
              em.
            </p>
          </div>
          <h2>Vé & combo</h2>
          <div className="cards">
            {products
              .filter((p) => p.kind !== "SERVICE")
              .map((p, i) => (
                <article className="card" key={p.id}>
                  <div className={"art a" + (i % 3)}>
                    {p.kind === "COMBO" ? "✧" : "≈"}
                  </div>
                  <div className="cardbody">
                    <span className="badge">{labels[p.kind]}</span>
                    <h3>{p.name}</h3>
                    <p>{p.description}</p>
                    {p.includedServiceIds.length > 0 && (
                      <p className="muted">
                        Bao gồm:{" "}
                        {p.includedServiceIds
                          .map(
                            (id) =>
                              products.find((s) => s.id === id)?.name ||
                              "Dịch vụ",
                          )
                          .join(", ")}
                      </p>
                    )}
                    <b className="price">{money(p.price)}</b>
                    {quantity(p)}
                  </div>
                </article>
              ))}
          </div>
          <section className="panel">
            <h2>Dịch vụ mua thêm</h2>
            {products
              .filter((p) => p.kind === "SERVICE")
              .map((p) => (
                <div className="service row" key={p.id}>
                  <div>
                    <b>{p.name}</b>
                    <p className="muted">{money(p.price)} / lượt</p>
                  </div>
                  {quantity(p)}
                </div>
              ))}
          </section>
          <section className="panel">
            <h2>Thông tin khách hàng</h2>
            <div className="two-col">
              {[
                ["customerName", "Họ và tên", "text"],
                ["phone", "Số điện thoại", "tel"],
                ["email", "Email (không bắt buộc)", "email"],
              ].map(([k, l, t]) => (
                <Field
                  key={k}
                  label={l}
                  type={t}
                  value={customer[k]}
                  required={k !== "email"}
                  maxLength={k === "email" ? 254 : 150}
                  onChange={(e) => S({ ...customer, [k]: e.target.value })}
                />
              ))}
            </div>
            <Field
              label="Voucher dành riêng cho khách hàng"
              placeholder="Nhập mã VG-… nếu được cấp"
              value={customer.voucherCode}
              maxLength={64}
              onChange={(e) =>
                S({ ...customer, voucherCode: e.target.value.toUpperCase() })
              }
            />
            <p className="muted">
              Voucher giảm % tổng tiền vé và dịch vụ, dùng một lần theo số điện
              thoại được cấp. Server kiểm tra và xác nhận mức giảm khi tạo đơn.
            </p>
          </section>
        </div>
        <aside className="panel summary">
          <span className="eyebrow">CHUYẾN ĐI CỦA BẠN</span>
          <h2>Thông tin đặt vé</h2>
          <p>
            Ngày sử dụng: <b>{date.split("-").reverse().join("/")}</b>
          </p>
          {selected.map((p) => (
            <div className="row summaryline" key={p.id}>
              <span>
                {qty[p.id]} × {p.name}
              </span>
              <b>{money(p.price * qty[p.id])}</b>
            </div>
          ))}
          <div className="row total">
            <span>Tổng trước ưu đãi</span>
            <b>{money(subtotal)}</b>
          </div>
          <p className="muted">
            Số tiền sau giảm hiển thị ở bước xác nhận đơn.
          </p>
          <button className="full" disabled={busy || !subtotal}>
            Tạo đơn & xem thanh toán →
          </button>
          <p className="muted">Đơn và voucher được giữ tối đa 15 phút.</p>
        </aside>
      </form>
    </>
  );
}
function Lookup({ open, busy }) {
  const [token, T] = useState(localStorage.getItem("vticket-last-token") || "");
  return (
    <div className="panel narrow">
      <h1>Tra cứu vé</h1>
      <p>Nhập mã tra cứu bí mật được cung cấp khi tạo đơn.</p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          open(token.trim());
        }}
      >
        <Field
          label="Mã tra cứu đơn"
          value={token}
          onChange={(e) => T(e.target.value)}
          required
        />
        <button className="full" disabled={busy}>
          Tra cứu
        </button>
      </form>
    </div>
  );
}
function OrderView({ d, config, run, busy, refresh, staff, onUpdated }) {
  const { order: b, tickets, payment } = d;
  const [pollError, setPollError] = useState(false);
  const expired = Date.parse(b.expiresAt) <= Date.now();
  useEffect(() => {
    setPollError(false);
    if (b.status !== "PENDING") return;
    let active = true,
      inFlight = false;
    const controller = new AbortController();
    const timer = setInterval(async () => {
      if (inFlight) return;
      inFlight = true;
      try {
        const next = await api(`/public/orders/${b.accessToken}`, {
          signal: controller.signal,
        });
        if (active) {
          onUpdated(next);
          setPollError(false);
        }
      } catch {
        if (active) setPollError(true);
      } finally {
        inFlight = false;
      }
    }, 3000);
    return () => {
      active = false;
      controller.abort();
      clearInterval(timer);
    };
  }, [b.accessToken, b.status]);
  return (
    <>
      <div className="row">
        <h1>Trải nghiệm tại Venusgiti</h1>
        <span className={"badge " + b.status}>{labels[b.status]}</span>
      </div>
      <div className="layout">
        <div>
          <section className="panel">
            <h2>Thông tin đặt vé</h2>
            <p>
              {b.customerName} · {b.phone}
              <br />
              Ngày sử dụng: <b>{b.visitDate}</b>
              <br />
              {b.channel === "COUNTER" ? "Mua tại quầy" : "Mua online"}
            </p>
            <div className="tablewrap">
              <table>
                <thead>
                  <tr>
                    <th>Vé / dịch vụ</th>
                    <th>SL</th>
                    <th>Thành tiền</th>
                  </tr>
                </thead>
                <tbody>
                  {b.lines.map((l, i) => (
                    <tr key={i}>
                      <td>
                        {l.name}
                        <small>{l.includedServices}</small>
                      </td>
                      <td>{l.quantity}</td>
                      <td>{money(l.unitPrice * l.quantity)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="panel">
            <h3>Mã tra cứu đơn</h3>
            <code className="break">{b.accessToken}</code>
            <p className="muted">
              Lưu mã này để xem lại đơn; không chia sẻ với người khác.
            </p>
            <button className="secondary" onClick={refresh}>
              Cập nhật trạng thái
            </button>
          </section>
          {tickets.length > 0 && (
            <>
              <h2>Vé điện tử ({tickets.length})</h2>
              <div className="cards ticketcards">
                {tickets.map((t) => (
                  <div className="panel ticket" key={t.id}>
                    <b className="brandtext">♨ Venusgiti</b>
                    <h3>{t.productName}</h3>
                    <img
                      src={"/api/public/qr/" + t.qrToken}
                      width="200"
                      height="200"
                      alt="QR vé vào cổng"
                    />
                    <p>{t.visitDate}</p>
                    <code className="break">{t.ticketCode || t.qrToken}</code>
                    {t.source !== "SMILE" && t.smileSerial && (
                      <p>
                        Seri: <b>{t.smileSerial}</b>
                        <br />
                        Barcode: <code>{t.smileBarcode}</code>
                      </p>
                    )}
                    {t.source === "SMILE" && (
                      <p>
                        Seri Smile: <b>{t.smileSerial}</b>
                        <br />
                        Barcode Smile: <code>{t.smileBarcode}</code>
                        <br />
                        <small>
                          Loại Smile: {t.smileTicketTypeName} (
                          {t.smileTicketTypeCode}). Chưa đồng bộ Smile; QR trên
                          vé dùng tại vticket.
                        </small>
                      </p>
                    )}
                    <p className="muted">{t.includedServices}</p>
                    <span className="badge">
                      {t.checkedInAt ? "Đã check-in" : "Chưa sử dụng"}
                    </span>
                  </div>
                ))}
              </div>
              <div className="tabs no-print">
                <a
                  className="button"
                  href={`/api/public/orders/${b.accessToken}/tickets.pdf`}
                  download
                >
                  Tải PDF vé ({tickets.length} vé)
                </a>
                <button className="secondary" onClick={() => window.print()}>
                  In vé
                </button>
              </div>
            </>
          )}
        </div>
        <aside className="panel summary">
          <h2>Thanh toán</h2>
          <div className="row">
            <span>Tổng tiền</span>
            <b>{money(b.subtotal)}</b>
          </div>
          <div className="row">
            <span>Giảm {b.discountPercent}%</span>
            <b>−{money(b.discount)}</b>
          </div>
          <div className="row total">
            <b>Cần thanh toán</b>
            <strong className="price">{money(b.total)}</strong>
          </div>
          {b.voucherCode && <p className="muted">Voucher: {b.voucherCode}</p>}
          {b.status === "PENDING" && (
            <>
              <p className="muted">
                Hạn thanh toán của đơn:{" "}
                {new Date(b.expiresAt).toLocaleString("vi-VN")}
              </p>
              {pollError && (
                <p className="muted">
                  Chưa cập nhật được trạng thái. Có thể bấm Cập nhật trạng thái
                  bên trái.
                </p>
              )}
              {expired ? (
                <p className="alert">
                  Đơn đã hết thời gian thanh toán. Nếu đã chuyển tiền, vui lòng
                  liên hệ quầy để đối soát.
                </p>
              ) : Number(b.total) === 0 ? (
                <>
                  <p>Đơn được ưu đãi còn 0 đồng, không cần chuyển khoản.</p>
                  <button
                    disabled={busy}
                    className="full"
                    onClick={() =>
                      run(async () => {
                        await api(
                          `/public/orders/${b.accessToken}/confirm-zero`,
                          { method: "POST" },
                        );
                        await refresh();
                      })
                    }
                  >
                    Xác nhận đơn 0 đồng & nhận vé
                  </button>
                </>
              ) : (
                <>
                  {payment?.state === "READY" ? (
                    <div className="payment-qr">
                      <h3>Chuyển khoản bằng VietQR</h3>
                      <img
                        src={`/api/public/orders/${b.accessToken}/vietqr/image`}
                        alt="QR thanh toán do VietQR cung cấp"
                        width="260"
                        height="260"
                      />
                      <p>
                        <b>
                          {payment.bankCode} ·{" "}
                          {payment.virtualAccount || payment.bankAccount}
                        </b>
                        <br />
                        {payment.accountName}
                      </p>
                      <div className="row">
                        <span>Số tiền</span>
                        <b>{money(payment.amount)}</b>
                      </div>
                      <p className="muted">
                        Nội dung: <b className="break">{payment.content}</b>
                      </p>
                      <p className="muted">
                        Quét QR và giữ nguyên số tiền, nội dung. Hệ thống tự xác
                        nhận khi VietQR báo có; không cần bấm “đã chuyển tiền”.
                      </p>
                    </div>
                  ) : payment?.state === "CREATING" ? (
                    <p>Đang tạo VietQR, vui lòng chờ…</p>
                  ) : (
                    <>
                      <button
                        disabled={busy || !config.vietqrEnabled}
                        className="full"
                        onClick={() =>
                          run(async () => {
                            await api(
                              `/public/orders/${b.accessToken}/vietqr`,
                              { method: "POST" },
                            );
                            await refresh();
                          })
                        }
                      >
                        {payment?.state === "FAILED"
                          ? "Thử tạo lại VietQR"
                          : "Thanh toán chuyển khoản VietQR"}
                      </button>
                      {payment?.message && (
                        <p className="alert">{payment.message}</p>
                      )}
                      {!config.vietqrEnabled && (
                        <p className="muted">
                          VietQR chưa được cấu hình. Vui lòng liên hệ quầy.
                        </p>
                      )}
                    </>
                  )}
                  {staff &&
                    b.channel === "COUNTER" &&
                    b.paymentMethod !== "VIETQR" && (
                      <button
                        disabled={busy}
                        className="secondary full"
                        onClick={() => {
                          if (
                            window.confirm(
                              `Xác nhận đã thu đủ ${money(b.total)} bằng tiền mặt?`,
                            )
                          )
                            run(async () => {
                              await api(`/staff/orders/${b.id}/cash`, {
                                method: "POST",
                              });
                              await refresh();
                            });
                        }}
                      >
                        Thu tiền mặt & xuất vé
                      </button>
                    )}
                  {staff &&
                    b.channel === "COUNTER" &&
                    b.paymentMethod === "VIETQR" && (
                      <p className="muted">
                        Đơn đang chọn VietQR. Nếu cần đổi sang tiền mặt, hủy đơn
                        chưa thanh toán và tạo lại.
                      </p>
                    )}
                </>
              )}
              <button
                disabled={busy}
                className="secondary full"
                onClick={() => {
                  if (
                    window.confirm(
                      "Hủy đơn chưa thanh toán và trả mã vé về kho?",
                    )
                  )
                    run(async () => {
                      await api(`/public/orders/${b.accessToken}/cancel`, {
                        method: "POST",
                      });
                      await refresh();
                    });
                }}
              >
                Hủy đơn chưa thanh toán
              </button>
            </>
          )}
          {b.status === "PAID" && (
            <p className="success">
              Đã thanh toán ·{" "}
              {b.paymentMethod === "CASH"
                ? "Tiền mặt"
                : b.paymentMethod === "VIETQR"
                  ? "Chuyển khoản VietQR"
                  : b.paymentMethod === "DEMO"
                    ? "Giao dịch thử nghiệm cũ"
                    : b.paymentMethod || "Không phát sinh thu tiền"}
            </p>
          )}
        </aside>
      </div>
    </>
  );
}
function CheckIn({ run, busy }) {
  const [token, T] = useState(""),
    [result, R] = useState(null);
  return (
    <section className="panel narrow">
      <span className="eyebrow">KIỂM SOÁT VÀO CỔNG</span>
      <h1>Check-in vé</h1>
      <p>
        Quét bằng máy đọc QR USB vào ô bên dưới hoặc dán mã vé. Mỗi QR tương ứng
        một lượt vào cổng.
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          R(null);
          run(async () => {
            R(
              await api("/staff/check-in", {
                method: "POST",
                body: { qrToken: token.trim() },
              }),
            );
            T("");
          });
        }}
      >
        <Field
          label="Mã QR vé"
          value={token}
          onChange={(e) => T(e.target.value)}
          autoFocus
          required
        />
        <button disabled={busy} className="full">
          Kiểm tra & check-in
        </button>
      </form>
      {result && (
        <div className="success">
          <h3>✓ Check-in thành công</h3>
          {result.productName}
          <p>{result.includedServices}</p>
          <small>{new Date(result.checkedInAt).toLocaleString("vi-VN")}</small>
        </div>
      )}
    </section>
  );
}
function Orders({ run, busy, open }) {
  const [rows, R] = useState([]),
    [search, S] = useState("");
  const load = () => run(async () => R(await api("/staff/orders")));
  useEffect(() => {
    load();
  }, []);
  const paid = rows.filter((b) => b.status === "PAID");
  return (
    <>
      <div className="row">
        <h1>Đơn hàng & doanh thu</h1>
        <button className="secondary" onClick={load}>
          Làm mới
        </button>
      </div>
      <div className="stats">
        {[
          [
            "Doanh thu đơn đã tải",
            money(paid.reduce((s, b) => s + Number(b.total), 0)),
          ],
          ["Đơn đã thanh toán", paid.length],
          ["Đơn online", rows.filter((b) => b.channel === "ONLINE").length],
          ["Đơn tại quầy", rows.filter((b) => b.channel === "COUNTER").length],
        ].map(([l, v]) => (
          <div className="panel stat" key={l}>
            <span className="muted">{l}</span>
            <strong>{v}</strong>
          </div>
        ))}
      </div>
      <p className="muted">
        Tối đa 200 đơn gần nhất. Chỉ tiêu tính trên danh sách đã tải.
      </p>
      <div className="panel">
        <Field
          label="Tìm kiếm đơn hàng"
          placeholder="Tên khách, điện thoại, mã đơn…"
          value={search}
          onChange={(e) => S(e.target.value)}
        />
        <div className="tablewrap">
          <table>
            <thead>
              <tr>
                <th>Khách hàng / Mã đơn</th>
                <th>Ngày sử dụng</th>
                <th>Kênh</th>
                <th>Thành tiền</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {rows
                .filter((b) =>
                  `${b.id} ${b.customerName} ${b.phone}`
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map((b) => (
                  <tr key={b.id}>
                    <td>
                      <b>{b.customerName}</b>
                      <small>
                        {b.phone} · {b.id.slice(0, 8)}
                      </small>
                    </td>
                    <td>{b.visitDate}</td>
                    <td>{b.channel === "ONLINE" ? "Online" : "Tại quầy"}</td>
                    <td>{money(b.total)}</td>
                    <td>
                      <span className={"badge " + b.status}>
                        {labels[b.status]}
                      </span>
                    </td>
                    <td>
                      <button
                        className="secondary compact"
                        onClick={() => open(b.accessToken)}
                      >
                        {b.status === "PENDING"
                          ? "Thanh toán / Xem đơn"
                          : "Xem vé"}
                      </button>
                      {b.status === "PENDING" &&
                        b.channel === "COUNTER" &&
                        b.paymentMethod !== "VIETQR" &&
                        Number(b.total) > 0 && (
                          <button
                            disabled={busy}
                            className="compact"
                            onClick={() => {
                              if (
                                window.confirm(
                                  "Xác nhận đã thu đủ " +
                                    money(b.total) +
                                    " tiền mặt?",
                                )
                              )
                                run(async () => {
                                  await api("/staff/orders/" + b.id + "/cash", {
                                    method: "POST",
                                  });
                                  await load();
                                });
                            }}
                          >
                            Đã thu tiền mặt
                          </button>
                        )}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
function Manage({ run, busy, refreshCatalog }) {
  const [tab, T] = useState("tickets"),
    [products, P] = useState([]),
    [vouchers, V] = useState([]),
    [audit, A] = useState([]),
    [editing, E] = useState(null),
    [show, F] = useState(false);
  const load = () =>
    run(async () => {
      P(await api("/admin/products"));
      V(await api("/admin/vouchers"));
      A(await api("/admin/audit"));
    });
  useEffect(() => {
    load();
  }, []);
  return (
    <>
      <span className="eyebrow">QUẢN TRỊ HỆ THỐNG</span>
      <h1>Danh mục & ưu đãi</h1>
      <div className="tabs">
        {[
          ["tickets", "Quản lý vé"],
          ["services", "Dịch vụ đi kèm"],
          ["batches", "Lô vé & mã vé"],
          ["vouchers", "Voucher khách hàng"],
          ["receipts", "Đối soát VietQR"],
          ["audit", "Nhật ký"],
        ].map(([k, l]) => (
          <button
            className={tab === k ? "" : "secondary"}
            key={k}
            onClick={() => {
              T(k);
              F(false);
            }}
          >
            {l}
          </button>
        ))}
      </div>
      {["tickets", "services"].includes(tab) && (
        <>
          <div className="row">
            <h2>
              {tab === "services"
                ? "Dịch vụ đi kèm"
                : "Danh mục loại vé & combo"}
            </h2>
            <button
              onClick={() => {
                E(null);
                F(true);
              }}
            >
              {tab === "services" ? "+ Tạo dịch vụ" : "+ Tạo loại vé"}
            </button>
          </div>
          {show && (
            <ProductForm
              key={`${tab}-${editing?.id || "new"}`}
              catalogType={tab}
              product={editing}
              products={products}
              busy={busy}
              close={() => F(false)}
              save={(input) =>
                run(async () => {
                  await api(
                    "/admin/products" + (editing ? "/" + editing.id : ""),
                    { method: editing ? "PUT" : "POST", body: input },
                  );
                  F(false);
                  await load();
                  await refreshCatalog();
                })
              }
            />
          )}
          <div className="panel tablewrap">
            <table>
              <thead>
                <tr>
                  <th>
                    {tab === "services"
                      ? "Mã / Tên dịch vụ"
                      : "Mã loại vé / Tên vé"}
                  </th>
                  <th>Loại</th>
                  <th>Giá</th>
                  {tab === "tickets" && <th>Dịch vụ bao gồm</th>}
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {products
                  .filter((p) =>
                    tab === "services"
                      ? p.kind === "SERVICE"
                      : p.kind !== "SERVICE",
                  )
                  .map((p) => (
                    <tr key={p.id}>
                      <td>
                        <b>{p.name}</b>
                        <small>{p.code}</small>
                      </td>
                      <td>{labels[p.kind]}</td>
                      <td>{money(p.price)}</td>
                      {tab === "tickets" && (
                        <td>
                          {p.includedServiceIds
                            .map(
                              (id) => products.find((s) => s.id === id)?.name,
                            )
                            .join(", ") || "—"}
                        </td>
                      )}
                      <td>{p.active ? "Đang bán" : "Ngừng bán"}</td>
                      <td>
                        <button
                          className="secondary compact"
                          onClick={() => {
                            E(p);
                            F(true);
                          }}
                        >
                          Chỉnh sửa
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {tab === "batches" && (
        <BatchManager products={products} run={run} busy={busy} />
      )}
      {tab === "receipts" && <PaymentReceipts run={run} />}
      {tab === "vouchers" && (
        <>
          <div className="row">
            <div>
              <h2>Voucher khách hàng thân quen</h2>
              <p>Cấp riêng cho khách · Giảm % tổng đơn · Dùng một lần</p>
            </div>
            <button onClick={() => F(!show)}>+ Cấp voucher</button>
          </div>
          {show && (
            <VoucherForm
              busy={busy}
              save={(input) =>
                run(async () => {
                  await api("/admin/vouchers", { method: "POST", body: input });
                  F(false);
                  await load();
                })
              }
            />
          )}
          <div className="panel tablewrap">
            <table>
              <thead>
                <tr>
                  <th>Mã voucher</th>
                  <th>Khách được cấp</th>
                  <th>Giảm</th>
                  <th>Hiệu lực</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {vouchers.map(({ voucher: v, status }) => (
                  <tr key={v.id}>
                    <td>
                      <code>{v.code}</code>
                    </td>
                    <td>
                      <b>{v.customerName}</b>
                      <small>{v.phone}</small>
                    </td>
                    <td>{v.percentage}%</td>
                    <td>
                      {new Date(v.validFrom).toLocaleDateString("vi-VN")} –{" "}
                      {new Date(v.validUntil).toLocaleString("vi-VN")}
                    </td>
                    <td>
                      <span className="badge">{labels[status]}</span>
                      {v.usedOrderId && (
                        <small>Đơn {v.usedOrderId.slice(0, 8)}</small>
                      )}
                    </td>
                    <td>
                      {["AVAILABLE", "SCHEDULED"].includes(status) && (
                        <button
                          disabled={busy}
                          className="secondary compact"
                          onClick={() => {
                            if (
                              window.confirm("Thu hồi voucher " + v.code + "?")
                            )
                              run(async () => {
                                await api(
                                  "/admin/vouchers/" + v.code + "/revoke",
                                  { method: "POST" },
                                );
                                await load();
                              });
                          }}
                        >
                          Thu hồi
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {tab === "audit" && (
        <div className="panel tablewrap">
          <p className="muted">100 thao tác gần nhất</p>
          <table>
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>Tài khoản</th>
                <th>Thao tác</th>
                <th>Đối tượng</th>
              </tr>
            </thead>
            <tbody>
              {audit.map((a) => (
                <tr key={a.id}>
                  <td>{new Date(a.happenedAt).toLocaleString("vi-VN")}</td>
                  <td>{a.actor}</td>
                  <td>{a.action}</td>
                  <td className="break">{a.target}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function PaymentReceipts({ run }) {
  const [rows, setRows] = useState([]),
    [filter, setFilter] = useState("REVIEW");
  const load = () =>
    run(async () => setRows(await api("/admin/payment-receipts")));
  useEffect(() => {
    load();
  }, []);
  const reasons = {
    WRONG_AMOUNT: "Số tiền không khớp",
    WRONG_ACCOUNT: "Tài khoản nhận không khớp",
    NOT_CREDIT: "Giao dịch ghi nợ",
    UNKNOWN_ORDER: "Không tìm thấy mã đơn VietQR",
    INVALID_TRANSACTION_TIME: "Thời gian giao dịch không hợp lệ",
    CONTENT_MISMATCH: "Nội dung chuyển khoản không khớp",
    ALREADY_PAID: "Đơn đã được thanh toán",
    QR_NOT_READY: "QR chưa sẵn sàng",
    ORDER_EXPIRED: "Đơn hết thời gian thanh toán",
    ORDER_NOT_PENDING: "Đơn đã hủy/hết hạn/đã thanh toán",
    PAYMENT_METHOD_CHANGED: "Phương thức thanh toán không khớp",
    VOUCHER_INVALID: "Voucher không còn hợp lệ",
    INVENTORY_UNAVAILABLE: "Không còn đủ mã vé đang giữ",
  };
  return (
    <>
      <div className="row">
        <div>
          <h2>Đối soát chuyển khoản VietQR</h2>
          <p>
            Giao dịch không khớp được lưu để kiểm tra, không tự phát hành vé.
          </p>
        </div>
        <button className="secondary" onClick={load}>
          Làm mới
        </button>
      </div>
      <div className="panel">
        <label className="field">
          Trạng thái
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="REVIEW">Cần đối soát</option>
            <option value="APPLIED">Đã xác nhận thanh toán</option>
            <option value="">Tất cả</option>
          </select>
        </label>
        <p className="muted">
          Tối đa 200 giao dịch gần nhất. Đối chiếu ngân hàng trước khi xử lý
          hoàn tiền ngoài hệ thống.
        </p>
        <div className="tablewrap">
          <table>
            <thead>
              <tr>
                <th>Thời gian nhận</th>
                <th>Giao dịch / tham chiếu</th>
                <th>Mã đơn VietQR</th>
                <th>Tài khoản</th>
                <th>Số tiền</th>
                <th>Kết quả</th>
              </tr>
            </thead>
            <tbody>
              {rows
                .filter((r) => !filter || r.state === filter)
                .map((r) => (
                  <tr key={r.id}>
                    <td>{new Date(r.receivedAt).toLocaleString("vi-VN")}</td>
                    <td>
                      {r.transactionId}
                      <small>{r.referenceNumber}</small>
                    </td>
                    <td>
                      {r.providerOrderId}
                      <small>{r.bookingId || "Không tìm thấy đơn"}</small>
                    </td>
                    <td>{r.bankAccount}</td>
                    <td>{money(r.amount)}</td>
                    <td>
                      <span className="badge">
                        {r.state === "APPLIED" ? "Đã xác nhận" : "Cần đối soát"}
                      </span>
                      <small>{reasons[r.reason] || r.reason || ""}</small>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

function BatchManager({ products, run, busy }) {
  const [batches, setBatches] = useState([]),
    [show, setShow] = useState(false),
    [codes, setCodes] = useState(null),
    [state, setState] = useState(""),
    [search, setSearch] = useState(""),
    [source, setSource] = useState("VTICKET"),
    [smileText, setSmileText] = useState("");
  let smilePreview = [],
    smileError = "";
  if (source === "SMILE" && smileText.trim()) {
    try {
      smilePreview = parseSmileCodes(smileText);
    } catch (e) {
      smileError = e.message;
    }
  }
  const labelsStock = {
    AVAILABLE: "Còn bán",
    RESERVED: "Đang giữ",
    SOLD: "Đã bán, chưa dùng",
    USED: "Đã sử dụng",
  };
  const load = () =>
    run(async () => setBatches(await api("/admin/ticket-batches")));
  useEffect(() => {
    load();
  }, []);
  const view = (id, filter = "", page = 0) =>
    run(async () => {
      setCodes(
        await api(
          `/admin/ticket-batches/${id}/codes?${filter ? "state=" + filter + "&" : ""}page=${page}`,
        ),
      );
      setState(filter);
    });
  const totals = batches.reduce(
    (a, b) => {
      for (const k of Object.keys(labelsStock)) a[k] += b.counts[k] || 0;
      return a;
    },
    { AVAILABLE: 0, RESERVED: 0, SOLD: 0, USED: 0 },
  );
  return (
    <>
      <div className="row">
        <div>
          <h2>Lô vé & kho mã vé</h2>
          <p>Mỗi mã tương ứng một vé vào cổng. Dịch vụ không quản lý lô.</p>
        </div>
        <div className="tabs">
          <button
            className="secondary"
            onClick={() => {
              load();
              if (codes) view(codes.batch.id, state, codes.page);
            }}
          >
            Làm mới
          </button>
          <button onClick={() => setShow(!show)}>+ Tạo lô vé</button>
        </div>
      </div>
      <p className="muted">
        Có thể sinh mã nội bộ hoặc nhập nguyên cặp Seri / barcode từ Smile. Chưa
        kết nối API Smile; QR PDF hiện vẫn dùng tại vticket, chưa dùng tại cổng
        Smile.
      </p>
      <div className="stats">
        {[
          ["Còn bán / chưa bán", totals.AVAILABLE],
          ["Đang giữ theo đơn", totals.RESERVED],
          ["Đã bán (gồm đã dùng)", totals.SOLD + totals.USED],
          ["Đã sử dụng", totals.USED],
        ].map(([label, value]) => (
          <div className="panel stat" key={label}>
            <span className="muted">{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
      <p className="muted">
        Thống kê tối đa 200 lô mới nhất; số chưa bán gồm cả mã thuộc lô đã tạm
        dừng/hết hạn.
      </p>
      {show && (
        <form
          className="panel"
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            run(async () => {
              const body = {
                code: f.get("code").trim().toUpperCase(),
                productId: Number(f.get("productId")),
                validFrom: f.get("validFrom"),
                validUntil: f.get("validUntil"),
              };
              if (source === "SMILE") {
                body.smileTicketTypeCode = f.get("smileTicketTypeCode").trim();
                body.smileTicketTypeName = f.get("smileTicketTypeName").trim();
                body.codes = parseSmileCodes(smileText);
                body.smileIsWeekday = f.get("smileIsWeekday") === "on";
                body.smileIsChild = f.get("smileIsChild") === "on";
              } else body.quantity = Number(f.get("quantity"));
              await api(
                source === "SMILE"
                  ? "/admin/ticket-batches/import-smile"
                  : "/admin/ticket-batches",
                { method: "POST", body },
              );
              setShow(false);
              await load();
            });
          }}
        >
          <h3>
            {source === "SMILE"
              ? "Nhập lô vé từ Smile"
              : "Tạo lô và sinh mã vé"}
          </h3>
          <label className="field">
            Nguồn mã vé
            <select value={source} onChange={(e) => setSource(e.target.value)}>
              <option value="VTICKET">Sinh mã nội bộ vticket</option>
              <option value="SMILE">Nhập Seri / barcode đã có từ Smile</option>
            </select>
          </label>
          <div className="two-col">
            <Field
              label="Mã lô (không trùng)"
              name="code"
              placeholder="VD: TAMKHOANG-2026-01"
              pattern="[A-Za-z0-9_-]+"
              maxLength={40}
              required
            />
            <label className="field">
              Loại vé / combo
              <select name="productId" required defaultValue="">
                <option value="" disabled>
                  Chọn loại vé
                </option>
                {products
                  .filter((p) => p.kind !== "SERVICE")
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                      {p.active ? "" : " (đã ngừng bán)"}
                    </option>
                  ))}
              </select>
            </label>
            {source === "VTICKET" && (
              <Field
                label="Số lượng mã trong lô"
                type="number"
                name="quantity"
                min="1"
                max="10000"
                step="1"
                defaultValue="100"
                required
              />
            )}
            <Field
              label="Ngày sử dụng từ"
              type="date"
              name="validFrom"
              defaultValue={today()}
              required
            />
            <Field
              label="Ngày sử dụng đến hết"
              type="date"
              name="validUntil"
              min={today()}
              required
            />
          </div>
          {source === "SMILE" ? (
            <>
              <div className="two-col">
                <Field
                  label="PkgCode – mã loại vé Smile"
                  name="smileTicketTypeCode"
                  maxLength={64}
                  pattern="[A-Za-z0-9._/-]+"
                  required
                />
                <Field
                  label="Tên loại vé trên Smile"
                  name="smileTicketTypeName"
                  maxLength={150}
                  required
                />
              </div>
              <div className="two-col">
                <label>
                  <input type="checkbox" name="smileIsWeekday" /> IsWeekday – vé
                  ngày thường
                </label>
                <label>
                  <input type="checkbox" name="smileIsChild" /> IsChild – vé trẻ
                  em
                </label>
              </div>
              <p className="muted">
                Hai cờ được lưu theo Smile. Lô có cờ bật chỉ lưu để chuẩn bị
                tích hợp, chưa cho mở bán khi chưa có quy tắc sử dụng tương ứng.
              </p>
              <label className="field">
                Danh sách Seri và barcode
                <textarea
                  rows={7}
                  value={smileText}
                  onChange={(e) => setSmileText(e.target.value)}
                  placeholder={
                    "Seri\tbarcode\nVN000945\t<barcode gốc từ Smile>"
                  }
                  required
                />
              </label>
              <p className="muted">
                Dán 2 cột Seri, barcode; hoặc 3 cột SeqNo, Serial, BarCode. Có
                thể dán kết quả SQL kèm hàng tiêu đề đúng tên cột Smile (SeqNo,
                BarCode, Serial, PkgCode, FromDate, ToDate, IsWeekday, IsChild,
                IsCancel, IsLoss). Mỗi dòng một vé. Đặt cả hai cột là Text trước
                khi xuất từ Smile để giữ số 0 đầu. Không tự suy barcode từ Seri.
                Mỗi lô chỉ gồm một loại vé Smile và cùng thời hạn; chỉ nhập mã
                chưa bán/chưa dùng đã dành riêng cho kênh web.
              </p>
              {smileError ? (
                <p className="alert">{smileError}</p>
              ) : (
                <p>
                  <b>{smilePreview.length} vé</b> hợp lệ về cấu trúc, số lượng
                  lấy từ danh sách.
                </p>
              )}
              {!!smilePreview.length && !smileError && (
                <div className="tablewrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Seri</th>
                        <th>Barcode</th>
                      </tr>
                    </thead>
                    <tbody>
                      {smilePreview.slice(0, 5).map((row) => (
                        <tr key={row.serial}>
                          <td>{row.serial}</td>
                          <td>{row.barcode}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <p className="muted">
                    Xem trước tối đa 5 vé. Hệ thống kiểm tra trùng toàn bộ danh
                    sách và các lô đã nhập.
                  </p>
                </div>
              )}
              <p className="alert">
                Lô Smile mới nhập được tạm dừng để kiểm tra trước khi mở bán.
                Chưa tự kiểm tra tồn hoặc đồng bộ bán/soát vé với Smile.
              </p>
            </>
          ) : (
            <p className="muted">
              Mỗi vé có mã nội bộ, Seri và barcode riêng được tự sinh. Giá bán
              lấy từ danh mục loại vé; thời hạn là khoảng ngày khách được dùng
              vé.
            </p>
          )}
          <div className="tabs">
            <button
              disabled={
                busy ||
                (source === "SMILE" && (!!smileError || !smilePreview.length))
              }
            >
              {source === "SMILE" ? "Nhập lô Smile (tạm dừng)" : "Tạo lô vé"}
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => setShow(false)}
            >
              Đóng
            </button>
          </div>
        </form>
      )}
      <div className="panel">
        <Field
          label="Tìm lô / loại vé"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <div className="tablewrap">
          <table>
            <thead>
              <tr>
                <th>Mã lô / Loại vé</th>
                <th>Thời hạn sử dụng</th>
                <th>Tổng mã</th>
                <th>Chưa bán</th>
                <th>Đang giữ</th>
                <th>Đã bán</th>
                <th>Đã dùng</th>
                <th>Trạng thái</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {batches
                .filter(({ batch: b, productName }) =>
                  `${b.code} ${productName} ${b.smileTicketTypeCode || ""} ${b.smileTicketTypeName || ""}`
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map(({ batch: b, productName, counts: c }) => (
                  <tr key={b.id}>
                    <td>
                      <b>{b.code}</b>
                      <small>{productName}</small>
                      <small>
                        {b.source === "SMILE"
                          ? `Smile · ${b.smileTicketTypeCode} · ${b.smileTicketTypeName}`
                          : "Mã nội bộ vticket"}
                      </small>
                    </td>
                    <td>
                      {b.validFrom}
                      <small>đến {b.validUntil}</small>
                    </td>
                    <td>{b.quantity}</td>
                    <td>{c.AVAILABLE}</td>
                    <td>{c.RESERVED}</td>
                    <td>{c.SOLD + c.USED}</td>
                    <td>{c.USED}</td>
                    <td>
                      {b.validUntil < today()
                        ? "Hết hạn"
                        : b.active
                          ? "Đang mở bán"
                          : "Tạm dừng"}
                    </td>
                    <td>
                      <button
                        className="secondary compact"
                        onClick={() => view(b.id)}
                      >
                        Xem mã
                      </button>
                      {b.source === "VTICKET" && (
                        <button
                          className="secondary compact"
                          disabled={busy}
                          onClick={() =>
                            run(async () => {
                              await api(
                                `/admin/ticket-batches/${b.id}/generate-identifiers`,
                                { method: "POST" },
                              );
                              await load();
                              if (codes?.batch.id === b.id)
                                await view(b.id, state, codes.page);
                            })
                          }
                        >
                          Bổ sung Seri / barcode
                        </button>
                      )}
                      <button
                        disabled={busy}
                        className="secondary compact"
                        onClick={() =>
                          run(async () => {
                            if (
                              !b.active &&
                              b.source === "SMILE" &&
                              !window.confirm(
                                "Chưa đồng bộ API Smile. Chỉ mở bán nếu đã kiểm tra mã chưa bán/chưa dùng và dành riêng lô này cho web để tránh bán trùng. QR PDF hiện vẫn soát tại vticket. Tiếp tục mở bán?",
                              )
                            )
                              return;
                            await api(`/admin/ticket-batches/${b.id}/active`, {
                              method: "PUT",
                              body: { active: !b.active },
                            });
                            await load();
                          })
                        }
                      >
                        {b.active ? "Tạm dừng" : "Mở bán"}
                      </button>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </div>
      {codes && (
        <div className="panel">
          <div className="row">
            <div>
              <h2>Mã vé trong lô {codes.batch.code}</h2>
              <p className="muted">
                {codes.total} mã theo bộ lọc. Mỗi trang tối đa 100 mã.
              </p>
              {codes.batch.source === "SMILE" && (
                <p>
                  Smile: {codes.batch.smileTicketTypeCode} ·{" "}
                  {codes.batch.smileTicketTypeName}
                  <br />
                  <span className="muted">
                    Đã lưu thông tin mã; chưa đồng bộ Smile.
                  </span>
                </p>
              )}
            </div>
            <button className="secondary" onClick={() => setCodes(null)}>
              Đóng danh sách
            </button>
          </div>
          <label className="field">
            Lọc trạng thái
            <select
              value={state}
              onChange={(e) => view(codes.batch.id, e.target.value, 0)}
            >
              <option value="">Tất cả trạng thái</option>
              {Object.entries(labelsStock).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <div className="tablewrap">
            <table>
              <thead>
                <tr>
                  <th>Mã nội bộ</th>
                  <th>SeqNo Smile</th>
                  <th>Seri</th>
                  <th>Barcode</th>
                  <th>Trạng thái</th>
                  <th>Đơn liên quan</th>
                  <th>Thời điểm</th>
                </tr>
              </thead>
              <tbody>
                {codes.items.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <code>{c.code}</code>
                    </td>
                    <td>
                      <code>{c.smileSeqNo || "—"}</code>
                    </td>
                    <td>
                      <code>{c.smileSerial || "—"}</code>
                    </td>
                    <td>
                      <code>{c.smileBarcode || "—"}</code>
                    </td>
                    <td>
                      <span className="badge">{labelsStock[c.state]}</span>
                    </td>
                    <td className="break">
                      {c.soldOrderId || c.reservedOrderId || "—"}
                    </td>
                    <td>
                      {c.usedAt
                        ? "Đã dùng: " +
                          new Date(c.usedAt).toLocaleString("vi-VN")
                        : c.soldAt
                          ? "Đã bán: " +
                            new Date(c.soldAt).toLocaleString("vi-VN")
                          : c.reservedUntil
                            ? "Giữ đến: " +
                              new Date(c.reservedUntil).toLocaleString("vi-VN")
                            : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="tabs">
            <button
              className="secondary"
              disabled={busy || codes.page === 0}
              onClick={() => view(codes.batch.id, state, codes.page - 1)}
            >
              ← Trước
            </button>
            <span>
              Trang {codes.totalPages ? codes.page + 1 : 0}/{codes.totalPages}
            </span>
            <button
              className="secondary"
              disabled={busy || codes.page + 1 >= codes.totalPages}
              onClick={() => view(codes.batch.id, state, codes.page + 1)}
            >
              Sau →
            </button>
          </div>
        </div>
      )}
    </>
  );
}

function ProductForm({ product, products, busy, close, save, catalogType }) {
  const [kind, K] = useState(
      product?.kind || (catalogType === "services" ? "SERVICE" : "TICKET"),
    ),
    [ids, I] = useState(product?.includedServiceIds || []);
  return (
    <form
      className="panel"
      onSubmit={(e) => {
        e.preventDefault();
        const f = new FormData(e.currentTarget);
        save({
          code: f.get("code").toUpperCase().trim(),
          name: f.get("name"),
          description: f.get("description"),
          price: Number(f.get("price")),
          kind,
          active: f.get("active") === "on",
          includedServiceIds: kind === "SERVICE" ? [] : ids,
        });
      }}
    >
      <h3>
        {product ? "Chỉnh sửa" : "Tạo"}{" "}
        {catalogType === "services" ? "dịch vụ" : "loại vé"}
      </h3>
      <div className="two-col">
        <Field
          label={
            catalogType === "services"
              ? "Mã dịch vụ"
              : "Mã loại vé (không phải mã vé vào cổng)"
          }
          name="code"
          defaultValue={product?.code}
          pattern="[A-Za-z0-9_-]+"
          maxLength={40}
          required
        />
        <Field
          label="Tên"
          name="name"
          defaultValue={product?.name}
          maxLength={150}
          required
        />
        <label className="field">
          Loại
          <select value={kind} onChange={(e) => K(e.target.value)}>
            {(catalogType === "services"
              ? ["SERVICE"]
              : ["TICKET", "COMBO"]
            ).map((k) => (
              <option value={k} key={k}>
                {labels[k]}
              </option>
            ))}
          </select>
        </label>
        <Field
          label="Giá bán (VND)"
          type="number"
          name="price"
          defaultValue={product?.price || 0}
          min="0"
          max="1000000000"
          step="1"
          required
        />
      </div>
      <Field
        label="Mô tả"
        name="description"
        defaultValue={product?.description}
        maxLength={1000}
      />
      {kind !== "SERVICE" && (
        <>
          <b>Dịch vụ đã bao gồm trong giá vé</b>
          <div className="checks">
            {products
              .filter((p) => p.kind === "SERVICE" && p.active)
              .map((p) => (
                <label key={p.id}>
                  <input
                    type="checkbox"
                    checked={ids.includes(p.id)}
                    onChange={(e) =>
                      I(
                        e.target.checked
                          ? [...ids, p.id]
                          : ids.filter((id) => id !== p.id),
                      )
                    }
                  />
                  {p.name}
                </label>
              ))}
          </div>
        </>
      )}
      <label className="check">
        <input
          type="checkbox"
          name="active"
          defaultChecked={product?.active ?? true}
        />{" "}
        Đang bán
      </label>
      <div className="tabs">
        <button disabled={busy}>Lưu</button>
        <button type="button" className="secondary" onClick={close}>
          Đóng
        </button>
      </div>
    </form>
  );
}
function VoucherForm({ save, busy }) {
  return (
    <form
      className="panel"
      onSubmit={(e) => {
        e.preventDefault();
        const f = new FormData(e.currentTarget);
        save({
          customerName: f.get("customerName"),
          phone: f.get("phone"),
          percentage: Number(f.get("percentage")),
          validFrom: new Date(f.get("validFrom")).toISOString(),
          validUntil: new Date(f.get("validUntil")).toISOString(),
          note: f.get("note"),
        });
      }}
    >
      <h3>Cấp voucher mới</h3>
      <div className="two-col">
        <Field
          label="Tên khách hàng"
          name="customerName"
          maxLength={150}
          required
        />
        <Field
          label="Số điện thoại được sử dụng"
          name="phone"
          type="tel"
          required
        />
        <Field
          label="Giảm trên tổng đơn (%)"
          name="percentage"
          type="number"
          min="1"
          max="100"
          defaultValue="10"
          required
        />
        <Field label="Ghi chú" name="note" maxLength={500} />
        <Field
          label="Có hiệu lực từ (giờ máy đang dùng)"
          name="validFrom"
          type="datetime-local"
          required
        />
        <Field
          label="Hết hạn lúc (giờ máy đang dùng)"
          name="validUntil"
          type="datetime-local"
          required
        />
      </div>
      <button disabled={busy}>Tạo mã & cấp cho khách</button>
    </form>
  );
}
createRoot(document.getElementById("root")).render(<App />);
