package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import java.util.*;
import java.time.*;
import java.io.ByteArrayOutputStream;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;

@RestController @RequestMapping("/api")
public class ApiController {
 private final Products products; private final Vouchers vouchers;private final Bookings bookings;private final Audits audits;private final BookingService service;private final Clock clock;private final VietQrProperties vietqr;private final VietQrPaymentService payments;
 ApiController(Products p,Vouchers v,Bookings b,Audits a,BookingService s,Clock c,VietQrProperties vq,VietQrPaymentService paymentService){products=p;vouchers=v;bookings=b;audits=a;service=s;clock=c;vietqr=vq;payments=paymentService;}
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping("/me") public Map<String,Object> me(Authentication a){return Map.of("username",a.getName(),"roles",a.getAuthorities().stream().map(Object::toString).toList());}
 @GetMapping("/public/config") public Map<String,Object> config(){return Map.of("brand","Venusgiti","vietqrEnabled",vietqr.ready(),"vietqrEnvironment",vietqr.client().baseUrl().contains("dev.vietqr.org")?"sandbox":"production");}
 @GetMapping("/public/products") public List<Product> catalog(){return products.findByActiveTrueOrderByIdAsc();}
 @PostMapping("/public/orders") public Booking order(@Valid @RequestBody OrderInput in){return service.create(in,Channel.ONLINE,"guest");}
 @GetMapping("/public/orders/{token}") public OrderDetails detail(@PathVariable String token){var b=service.getPublic(token);return new OrderDetails(b,service.ticketList(b.id),payments.view(b.id));}
 public record OrderDetails(Booking order,List<Ticket> tickets,VietQrPaymentService.View payment) {}
 @PostMapping("/public/orders/{token}/cancel") public Booking cancel(@PathVariable String token){return service.cancel(service.getPublic(token).id,"guest",false);}
 @GetMapping(value="/public/qr/{qrToken}",produces=MediaType.IMAGE_PNG_VALUE) public byte[] qr(@PathVariable String qrToken) throws Exception {try{UUID.fromString(qrToken);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã QR không hợp lệ");}var matrix=new MultiFormatWriter().encode(qrToken,BarcodeFormat.QR_CODE,260,260);var out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}
 @GetMapping("/staff/orders") public List<Booking> orders(){return bookings.findTop200ByOrderByCreatedAtDesc();}
 @PostMapping("/staff/orders") public Booking counter(@Valid @RequestBody OrderInput in,Authentication a){return service.create(in,Channel.COUNTER,a.getName());}
 @PostMapping("/staff/orders/{id}/cash") public Booking cash(@PathVariable String id,Authentication a){return service.cash(id,a.getName());}
 @PostMapping("/staff/orders/{id}/cancel") public Booking staffCancel(@PathVariable String id,Authentication a){return service.cancel(id,a.getName(),false);}
 @PostMapping("/staff/check-in") public Ticket checkIn(@Valid @RequestBody CheckInInput in,Authentication a){return service.checkIn(in.qrToken(),a.getName());}
 @GetMapping("/admin/products") public List<Product> allProducts(){return products.findAll();}
 @PostMapping("/admin/products") @Transactional public Product add(@Valid @RequestBody ProductInput in,Authentication a){return saveProduct(new Product(),in,a.getName());}
 @PutMapping("/admin/products/{id}") @Transactional public Product update(@PathVariable Long id,@Valid @RequestBody ProductInput in,Authentication a){return saveProduct(products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)),in,a.getName());}
 private Product saveProduct(Product p,ProductInput in,String actor){BookingService.require(p.id==null||p.kind==in.kind(),"Không đổi loại danh mục vé / combo / dịch vụ sau khi tạo");Set<Long> ids=in.includedServiceIds()==null?Set.of():in.includedServiceIds();BookingService.require(in.kind()!=Kind.SERVICE||ids.isEmpty(),"Dịch vụ không chứa dịch vụ con");for(Long id:ids){Product s=products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Không tìm thấy dịch vụ đi kèm"));BookingService.require(s.kind==Kind.SERVICE&&s.active,"Chỉ chọn dịch vụ đang hoạt động");}if(in.kind()==Kind.COMBO){BookingService.require(in.comboTicketId()!=null&&in.comboBuyQuantity()!=null&&in.comboBuyQuantity()>=1&&in.comboBuyQuantity()<=1000&&in.comboGiftQuantity()!=null&&in.comboGiftQuantity()>=0&&in.comboGiftQuantity()<=1000&&in.comboBuyQuantity()+in.comboGiftQuantity()<=1000,"Combo cần loại vé gốc, số vé mua và số vé tặng hợp lệ");var base=products.findById(in.comboTicketId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Không tìm thấy vé gốc"));BookingService.require(base.kind==Kind.TICKET&&(!in.active()||base.active),"Combo phải tham chiếu một loại vé đang bán, không tham chiếu combo/dịch vụ");p.comboTicketId=base.id;p.comboBuyQuantity=in.comboBuyQuantity();p.comboGiftQuantity=in.comboGiftQuantity();}else{p.comboTicketId=null;p.comboBuyQuantity=null;p.comboGiftQuantity=null;}
 p.code=in.code();p.name=in.name();p.description=in.description();p.kind=in.kind();p.price=in.price();p.active=in.active();p.includedServiceIds.clear();p.includedServiceIds.addAll(ids);products.save(p);service.audit(actor,"PRODUCT_SAVED",p.code);return p;}
 @GetMapping("/admin/vouchers") public List<Map<String,Object>> voucherList(){return vouchers.findAll().stream().map(v->{Map<String,Object> m=new LinkedHashMap<>();m.put("voucher",v);m.put("status",v.usedOrderId!=null?"USED":v.revoked?"REVOKED":!clock.instant().isBefore(v.validUntil)?"EXPIRED":clock.instant().isBefore(v.validFrom)?"SCHEDULED":v.reservedUntil!=null&&v.reservedUntil.isAfter(clock.instant())?"RESERVED":"AVAILABLE");return m;}).toList();}
 @PostMapping("/admin/vouchers") @Transactional public Voucher issue(@Valid @RequestBody VoucherInput in,Authentication a){BookingService.require(in.validUntil().isAfter(in.validFrom())&&in.validUntil().isAfter(clock.instant()),"Thời hạn voucher không hợp lệ");Voucher v=new Voucher();v.code="VG-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);v.customerName=in.customerName().trim();v.phone=BookingService.phone(in.phone());v.percentage=in.percentage();v.validFrom=in.validFrom();v.validUntil=in.validUntil();v.note=in.note();vouchers.save(v);service.audit(a.getName(),"VOUCHER_ISSUED",v.code);return v;}
 @PostMapping("/admin/vouchers/{code}/revoke") @Transactional public Voucher revoke(@PathVariable String code,Authentication a){Voucher v=vouchers.lockCode(code).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));BookingService.require(v.usedOrderId==null,"Voucher đã sử dụng");BookingService.require(v.reservedUntil==null||!v.reservedUntil.isAfter(clock.instant()),"Voucher đang giữ cho đơn chờ thanh toán");v.revoked=true;service.audit(a.getName(),"VOUCHER_REVOKED",v.code);return v;}
 @GetMapping("/admin/audit") public List<Audit> audit(){return audits.findTop100ByOrderByHappenedAtDesc();}
}
