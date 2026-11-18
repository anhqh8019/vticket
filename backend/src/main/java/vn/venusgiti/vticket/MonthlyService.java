package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.MonthlyDomain.*;

/** A paid entitlement owns selected calendar days. A separate zero-value booking consumes stock per visit. */
@Service @Transactional
public class MonthlyService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
 private final MonthlyPlans plans; private final MonthlyPasses passes; private final Products products;
 private final Bookings bookings; private final Tickets tickets; private final InventoryService inventory;
 private final ComboPricing pricing; private final Audits audits; private final Clock clock;
 MonthlyService(MonthlyPlans plans,MonthlyPasses passes,Products products,Bookings bookings,Tickets tickets,InventoryService inventory,ComboPricing pricing,Audits audits,Clock clock){this.plans=plans;this.passes=passes;this.products=products;this.bookings=bookings;this.tickets=tickets;this.inventory=inventory;this.pricing=pricing;this.audits=audits;this.clock=clock;}
 public record PlanInput(@NotNull Long ticketProductId,Long mealProductId,@NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal weekdayPrice,@NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal weekendPrice,@NotNull Boolean active) {}
 public record SaleInput(@NotNull Tier tier,@NotBlank @Size(max=150) String holderName,@Size(max=20) @Pattern(regexp="^$|^\\+?[0-9 ()-]{8,20}$") String phone,@Email @Size(max=254) String email,@NotEmpty @Size(max=31) List<@NotNull LocalDate> dates) {}
 public record RedeemInput(@NotBlank @Size(max=36) String qrToken,@NotNull Boolean holderConfirmed) {}
 public record DayView(LocalDate date,String dayType,BigDecimal price,String issuedBookingId,Instant issuedAt) {}
 public record View(String bookingId,Tier tier,String holderName,String ticketName,String mealName,String qrToken,String status,List<DayView> days,int remaining) {}
 public record Issued(String accessToken,List<Ticket> tickets,boolean alreadyIssued) {}
 private LocalDate today(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));}
 private static void require(boolean ok,String message){BookingService.require(ok,message);}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thẻ/gói vé tháng");}
 private void audit(String actor,String action,String id){var a=new Audit();a.actor=actor;a.action=action;a.target=id;a.happenedAt=clock.instant();audits.save(a);}
 private Product ticket(Long id){var p=products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Không tìm thấy loại vé tham chiếu"));require(p.kind==Kind.TICKET&&p.active,"Loại vé tham chiếu phải là vé đang bán, có kho mã riêng");return p;}
 public List<Plan> plans(){return plans.findAll().stream().sorted(Comparator.comparing(p->p.tier)).toList();}
 public Plan save(Tier tier,PlanInput in,String actor){
  var ids=new ArrayList<Long>();ids.add(in.ticketProductId());if(in.mealProductId()!=null)ids.add(in.mealProductId());inventory.lockProducts(ids);ticket(in.ticketProductId());
  if(tier==Tier.PLATINUM){require(in.mealProductId()!=null&&!in.mealProductId().equals(in.ticketProductId()),"Platinum cần một loại vé ăn riêng, khác vé vào cổng");ticket(in.mealProductId());}
  var p=plans.lockTier(tier).orElseGet(Plan::new);p.tier=tier;p.ticketProductId=in.ticketProductId();p.mealProductId=tier==Tier.PLATINUM?in.mealProductId():null;p.weekdayPrice=in.weekdayPrice();p.weekendPrice=in.weekendPrice();p.active=in.active();plans.save(p);audit(actor,"MONTHLY_PLAN_SAVED",tier.name());return p;
 }
 private Booking booking(String holder,String phone,String email,LocalDate date,String actor){var b=new Booking();b.id=UUID.randomUUID().toString();b.accessToken=UUID.randomUUID().toString();b.customerName=holder.strip();b.phone=BookingService.phone(phone);b.email=email==null||email.isBlank()?null:email.strip();b.visitDate=date;b.channel=Channel.COUNTER;b.status=Status.PENDING;b.createdAt=clock.instant();b.expiresAt=b.createdAt.plusSeconds(900);b.soldBy=actor;b.subtotal=BigDecimal.ZERO;b.discount=BigDecimal.ZERO;b.total=BigDecimal.ZERO;return b;}
 public Booking sell(SaleInput in,String actor){
  var p=plans.findById(in.tier()).orElseThrow(MonthlyService::missing);require(p.active,"Gói vé tháng đã ngừng bán");
  var ids=new ArrayList<Long>();ids.add(p.ticketProductId);if(p.mealProductId!=null)ids.add(p.mealProductId);inventory.lockProducts(ids);
  // Re-read after the product lock: plan edits use the same lock ordering.
  p=plans.lockTier(in.tier()).orElseThrow(MonthlyService::missing);entityManager.refresh(p);require(p.active&&ids.contains(p.ticketProductId)&&(p.mealProductId==null||ids.contains(p.mealProductId)),"Cấu hình gói vừa thay đổi, vui lòng tạo lại");
  var base=ticket(p.ticketProductId);var meal=p.mealProductId==null?null:ticket(p.mealProductId);
  var dates=new TreeSet<>(in.dates());require(dates.size()==in.dates().size(),"Ngày sử dụng không được trùng");require(!dates.first().isBefore(today()),"Không chọn ngày sử dụng đã qua");require(YearMonth.from(dates.first()).equals(YearMonth.from(dates.last())),"Chọn các ngày trong cùng một tháng");
  var b=booking(in.holderName(),in.phone(),in.email(),dates.first(),actor);var pass=new Pass();pass.bookingId=b.id;pass.qrToken=UUID.randomUUID().toString();pass.tier=p.tier;pass.ticketProductId=base.id;pass.ticketName=base.name;pass.mealProductId=meal==null?null:meal.id;pass.mealName=meal==null?null:meal.name;pass.holderName=b.customerName;pass.createdAt=clock.instant();
  for(var date:dates){var d=new Day();d.dayType=pricing.dayType(date);require(p.tier!=Tier.SILVER||d.dayType.equals("WEEKDAY"),"Silver chỉ sử dụng ngày thường, không gồm thứ Bảy, Chủ nhật và lễ Tết");d.price=d.dayType.equals("WEEKDAY")?p.weekdayPrice:p.weekendPrice;pass.days.put(date,d);b.subtotal=b.subtotal.add(d.price);}
  b.total=b.subtotal;var line=new BookingLine();line.productId=base.id;line.name="Vé tháng "+p.tier+" · "+dates.size()+" ngày";line.kind=Kind.SERVICE;line.quantity=1;line.unitPrice=b.total;line.ticketQuantity=0;line.includedServices="Mỗi ngày đã chọn: 1 vé "+base.name+(meal==null?"":" + 1 vé "+meal.name);b.lines.add(line);
  bookings.saveAndFlush(b);passes.save(pass);audit(actor,"MONTHLY_PASS_CREATED",b.id);return b;
 }
 public View view(String bookingId){return passes.findById(bookingId).map(p->view(p,bookings.findById(bookingId).orElseThrow())).orElse(null);}
 private View view(Pass p,Booking b){
  int remaining=(int)p.days.entrySet().stream().filter(e->!e.getKey().isBefore(today())&&e.getValue().issuedBookingId==null).count();
  String state=b.status!=Status.PAID?b.status.name():p.days.keySet().stream().allMatch(d->d.isBefore(today()))?"EXPIRED":remaining==0?"EXHAUSTED":p.days.keySet().stream().allMatch(d->d.isAfter(today()))?"SCHEDULED":"ACTIVE";
  return new View(p.bookingId,p.tier,p.holderName,p.ticketName,p.mealName,b.status==Status.PAID?p.qrToken:null,state,p.days.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new DayView(e.getKey(),e.getValue().dayType,e.getValue().price,e.getValue().issuedBookingId,e.getValue().issuedAt)).toList(),remaining);
 }
 public List<View> list(){return passes.findTop200ByOrderByCreatedAtDesc().stream().map(p->view(p,bookings.findById(p.bookingId).orElseThrow())).toList();}
 public View lookup(String token){var pass=passes.findByQrToken(token).orElseThrow(MonthlyService::missing);return view(pass,bookings.findById(pass.bookingId).orElseThrow());}
 public Issued redeem(RedeemInput in,String actor){
  require(Boolean.TRUE.equals(in.holderConfirmed()),"Nhân viên cần đối chiếu người sử dụng trước khi cấp vé");
  var found=passes.findByQrToken(in.qrToken().strip()).orElseThrow(MonthlyService::missing);
  // Serialize every issuance for this pass, including concurrent requests at different counters.
  var pass=passes.lockId(found.bookingId).orElseThrow(MonthlyService::missing);entityManager.refresh(pass);
  var parent=bookings.findById(pass.bookingId).orElseThrow();require(parent.status==Status.PAID,"Thẻ tháng chưa thanh toán");
  var day=pass.days.get(today());require(day!=null,"Hôm nay không nằm trong các ngày đã đăng ký của thẻ");
  if(day.issuedBookingId!=null){var old=bookings.findById(day.issuedBookingId).orElseThrow();return new Issued(old.accessToken,tickets.findByBookingId(old.id),true);}
  var ids=new ArrayList<Long>();ids.add(pass.ticketProductId);if(pass.mealProductId!=null)ids.add(pass.mealProductId);inventory.lockProducts(ids);
  var b=booking(pass.holderName,parent.phone,null,today(),actor);
  b.lines.add(visitLine(ticket(pass.ticketProductId),"Quyền lợi thẻ tháng "+pass.tier));
  if(pass.mealProductId!=null)b.lines.add(visitLine(ticket(pass.mealProductId),"Vé ăn kèm thẻ Platinum"));
  inventory.reserve(b);bookings.saveAndFlush(b);inventory.issue(b,clock.instant());b.status=Status.PAID;b.paidAt=clock.instant();b.paymentMethod="MONTHLY_PASS";b.paymentReference=pass.bookingId;
  day.issuedBookingId=b.id;day.issuedAt=clock.instant();day.issuedBy=actor;passes.saveAndFlush(pass);audit(actor,"MONTHLY_PASS_REDEEMED",pass.bookingId);return new Issued(b.accessToken,tickets.findByBookingId(b.id),false);
 }
 private BookingLine visitLine(Product p,String note){var l=new BookingLine();l.productId=p.id;l.ticketProductId=p.id;l.ticketProductName=p.name;l.name=p.name;l.kind=Kind.TICKET;l.quantity=1;l.ticketQuantity=1;l.unitPrice=BigDecimal.ZERO;l.includedServices=note;return l;}
}
