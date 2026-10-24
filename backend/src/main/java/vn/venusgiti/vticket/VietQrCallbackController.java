package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
public class VietQrCallbackController {
 private final VietQrProperties properties;private final VietQrCallbackTokens tokens;private final VietQrPaymentService payments;
 VietQrCallbackController(VietQrProperties p,VietQrCallbackTokens t,VietQrPaymentService s){properties=p;tokens=t;payments=s;}
 @PostMapping({"/vietqr/api/token_generate","/api/vietqr/token_generate","/api-vietqr-callback/api-get-token"})
 public ResponseEntity<?> token(@RequestHeader(value="Authorization",required=false) String authorization){
  if(!properties.enabled())return ResponseEntity.status(503).body(Map.of("status","FAILED","message","VIETQR_DISABLED"));
  try{if(authorization==null||!authorization.regionMatches(true,0,"Basic ",0,6))throw new IllegalArgumentException();String decoded=new String(Base64.getDecoder().decode(authorization.substring(6).trim()),StandardCharsets.UTF_8);int colon=decoded.indexOf(':');if(colon<1)throw new IllegalArgumentException();boolean user=equal(properties.callback().username(),decoded.substring(0,colon)),password=equal(properties.callback().password(),decoded.substring(colon+1));if(!(user&password))throw new IllegalArgumentException();}catch(IllegalArgumentException e){return ResponseEntity.status(401).body(Map.of("status","FAILED","message","INVALID_CREDENTIALS"));}
  return ResponseEntity.ok(Map.of("access_token",tokens.create(),"token_type","Bearer","expires_in",properties.callback().tokenExpirationSeconds()));
 }
 @PostMapping({"/vietqr/bank/api/transaction-sync","/api/vietqr/transaction-sync","/api-vietqr-callback/api-transaction-sync"})
 public ResponseEntity<SyncResponse> sync(@RequestHeader(value="Authorization",required=false) String authorization,@Valid @RequestBody SyncRequest input){
  if(!properties.enabled())return error(503,"VIETQR_DISABLED","Chưa bật VietQR");
  if(authorization==null||!authorization.regionMatches(true,0,"Bearer ",0,7)||!tokens.valid(authorization.substring(7).trim()))return error(401,"INVALID_TOKEN","Callback token không hợp lệ hoặc hết hạn");
  try{var r=payments.callback(input);return ResponseEntity.ok(new SyncResponse(false,null,r.state==vn.venusgiti.vticket.Domain.ReceiptState.APPLIED?"Đã xác nhận thanh toán":"Đã ghi nhận giao dịch cần đối soát: "+r.reason,new SyncObject(r.id)));}catch(ResponseStatusException e){return error(400,"TRANSACTION_CONFLICT",e.getReason());}
 }
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<SyncResponse> invalid(){return error(400,"INVALID_REQUEST","Dữ liệu callback không hợp lệ");}
 private ResponseEntity<SyncResponse> error(int status,String code,String message){return ResponseEntity.status(status).body(new SyncResponse(true,code,message,null));}
 private boolean equal(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
 public record SyncRequest(@NotBlank @Size(max=64) String bankaccount,@NotNull @DecimalMin("1") @Digits(integer=18,fraction=0) BigDecimal amount,@NotBlank @Pattern(regexp="[CDcd]") String transType,@NotBlank @Size(max=2000) String content,@NotBlank @Size(max=128) String transactionid,@NotNull @Positive Long transactiontime,@NotBlank @Size(max=128) String referencenumber,@NotBlank @Size(max=64) String orderId,String terminalCode,String subTerminalCode,String serviceCode,String urlLink,String sign) {}
 public record SyncObject(String reftransactionid) {}
 public record SyncResponse(boolean error,String errorReason,String toastMessage,SyncObject object) {}
}
