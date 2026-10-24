package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VietQrApiClientTest {
 static VietQrProperties properties(){return new VietQrProperties(true,new VietQrProperties.Client("https://dev.vietqr.org","api-unit-test",UUID.randomUUID().toString()),new VietQrProperties.Bank("970422","000000000000","Venusgiti Thử Nghiệm"),new VietQrProperties.Callback("callback-unit-test",UUID.randomUUID().toString(),UUID.randomUUID().toString()+UUID.randomUUID(),300));}
 String generated(String id,long amount,String bank){return "{\"bankCode\":\""+bank+"\",\"bankAccount\":\"000000000000\",\"userBankName\":\"VENUSGITI THU NGHIEM\",\"amount\":\""+amount+"\",\"content\":\"VT"+id+"\",\"qrCode\":\"PROVIDER-PAYLOAD\",\"transactionRefId\":\"provider-ref\",\"qrLink\":\"https://dev.vietqr.org/qr\",\"orderId\":\""+id+"\",\"additionalData\":[]}";}
 @Test void callsProviderTokenThenGenerateAndCachesToken(){var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var p=properties();var client=new VietQrApiClient(p,Clock.systemUTC(),builder);
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/token_generate")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Basic "+Base64.getEncoder().encodeToString((p.client().username()+":"+p.client().password()).getBytes(java.nio.charset.StandardCharsets.UTF_8)))).andRespond(withSuccess("{\"access_token\":\"provider-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer")).andExpect(header("Authorization","Bearer provider-token")).andExpect(jsonPath("$.qrType").value(0)).andExpect(jsonPath("$.transType").value("C")).andExpect(jsonPath("$.orderId").value("ABCDEFGHIJKLM")).andExpect(jsonPath("$.bankCode").value("MB")).andExpect(jsonPath("$.amount").value(500000)).andExpect(jsonPath("$.userBankName").value("VENUSGITI THU NGHIEM")).andRespond(withSuccess(generated("ABCDEFGHIJKLM",500000,"MB"),MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer")).andExpect(header("Authorization","Bearer provider-token")).andRespond(withSuccess(generated("NOPQRSTUVWXYZ",500000,"MB"),MediaType.APPLICATION_JSON));
  assertEquals("PROVIDER-PAYLOAD",client.generate("ABCDEFGHIJKLM",BigDecimal.valueOf(500000),"VTABCDEFGHIJKLM").qrCode());client.generate("NOPQRSTUVWXYZ",BigDecimal.valueOf(500000),"VTNOPQRSTUVWXYZ");server.verify();
 }
 @Test void retriesExpiredProviderTokenOnlyOnce(){var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=new VietQrApiClient(properties(),Clock.systemUTC(),builder);
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/token_generate")).andRespond(withSuccess("{\"access_token\":\"old-token\",\"expires_in\":300}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/token_generate")).andRespond(withSuccess("{\"access_token\":\"new-token\",\"expires_in\":300}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer")).andExpect(header("Authorization","Bearer new-token")).andRespond(withSuccess(generated("ABCDEFGHIJKLM",100,"MB"),MediaType.APPLICATION_JSON));client.generate("ABCDEFGHIJKLM",BigDecimal.valueOf(100),"VTABCDEFGHIJKLM");server.verify();
 }
 @Test void rejectsProviderAmountOrBankMismatch(){var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=new VietQrApiClient(properties(),Clock.systemUTC(),builder);server.expect(requestTo("https://dev.vietqr.org/vqr/api/token_generate")).andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":300}",MediaType.APPLICATION_JSON));server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer")).andRespond(withSuccess(generated("ABCDEFGHIJKLM",99,"OTHER"),MediaType.APPLICATION_JSON));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->client.generate("ABCDEFGHIJKLM",BigDecimal.valueOf(100),"VTABCDEFGHIJKLM"));server.verify();}
}
