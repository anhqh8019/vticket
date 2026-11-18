package vn.venusgiti.vticket;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.MonthlyDomain.*;
import vn.venusgiti.vticket.Requests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:monthly;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","app.seed-data=false","app.ticket-email.enabled=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class MonthlyIntegrationTest {
 static class TestClock extends Clock {
  volatile Instant instant=Instant.parse("2027-01-04T03:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(instant,zone);}public Instant instant(){return instant;}
 }
 @TestConfiguration static class Config {@Bean @Primary TestClock monthlyClock(){return new TestClock();}}
 @Autowired MonthlyService monthly;@Autowired Products products;@Autowired InventoryService inventory;@Autowired BookingService service;@Autowired MonthlyPasses passes;@Autowired Bookings bookings;@Autowired TicketStock stock;@Autowired TicketPdfService pdf;@Autowired MockMvc mvc;@Autowired TestClock clock;
 Product base,meal;
 Product product(String name){var p=new Product();p.code="M-"+UUID.randomUUID();p.name=name;p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(999);return products.saveAndFlush(p);}
 void batch(Product p,int quantity){inventory.createBatch(new BatchInput("M-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),p.id,quantity,LocalDate.of(2027,1,1),LocalDate.of(2027,12,31)),"admin");}
 void plan(Tier tier){monthly.save(tier,new MonthlyService.PlanInput(base.id,BigDecimal.valueOf(100),BigDecimal.valueOf(150),true),"admin");}
 Booking sale(Tier tier,String...dates){return monthly.sell(new MonthlyService.SaleInput(tier,"Khách tháng",null,"",Arrays.stream(dates).map(LocalDate::parse).toList()),"staff");}
 MonthlyService.RedeemInput redeemInput(Booking b){return new MonthlyService.RedeemInput(passes.findById(b.id).orElseThrow().qrToken,true,true);}
 @BeforeEach void setup(){clock.instant=Instant.parse("2027-01-04T03:00:00Z");base=product("Vé tắm");meal=product("Vé ăn");batch(base,20);for(var t:Tier.values())plan(t);}
 @Test void pricesDatesAndPaymentGateWithPdf()throws Exception{
  var b=sale(Tier.GOLD,"2027-01-04","2027-01-09","2027-01-10");assertEquals(0,b.total.compareTo(BigDecimal.valueOf(400)));assertEquals(Channel.COUNTER,b.channel);assertNull(monthly.view(b.id).qrToken());assertTrue(service.ticketList(b.id).isEmpty());
  assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));assertThrows(ResponseStatusException.class,()->pdf.generate(b.accessToken));
  service.cash(b.id,"staff");assertNotNull(monthly.view(b.id).qrToken());assertTrue(service.ticketList(b.id).isEmpty());assertTrue(pdf.generate(b.accessToken).length>1000);
  var issued=monthly.redeem(redeemInput(b),"staff");assertEquals(1,issued.tickets().size());assertFalse(issued.alreadyIssued());assertEquals(2,monthly.view(b.id).remaining());
  var retry=monthly.redeem(redeemInput(b),"staff");assertTrue(retry.alreadyIssued());assertEquals(issued.accessToken(),retry.accessToken());assertEquals(issued.tickets().getFirst().id,retry.tickets().getFirst().id);
  service.checkIn(issued.tickets().getFirst().qrToken,"gate");assertThrows(ResponseStatusException.class,()->service.checkIn(issued.tickets().getFirst().qrToken,"gate"));
  assertTrue(pdf.generate(issued.accessToken()).length>1000);
 }
 @Test void silverRejectsWeekendsAndHolidaysAndDatesMustBeUniqueSameMonth(){
  for(var dates:List.of(new String[]{"2027-01-09"},new String[]{"2027-11-24"},new String[]{"2027-01-04","2027-02-04"},new String[]{"2027-01-04","2027-01-04"},new String[]{"2027-01-03"}))assertThrows(ResponseStatusException.class,()->sale(Tier.SILVER,dates));
  assertEquals(0,sale(Tier.SILVER,"2027-01-04").total.compareTo(BigDecimal.valueOf(100)));
 }
 @Test void platinumUsesOneGateTicketAndRecordsPhysicalMealOnlyOnce(){
  var b=sale(Tier.PLATINUM,"2027-01-04");service.cash(b.id,"staff");
  var token=redeemInput(b).qrToken();assertThrows(ResponseStatusException.class,()->monthly.redeem(new MonthlyService.RedeemInput(token,true,false),"staff"));
  assertNull(passes.findById(b.id).orElseThrow().days.get(LocalDate.of(2027,1,4)).issuedBookingId);
  var first=monthly.redeem(new MonthlyService.RedeemInput(token,true,true),"staff");assertEquals(1,first.tickets().size());assertEquals("Vé tắm",first.tickets().getFirst().productName);
  assertTrue(monthly.view(b.id).days().getFirst().physicalMealIssued());assertEquals(first.accessToken(),monthly.redeem(new MonthlyService.RedeemInput(token,true,false),"staff").accessToken());
 }
 @Test void noGateStockDoesNotConsumeDayOrRecordMeal(){
  var empty=product("Vé hết kho");monthly.save(Tier.PLATINUM,new MonthlyService.PlanInput(empty.id,BigDecimal.valueOf(100),BigDecimal.valueOf(150),true),"admin");
  var b=sale(Tier.PLATINUM,"2027-01-04");service.cash(b.id,"staff");assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));assertFalse(monthly.view(b.id).days().getFirst().physicalMealIssued());assertEquals(1,monthly.view(b.id).remaining());
 }
 @Test void sameCardCanBeUsedOnDifferentSelectedDaysWithNewSmileCodes(){
  var b=sale(Tier.GOLD,"2027-01-04","2027-01-05","2027-01-09");service.cash(b.id,"staff");var first=monthly.redeem(redeemInput(b),"staff");clock.instant=Instant.parse("2027-01-05T03:00:00Z");var second=monthly.redeem(redeemInput(b),"staff");assertNotEquals(first.tickets().getFirst().smileBarcode,second.tickets().getFirst().smileBarcode);
  clock.instant=Instant.parse("2027-01-06T03:00:00Z");assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));clock.instant=Instant.parse("2027-02-01T03:00:00Z");assertEquals("EXPIRED",monthly.view(b.id).status());assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));
 }
 @Test void snapshotSurvivesPlanEditsAndConcurrentCountersIssueOnlyOnce()throws Exception{
  var b=sale(Tier.GOLD,"2027-01-04");service.cash(b.id,"staff");monthly.save(Tier.GOLD,new MonthlyService.PlanInput(meal.id,BigDecimal.valueOf(900),BigDecimal.valueOf(999),false),"admin");
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{
   Callable<MonthlyService.Issued> task=()->{start.await();return monthly.redeem(redeemInput(b),"staff");};var a=pool.submit(task);var c=pool.submit(task);start.countDown();var one=a.get(15,TimeUnit.SECONDS);var two=c.get(15,TimeUnit.SECONDS);assertEquals(one.accessToken(),two.accessToken());assertEquals("Vé tắm",one.tickets().getFirst().productName);assertEquals(0,bookings.findById(b.id).orElseThrow().total.compareTo(BigDecimal.valueOf(100)));
  }finally{pool.shutdownNow();}
 }
 @Test void httpSecurityValidationAndPaidMonthlyDetail()throws Exception{
  mvc.perform(get("/api/staff/monthly/plans")).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/admin/monthly/plans/GOLD").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/staff/monthly/orders").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"tier\":\"GOLD\",\"holderName\":\"Test\",\"dates\":[]}")).andExpect(status().isBadRequest());
  var b=sale(Tier.GOLD,"2027-01-04");mvc.perform(get("/api/public/orders/"+b.accessToken)).andExpect(status().isOk()).andExpect(jsonPath("$.monthlyPass.qrToken").doesNotExist());service.cash(b.id,"staff");
  mvc.perform(get("/api/public/orders/"+b.accessToken)).andExpect(status().isOk()).andExpect(jsonPath("$.monthlyPass.tier").value("GOLD")).andExpect(jsonPath("$.monthlyPass.qrToken").isNotEmpty());
  assertThrows(ResponseStatusException.class,()->monthly.redeem(new MonthlyService.RedeemInput(redeemInput(b).qrToken(),false,false),"staff"));
 }
}
