import { priceForDate, dayType } from "./combo-pricing.js";
import { comboQuantityError, comboTicketCount } from "./combo.js";
import { parseSmileCodes } from "./smile-import.js";
import React, { useState, useEffect, useRef } from "react";
import { createRoot } from "react-dom/client";
import { api, login, money, today, labels } from "./api";
import { rememberStaffPage, restoredStaffPage, forgetStaffPage } from "./auth-navigation.js";
import "./style.css";
import { createQrAutoSubmit } from "./qr-auto-submit.js";
import Monthly, { MonthlyCard } from "./monthly.jsx";
function Field({ label, ...props }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}
function PriceDayBadge({ product, date, holidays }) {
  if (product.kind !== "TICKET" && product.kind !== "COMBO") return null;
  const type = dayType(date, holidays);
  const specialPrice = type === "HOLIDAY" ? (product.child?product.childHolidayPrice:product.holidayPrice) : type === "WEEKEND" ? (product.child?product.childWeekendPrice:product.weekendPrice) : null;
  if (specialPrice == null) return null;
  const label = type === "HOLIDAY" ? "Giá lễ Tết" : "Giá cuối tuần";
  const description = `${label} theo ngày sử dụng ${date.split("-").reverse().join("/")}. Giá ngày thường: ${money(product.child?product.childPrice:product.price)}.`;
  return <span className={`price-day-badge ${type.toLowerCase()}`} title={description} aria-label={description}>
    <svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18m-12 5 2 2 4-4"/></svg>
    {label}
  </span>;
}
function ProductDetailsModal({ product, date, holidays = [], close }) {
  const ref=useRef(null);
  useEffect(()=>{
    const dialog=ref.current, previous=document.activeElement, overflow=document.body.style.overflow;
    if(!dialog.open)dialog.showModal();document.body.style.overflow="hidden";
    return ()=>{document.body.style.overflow=overflow;previous?.focus?.();};
  },[]);
  const [imageFailed,setImageFailed]=useState([]);
  const images=(product.detailImageUrls || [product.detailImageUrl]).filter(Boolean).filter(url=>!imageFailed.includes(url));
  const price=priceForDate(product,date,holidays);
  return <dialog ref={ref} className="product-detail-dialog" aria-labelledby="product-detail-title" onCancel={close} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)close();}}}>
    <button type="button" className="product-detail-close secondary" aria-label="Đóng chi tiết" autoFocus onClick={close}>×</button>
    <div className="product-detail-grid">
      <div className="product-detail-image">
        {images.length ? images.map((url,index)=><img key={url} src={url} alt={`${product.name} — ảnh ${index+1}`} onError={()=>setImageFailed(previous=>[...previous,url])} />) : <div className="product-detail-placeholder"><img src="/venusgiti-logo.svg" alt="Venusgiti" /><span>{product.name}</span></div>}
      </div>
      <div className="product-detail-copy">
        <span className="eyebrow">{product.kind==="COMBO"?"COMBO ƯU ĐÃI":"VÉ TRẢI NGHIỆM"}{product.child?" · TRẺ EM":""}</span>
        <h2 id="product-detail-title">{product.name}</h2>
        <div className="price-with-day"><b className="price">{money(price)}{product.kind==="COMBO"?" / combo":" / vé"}</b><PriceDayBadge product={product} date={date} holidays={holidays} /></div>
        <p className="muted">Ngày sử dụng: {date.split("-").reverse().join("/")}</p>
        {product.kind==="COMBO" && <p className="product-detail-combo">Mỗi nhóm mua {product.comboBuyQuantity} vé{product.comboGiftQuantity?`, tặng ${product.comboGiftQuantity} vé`:""}.</p>}
        <div className="product-detail-description">{product.detailContent?.trim() || product.description?.trim() || "Thông tin chi tiết đang được cập nhật. Vui lòng liên hệ quầy để được hỗ trợ."}</div>
      </div>
    </div>
  </dialog>;
}

function App() {
  const [page, P] = useState("shop"),
    [user, U] = useState(null),
    [products, C] = useState([]),
    [config, G] = useState({}),
    [error, E] = useState(""),
    [busy, B] = useState(false),
    [detail, D] = useState(null),
    [restoring, setRestoring] = useState(true);
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
    let active = true;
    Promise.allSettled([
      api("/public/config"),
      api("/public/products"),
      api("/me", { silentUnauthorized: true }),
    ]).then(([settings, catalog, session]) => {
      if (!active) return;
      if (settings.status === "fulfilled") G(settings.value);
      if (catalog.status === "fulfilled") C(catalog.value);
      if (session.status === "fulfilled") {
        U(session.value);
        P(restoredStaffPage(session.value));
      } else if (session.reason.status !== 401) {
        E("Không kiểm tra được phiên đăng nhập. Vui lòng tải lại trang.");
      }
      if (settings.status === "rejected" || catalog.status === "rejected") {
        E("Không tải được cấu hình hoặc danh mục. Vui lòng tải lại trang.");
      }
      setRestoring(false);
    });
    const expired = () => {
      U(null);
      D(null);
      forgetStaffPage();
      P("login");
      E("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    };
    window.addEventListener("vticket-session-expired", expired);
    return () => {
      active = false;
      window.removeEventListener("vticket-session-expired", expired);
    };
  }, []);
  useEffect(() => {
    if (user) rememberStaffPage(page);
  }, [page, user]);
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
          <img
            className="brand-logo"
            src="/venusgiti-logo.svg"
            alt="Venusgiti"
            width="144"
            height="108"
          />
          <small>HOT SPRING RESOFT SPA</small>
        </button>
        <nav>
          <button onClick={() => go("shop")}>Đặt vé</button>
          <button onClick={() => go("lookup")}>Tra cứu vé</button>
          {user ? (
            <>
              <button onClick={() => go("counter")}>Bán tại quầy</button>
              <button onClick={() => go("monthly")}>Vé tháng</button>
              <button onClick={() => go("checkin")}>Kiểm soát vé</button>
              <button onClick={() => go("orders")}>Đơn hàng</button>
              {admin && <button onClick={() => go("manage")}>Quản trị</button>}
              <button
                disabled={busy}
                onClick={() => run(async () => {
                  await api("/auth/logout", { method: "POST" });
                  forgetStaffPage();
                  U(null);
                  D(null);
                  go("shop");
                })}
              >
                Đăng xuất
              </button>
            </>
          ) : (
            <button disabled={restoring} onClick={() => go("login")}>Nhân viên</button>
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
        {(busy || restoring) && (
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
                  U(await login(f.get("username"), f.get("password")));
                  P("orders");
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
        {page === "monthly" && user && <Monthly products={products} admin={admin} run={run} busy={busy} open={open} />}
        {page === "checkin" && user && <CheckIn run={run} busy={busy} open={open} />}
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
  const [viewDetails, setViewDetails] = useState(null);
  const [calendar, setCalendar] = useState(null);
  const [calendarError, setCalendarError] = useState("");
  const year = date.slice(0,4);
  useEffect(() => {
    let active=true;setCalendarError("");
    api(`/public/holidays?year=${year}`).then(days=>{if(active)setCalendar({year,days});}).catch(()=>{if(active)setCalendarError("Không tải được lịch giá. Vui lòng tải lại trang.");});
    return () => {active=false;};
  }, [year]);
  const [childModes, setChildModes] = useState({});
  const isChild=p=>p.child ?? (counter && !!childModes[p.id] && p.kind!=="SERVICE");
  const key=p=>isChild(p)?`${p.id}:child`:String(p.id);
  const calendarReady=calendar?.year===year&&!calendarError;
  const effectivePrice=p=>priceForDate({...p,child:isChild(p)},date,calendarReady?calendar.days:[]);
  const selected = products.flatMap(p => [{...p,child:false},...(counter&&p.kind!=="SERVICE"?[{...p,child:true}]:[])]).filter((p) => qty[key(p)]),
    subtotal = selected.reduce((s, p) => s + effectivePrice(p) * qty[key(p)], 0);
  const quantityErrors = selected
    .map((p) => comboQuantityError(p, qty[key(p)]))
    .filter(Boolean);
  if (
    selected
      .filter((p) => p.kind !== "SERVICE")
      .reduce((sum, p) => sum + comboTicketCount(p, qty[key(p)]), 0) > 1000
  )
    quantityErrors.push(
      "Tổng số vé nhận, gồm vé tặng, tối đa 1.000 vé mỗi đơn.",
    );
  const change = (id, n) => Q({ ...qty, [id]: Math.max(0, Math.min(1000, n)) });
  const quantity = (p) => (
    <div className="qty">
      <button
        type="button"
        className="secondary"
        aria-label={"Giảm " + p.name}
        onClick={() =>
          change(
            key(p),
            (qty[key(p)] || 0) -
              (p.kind === "COMBO" ? p.comboBuyQuantity || 1 : 1),
          )
        }
      >
        −
      </button>
      <input
        type="number"
        min="0"
        max="1000"
        step="1"
        aria-label={"Số vé " + p.name}
        value={qty[key(p)] || 0}
        onChange={(e) => change(key(p), Number(e.target.value))}
      />
      <button
        type="button"
        className="secondary"
        aria-label={"Tăng " + p.name}
        onClick={() =>
          change(
            key(p),
            (qty[key(p)] || 0) +
              (p.kind === "COMBO" ? p.comboBuyQuantity || 1 : 1),
          )
        }
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
          <div className="hero-video">
            <iframe
              src="https://www.youtube.com/embed/wg8SBmA23cY?autoplay=1&mute=1&loop=1&playlist=wg8SBmA23cY&playsinline=1&rel=0"
              title="Video giới thiệu Venusgiti"
              allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
              allowFullScreen
              referrerPolicy="strict-origin-when-cross-origin"
            />
          </div>
        </section>
      ) : (
        <>
          <span className="eyebrow">QUẦY LỄ TÂN</span>
          <h1>Bán vé tại quầy</h1>
        </>
      )}
      {viewDetails && <ProductDetailsModal product={viewDetails} date={date} holidays={calendarReady?calendar.days:[]} close={()=>setViewDetails(null)} />}
      <form
        className="layout"
        onSubmit={(e) => {
          e.preventDefault();
          if (!calendarReady) return;
          run(async () => {
            if (quantityErrors.length) throw new Error(quantityErrors[0]);
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
                    quantity: qty[key(p)],
                    ...(counter ? {child: !!p.child} : {}),
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
              Vé có hiệu lực trong ngày đã chọn. Chưa áp dụng hạn mức. Giá trẻ em chỉ bán tại quầy.
            </p>
          </div>
          {[
            { kind: "TICKET", title: "Vé" },
            { kind: "COMBO", title: "Combo ưu đãi" },
          ].map((group) => (
            <section key={group.kind}>
              <h2>{group.title}</h2>
              <div className="cards">
                {products
                  .filter((p) => p.kind === group.kind)
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
                        {p.kind === "COMBO" && (
                          <p className="muted">
                            Áp dụng vé:{" "}
                            {products.find((t) => t.id === p.comboTicketId)
                              ?.name || "Chưa cấu hình"}
                            <br />
                            Mua {p.comboBuyQuantity} vé
                            {p.comboGiftQuantity
                              ? ` tặng ${p.comboGiftQuantity} vé`
                              : ""}
                            . Nhập số vé là bội số của {p.comboBuyQuantity}.{" "}
                            {qty[key(p)] > 0 &&
                              `Bạn nhận ${comboTicketCount(p, qty[key(p)])} vé (gồm vé tặng).`}
                          </p>
                        )}
                        {counter && <label className="field">Đối tượng
                          <select value={isChild(p)?"CHILD":"ADULT"} onChange={e=>setChildModes({...childModes,[p.id]:e.target.value==="CHILD"})}>
                            <option value="ADULT">Người lớn</option>
                            <option value="CHILD" disabled={p.childPrice==null}>Trẻ em{p.childPrice==null?" (chưa có giá)":""}</option>
                          </select>
                        </label>}
                        <div className="price-with-day">
                          <b className="price">
                            {money(effectivePrice(p))}
                            {p.kind === "COMBO" ? " / combo" : " / vé"}
                          </b>
                          {calendarReady && <PriceDayBadge product={{...p,child:isChild(p)}} date={date} holidays={calendar.days} />}
                        </div>
                        {quantity(p)}
                        <button type="button" className="product-details-link" onClick={()=>setViewDetails({...p,child:isChild(p)})}>Xem chi tiết <span aria-hidden="true">→</span></button>
                      </div>
                    </article>
                  ))}
              </div>
            </section>
          ))}
          <section className="panel">
            <h2>Dịch vụ mua thêm</h2>
            {products
              .filter((p) => p.kind === "SERVICE")
              .map((p) => (
                <div className="service row" key={p.id}>
                  <div>
                    <b>{p.name}</b>
                    <p className="muted">{money(effectivePrice(p))} / lượt</p>
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
                ["phone", "Số điện thoại (không bắt buộc)", "tel"],
                ["email", "Email (không bắt buộc)", "email"],
              ].map(([k, l, t]) => (
                <Field
                  key={k}
                  label={l}
                  type={t}
                  value={customer[k]}
                  required={k === "customerName" || (k === "phone" && !!customer.voucherCode.trim())}
                  maxLength={k === "email" ? 254 : k === "phone" ? 20 : 150}
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
            <div className="row summaryline" key={key(p)}>
              <span>
                {qty[key(p)]} × {p.name}{counter ? (p.child ? " · Trẻ em" : p.kind!=="SERVICE" ? " · Người lớn" : "") : ""}
              </span>
              <b>{money(effectivePrice(p) * qty[key(p)])}</b>
            </div>
          ))}
          <div className="row total">
            <span>Tổng trước ưu đãi</span>
            <b>{money(subtotal)}</b>
          </div>
          <p className="muted">
            Số tiền sau giảm hiển thị ở bước xác nhận đơn.
          </p>
          {calendarError && <p className="alert">{calendarError}</p>}
          {!calendarReady && !calendarError && <p>Đang tải lịch giá…</p>}
          {calendarReady && <p className="muted">Giá vé và combo theo ngày sử dụng: {{WEEKDAY:"Ngày thường",WEEKEND:"Cuối tuần",HOLIDAY:"Lễ Tết"}[dayType(date,calendar.days)]}.</p>}
          {quantityErrors.map((error) => (
            <p className="alert" key={error}>
              {error}
            </p>
          ))}
          <button
            className="full"
            disabled={busy || !calendarReady || !subtotal || quantityErrors.length > 0}
          >
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
  const { order: b, tickets, payment, emailDelivery } = d;
  const [pollError, setPollError] = useState(false);
  const expired = Date.parse(b.expiresAt) <= Date.now();
  useEffect(() => {
    setPollError(false);
    if (b.status !== "PENDING" && !["PENDING", "SENDING", "RETRY"].includes(emailDelivery?.state)) return;
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
  }, [b.accessToken, b.status, emailDelivery?.state]);
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
                        {l.name}{l.child && <small>Trẻ em</small>}
                        <small>{l.includedServices}</small>
                      </td>
                      <td>
                        {l.quantity}
                        {l.kind === "COMBO" && (
                          <small>
                            Cấp {l.ticketQuantity ?? l.quantity} vé, gồm vé tặng
                          </small>
                        )}
                      </td>
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
          {d.monthlyVisit && <section className="panel"><h2>Vé lượt từ thẻ tháng {d.monthlyVisit.tier}</h2><p>Lượt hôm nay đã được cấp. Xem hoặc in lại vé không tạo lượt mới.</p>{d.monthlyVisit.physicalMealIssued&&<p><b>Đã ghi nhận phát 1 phiếu ăn vật lý cho lượt Platinum này. Không phát thêm khi quét hoặc in lại.</b></p>}</section>}
          {d.monthlyPass && <MonthlyCard pass={d.monthlyPass} token={b.accessToken} busy={busy} run={run} />}
          {tickets.length > 0 && (
            <>
              <h2>Vé điện tử ({tickets.length})</h2>
              <div className="cards ticketcards">
                {tickets.slice(0, 20).map((t) => (
                  <div className="panel ticket" key={t.id}>
                    <b className="brandtext">♨ Venusgiti</b>
                    <h3>{t.productName}{t.child && <small>Trẻ em</small>}</h3>
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
                        Barcode:{" "}
                        <code>{t.smileGateBarcode || t.smileBarcode}</code>
                      </p>
                    )}
                    {t.source === "SMILE" && (
                      <p>
                        Seri Smile: <b>{t.smileSerial}</b>
                        <br />
                        Barcode Smile:{" "}
                        <code>{t.smileGateBarcode || t.smileBarcode}</code>
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
              {tickets.length > 20 && (
                <p className="muted">
                  Xem trước 20 / {tickets.length} vé. ZIP chứa toàn bộ vé, mỗi
                  file PDF có Seri, barcode và QR riêng để gửi cho khách mua
                  lại. Ngày sử dụng giữ theo đơn đã đặt.
                </p>
              )}
              {b.email && (
                <p className="muted">
                  {emailDelivery?.state === "SENT"
                    ? `Vé đã được gửi qua máy chủ email đến ${b.email}. Kiểm tra cả thư rác nếu chưa thấy thư.`
                    : ["PENDING", "SENDING", "RETRY"].includes(emailDelivery?.state)
                      ? `Vé đang chờ gửi email đến ${b.email}. Bạn có thể tải vé ngay bên dưới.`
                      : emailDelivery?.state === "FAILED"
                        ? "Gửi email chưa thành công. Vé đã thanh toán vẫn có thể tải bên dưới; vui lòng liên hệ quầy nếu cần hỗ trợ."
                        : "Bạn có thể tải vé tại đây và lưu mã tra cứu để mở lại đơn."}
                </p>
              )}
              <div className="tabs no-print">
                <a
                  className="button"
                  href={`/api/public/orders/${b.accessToken}/tickets.zip`}
                  download
                >
                  Tải ZIP · {tickets.length} vé PDF riêng
                </a>
                <a
                  className="button"
                  href={`/api/public/orders/${b.accessToken}/tickets.pdf`}
                  download
                >
                  Tải PDF gộp ({tickets.length} vé)
                </a>
                {tickets.length <= 20 && (
                  <button className="secondary" onClick={() => window.print()}>
                    In vé
                  </button>
                )}
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
function CheckIn({ run, busy, open }) {
  const [token, T] = useState(""), [result, R] = useState(null);
  const inputRef=useRef(null), tokenRef=useRef(""), inFlight=useRef(false), submitRef=useRef(null), autoRef=useRef(null);
  if(!autoRef.current)autoRef.current=createQrAutoSubmit(code=>submitRef.current?.(code));
  async function submitCode(value){
    const code=value.trim();
    autoRef.current.cancel();
    if(!code||busy||inFlight.current)return;
    inFlight.current=true;R(null);
    try{
      await run(async()=>{
        const next=await api("/staff/scan",{method:"POST",body:{qrToken:code}});
        tokenRef.current="";T("");R(next);
      });
    }finally{inFlight.current=false;}
  }
  submitRef.current=submitCode;
  useEffect(()=>()=>autoRef.current.cancel(),[]);
  useEffect(()=>{if(!busy)inputRef.current?.focus();},[busy]);
  return <>
    <section className="panel narrow">
      <span className="eyebrow">KIỂM SOÁT VÉ</span>
      <h1>Quét vé / thẻ tháng</h1>
      <p>Quét bằng máy đọc QR USB hoặc nhập mã. Vé lượt sẽ ghi nhận check-in trên VTicket; thẻ tháng sẽ mở thông tin để xác nhận cấp vé lượt.</p>
      <form onSubmit={e=>{e.preventDefault();submitCode(tokenRef.current);}}>
        <Field label="Mã QR vé / thẻ tháng" value={token} ref={inputRef} onChange={e=>{
          const value=e.target.value;tokenRef.current=value;T(value);R(null);autoRef.current.update(value);
        }} disabled={busy} autoFocus required maxLength={64} autoComplete="off"/>
        <p className="muted">Quét xong mã QR, hệ thống tự kiểm tra. Bạn vẫn có thể nhấn Enter hoặc nút bên dưới khi nhập mã thủ công.</p>
        <button disabled={busy} className="full">Kiểm tra mã</button>
      </form>
      {result?.type==="TICKET"&&<div className="success">
        <h3>✓ Check-in vé lượt thành công trên VTicket</h3>
        {result.ticket.productName}<p>{result.ticket.includedServices}</p>
        <small>{new Date(result.ticket.checkedInAt).toLocaleString("vi-VN")}</small>
      </div>}
      {result?.type==="MONTHLY_PASS"&&<p>Đã nhận diện thẻ tháng. Đối chiếu thông tin khách và xác nhận bên dưới để nhận vé lượt; thao tác quét thẻ chưa trừ lượt.</p>}
    </section>
    {result?.type==="MONTHLY_PASS"&&<MonthlyCard key={result.monthlyPass.bookingId} pass={result.monthlyPass} busy={busy} run={run} open={open}/>}
  </>;
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
function HolidayManagement({ run, busy }) {
  const [year, setYear] = useState(new Date().getFullYear());
  const [calendar, setCalendar] = useState(null);
  const [days, setDays] = useState([]);
  const [saved, setSaved] = useState(false);
  useEffect(() => {
    let active=true;setCalendar(null);setSaved(false);setDays([]);
    run(async () => {const rows=await api(`/admin/holidays?year=${year}`);if(active){setDays(rows);setCalendar(year);}});
    return () => {active=false;};
  }, [year]);
  return <section className="panel">
    <h2>Lịch lễ Tết theo năm</h2>
    <p className="muted">Thứ Bảy và Chủ nhật áp dụng giá cuối tuần. Các ngày trong lịch này ưu tiên giá lễ Tết, kể cả khi trùng cuối tuần. Lịch 2027 đã được nạp theo danh sách cung cấp.</p>
    <label className="field">Năm<select value={year} disabled={busy} onChange={e=>setYear(Number(e.target.value))}>{Array.from({length:101},(_,i)=>2000+i).map(y=><option key={y} value={y}>{y}</option>)}</select></label>
    {calendar===year && <form onSubmit={e=>{e.preventDefault();setSaved(false);run(async()=>{const rows=await api(`/admin/holidays/${year}`,{method:"PUT",body:{days}});setDays(rows);setSaved(true);});}}>
      {days.length===0 && <p>Chưa có ngày lễ trong năm này. Bạn có thể thêm bên dưới.</p>}
      {days.map((day,i)=><div className="row" key={i}>
        <Field label="Ngày lễ" type="date" required min={`${year}-01-01`} max={`${year}-12-31`} value={day.date} onChange={e=>{setDays(days.map((d,j)=>j===i?{...d,date:e.target.value}:d));setSaved(false);}} />
        <Field label="Tên ngày lễ" required maxLength={150} value={day.name} onChange={e=>{setDays(days.map((d,j)=>j===i?{...d,name:e.target.value}:d));setSaved(false);}} />
        <button type="button" className="secondary" disabled={busy} onClick={()=>{setDays(days.filter((_,j)=>j!==i));setSaved(false);}}>Bỏ ngày</button>
      </div>)}
      <button type="button" className="secondary" disabled={busy||days.length>=366} onClick={()=>{setDays([...days,{date:"",name:""}]);setSaved(false);}}>+ Thêm ngày lễ</button>{" "}
      <button disabled={busy}>Lưu lịch năm {year}</button>
      {saved && <p role="status">Đã lưu lịch lễ Tết năm {year}.</p>}
      <p className="muted">Thay đổi lịch áp dụng cho đơn tạo mới. Đơn đã tạo giữ nguyên giá đã chốt. Chuyển năm trước khi lưu sẽ bỏ các thay đổi chưa lưu.</p>
    </form>}
  </section>;
}

function UserManagement({ run, busy }) {
  const [users, setUsers] = useState([]);
  const [editing, setEditing] = useState(null);
  const [resetting, setResetting] = useState(null);
  const [creating, setCreating] = useState(false);
  const load = async () => setUsers(await api("/admin/users"));
  useEffect(() => { run(load); }, []);
  const roleLabel = (role) => role === "ADMIN" ? "Quản trị viên" : "Nhân viên";
  return <section className="panel">
    <div className="row"><h2>Quản lý người dùng</h2><button disabled={busy} onClick={() => {setCreating(true);setEditing(null);setResetting(null);}}>+ Tạo tài khoản</button></div>
    <p className="muted">Nhân viên được bán vé tại quầy, kiểm soát vé và xem đơn hàng. Quản trị viên có thêm quyền quản lý hệ thống và tài khoản.</p>
    {(creating || editing) && <form key={editing?.id || "create"} onSubmit={e => {
      e.preventDefault();const form=e.currentTarget;const f=new FormData(form);
      run(async () => {
        const body={displayName:f.get("displayName"),role:f.get("role")};
        if(editing) body.enabled=f.get("enabled")==="on";
        else {body.username=f.get("username");body.password=f.get("password");}
        await api(editing ? `/admin/users/${editing.id}` : "/admin/users",{method:editing?"PUT":"POST",body});
        form.reset();setCreating(false);setEditing(null);await load();
      });
    }}>
      <h3>{editing ? `Cập nhật ${editing.username}` : "Tạo tài khoản mới"}</h3>
      <div className="two-col">
        {!editing && <Field label="Tên đăng nhập" name="username" required minLength={3} maxLength={100} pattern="[A-Za-z0-9][A-Za-z0-9._@\-]{2,99}" autoComplete="off" />}
        <Field label="Họ và tên" name="displayName" required maxLength={150} defaultValue={editing?.displayName || ""} />
        {!editing && <Field label="Mật khẩu (ít nhất 12 ký tự, tối đa 72 byte UTF-8)" name="password" type="password" required minLength={12} maxLength={72} autoComplete="new-password" />}
        <label>Quyền<select name="role" defaultValue={editing?.role || "STAFF"}><option value="STAFF">Nhân viên</option><option value="ADMIN">Quản trị viên</option></select></label>
      </div>
      {editing && <label><input type="checkbox" name="enabled" defaultChecked={editing.enabled} /> Cho phép đăng nhập</label>}
      <p className="muted">Khóa tài khoản hoặc đổi quyền sẽ kết thúc các phiên đăng nhập hiện có. Không thể tự khóa hoặc hạ quyền tài khoản đang dùng.</p>
      <button disabled={busy}>Lưu tài khoản</button>{" "}<button type="button" className="secondary" disabled={busy} onClick={() => {setCreating(false);setEditing(null);}}>Hủy</button>
    </form>}
    {resetting && <form key={`password-${resetting.id}`} onSubmit={e => {
      e.preventDefault();const form=e.currentTarget;const password=new FormData(form).get("password");
      run(async () => {await api(`/admin/users/${resetting.id}/password`,{method:"POST",body:{password}});form.reset();setResetting(null);await load();});
    }}>
      <h3>Đặt lại mật khẩu: {resetting.username}</h3>
      <Field label="Mật khẩu mới (ít nhất 12 ký tự, tối đa 72 byte UTF-8)" type="password" name="password" required minLength={12} maxLength={72} autoComplete="new-password" />
      <p className="muted">Các phiên hiện tại của tài khoản này sẽ hết hiệu lực và cần đăng nhập lại.</p>
      <button disabled={busy}>Đặt lại mật khẩu</button>{" "}<button type="button" className="secondary" disabled={busy} onClick={() => setResetting(null)}>Hủy</button>
    </form>}
    <div className="table-wrap"><table><thead><tr><th>Tên đăng nhập</th><th>Họ và tên</th><th>Quyền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
      {users.map(u => <tr key={u.id}><td>{u.username}</td><td>{u.displayName}</td><td>{roleLabel(u.role)}</td><td>{u.enabled ? "Hoạt động" : "Đã khóa"}</td><td><button className="secondary" disabled={busy} onClick={() => {setEditing(u);setCreating(false);setResetting(null);}}>Sửa / Khóa, mở</button>{" "}<button className="secondary" disabled={busy} onClick={() => {setResetting(u);setEditing(null);setCreating(false);}}>Đặt lại mật khẩu</button></td></tr>)}
    </tbody></table></div>
  </section>;
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
          ["combos", "Quản lý combo"],
          ["services", "Dịch vụ đi kèm"],
          ["batches", "Lô vé & mã vé"],
          ["vouchers", "Voucher khách hàng"],
          ["receipts", "Đối soát VietQR"],
          ["smile", "Smile Onsen"],
          ["holidays", "Lịch lễ Tết"],
          ["users", "Người dùng"],
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
      {["tickets", "combos", "services"].includes(tab) && (
        <>
          <div className="row">
            <h2>
              {tab === "services"
                ? "Dịch vụ đi kèm"
                : tab === "combos"
                  ? "Danh mục combo"
                  : "Danh mục loại vé"}
            </h2>
            <button
              onClick={() => {
                E(null);
                F(true);
              }}
            >
              {tab === "services"
                ? "+ Tạo dịch vụ"
                : tab === "combos"
                  ? "+ Tạo combo"
                  : "+ Tạo loại vé"}
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
                  {tab === "combos" && <th>Vé tham chiếu / Điều kiện</th>}
                  {tab !== "services" && <th>Dịch vụ bao gồm</th>}
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {products
                  .filter(
                    (p) =>
                      p.kind ===
                      (tab === "services"
                        ? "SERVICE"
                        : tab === "combos"
                          ? "COMBO"
                          : "TICKET"),
                  )
                  .map((p) => (
                    <tr key={p.id}>
                      <td>
                        <b>{p.name}</b>
                        <small>{p.code}</small>
                      </td>
                      <td>{labels[p.kind]}</td>
                      <td>
                        {money(p.price)}
                        {p.kind !== "SERVICE" && <><small>{p.kind === "COMBO" ? "/ combo · Ngày thường" : "/ vé · Ngày thường"}</small><small>Cuối tuần: {money(p.weekendPrice ?? p.price)}</small><small>Lễ Tết: {money(p.holidayPrice ?? p.price)}</small></>}
                      </td>
                      {tab === "combos" && (
                        <td>
                          {products.find((t) => t.id === p.comboTicketId)
                            ?.name || "Chưa cấu hình"}
                          <small>
                            Mua {p.comboBuyQuantity || "?"}, tặng{" "}
                            {p.comboGiftQuantity || 0}
                          </small>
                        </td>
                      )}
                      {tab !== "services" && (
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
      {tab === "holidays" && <HolidayManagement run={run} busy={busy} />}
      {tab === "users" && <UserManagement run={run} busy={busy} />}
      {tab === "batches" && (
        <BatchManager products={products} run={run} busy={busy} />
      )}
      {tab === "receipts" && <PaymentReceipts run={run} />}
      {tab === "smile" && <SmilePanel run={run} busy={busy} />}
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
    [smileText, setSmileText] = useState(""),
    [smileStatus, setSmileStatus] = useState(null),
    [batchProductId, setBatchProductId] = useState("");
  const batchProduct = products.find((p) => String(p.id) === batchProductId);
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
    run(async () => {
      setBatches(await api("/admin/ticket-batches"));
      setSmileStatus(await api("/admin/smile/status"));
    });
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
              } else {
                body.quantity = Number(f.get("quantity"));
              }
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
              Loại vé
              <select name="productId" required value={batchProductId} onChange={(e) => setBatchProductId(e.target.value)}>
                <option value="" disabled>
                  Chọn loại vé
                </option>
                {products
                  .filter((p) => p.kind === "TICKET")
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
          {source === "VTICKET" && (
            <>
              <div className="two-col">
                <Field
                  label="PkgCode trên Smile"
                  name="internalSmilePkg"
                  value={batchProduct?.code || ""}
                  readOnly
                />
                <Field
                  label="Tên loại vé trên Smile"
                  name="internalSmileName"
                  value={batchProduct?.name || ""}
                  readOnly
                />
              </div>
              <p className="muted">
                {smileStatus?.enabled
                  ? "Lô mới sẽ được ghi ngay sang Smile và tạm dừng bán. Khi đồng bộ thành công, kiểm tra rồi mở bán lô."
                  : "Mã và tên lấy từ loại vé đã chọn. Mã loại vé phải trùng mã trên Smile."}
              </p>
            </>
          )}
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
                      {b.smileSyncState && (
                        <small>Smile: {b.smileSyncState}</small>
                      )}
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
          {codes.batch.source === "VTICKET" && smileStatus?.enabled && (
            <form
              className="panel"
              key={codes.batch.id + "-smile"}
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                run(async () => {
                  await api(`/admin/smile/batches/${codes.batch.id}/sync`, {
                    method: "POST",
                    body: {
                      pkgCode: f.get("pkgCode").trim(),
                      typeName: f.get("typeName").trim(),
                    },
                  });
                  await load();
                  await view(codes.batch.id, state, codes.page);
                });
              }}
            >
              <h3>Đồng bộ lô sang Smile</h3>
              <p>
                Trạng thái: {codes.batch.smileSyncState || "Chưa đồng bộ"}. Chỉ
                áp dụng cho lô chưa giữ/bán/dùng mã. Đồng bộ xong, kiểm tra và
                bấm Mở bán.
              </p>
              <div className="two-col">
                <Field
                  name="pkgCode"
                  label="PkgCode Smile"
                  defaultValue={codes.batch.smileTicketTypeCode || ""}
                  pattern="[A-Za-z0-9._/-]+"
                  maxLength={64}
                  required
                  readOnly={!!codes.batch.smileSyncState}
                />
                <Field
                  name="typeName"
                  label="Tên loại vé Smile"
                  defaultValue={codes.batch.smileTicketTypeName || ""}
                  maxLength={150}
                />
              </div>
              <button
                disabled={
                  busy ||
                  ["SYNCED", "SYNCING"].includes(codes.batch.smileSyncState)
                }
              >
                Xếp hàng / thử lại đồng bộ
              </button>
            </form>
          )}
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
                  <th>Barcode số nội bộ</th>
                  <th>Barcode cổng Smile</th>
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
                      <code>{c.smileSerial || (c.smileSerialFromSeq ? "Chờ SeqNo Smile" : "—")}</code>
                    </td>
                    <td>
                      <code>{c.smileBarcode || "—"}</code>
                    </td>
                    <td>
                      <code>
                        {c.smileGateBarcode ||
                          (codes.batch.source === "SMILE"
                            ? c.smileBarcode
                            : "—")}
                      </code>
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
      product?.kind ||
        (catalogType === "services"
          ? "SERVICE"
          : catalogType === "combos"
            ? "COMBO"
            : "TICKET"),
    ),
    [ids, I] = useState(product?.includedServiceIds || []);
  const [detailText,setDetailText]=useState(product?.detailContent || "");
  const [imageData,setImageData]=useState([null,null,null]);
  const [imageReading,setImageReading]=useState(false);
  const [imageInputKey,setImageInputKey]=useState([0,0,0]);
  const [imageError,setImageError]=useState("");
  const storedImages=product?.detailImageUrls || [product?.detailImageUrl];
  const imagePreviews=imageData.map((data,slot)=>data===null ? storedImages[slot]?.replace("/public/","/admin/") : data);
  async function readDetailImage(e,slot){
    const input=e.currentTarget,file=input.files?.[0];if(!file)return;
    setImageError("");
    if(!["image/png","image/jpeg"].includes(file.type)||file.size>2097152){setImageError("Chọn ảnh PNG/JPG tối đa 2 MB.");input.value="";return;}
    setImageReading(true);
    try{const data=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(Error());reader.readAsDataURL(file);});setImageData(previous=>previous.map((old,index)=>index===slot?data:old));}
    catch{setImageError("Không đọc được ảnh. Vui lòng chọn lại.");}
    finally{setImageReading(false);}
  }
  return (
    <form
      className="panel"
      onSubmit={(e) => {
        e.preventDefault();
        if(imageReading)return;
        const f = new FormData(e.currentTarget);
        save({
          code: f.get("code").toUpperCase().trim(),
          name: f.get("name"),
          description: f.get("description"),
          detailContent: kind !== "SERVICE" ? detailText : undefined,
          detailImagesData: kind !== "SERVICE" ? imageData : undefined,
          price: Number(f.get("price")),
          childPrice: kind !== "SERVICE" && f.get("childPrice") !== "" ? Number(f.get("childPrice")) : null,
          childWeekendPrice: kind !== "SERVICE" && f.get("childWeekendPrice") !== "" ? Number(f.get("childWeekendPrice")) : null,
          childHolidayPrice: kind !== "SERVICE" && f.get("childHolidayPrice") !== "" ? Number(f.get("childHolidayPrice")) : null,
          weekendPrice: kind !== "SERVICE" && f.get("weekendPrice") !== "" ? Number(f.get("weekendPrice")) : null,
          holidayPrice: kind !== "SERVICE" && f.get("holidayPrice") !== "" ? Number(f.get("holidayPrice")) : null,
          kind,
          active: f.get("active") === "on",
          includedServiceIds: kind === "SERVICE" ? [] : ids,
          comboTicketId:
            kind === "COMBO" ? Number(f.get("comboTicketId")) : null,
          comboBuyQuantity:
            kind === "COMBO" ? Number(f.get("comboBuyQuantity")) : null,
          comboGiftQuantity:
            kind === "COMBO" ? Number(f.get("comboGiftQuantity")) : null,
        });
      }}
    >
      <h3>
        {product ? "Chỉnh sửa" : "Tạo"}{" "}
        {catalogType === "services"
          ? "dịch vụ"
          : catalogType === "combos"
            ? "combo"
            : "loại vé"}
      </h3>
      <div className="two-col">
        <Field
          label={
            catalogType === "services"
              ? "Mã dịch vụ"
              : "Mã loại vé (nhập đúng mã trên Smile)"
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
              : catalogType === "combos"
                ? ["COMBO"]
                : ["TICKET"]
            ).map((k) => (
              <option value={k} key={k}>
                {labels[k]}
              </option>
            ))}
          </select>
        </label>
        <Field
          label={
            kind === "COMBO"
              ? "Đơn giá ngày thường / một vé mua (VND)"
              : kind === "TICKET" ? "Đơn giá ngày thường (VND)" : "Giá bán (VND)"
          }
          type="number"
          name="price"
          defaultValue={product?.price || 0}
          min="0"
          max="1000000000"
          step="1"
          required
        />
      </div>
      {kind !== "SERVICE" && <>
        <div className="two-col">
          <Field label={kind === "COMBO" ? "Đơn giá cuối tuần / một vé mua (VND)" : "Đơn giá cuối tuần (VND)"} name="weekendPrice" type="number" min="0" max="1000000000" step="1" defaultValue={product?.weekendPrice ?? ""} placeholder="Để trống: dùng giá ngày thường" />
          <Field label={kind === "COMBO" ? "Đơn giá lễ Tết / một vé mua (VND)" : "Đơn giá lễ Tết (VND)"} name="holidayPrice" type="number" min="0" max="1000000000" step="1" defaultValue={product?.holidayPrice ?? ""} placeholder="Để trống: dùng giá ngày thường" />
        </div>
        <h4>Giá trẻ em — chỉ bán tại quầy</h4>
        <div className="two-col">
          <Field label="Giá trẻ em ngày thường (VND)" name="childPrice" type="number" min="0" max="1000000000" step="1" defaultValue={product?.childPrice ?? ""} placeholder="Để trống: không bán giá trẻ em" />
          <Field label="Giá trẻ em cuối tuần (VND)" name="childWeekendPrice" type="number" min="0" max="1000000000" step="1" defaultValue={product?.childWeekendPrice ?? ""} placeholder="Để trống: dùng giá trẻ em ngày thường" />
          <Field label="Giá trẻ em lễ Tết (VND)" name="childHolidayPrice" type="number" min="0" max="1000000000" step="1" defaultValue={product?.childHolidayPrice ?? ""} placeholder="Để trống: dùng giá trẻ em ngày thường" />
        </div>
        <p className="muted">Cuối tuần: thứ Bảy, Chủ nhật. Lễ Tết ưu tiên theo lịch đã cấu hình trong mục Lịch lễ Tết. Giá tính theo ngày sử dụng.{kind === "COMBO" ? " Vé tặng không tính tiền." : ""}</p>
      </>}
      {kind === "COMBO" && (
        <>
          <label className="field">
            Loại vé tham chiếu
            <select
              name="comboTicketId"
              required
              defaultValue={product?.comboTicketId || ""}
            >
              <option value="" disabled>
                Chọn một loại vé
              </option>
              {products
                .filter((p) => p.kind === "TICKET")
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.active ? "" : " (ngừng bán)"}
                  </option>
                ))}
            </select>
          </label>
          <div className="two-col">
            <Field
              label="Số vé phải mua mỗi nhóm"
              name="comboBuyQuantity"
              type="number"
              min="1"
              max="1000"
              step="1"
              defaultValue={product?.comboBuyQuantity || 10}
              required
            />
            <Field
              label="Số vé tặng mỗi nhóm"
              name="comboGiftQuantity"
              type="number"
              min="0"
              max="1000"
              step="1"
              defaultValue={product?.comboGiftQuantity ?? 1}
              required
            />
          </div>
          <p className="muted">
            Hỗ trợ combo đại lý 300/500 vé; tổng vé nhận tối đa 1.000 vé/đơn,
            gồm vé tặng. Ví dụ mua 10 tặng 1, đơn giá 450.000đ: nhập 10 thì thu
            4.500.000đ và cấp 11 vé. Combo lấy cả vé mua và vé tặng từ kho của
            loại vé tham chiếu, không tạo lô riêng.
          </p>
        </>
      )}
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
      {kind!=="SERVICE" && <section className="product-detail-settings">
        <h3>Nội dung popup “Xem chi tiết”</h3>
        <div className="two-col">
          <div>
            <p className="muted">Tối đa 3 ảnh, hiển thị từ trên xuống theo thứ tự 1–2–3.</p>
            {[0,1,2].map(slot=><div className="product-detail-upload-slot" key={slot}>
              <Field key={`${slot}-${imageInputKey[slot]}`} label={`Ảnh ${slot+1} (PNG/JPG, tối đa 2 MB)`} type="file" accept="image/png,image/jpeg" disabled={busy||imageReading} onChange={e=>readDetailImage(e,slot)} />
              {imagePreviews[slot] && <><img className="product-detail-preview" src={imagePreviews[slot]} alt={`Xem trước ảnh chi tiết ${slot+1}`} /><button type="button" className="secondary" disabled={busy||imageReading} onClick={()=>{setImageData(previous=>previous.map((old,i)=>i===slot?"":old));setImageError("");setImageInputKey(previous=>previous.map((old,i)=>i===slot?old+1:old));}}>Bỏ ảnh {slot+1}</button></>}
            </div>)}
            {imageReading && <p>Đang đọc ảnh…</p>}{imageError && <p className="alert" role="alert">{imageError}</p>}
          </div>
          <label className="field">Nội dung chi tiết
            <textarea rows={12} maxLength={12000} value={detailText} onChange={e=>setDetailText(e.target.value)} placeholder={"Các dịch vụ bao gồm…\nĐiều kiện sử dụng…\nLưu ý cho khách hàng…"} />
            <small>{detailText.length}/12.000 ký tự. Mỗi dòng được giữ nguyên trong popup. Nếu để trống, popup dùng mô tả ngắn.</small>
          </label>
        </div>
      </section>}
      <label className="check">
        <input
          type="checkbox"
          name="active"
          defaultChecked={product?.active ?? true}
        />{" "}
        Đang bán
      </label>
      <div className="tabs">
        <button disabled={busy||imageReading}>Lưu</button>
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

function SmilePanel({ run, busy }) {
  const [status, setStatus] = useState(null),
    [jobs, setJobs] = useState([]),
    [check, setCheck] = useState(null);
  const labels = {
    PENDING: "Chờ đồng bộ",
    SYNCING: "Đang ghi sang Smile",
    SYNCED: "Đã đồng bộ",
    FAILED: "Cần kiểm tra / thử lại",
  };
  const load = () =>
    run(async () => {
      setStatus(await api("/admin/smile/status"));
      setJobs(await api("/admin/smile/jobs"));
    });
  useEffect(() => {
    load();
  }, []);
  return (
    <>
      <div className="row">
        <div>
          <h2>Tích hợp Smile Onsen</h2>
          <p>
            Ghi toàn bộ mã ngay khi tạo lô. Không tự thay đổi trạng thái
            bán/soát/hủy bên Smile.
          </p>
        </div>
        <button className="secondary" onClick={load} disabled={busy}>
          Làm mới
        </button>
      </div>
      <div className="panel">
        <p>
          {status?.enabled ? "Tích hợp đã bật" : "Tích hợp đang tắt"} ·{" "}
          {status?.configured
            ? "Đã khai báo kết nối"
            : "Chưa đủ cấu hình kết nối"}
        </p>
        <p>
          Bảng: {status?.schema}.{status?.table}
          <br />
          Ánh xạ:{" "}
          {status?.mapping === "FIELD_NAMES"
            ? "BarCode = mã số; Serial = VN…"
            : "BarCode = VN…; Serial = mã số"}
        </p>
        <button
          disabled={busy || !status?.enabled || !status?.configured}
          onClick={() =>
            run(async () =>
              setCheck(
                await api("/admin/smile/connection-test", { method: "POST" }),
              ),
            )
          }
        >
          Kiểm tra kết nối và cấu trúc bảng
        </button>
        {check && (
          <p className={check.ok ? "muted" : "alert"}>
            {check.ok
              ? "Kết nối và các cột bắt buộc hợp lệ; chưa ghi dữ liệu thử."
              : check.message}
          </p>
        )}
        <p className="muted">
          Lô chờ/lỗi đồng bộ không được mở bán. Vào Lô vé & mã vé → Xem mã để
          kiểm tra PkgCode và thử lại. Sau khi thành công mới chủ động mở bán.
        </p>
      </div>
      <div className="panel tablewrap">
        <table>
          <thead>
            <tr>
              <th>Lô (ID)</th>
              <th>Trạng thái</th>
              <th>Số lần thử</th>
              <th>Thời gian</th>
              <th>Chi tiết</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((j) => (
              <tr key={j.batchId}>
                <td className="break">{j.batchId}</td>
                <td>{labels[j.state] || j.state}</td>
                <td>{j.attempts}</td>
                <td>
                  {new Date(j.syncedAt || j.createdAt).toLocaleString("vi-VN")}
                </td>
                <td>
                  {j.lastError ||
                    (j.state === "SYNCED"
                      ? "Đã ghi/đối chiếu toàn bộ mã"
                      : "Đang chờ xử lý nền")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
