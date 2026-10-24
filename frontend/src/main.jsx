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
        {config.demoPayment
          ? "MÔI TRƯỜNG THỬ NGHIỆM · Thanh toán mô phỏng · Không thu tiền thật"
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
function OrderView({ d, config, run, busy, refresh }) {
  const { order: b, tickets } = d;
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
                    <code className="break">{t.qrToken}</code>
                    <p className="muted">{t.includedServices}</p>
                    <span className="badge">
                      {t.checkedInAt ? "Đã check-in" : "Chưa sử dụng"}
                    </span>
                  </div>
                ))}
              </div>
              <button
                className="secondary no-print"
                onClick={() => window.print()}
              >
                In / Lưu PDF vé
              </button>
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
                Hạn thanh toán: {new Date(b.expiresAt).toLocaleString("vi-VN")}
              </p>
              {config.demoPayment ? (
                <button
                  disabled={busy}
                  className="full"
                  onClick={() =>
                    run(async () => {
                      await api(
                        "/public/orders/" + b.accessToken + "/demo-pay",
                        { method: "POST" },
                      );
                      await refresh();
                    })
                  }
                >
                  Thanh toán thử nghiệm
                </button>
              ) : (
                <p>
                  Chưa kết nối cổng thanh toán trực tuyến. Liên hệ quầy để được
                  hỗ trợ.
                </p>
              )}
              <button
                disabled={busy}
                className="secondary full"
                onClick={() =>
                  run(async () => {
                    await api("/public/orders/" + b.accessToken + "/cancel", {
                      method: "POST",
                    });
                    await refresh();
                  })
                }
              >
                Hủy đơn chưa thanh toán
              </button>
            </>
          )}
          {b.status === "PAID" && (
            <p className="success">
              Đã thanh toán ·{" "}
              {b.paymentMethod === "DEMO"
                ? "Giao dịch thử nghiệm"
                : b.paymentMethod}
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
                        Xem vé
                      </button>
                      {b.status === "PENDING" && b.channel === "COUNTER" && (
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
  const [tab, T] = useState("products"),
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
          ["products", "Vé & dịch vụ"],
          ["vouchers", "Voucher khách hàng"],
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
      {tab === "products" && (
        <>
          <div className="row">
            <h2>Vé, combo & dịch vụ đi kèm</h2>
            <button
              onClick={() => {
                E(null);
                F(true);
              }}
            >
              + Tạo mới
            </button>
          </div>
          {show && (
            <ProductForm
              key={editing?.id || "new"}
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
                  <th>Mã / Tên</th>
                  <th>Loại</th>
                  <th>Giá</th>
                  <th>Dịch vụ bao gồm</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {products.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <b>{p.name}</b>
                      <small>{p.code}</small>
                    </td>
                    <td>{labels[p.kind]}</td>
                    <td>{money(p.price)}</td>
                    <td>
                      {p.includedServiceIds
                        .map((id) => products.find((s) => s.id === id)?.name)
                        .join(", ") || "—"}
                    </td>
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
function ProductForm({ product, products, busy, close, save }) {
  const [kind, K] = useState(product?.kind || "TICKET"),
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
      <h3>{product ? "Chỉnh sửa" : "Tạo"} vé / dịch vụ</h3>
      <div className="two-col">
        <Field
          label="Mã"
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
            {["TICKET", "COMBO", "SERVICE"].map((k) => (
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
