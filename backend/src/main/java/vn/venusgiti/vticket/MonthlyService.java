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
 public record PlanInput(@NotNull Long ticketProductId,@NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal weekdayPrice,@NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal weekendPrice,@NotNull Boolean active) {}
 public record SaleInput(@NotNull Tier tier,@NotBlank @Size(max=150) String holderName,@NotBlank @Size(max=20) @Pattern(regexp="^\\+?[0-9 ()-]{8,20}$") String phone,@Email @Size(max=254) String email,@NotEmpty @Size(max=366) List<@NotNull LocalDate> dates) {}
 public record RedeemInput(@NotBlank @Size(max=36) String qrToken,@NotNull Boolean holderConfirmed,Boolean physicalMealConfirmed) {}
 public record DayView(LocalDate date,String dayType,BigDecimal price,String issuedBookingId,Instant issuedAt,boolean physicalMealIssued) {}
 public record View(String bookingId,Tier tier,String holderName,String phone,String ticketName,String mealName,String qrToken,String status,List<DayView> days,int remaining) {}
 public record VisitInfo(Tier tier,boolean physicalMealIssued) {}
 public VisitInfo visit(String bookingId){var b=bookings.findById(bookingId).orElseThrow();if(!"MONTHLY_PASS".equals(b.paymentMethod))return null;var p=passes.findById(b.paymentReference).orElseThrow();return new VisitInfo(p.tier,p.tier==Tier.PLATINUM);}
 public record Issued(String accessToken,List<Ticket> tickets,boolean alreadyIssued) {}
 private LocalDate today(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));}
 private static void require(boolean ok,String message){BookingService.require(ok,message);}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thẻ/gói vé tháng");}
 private void audit(String actor,String action,String id){var a=new Audit();a.actor=actor;a.action=action;a.target=id;a.happenedAt=clock.instant();audits.save(a);}
 private Product ticket(Long id){var p=products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Không tìm thấy loại vé tham chiếu"));require(p.kind==Kind.TICKET&&p.active,"Loại vé tham chiếu phải là vé đang bán, có kho mã riêng");return p;}
 public List<Plan> plans(){return plans.findAll().stream().sorted(Comparator.comparing(p->p.tier)).toList();}
 public Plan save(Tier tier,PlanInput in,String actor){
  var ids=new ArrayList<Long>();ids.add(in.ticketProductId());inventory.lockProducts(ids);ticket(in.ticketProductId());
  var p=plans.lockTier(tier).orElseGet(Plan::new);p.tier=tier;p.ticketProductId=in.ticketProductId();p.weekdayPrice=in.weekdayPrice();p.weekendPrice=in.weekendPrice();p.active=in.active();plans.save(p);audit(actor,"MONTHLY_PLAN_SAVED",tier.name());return p;
 }
 private Booking booking(String holder,String phone,String email,LocalDate date,String actor){var b=new Booking();b.id=UUID.randomUUID().toString();b.accessToken=UUID.randomUUID().toString();b.customerName=holder.strip();b.phone=BookingService.phone(phone);b.email=email==null||email.isBlank()?null:email.strip();b.visitDate=date;b.channel=Channel.COUNTER;b.status=Status.PENDING;b.createdAt=clock.instant();b.expiresAt=b.createdAt.plusSeconds(900);b.soldBy=actor;b.subtotal=BigDecimal.ZERO;b.discount=BigDecimal.ZERO;b.total=BigDecimal.ZERO;return b;}
 public Booking sell(SaleInput in,String actor){
  String phone=in.phone()==null?"":in.phone().strip().replaceAll("[ ()-]","");
  require(phone.matches("\\+?[0-9]{8,15}"),"Vé tháng bắt buộc có số điện thoại hợp lệ");
  var p=plans.findById(in.tier()).orElseThrow(MonthlyService::missing);require(p.active,"Gói vé tháng đã ngừng bán");
  var ids=new ArrayList<Long>();ids.add(p.ticketProductId);inventory.lockProducts(ids);
  // Re-read after the product lock: plan edits use the same lock ordering.
  p=plans.lockTier(in.tier()).orElseThrow(MonthlyService::missing);entityManager.refresh(p);require(p.active&&ids.contains(p.ticketProductId),"Cấu hình gói vừa thay đổi, vui lòng tạo lại");
  var base=ticket(p.ticketProductId);
  var dates=new TreeSet<>(in.dates());require(dates.size()==in.dates().size(),"Ngày sử dụng không được trùng");require(!dates.first().isBefore(today()),"Không chọn ngày sử dụng đã qua");require(dates.size()<=366,"Tối đa 366 ngày trong một thẻ");
  require(p.tier!=Tier.SILVER||dates.size()>=12,"Silver cần chọn tối thiểu 12 ngày thường");
  require(p.tier==Tier.SILVER||dates.stream().anyMatch(d->d.getDayOfWeek()==DayOfWeek.SATURDAY||d.getDayOfWeek()==DayOfWeek.SUNDAY),"Gold và Platinum cần ít nhất 1 ngày thứ Bảy hoặc Chủ nhật");
  var b=booking(in.holderName(),phone,in.email(),dates.first(),actor);var pass=new Pass();pass.bookingId=b.id;pass.qrToken=UUID.randomUUID().toString();pass.tier=p.tier;pass.ticketProductId=base.id;pass.ticketName=base.name;pass.mealName=p.tier==Tier.PLATINUM?"Phiếu ăn vật lý":null;pass.holderName=b.customerName;pass.createdAt=clock.instant();
  for(var date:dates){var d=new Day();d.dayType=pricing.dayType(date);require(p.tier!=Tier.SILVER||d.dayType.equals("WEEKDAY"),"Silver chỉ sử dụng ngày thường, không gồm thứ Bảy, Chủ nhật và lễ Tết");d.price=d.dayType.equals("WEEKDAY")?p.weekdayPrice:p.weekendPrice;pass.days.put(date,d);b.subtotal=b.subtotal.add(d.price);}
  b.total=b.subtotal;var line=new BookingLine();line.productId=base.id;line.name="Vé tháng "+p.tier+" · "+dates.size()+" ngày";line.kind=Kind.SERVICE;line.quantity=1;line.unitPrice=b.total;line.ticketQuantity=0;line.includedServices="Mỗi ngày đã chọn: 1 vé "+base.name+(p.tier==Tier.PLATINUM?" + 1 phiếu ăn vật lý":"");b.lines.add(line);
  bookings.saveAndFlush(b);passes.save(pass);audit(actor,"MONTHLY_PASS_CREATED",b.id);return b;
 }
 public View view(String bookingId){return passes.findById(bookingId).map(p->view(p,bookings.findById(bookingId).orElseThrow())).orElse(null);}
 private View view(Pass p,Booking b){
  int remaining=(int)p.days.entrySet().stream().filter(e->!e.getKey().isBefore(today())&&e.getValue().issuedBookingId==null).count();
  String state=b.status!=Status.PAID?b.status.name():p.days.keySet().stream().allMatch(d->d.isBefore(today()))?"EXPIRED":remaining==0?"EXHAUSTED":p.days.keySet().stream().allMatch(d->d.isAfter(today()))?"SCHEDULED":"ACTIVE";
  return new View(p.bookingId,p.tier,p.holderName,b.phone,p.ticketName,p.mealName,b.status==Status.PAID?p.qrToken:null,state,p.days.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new DayView(e.getKey(),e.getValue().dayType,e.getValue().price,e.getValue().issuedBookingId,e.getValue().issuedAt,p.tier==Tier.PLATINUM&&e.getValue().issuedBookingId!=null)).toList(),remaining);
 }
 public List<View> list(){return passes.findTop200ByOrderByCreatedAtDesc().stream().map(p->view(p,bookings.findById(p.bookingId).orElseThrow())).toList();}
 public View recognize(String token){return passes.findByQrToken(token.strip()).map(p->view(p,bookings.findById(p.bookingId).orElseThrow())).orElse(null);}
 public View lookup(String token){var found=recognize(token);if(found==null)throw missing();return found;}
 public Issued redeem(RedeemInput in,String actor){
  require(Boolean.TRUE.equals(in.holderConfirmed()),"Nhân viên cần đối chiếu người sử dụng trước khi cấp vé");
  var found=passes.findByQrToken(in.qrToken().strip()).orElseThrow(MonthlyService::missing);
  // Serialize every issuance for this pass, including concurrent requests at different counters.
  var pass=passes.lockId(found.bookingId).orElseThrow(MonthlyService::missing);entityManager.refresh(pass);
  var parent=bookings.findById(pass.bookingId).orElseThrow();require(parent.status==Status.PAID,"Thẻ tháng chưa thanh toán");
  var day=pass.days.get(today());require(day!=null,"Hôm nay không nằm trong các ngày đã đăng ký của thẻ");
  if(day.issuedBookingId!=null){var old=bookings.findById(day.issuedBookingId).orElseThrow();return new Issued(old.accessToken,tickets.findByBookingId(old.id),true);}
  require(pass.tier!=Tier.PLATINUM||Boolean.TRUE.equals(in.physicalMealConfirmed()),"Cần xác nhận phát 1 phiếu ăn vật lý cho lượt Platinum");
  var ids=new ArrayList<Long>();ids.add(pass.ticketProductId);inventory.lockProducts(ids);
  var b=booking(pass.holderName,parent.phone,null,today(),actor);
  b.lines.add(visitLine(ticket(pass.ticketProductId),"Quyền lợi thẻ tháng "+pass.tier));

  inventory.reserve(b);b=bookings.saveAndFlush(b);inventory.issue(b,clock.instant());b.status=Status.PAID;b.paidAt=clock.instant();b.paymentMethod="MONTHLY_PASS";b.paymentReference=pass.bookingId;
  day.issuedBookingId=b.id;day.issuedAt=clock.instant();day.issuedBy=actor;passes.saveAndFlush(pass);audit(actor,"MONTHLY_PASS_REDEEMED",pass.bookingId);if(pass.tier==Tier.PLATINUM)audit(actor,"MONTHLY_PHYSICAL_MEAL_ISSUED",b.id);return new Issued(b.accessToken,tickets.findByBookingId(b.id),false);
 }
 private BookingLine visitLine(Product p,String note){var l=new BookingLine();l.productId=p.id;l.ticketProductId=p.id;l.ticketProductName=p.name;l.name=p.name;l.kind=Kind.TICKET;l.quantity=1;l.ticketQuantity=1;l.unitPrice=BigDecimal.ZERO;l.includedServices=note;return l;}
}
