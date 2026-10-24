package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.security.*;
import java.nio.charset.StandardCharsets;
import vn.venusgiti.vticket.Domain.*;

@Service
public class VietQrPaymentService {
 private final Bookings bookings;private final PaymentIntents intents;private final PaymentReceipts receipts;private final CallbackLocks locks;private final BookingService service;private final VietQrGateway gateway;private final VietQrProperties properties;private final Clock clock;private final TransactionTemplate tx;
 private final SecureRandom random=new SecureRandom();
 VietQrPaymentService(Bookings b,PaymentIntents i,PaymentReceipts r,CallbackLocks l,BookingService s,VietQrGateway g,VietQrProperties p,Clock c,PlatformTransactionManager manager){bookings=b;intents=i;receipts=r;locks=l;service=s;gateway=g;properties=p;clock=c;tx=new TransactionTemplate(manager);}
 public record View(String orderId,String state,BigDecimal amount,String bankCode,String bankAccount,String accountName,String virtualAccount,String content,Instant expiresAt,String message) {}
 private record Claim(PaymentIntent intent,boolean callProvider) {}
 private String nextId(){String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";StringBuilder s=new StringBuilder();for(int i=0;i<13;i++)s.append(alphabet.charAt(random.nextInt(alphabet.length())));return s.toString();}
 public View initiate(String token){
  Booking known=service.getPublic(token);
  Claim claim=tx.execute(status->{
   Booking b=bookings.lockId(known.id).orElseThrow();BookingService.require(b.status==Status.PENDING&&clock.instant().isBefore(b.expiresAt),"Đơn không còn thời gian thanh toán");BookingService.require(b.total.signum()>0,"Đơn 0 đồng không cần chuyển khoản");
   if(!properties.ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình đầy đủ VietQR. Vui lòng liên hệ quầy.");
   var existing=intents.findByBookingId(b.id).orElse(null);
   if(existing!=null&&(existing.state==IntentState.READY||existing.state==IntentState.CREATING&&existing.attemptStartedAt.plusSeconds(60).isAfter(clock.instant())))return new Claim(existing,false);
   PaymentIntent p=existing;
   if(p==null){p=new PaymentIntent();p.id=nextId();p.bookingId=b.id;p.amount=b.total;p.createdAt=clock.instant();p.expiresAt=b.expiresAt;p.bankCode=VietQrApiClient.bankCode(properties.bank().code());p.bankAccount=properties.bank().account().trim();p.accountName=VietQrApiClient.ascii(properties.bank().accountName());p.content="VT"+p.id;}
   BookingService.require(p.bankAccount.equals(properties.bank().account().trim())&&p.bankCode.equals(VietQrApiClient.bankCode(properties.bank().code())),"Tài khoản nhận đã thay đổi. Vui lòng hủy đơn và tạo lại.");
   p.state=IntentState.CREATING;p.attemptId=UUID.randomUUID().toString();p.attemptStartedAt=clock.instant();p.lastError=null;b.paymentMethod="VIETQR";intents.save(p);return new Claim(p,true);
  });
  PaymentIntent pending=Objects.requireNonNull(claim).intent();if(!claim.callProvider())return view(pending);
  try{
   var qr=gateway.generate(pending.id,pending.amount,pending.content);
   tx.executeWithoutResult(status->{var b=bookings.lockId(pending.bookingId).orElseThrow();var p=intents.findById(pending.id).orElseThrow();if(!Objects.equals(p.attemptId,pending.attemptId))return;if(b.status!=Status.PENDING||!clock.instant().isBefore(b.expiresAt)){p.state=IntentState.CLOSED;return;}p.qrCode=qr.qrCode();p.qrLink=qr.qrLink();p.transactionRefId=qr.transactionRefId();p.bankAccount=qr.bankAccount();p.bankCode=qr.bankCode();p.accountName=qr.userBankName();p.virtualAccount=qr.vaAccount();p.content=qr.content();p.state=IntentState.READY;service.audit("vietqr","VIETQR_CREATED",b.id);});
  }catch(RuntimeException e){tx.executeWithoutResult(status->{bookings.lockId(pending.bookingId).orElseThrow();var p=intents.findById(pending.id).orElseThrow();if(Objects.equals(p.attemptId,pending.attemptId)&&p.state==IntentState.CREATING){p.state=IntentState.FAILED;p.lastError=e instanceof ResponseStatusException r&&r.getReason()!=null?r.getReason():"Không tạo được VietQR. Vui lòng thử lại.";p.lastError=p.lastError.substring(0,Math.min(300,p.lastError.length()));}});throw e;}
  return view(pending.bookingId);
 }
 @Transactional(readOnly=true) public View view(String bookingId){return intents.findByBookingId(bookingId).map(this::view).orElse(null);}
 private View view(PaymentIntent p){String state=p.state.name();if(p.state==IntentState.CREATING&&p.attemptStartedAt!=null&&!clock.instant().isBefore(p.attemptStartedAt.plusSeconds(60)))state="FAILED";var b=bookings.findById(p.bookingId).orElseThrow();if(b.status==Status.PAID&&p.state==IntentState.PAID)state="PAID";else if(b.status!=Status.PENDING||!clock.instant().isBefore(p.expiresAt))state="CLOSED";return new View(p.id,state,p.amount,p.bankCode,p.bankAccount,p.accountName,p.virtualAccount,p.content,p.expiresAt,p.lastError);}
 @Transactional(readOnly=true) public String qrPayload(String token){var b=service.getPublic(token);BookingService.require(b.status==Status.PENDING&&clock.instant().isBefore(b.expiresAt),"Đơn không còn chờ thanh toán");var p=intents.findByBookingId(b.id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));BookingService.require(p.state==IntentState.READY&&p.qrCode!=null,"QR chưa sẵn sàng");return p.qrCode;}
 @Transactional public PaymentReceipt callback(VietQrCallbackController.SyncRequest in){
  locks.acquire();String key=digest(in.bankaccount().trim()+"|"+in.referencenumber().trim());
  var prior=receipts.findByTransactionId(in.transactionid()).orElseGet(()->receipts.findByReceiptKey(key).orElse(null));
  if(prior!=null){BookingService.require(prior.providerOrderId.equals(in.orderId().trim().toUpperCase(Locale.ROOT))&&prior.bankAccount.equals(in.bankaccount().trim())&&prior.amount.compareTo(in.amount())==0&&prior.transType.equalsIgnoreCase(in.transType())&&prior.referenceNumber.equals(in.referencenumber().trim()),"Callback trùng mã nhưng khác dữ liệu giao dịch");return prior;}
  var r=new PaymentReceipt();r.id=UUID.randomUUID().toString();r.transactionId=in.transactionid();r.receiptKey=key;r.referenceNumber=in.referencenumber().trim();r.providerOrderId=in.orderId().trim().toUpperCase(Locale.ROOT);r.bankAccount=in.bankaccount().trim();r.amount=in.amount();r.transType=in.transType().toUpperCase(Locale.ROOT);r.content=in.content();r.transactionAt=Instant.ofEpochMilli(in.transactiontime());r.receivedAt=clock.instant();r.state=ReceiptState.REVIEW;
  var p=intents.findById(r.providerOrderId).orElse(null);r.reason=reason(r,p);
  if(p!=null)r.bookingId=p.bookingId;
  if(r.reason==null){var result=service.tryBankPayment(p.bookingId,r.transactionId);if(result.applied()){r.state=ReceiptState.APPLIED;p.state=IntentState.PAID;}else r.reason=result.reason();}
  receipts.save(r);service.audit("vietqr",r.state==ReceiptState.APPLIED?"VIETQR_APPLIED":"VIETQR_REVIEW",r.id);return r;
 }
 private String reason(PaymentReceipt r,PaymentIntent p){
  if(!"C".equals(r.transType))return "NOT_CREDIT";
  if(p==null)return "UNKNOWN_ORDER";
  if(!r.bankAccount.equals(p.bankAccount)&&!r.bankAccount.equals(p.virtualAccount))return "WRONG_ACCOUNT";
  if(r.amount.compareTo(p.amount)!=0)return "WRONG_AMOUNT";
  if(r.transactionAt.isAfter(clock.instant().plusSeconds(300))||r.transactionAt.isBefore(p.createdAt.minusSeconds(300)))return "INVALID_TRANSACTION_TIME";
  if(!normalize(r.content).contains(normalize(p.content)))return "CONTENT_MISMATCH";
  if(p.state!=IntentState.READY)return p.state==IntentState.PAID?"ALREADY_PAID":"QR_NOT_READY";
  return null;
 }
 static String normalize(String s){return VietQrApiClient.ascii(s).replaceAll("[^A-Z0-9]","");}
 private String digest(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @Transactional(readOnly=true) public List<PaymentReceipt> receipts(){return receipts.findTop200ByOrderByReceivedAtDesc();}
}
