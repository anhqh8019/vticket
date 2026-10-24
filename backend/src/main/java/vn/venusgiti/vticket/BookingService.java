package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;

@Service @Transactional
public class BookingService {
 private final Products products; private final Vouchers vouchers; private final Bookings bookings; private final Tickets tickets; private final Audits audits; private final Clock clock; private final InventoryService inventory;
 BookingService(Products p,Vouchers v,Bookings b,Tickets t,Audits a,Clock c,InventoryService i){products=p;vouchers=v;bookings=b;tickets=t;audits=a;clock=c;inventory=i;}
 static void require(boolean condition,String message){ if(!condition)throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
 static String phone(String value){return value.replaceAll("[ -]","").replaceFirst("^\\+84","0");}
 private Instant now(){return clock.instant();}
 void audit(String actor,String action,String target){Audit a=new Audit();a.happenedAt=now();a.actor=actor;a.action=action;a.target=target;audits.save(a);}
 public Booking create(OrderInput input,Channel channel,String actor){
  require(!input.visitDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))) ,"Ngày sử dụng đã qua");
  inventory.lockProducts(input.lines().stream().map(LineInput::productId).toList());
  Booking b=new Booking();b.id=UUID.randomUUID().toString();b.accessToken=UUID.randomUUID().toString();b.customerName=input.customerName().trim();b.phone=phone(input.phone());b.email=input.email();b.visitDate=input.visitDate();b.channel=channel;b.status=Status.PENDING;b.createdAt=now();b.expiresAt=now().plus(Duration.ofMinutes(15));b.soldBy=actor;b.subtotal=BigDecimal.ZERO;
  Set<Long> seen=new HashSet<>();boolean hasTicket=false;
  for(LineInput item:input.lines()){
   require(seen.add(item.productId()),"Loại vé/dịch vụ bị trùng dòng");
   Product p=products.findById(item.productId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy vé/dịch vụ"));require(p.active,"Vé/dịch vụ đã ngừng bán");
   BookingLine line=new BookingLine();line.productId=p.id;line.name=p.name;line.kind=p.kind;line.unitPrice=p.price;line.quantity=item.quantity();line.includedServices=p.includedServiceIds.stream().sorted().map(id->products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Dịch vụ trong combo không tồn tại")).name).reduce((a,c)->a+", "+c).orElse("");
   b.lines.add(line);b.subtotal=b.subtotal.add(p.price.multiply(BigDecimal.valueOf(item.quantity())));hasTicket|=p.kind!=Kind.SERVICE;
  }
  require(hasTicket,"Đơn cần có ít nhất một vé hoặc combo");
  inventory.reserve(b);
  if(input.voucherCode()!=null&&!input.voucherCode().isBlank()){
   Voucher v=vouchers.lockCode(input.voucherCode().trim().toUpperCase(Locale.ROOT)).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Voucher không hợp lệ"));
   require(!v.revoked&&v.usedOrderId==null&&!now().isBefore(v.validFrom)&&now().isBefore(v.validUntil)&&v.phone.equals(b.phone),"Voucher không hợp lệ, không đúng khách hoặc đã hết hạn/sử dụng");
   require(v.reservedOrderId==null||v.reservedUntil==null||!v.reservedUntil.isAfter(now()),"Voucher đang được giữ cho đơn khác");
   b.voucherCode=v.code;b.discountPercent=v.percentage;v.reservedOrderId=b.id;v.reservedUntil=b.expiresAt;
  }
  b.discount=b.subtotal.multiply(BigDecimal.valueOf(b.discountPercent)).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP);b.total=b.subtotal.subtract(b.discount);bookings.save(b);audit(actor,"ORDER_CREATED",b.id);return b;
 }
 public Booking getPublic(String token){return bookings.findByAccessToken(token).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn"));}

 public record BankSettlement(boolean applied,String reason) {}
 private String settlementFailure(Booking b,Instant at){
  if(b.status!=Status.PENDING)return "ORDER_NOT_PENDING";
  if(!at.isBefore(b.expiresAt))return "ORDER_EXPIRED";
  inventory.lockBooking(b);
  if(b.voucherCode!=null){var v=vouchers.lockCode(b.voucherCode).orElse(null);if(v==null||v.revoked||v.usedOrderId!=null||!b.id.equals(v.reservedOrderId)||!at.isBefore(v.validUntil))return "VOUCHER_INVALID";}
  if(!inventory.reservationValid(b,at))return "INVENTORY_UNAVAILABLE";
  return null;
 }
 private Booking finish(Booking b,String method,String reference,String actor,Instant at){
  if(b.voucherCode!=null){var v=vouchers.lockCode(b.voucherCode).orElseThrow();v.usedOrderId=b.id;v.usedAt=at;v.reservedOrderId=null;v.reservedUntil=null;}
  b.status=Status.PAID;b.paidAt=at;b.paymentMethod=method;b.paymentReference=reference;
  inventory.issue(b,at);audit(actor,method==null?"ZERO_TOTAL_CONFIRMED":"PAYMENT_"+method,b.id);return b;
 }
 Booking pay(String id,String method,String reference,String actor){
  Booking b=bookings.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn"));
  require("VIETQR".equals(method)||("CASH".equals(method)&&b.channel==Channel.COUNTER),"Phương thức thanh toán không hợp lệ");
  if(b.status==Status.PAID){require(Objects.equals(b.paymentMethod,method),"Đơn đã thanh toán bằng phương thức khác");return b;}
  Instant at=now();String reason=settlementFailure(b,at);require(reason==null,"Không thể thanh toán đơn: "+reason);return finish(b,method,reference,actor,at);
 }
 public BankSettlement tryBankPayment(String id,String reference){
  var b=bookings.lockId(id).orElseThrow();Instant at=now();
  if(!"VIETQR".equals(b.paymentMethod))return new BankSettlement(false,"PAYMENT_METHOD_CHANGED");
  String reason=settlementFailure(b,at);if(reason!=null)return new BankSettlement(false,reason);
  finish(b,"VIETQR",reference,"vietqr",at);return new BankSettlement(true,null);
 }
 public Booking confirmZero(String token){var b=bookings.lockId(getPublic(token).id).orElseThrow();require(b.total.signum()==0,"Đơn vẫn còn tiền cần thanh toán");if(b.status==Status.PAID)return b;var at=now();String reason=settlementFailure(b,at);require(reason==null,"Không xác nhận được đơn: "+reason);return finish(b,null,"ZERO-"+b.id,"guest",at);}
 public Booking cash(String id,String actor){var b=bookings.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn"));require(b.channel==Channel.COUNTER,"Chỉ xác nhận tiền mặt cho đơn tại quầy");require(!"VIETQR".equals(b.paymentMethod),"Đơn đã chọn VietQR. Hủy đơn và tạo lại nếu cần đổi sang tiền mặt.");return pay(id,"CASH","CASH-"+id,actor);}
 public Booking cancel(String id,String actor,boolean expired){Booking b=bookings.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn"));if(expired&&b.status!=Status.PENDING)return b;require(b.status==Status.PENDING,"Chỉ hủy đơn chưa thanh toán");inventory.lockBooking(b);inventory.release(b);if(b.voucherCode!=null){Voucher v=vouchers.lockCode(b.voucherCode).orElseThrow();if(b.id.equals(v.reservedOrderId)){v.reservedOrderId=null;v.reservedUntil=null;}}b.status=expired?Status.EXPIRED:Status.CANCELLED;audit(actor,expired?"ORDER_EXPIRED":"ORDER_CANCELLED",id);return b;}
 public Ticket checkIn(String token,String actor){Ticket t=tickets.lockTokenOrCode(token.trim(),token.trim().toUpperCase(Locale.ROOT)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mã vé không hợp lệ"));require(t.checkedInAt==null,"Vé đã được sử dụng");require(t.visitDate.equals(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))) ,"Vé chỉ sử dụng đúng ngày đã đặt");require(bookings.findById(t.bookingId).orElseThrow().status==Status.PAID,"Đơn chưa thanh toán");inventory.used(t);t.checkedInAt=now();t.checkedInBy=actor;audit(actor,"CHECK_IN",t.id);return t;}
 public List<Ticket> ticketList(String bookingId){return tickets.findByBookingId(bookingId);}
}
