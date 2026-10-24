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
 @Autowired BookingService service; @Autowired Products products; @Autowired Vouchers vouchers; @Autowired Bookings bookings; @Autowired Tickets tickets; @Autowired MockMvc mvc;
 Product ticket,extra;
 @BeforeEach void init(){ticket=product(Kind.TICKET,250000);extra=product(Kind.SERVICE,200000);}
 Product product(Kind kind,int price){Product p=new Product();p.code=UUID.randomUUID().toString();p.name="Sản phẩm thử";p.kind=kind;p.price=BigDecimal.valueOf(price);return products.saveAndFlush(p);}
 Voucher voucher(){Voucher v=new Voucher();v.code="VG-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);v.customerName="Khách quen";v.phone="0900000000";v.percentage=15;v.validFrom=Instant.now().minusSeconds(60);v.validUntil=Instant.now().plusSeconds(3600);return vouchers.saveAndFlush(v);}
 OrderInput input(String code){return new OrderInput("Khách quen","0900000000","demo@example.com",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),code,List.of(new LineInput(ticket.id,2),new LineInput(extra.id,1)));}
 Booking create(String code){return service.create(input(code),Channel.ONLINE,"guest");}
 @Test void discountCoversTicketsAndExtras(){var v=voucher();var b=create(v.code);assertEquals(0,new BigDecimal("700000").compareTo(b.subtotal));assertEquals(0,new BigDecimal("105000").compareTo(b.discount));assertEquals(0,new BigDecimal("595000").compareTo(b.total));}
 @Test void cancelReleasesVoucherAcrossChannels(){var v=voucher();var a=create(v.code);assertThrows(ResponseStatusException.class,()->create(v.code));service.cancel(a.id,"guest",false);var b=service.create(input(v.code),Channel.COUNTER,"staff");service.cash(b.id,"staff");assertThrows(ResponseStatusException.class,()->create(v.code));assertEquals(b.id,vouchers.findById(v.id).orElseThrow().usedOrderId);}
 @Test void repeatedPaymentDoesNotIssueDuplicateTickets(){var b=create(null);service.pay(b.id,"DEMO","ref","demo");service.pay(b.id,"DEMO","ref","demo");assertEquals(2,tickets.findByBookingId(b.id).size());}
 @Test void qrCheckInOnlyOnce(){var b=create(null);service.pay(b.id,"DEMO","ref","demo");var t=tickets.findByBookingId(b.id).get(0);assertNotNull(service.checkIn(t.qrToken,"staff").checkedInAt);assertThrows(ResponseStatusException.class,()->service.checkIn(t.qrToken,"staff"));}
 @Test void expiredVoucherRejected(){var v=voucher();v.validUntil=Instant.now().minusSeconds(1);vouchers.saveAndFlush(v);assertThrows(ResponseStatusException.class,()->create(v.code));}
 @Test void wrongCustomerRejected(){var v=voucher();v.phone="0911111111";vouchers.saveAndFlush(v);assertThrows(ResponseStatusException.class,()->create(v.code));}
 @Test void revokedVoucherRejected(){var v=voucher();v.revoked=true;vouchers.saveAndFlush(v);assertThrows(ResponseStatusException.class,()->create(v.code));}
 @Test void priceSnapshotSurvivesCatalogChange(){var b=create(null);ticket.price=BigDecimal.ONE;products.saveAndFlush(ticket);assertEquals(0,new BigDecimal("700000").compareTo(bookings.findById(b.id).orElseThrow().total));}
 @Test void expiredOrderCannotBePaidAndReleasesVoucher(){var v=voucher();var b=create(v.code);b=bookings.findById(b.id).orElseThrow();b.expiresAt=Instant.now().minusSeconds(1);bookings.saveAndFlush(b);String id=b.id;assertThrows(ResponseStatusException.class,()->service.pay(id,"DEMO","ref","demo"));service.cancel(id,"system",true);assertNotNull(create(v.code));}
 @Test void cashCannotSettleOnlineOrder(){var b=create(null);assertThrows(ResponseStatusException.class,()->service.cash(b.id,"staff"));}
 @Test void duplicateProductLineRejected(){var in=new OrderInput("Test","0900000000","",LocalDate.now(),null,List.of(new LineInput(ticket.id,1),new LineInput(ticket.id,1)));assertThrows(ResponseStatusException.class,()->service.create(in,Channel.ONLINE,"guest"));}
 @Test void concurrentReservationHasOnlyOneWinner() throws Exception {var v=voucher();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);Callable<Boolean> action=()->{start.await();try{create(v.code);return true;}catch(ResponseStatusException e){return false;}};try{var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertEquals(1,(a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}}
 @Test void concurrentCheckInHasOnlyOneWinner() throws Exception {var b=create(null);service.pay(b.id,"DEMO","ref","demo");String token=tickets.findByBookingId(b.id).get(0).qrToken;var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);Callable<Boolean> action=()->{start.await();try{service.checkIn(token,"staff");return true;}catch(ResponseStatusException e){return false;}};try{var a=pool.submit(action);var c=pool.submit(action);start.countDown();assertEquals(1,(a.get(10,TimeUnit.SECONDS)?1:0)+(c.get(10,TimeUnit.SECONDS)?1:0));}finally{pool.shutdownNow();}}
 @Test void adminEndpointsProtected() throws Exception {mvc.perform(get("/api/admin/vouchers")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/vouchers").with(httpBasic("staff","demo-staff-change-me"))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/vouchers").with(httpBasic("admin","demo-admin-change-me"))).andExpect(status().isOk());}
 @Test void csrfAndInputValidationEnforced() throws Exception {mvc.perform(post("/api/public/orders").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(post("/api/public/orders").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());}
 @Test void phoneNormalization(){assertEquals("0900000000",BookingService.phone("+84900000000"));}
}
