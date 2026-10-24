package vn.venusgiti.vticket;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
@ConfigurationProperties("app.vietqr")
public record VietQrProperties(boolean enabled,Client client,Bank bank,Callback callback) {
 public record Client(String baseUrl,String username,String password) { @Override public String toString(){return "Client[redacted]";} }
 public record Bank(String code,String account,String accountName) {}
 public record Callback(String username,String password,String jwtSecret,int tokenExpirationSeconds) { @Override public String toString(){return "Callback[redacted]";} }
 public boolean ready(){return enabled&&client!=null&&bank!=null&&!blank(client.username())&&!blank(client.password())&&!blank(bank.code())&&!blank(bank.account())&&!blank(bank.accountName());}
 static boolean blank(String value){return value==null||value.isBlank();}
 public VietQrProperties {
  if(client!=null&&client.baseUrl()!=null){URI u=URI.create(client.baseUrl());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("VIETQR_API_BASE_URL phải là HTTPS, không kèm tài khoản trong URL");}
  if(enabled&&(callback==null||blank(callback.username())||blank(callback.password())||callback.password().length()<12||callback.jwtSecret()==null||callback.jwtSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32||callback.tokenExpirationSeconds()<30||callback.tokenExpirationSeconds()>3600))throw new IllegalArgumentException("Cấu hình VIETQR_CALLBACK_USERNAME, PASSWORD (>=12 ký tự), JWT_SECRET (>=32 byte), TOKEN_EXPIRATION_SECONDS (30..3600)");
 }
}
