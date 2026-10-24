package vn.venusgiti.vticket;
import java.math.BigDecimal;
public interface VietQrGateway {
 @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
 record Generated(String bankCode,String bankAccount,String userBankName,BigDecimal amount,String content,String qrCode,String transactionRefId,String qrLink,String vaAccount,String orderId) {}
 Generated generate(String orderId,BigDecimal amount,String content);
}
