package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Service
public class VietQrApiClient implements VietQrGateway {
 private final VietQrProperties properties;private final RestClient client;private final Clock clock;private String accessToken;private Instant refreshAt=Instant.EPOCH;
 @Autowired public VietQrApiClient(VietQrProperties p,Clock c){this(p,c,builder());}
 VietQrApiClient(VietQrProperties p,Clock c,RestClient.Builder b){properties=p;clock=c;client=b.baseUrl(p.client().baseUrl()).build();}
 private static RestClient.Builder builder(){var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(10));return RestClient.builder().requestFactory(factory);}
 @Override public Generated generate(String orderId,BigDecimal amount,String content){
  if(!properties.ready())throw unavailable("Chưa cấu hình đầy đủ kết nối VietQR");
  var bank=properties.bank();var request=new GenerateRequest(bankCode(bank.code()),bank.account().trim(),ascii(bank.accountName()),content,0,amount.longValueExact(),orderId,"C");
  try {
   Generated result;
   try{result=send(request);}catch(RestClientResponseException e){if(e.getStatusCode().value()!=401)throw e;clear();result=send(request);}
   if(result==null||VietQrProperties.blank(result.qrCode())||result.qrCode().length()>4000||VietQrProperties.blank(result.content())||result.content().length()>500||!orderId.equals(result.orderId())||result.amount()==null||result.amount().compareTo(amount)!=0||!bankCode(bank.code()).equals(bankCode(result.bankCode()))||(!bank.account().trim().equals(result.bankAccount())&&!Objects.equals(result.bankAccount(),result.vaAccount()))||VietQrProperties.blank(result.bankAccount())||result.bankAccount().length()>64||VietQrProperties.blank(result.userBankName())||result.userBankName().length()>150||tooLong(result.vaAccount(),64)||tooLong(result.qrLink(),2000)||tooLong(result.transactionRefId(),512))throw unavailable("Phản hồi VietQR không khớp đơn hàng hoặc tài khoản nhận");
   return result;
  }catch(RestClientResponseException e){throw unavailable("VietQR từ chối yêu cầu (HTTP "+e.getStatusCode().value()+"). Kiểm tra tài khoản API và môi trường.");}catch(RestClientException e){throw unavailable("Không kết nối được VietQR. Vui lòng thử lại.");}
 }
 private static boolean tooLong(String value,int max){return value!=null&&value.length()>max;}
 private Generated send(GenerateRequest body){return client.post().uri("/vqr/api/qr/generate-customer").contentType(MediaType.APPLICATION_JSON).headers(h->h.setBearerAuth(token())).body(body).retrieve().body(Generated.class);}
 private synchronized String token(){if(accessToken!=null&&clock.instant().isBefore(refreshAt))return accessToken;var p=properties.client();TokenResponse t=client.post().uri("/vqr/api/token_generate").contentType(MediaType.APPLICATION_JSON).headers(h->h.setBasicAuth(p.username(),p.password())).retrieve().body(TokenResponse.class);if(t==null||VietQrProperties.blank(t.access_token())||t.expires_in()<1)throw unavailable("VietQR không trả về token hợp lệ");accessToken=t.access_token();refreshAt=clock.instant().plusSeconds(Math.max(0,Math.min(t.expires_in(),300)-10));return accessToken;}
 private synchronized void clear(){accessToken=null;refreshAt=Instant.EPOCH;}
 static String bankCode(String value){return "970422".equals(value)?"MB":value==null?"":value.trim().toUpperCase(Locale.ROOT);}
 static String ascii(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('Đ','D').replace('đ','d').toUpperCase(Locale.ROOT);}
 private RuntimeException unavailable(String message){return new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_GATEWAY,message);}
 @JsonIgnoreProperties(ignoreUnknown=true) private record TokenResponse(String access_token,String token_type,long expires_in) {}
 private record GenerateRequest(String bankCode,String bankAccount,String userBankName,String content,int qrType,long amount,String orderId,String transType) {}
}
