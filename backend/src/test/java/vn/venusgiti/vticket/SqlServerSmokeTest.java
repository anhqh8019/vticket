package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
@EnabledIfEnvironmentVariable(named="SQLSERVER_TEST_URL",matches=".+")
@SpringBootTest(properties={"spring.datasource.url=${SQLSERVER_TEST_URL}","spring.datasource.username=sa","spring.datasource.password=${SQLSERVER_TEST_PASSWORD}","app.admin-password=sql-test-admin-only","app.staff-password=sql-test-staff-only","app.seed-data=false"})
class SqlServerSmokeTest {
 @Autowired Products products;@Autowired Vouchers vouchers;@Autowired BookingService service;
 @Test void migrateAndSellWithSingleUseVoucher(){Product p=new Product();p.code="CI-"+UUID.randomUUID();p.name="Vé kiểm thử SQL Server";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(250000);products.saveAndFlush(p);Voucher v=new Voucher();v.code="CI-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);v.customerName="Khách CI";v.phone="0900000000";v.percentage=10;v.validFrom=Instant.now().minusSeconds(60);v.validUntil=Instant.now().plusSeconds(3600);vouchers.saveAndFlush(v);var b=service.create(new OrderInput("Khách CI","0900000000","",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),v.code,List.of(new LineInput(p.id,2))),Channel.COUNTER,"staff");assertEquals(0,BigDecimal.valueOf(450000).compareTo(b.total));service.cash(b.id,"staff");var t=service.ticketList(b.id);assertEquals(2,t.size());service.checkIn(t.get(0).qrToken,"staff");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.checkIn(t.get(0).qrToken,"staff"));assertEquals(b.id,vouchers.findById(v.id).orElseThrow().usedOrderId);}
}
