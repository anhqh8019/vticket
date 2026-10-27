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

 @org.springframework.test.context.DynamicPropertySource
 static void seedPreviousVersion(org.springframework.test.context.DynamicPropertyRegistry registry) throws Exception {
  String url=System.getenv("SQLSERVER_TEST_URL");String password=System.getenv("SQLSERVER_TEST_PASSWORD");
  org.flywaydb.core.Flyway.configure().dataSource(url,"sa",password).locations("classpath:db/migration/sqlserver").target("1").load().migrate();
  try(var connection=java.sql.DriverManager.getConnection(url,"sa",password);var statement=connection.createStatement()){
   statement.executeUpdate("INSERT INTO products(code,name,kind,price,active) VALUES('CI-LEGACY','Legacy ticket','TICKET',100000,1)");
   statement.executeUpdate("INSERT INTO bookings(id,access_token,customer_name,phone,visit_date,channel,status,discount_percent,subtotal,discount,total,created_at,expires_at) VALUES('ci-legacy-order','ci-legacy-token','Legacy guest','0900000000',CURRENT_TIMESTAMP,'COUNTER','PAID',0,200000,0,200000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   statement.executeUpdate("INSERT INTO tickets(id,booking_id,qr_token,product_name,included_services,visit_date) VALUES('ci-old-1','ci-legacy-order','ci-qr-old-1','Legacy ticket','',CURRENT_TIMESTAMP),('ci-old-2','ci-legacy-order','ci-qr-old-2','Legacy ticket','',CURRENT_TIMESTAMP)");
  }
 }
 @Autowired Tickets tickets; @Autowired InventoryService inventory; @Autowired TicketPdfService pdf; @Autowired Products products;@Autowired Vouchers vouchers;@Autowired BookingService service;
 @Test void migrateAndSellWithSingleUseVoucher() throws Exception {assertNull(tickets.findById("ci-old-1").orElseThrow().stockId);assertNull(tickets.findById("ci-old-2").orElseThrow().stockId);Product p=new Product();p.code="CI-"+UUID.randomUUID();p.name="Vé kiểm thử SQL Server";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(250000);products.saveAndFlush(p);var date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));inventory.createBatch(new BatchInput("CI-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),p.id,5,date,date.plusYears(1)),"test");Voucher v=new Voucher();v.code="CI-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);v.customerName="Khách CI";v.phone="0900000000";v.percentage=10;v.validFrom=Instant.now().minusSeconds(60);v.validUntil=Instant.now().plusSeconds(3600);vouchers.saveAndFlush(v);var b=service.create(new OrderInput("Khách CI","0900000000","",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),v.code,List.of(new LineInput(p.id,2))),Channel.COUNTER,"staff");assertEquals(0,BigDecimal.valueOf(450000).compareTo(b.total));service.cash(b.id,"staff");var t=service.ticketList(b.id);assertEquals(2,t.size());assertNotNull(t.get(0).ticketCode);assertTrue(pdf.generate(b.accessToken).length>1000);service.checkIn(t.get(0).qrToken,"staff");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.checkIn(t.get(0).qrToken,"staff"));assertEquals(b.id,vouchers.findById(v.id).orElseThrow().usedOrderId);}

 @Autowired PaymentIntents paymentIntents; @Autowired VietQrPaymentService payments;
 @Test void bankCallbackMigratesAndIssuesOnce() throws Exception {
  Product p=new Product();p.code="BANK-"+UUID.randomUUID().toString().substring(0,20);p.name="Vé VietQR SQL Server";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(100000);products.saveAndFlush(p);
  var date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));inventory.createBatch(new BatchInput("BANK-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),p.id,3,date,date.plusYears(1)),"test");
  var b=service.create(new OrderInput("Khách SQL","0900000000","",date,"",List.of(new LineInput(p.id,2))),Channel.ONLINE,"test");
  PaymentIntent intent=new PaymentIntent();intent.id="SQL"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(Locale.ROOT);intent.bookingId=b.id;intent.state=IntentState.READY;intent.amount=b.total;intent.bankCode="MB";intent.bankAccount="000000000000";intent.accountName="SQL TEST";intent.content="VT"+intent.id;intent.createdAt=Instant.now();intent.expiresAt=b.expiresAt;intent.attemptId=UUID.randomUUID().toString();intent.attemptStartedAt=Instant.now();paymentIntents.saveAndFlush(intent);
  // Set the same payment selection normally persisted by initiate(), without calling a live bank.
  selectVietQr(b.id);
  var request=new VietQrCallbackController.SyncRequest(intent.bankAccount,b.total,"C",intent.content,"sql-"+UUID.randomUUID(),System.currentTimeMillis(),"ref-"+UUID.randomUUID(),intent.id,null,null,null,null,null);
  var first=payments.callback(request);var duplicate=payments.callback(request);assertEquals(ReceiptState.APPLIED,first.state);assertEquals(first.id,duplicate.id);assertEquals(2,service.ticketList(b.id).size());assertTrue(pdf.generate(b.accessToken).length>1000);
 }
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager; @Autowired Bookings bookings;
 private void selectVietQr(String id){new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->bookings.lockId(id).orElseThrow().paymentMethod="VIETQR");}

 @Test void importSmileIdentifiersOnSqlServer(){
  var p=new Product();p.code="SM-"+UUID.randomUUID().toString().substring(0,20);p.name="Vé Smile SQL";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(100000);products.saveAndFlush(p);var date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));var key=UUID.randomUUID().toString().substring(0,12);var serial="VN"+key;var barcode="0000"+key;
  var batch=inventory.importSmile(new SmileBatchInput("SM-"+key.toUpperCase(Locale.ROOT),p.id,"SM-01","Loại vé Smile",date,date.plusYears(1),List.of(new SmileCodeInput(serial,barcode))),"admin");assertFalse(batch.active);inventory.active(batch.id,true,"admin");var b=service.create(new OrderInput("Khách Smile","0900000000","",date,"",List.of(new LineInput(p.id,1))),Channel.COUNTER,"staff");service.cash(b.id,"staff");var ticket=service.ticketList(b.id).getFirst();assertEquals(serial,ticket.smileSerial);assertEquals(barcode,ticket.smileBarcode);assertEquals("SM-01",ticket.smileTicketTypeCode);assertEquals("SMILE",ticket.source);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->inventory.importSmile(new SmileBatchInput("DUP-"+key.toUpperCase(Locale.ROOT),p.id,"SM-01","Loại vé Smile",date,date.plusYears(1),List.of(new SmileCodeInput(serial,"other-"+key))),"admin"));
 }
}
