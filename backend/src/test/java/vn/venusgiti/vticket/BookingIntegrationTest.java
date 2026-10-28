package vn.venusgiti.vticket;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:vtickettest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class BookingIntegrationTest {
 @Autowired BookingService service; @Autowired Products products; @Autowired Vouchers vouchers; @Autowired Bookings bookings; @Autowired Tickets tickets; @Autowired MockMvc mvc; @Autowired InventoryService inventory;
 @Autowired TicketStock stock; @Autowired TicketPdfService pdf;
 Product ticket,extra;
 @BeforeEach void init(){ticket=product(Kind.TICKET,250000);extra=product(Kind.SERVICE,200000);}
 Product product(Kind kind,int price){Product p=new Product();p.code=UUID.randomUUID().toString();p.name="Sản phẩm thử";p.kind=kind;p.price=BigDecimal.valueOf(price);p=products.saveAndFlush(p);if(kind!=Kind.SERVICE){var date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));inventory.createBatch(new BatchInput("TEST-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),p.id,100,date,date.plusYears(1)),"test");}return p;}
 Voucher voucher(){Voucher v=new Voucher();v.code="VG-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);v.customerName="Khách quen";v.phone="0900000000";v.percentage=15;v.validFrom=Instant.now().minusSeconds(60);v.validUntil=Instant.now().plusSeconds(3600);return vouchers.saveAndFlush(v);}
 OrderInput input(String code){return new OrderInput("Khách quen","0900000000","demo@example.com",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),code,List.of(new LineInput(ticket.id,2),new LineInput(extra.id,1)));}
 Booking create(String code){return service.create(input(code),Channel.ONLINE,"guest");}
 @Test void discountCoversTicketsAndExtras(){var v=voucher();var b=create(v.code);assertEquals(0,new BigDecimal("700000").compareTo(b.subtotal));assertEquals(0,new BigDecimal("105000").compareTo(b.discount));assertEquals(0,new BigDecimal("595000").compareTo(b.total));}
 @Test void cancelReleasesVoucherAcrossChannels(){var v=voucher();var a=create(v.code);assertThrows(ResponseStatusException.class,()->create(v.code));service.cancel(a.id,"guest",false);var b=service.create(input(v.code),Channel.COUNTER,"staff");service.cash(b.id,"staff");assertThrows(ResponseStatusException.class,()->create(v.code));assertEquals(b.id,vouchers.findById(v.id).orElseThrow().usedOrderId);}
 @Test void repeatedPaymentDoesNotIssueDuplicateTickets(){var b=create(null);service.pay(b.id,"VIETQR","ref","demo");service.pay(b.id,"VIETQR","ref","demo");assertEquals(2,tickets.findByBookingId(b.id).size());}
 @Test void qrCheckInOnlyOnce(){var b=create(null);service.pay(b.id,"VIETQR","ref","demo");var t=tickets.findByBookingId(b.id).get(0);assertNotNull(service.checkIn(t.qrToken,"staff").checkedInAt);assertThrows(ResponseStatusException.class,()->service.checkIn(t.qrToken,"staff"));}
 @Test void expiredVoucherRejected(){var v=voucher();v.validUntil=Instant.now().minusSeconds(1);vouchers.saveAndFlush(v);assertThrows(ResponseStatusException.class,()->create(v.code));}
 @Test void wrongCustomerRejected(){var v=voucher();v.phone="0911111111";vouchers.saveAndFlush(v);assertThrows(ResponseStatusException.class,()->create(v.code));}
 @Test void revokedVoucherRejected(){var v=voucher();v.revoked=true;vouchers.saveAndFlush(v);assertThrows(ResponseStatusException.class,()->create(v.code));}
 @Test void priceSnapshotSurvivesCatalogChange(){var b=create(null);ticket.price=BigDecimal.ONE;products.saveAndFlush(ticket);assertEquals(0,new BigDecimal("700000").compareTo(bookings.findById(b.id).orElseThrow().total));}
 @Test void expiredOrderCannotBePaidAndReleasesVoucher(){var v=voucher();var b=create(v.code);b=bookings.findById(b.id).orElseThrow();b.expiresAt=Instant.now().minusSeconds(1);bookings.saveAndFlush(b);String id=b.id;assertThrows(ResponseStatusException.class,()->service.pay(id,"VIETQR","ref","demo"));service.cancel(id,"system",true);assertNotNull(create(v.code));}
 @Test void cashCannotSettleOnlineOrder(){var b=create(null);assertThrows(ResponseStatusException.class,()->service.cash(b.id,"staff"));}
 @Test void duplicateProductLineRejected(){var in=new OrderInput("Test","0900000000","",LocalDate.now(),null,List.of(new LineInput(ticket.id,1),new LineInput(ticket.id,1)));assertThrows(ResponseStatusException.class,()->service.create(in,Channel.ONLINE,"guest"));}
 @Test void concurrentReservationHasOnlyOneWinner() throws Exception {var v=voucher();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);Callable<Boolean> action=()->{start.await();try{create(v.code);return true;}catch(ResponseStatusException e){return false;}};try{var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertEquals(1,(a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}}
 @Test void concurrentCheckInHasOnlyOneWinner() throws Exception {var b=create(null);service.pay(b.id,"VIETQR","ref","demo");String token=tickets.findByBookingId(b.id).get(0).qrToken;var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);Callable<Boolean> action=()->{start.await();try{service.checkIn(token,"staff");return true;}catch(ResponseStatusException e){return false;}};try{var a=pool.submit(action);var c=pool.submit(action);start.countDown();assertEquals(1,(a.get(10,TimeUnit.SECONDS)?1:0)+(c.get(10,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}}
 @Test void adminEndpointsProtected() throws Exception {mvc.perform(get("/api/admin/vouchers")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/vouchers").with(httpBasic("staff","demo-staff-change-me"))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/vouchers").with(httpBasic("admin","demo-admin-change-me"))).andExpect(status().isOk());}
 @Test void csrfAndInputValidationEnforced() throws Exception {mvc.perform(post("/api/public/orders").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(post("/api/public/orders").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());}
 @Test void phoneNormalization(){assertEquals("0900000000",BookingService.phone("+84900000000"));}

 Product limited(int quantity){Product p=new Product();p.code=UUID.randomUUID().toString();p.name="Vé Tắm Hoàng Kim và trải nghiệm thư giãn thiên nhiên";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(250000);p=products.saveAndFlush(p);if(quantity>0){var date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));inventory.createBatch(new BatchInput("LOT-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),p.id,quantity,date,date.plusDays(1)),"test");}return p;}
 OrderInput one(Product p,int quantity){return new OrderInput("Khách thử","0900000000","",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),null,List.of(new LineInput(p.id,quantity)));}
 @Test void servicesCannotHaveBatches(){var date=LocalDate.now();assertThrows(ResponseStatusException.class,()->inventory.createBatch(new BatchInput("BAD-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),extra.id,10,date,date.plusDays(1)),"test"));}
 @Test void outOfStockRejected(){var p=limited(1);assertThrows(ResponseStatusException.class,()->service.create(one(p,2),Channel.ONLINE,"guest"));}
 @Test void cancelReturnsSameCodeToInventory(){var p=limited(1);var b=service.create(one(p,1),Channel.ONLINE,"guest");var held=stock.findAll().stream().filter(x->b.id.equals(x.reservedOrderId)).findFirst().orElseThrow();service.cancel(b.id,"guest",false);var c=service.create(one(p,1),Channel.COUNTER,"staff");service.cash(c.id,"staff");assertEquals(held.code,service.ticketList(c.id).get(0).ticketCode);}
 @Test void expiredReservationCannotReleaseNewOwnersCode(){var p=limited(1);var b=service.create(one(p,1),Channel.ONLINE,"guest");var row=stock.findAll().stream().filter(x->b.id.equals(x.reservedOrderId)).findFirst().orElseThrow();row.reservedUntil=Instant.now().minusSeconds(1);stock.saveAndFlush(row);var old=bookings.findById(b.id).orElseThrow();old.expiresAt=Instant.now().minusSeconds(1);bookings.saveAndFlush(old);var next=service.create(one(p,1),Channel.COUNTER,"staff");service.cancel(b.id,"system",true);service.cash(next.id,"staff");assertEquals(row.code,service.ticketList(next.id).get(0).ticketCode);}
 @Test void soldStockFollowsCheckIn(){var p=limited(1);var b=service.create(one(p,1),Channel.COUNTER,"staff");service.cash(b.id,"staff");var t=service.ticketList(b.id).get(0);assertEquals(StockState.SOLD,stock.findById(t.stockId).orElseThrow().state);service.checkIn(t.ticketCode,"staff");assertEquals(StockState.USED,stock.findById(t.stockId).orElseThrow().state);}
 @Test void batchDateRestrictsVisitDate(){var p=limited(1);var in=new OrderInput("Test","0900000000","",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2),null,List.of(new LineInput(p.id,1)));assertThrows(ResponseStatusException.class,()->service.create(in,Channel.ONLINE,"guest"));}
 @Test void disabledBatchPreventsNewSale(){var p=limited(1);var batch=(TicketBatch)inventory.list().stream().filter(m->((TicketBatch)m.get("batch")).productId.equals(p.id)).findFirst().orElseThrow().get("batch");inventory.active(batch.id,false,"admin");assertThrows(ResponseStatusException.class,()->service.create(one(p,1),Channel.ONLINE,"guest"));}
 @Test void lastCodesCannotBeOversoldConcurrently() throws Exception {var p=limited(2);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);Callable<Boolean> action=()->{start.await();try{service.create(one(p,2),Channel.ONLINE,"guest");return true;}catch(ResponseStatusException e){return false;}};try{var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}}
 @Test void multiProductFailureRollsBackAllReservations(){var a=limited(1);var b=limited(0);var in=new OrderInput("Test","0900000000","",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),null,List.of(new LineInput(a.id,1),new LineInput(b.id,1)));assertThrows(ResponseStatusException.class,()->service.create(in,Channel.ONLINE,"guest"));assertNotNull(service.create(one(a,1),Channel.ONLINE,"guest"));}
 @Test void pendingOrderCannotDownloadPdf(){var b=create(null);assertThrows(ResponseStatusException.class,()->pdf.generate(b.accessToken));}
 @Test void pdfContainsOneScannableQrPerPaidTicket() throws Exception {
  var p=limited(2);var b=service.create(one(p,2),Channel.COUNTER,"staff");service.cash(b.id,"staff");var issued=service.ticketList(b.id);var bytes=pdf.generate(b.accessToken);
  try(var document=org.apache.pdfbox.Loader.loadPDF(bytes)){
   assertEquals(2,document.getNumberOfPages());var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertTrue(text.contains("Vé Tắm Hoàng Kim"));
   var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);Set<String> decoded=new HashSet<>();for(int i=0;i<document.getNumberOfPages();i++){var full=renderer.renderImageWithDPI(i,200);double scale=200.0/72;var image=full.getSubimage((int)(70*scale),(int)((419.53-311)*scale),(int)(158*scale),(int)(158*scale));var source=new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image);var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source));decoded.add(new com.google.zxing.MultiFormatReader().decode(bitmap,Map.of(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS,List.of(com.google.zxing.BarcodeFormat.QR_CODE))).getText());}
   assertEquals(new HashSet<>(issued.stream().map(t->t.qrToken).toList()),decoded);
   Set<String> bars=new HashSet<>();for(int i=0;i<document.getNumberOfPages();i++){var image=renderer.renderImageWithDPI(i,200);var crop=image.getSubimage(0,(int)(image.getHeight()*0.65),image.getWidth(),(int)(image.getHeight()*0.16));var source=new com.google.zxing.client.j2se.BufferedImageLuminanceSource(crop);bars.add(new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source)),Map.of(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS,List.of(com.google.zxing.BarcodeFormat.CODE_128))).getText());}
   assertEquals(new HashSet<>(issued.stream().map(t->t.smileBarcode).toList()),bars);
   var preview=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0,120),"PNG",preview);System.out.println("TICKET_QA_PNG_BASE64="+Base64.getEncoder().encodeToString(preview.toByteArray()));
  }
  java.nio.file.Path target=java.nio.file.Path.of("target/qa/ticket-sample.pdf");java.nio.file.Files.createDirectories(target.getParent());java.nio.file.Files.write(target,bytes);
 }

 @Test void v1PaidTicketsSurviveAdditiveMigration() throws Exception {
  String url="jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1";
  org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration/h2").target("1").load().migrate();
  try(var c=java.sql.DriverManager.getConnection(url,"sa","");var st=c.createStatement()){
   st.executeUpdate("INSERT INTO products(code,name,kind,price,active) VALUES('LEGACY','Vé cũ','TICKET',100000,1)");
   st.executeUpdate("INSERT INTO bookings(id,access_token,customer_name,phone,visit_date,channel,status,discount_percent,subtotal,discount,total,created_at,expires_at) VALUES('legacy-order','legacy-token','Khách cũ','0900000000',CURRENT_DATE,'COUNTER','PAID',0,200000,0,200000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   st.executeUpdate("INSERT INTO tickets(id,booking_id,qr_token,product_name,included_services,visit_date) VALUES('old-1','legacy-order','qr-old-1','Vé cũ','',CURRENT_DATE),('old-2','legacy-order','qr-old-2','Vé cũ','',CURRENT_DATE)");
  }
  org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration/h2").load().migrate();
  try(var c=java.sql.DriverManager.getConnection(url,"sa","");var st=c.createStatement();var rs=st.executeQuery("SELECT COUNT(*) FROM tickets WHERE stock_id IS NULL")){assertTrue(rs.next());assertEquals(2,rs.getInt(1));}
 }
}
