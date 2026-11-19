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
 static final List<String> DEFAULT_DATES=List.of("2027-01-04","2027-01-05","2027-01-07","2027-01-08","2027-01-09","2027-01-10","2027-01-11","2027-01-12","2027-01-14","2027-01-15","2027-01-16","2027-01-17");
 String[] withDefault(String... extra){var dates=new TreeSet<>(DEFAULT_DATES);dates.addAll(List.of(extra));return dates.toArray(String[]::new);}
 Product base,meal;
 Product product(String name){var p=new Product();p.code="M-"+UUID.randomUUID();p.name=name;p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(999);return products.saveAndFlush(p);}
 void batch(Product p,int quantity){inventory.createBatch(new BatchInput("M-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),p.id,quantity,LocalDate.of(2027,1,1),LocalDate.of(2027,12,31)),"admin");}
 void plan(Tier tier){monthly.save(tier,new MonthlyService.PlanInput(base.id,BigDecimal.valueOf(100),BigDecimal.valueOf(150),true),"admin");}
 Booking rawSale(Tier tier,String...dates){return monthly.sell(new MonthlyService.SaleInput(tier,"Khách tháng","0901234567","",Arrays.stream(dates).map(LocalDate::parse).toList()),"staff");}
 // Existing issuance tests use a valid sale fixture under the current minimum-day rules.
 Booking sale(Tier tier,String...dates){
  var chosen=new TreeSet<>(Arrays.stream(dates).map(LocalDate::parse).toList());
  if(tier==Tier.SILVER){for(var d=LocalDate.of(2027,1,4);chosen.size()<12;d=d.plusDays(1))if(d.getDayOfWeek().getValue()<=5)chosen.add(d);}
  else {
   for(var date:DEFAULT_DATES){if(chosen.size()>=12)break;chosen.add(LocalDate.parse(date));}
   for(var d:new ArrayList<>(chosen))if(d.getDayOfWeek().getValue()<6)chosen.add(d.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)));
  }
  return rawSale(tier,chosen.stream().map(LocalDate::toString).toArray(String[]::new));
 }
 MonthlyService.RedeemInput redeemInput(Booking b){return new MonthlyService.RedeemInput(passes.findById(b.id).orElseThrow().qrToken,true,true);}
 @BeforeEach void setup(){clock.instant=Instant.parse("2027-01-04T03:00:00Z");base=product("Vé tắm");meal=product("Vé ăn");batch(base,20);for(var t:Tier.values())plan(t);}
 @Test void pricesDatesAndPaymentGateWithPdf()throws Exception{
  var b=sale(Tier.GOLD,"2027-01-04","2027-01-09","2027-01-10");assertEquals(0,b.total.compareTo(BigDecimal.valueOf(1400)));assertEquals(Channel.COUNTER,b.channel);assertNull(monthly.view(b.id).qrToken());assertTrue(service.ticketList(b.id).isEmpty());
  assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));assertThrows(ResponseStatusException.class,()->pdf.generate(b.accessToken));
  service.cash(b.id,"staff");assertNotNull(monthly.view(b.id).qrToken());assertTrue(service.ticketList(b.id).isEmpty());assertTrue(pdf.generate(b.accessToken).length>1000);
  var issued=monthly.redeem(redeemInput(b),"staff");assertEquals(1,issued.tickets().size());assertFalse(issued.alreadyIssued());assertEquals(11,monthly.view(b.id).remaining());
  var retry=monthly.redeem(redeemInput(b),"staff");assertTrue(retry.alreadyIssued());assertEquals(issued.accessToken(),retry.accessToken());assertEquals(issued.tickets().getFirst().id,retry.tickets().getFirst().id);
  service.checkIn(issued.tickets().getFirst().qrToken,"gate");assertThrows(ResponseStatusException.class,()->service.checkIn(issued.tickets().getFirst().qrToken,"gate"));
  assertTrue(pdf.generate(issued.accessToken()).length>1000);
 }
 @Test void silverRejectsInvalidDatesAndDuplicates(){
  for(var dates:List.of(new String[]{"2027-01-09"},new String[]{"2027-11-24"},new String[]{"2027-01-04","2027-01-04"},new String[]{"2027-01-03"}))assertThrows(ResponseStatusException.class,()->rawSale(Tier.SILVER,dates));
  assertEquals(0,sale(Tier.SILVER,"2027-01-04").total.compareTo(BigDecimal.valueOf(1200)));
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
  var b=sale(Tier.PLATINUM,"2027-01-04");service.cash(b.id,"staff");assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));assertFalse(monthly.view(b.id).days().getFirst().physicalMealIssued());assertEquals(12,monthly.view(b.id).remaining());
 }
 @Test void sameCardCanBeUsedOnDifferentSelectedDaysWithNewSmileCodes(){
  var b=sale(Tier.GOLD,"2027-01-04","2027-01-05","2027-01-09");service.cash(b.id,"staff");var first=monthly.redeem(redeemInput(b),"staff");clock.instant=Instant.parse("2027-01-05T03:00:00Z");var second=monthly.redeem(redeemInput(b),"staff");assertNotEquals(first.tickets().getFirst().smileBarcode,second.tickets().getFirst().smileBarcode);
  clock.instant=Instant.parse("2027-01-06T03:00:00Z");assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));clock.instant=Instant.parse("2027-02-01T03:00:00Z");assertEquals("EXPIRED",monthly.view(b.id).status());assertThrows(ResponseStatusException.class,()->monthly.redeem(redeemInput(b),"staff"));
 }
 @Test void snapshotSurvivesPlanEditsAndConcurrentCountersIssueOnlyOnce()throws Exception{
  var b=sale(Tier.GOLD,"2027-01-04");service.cash(b.id,"staff");monthly.save(Tier.GOLD,new MonthlyService.PlanInput(meal.id,BigDecimal.valueOf(900),BigDecimal.valueOf(999),false),"admin");
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{
   Callable<MonthlyService.Issued> task=()->{start.await();return monthly.redeem(redeemInput(b),"staff");};var a=pool.submit(task);var c=pool.submit(task);start.countDown();var one=a.get(15,TimeUnit.SECONDS);var two=c.get(15,TimeUnit.SECONDS);assertEquals(one.accessToken(),two.accessToken());assertEquals("Vé tắm",one.tickets().getFirst().productName);assertEquals(0,bookings.findById(b.id).orElseThrow().total.compareTo(BigDecimal.valueOf(1400)));
  }finally{pool.shutdownNow();}
 }
 @Test void monthlyPhoneIsRequiredAtSaleAndDisplayedOnCard()throws Exception{
  for(String phone:new String[]{null,"","   ","12 -- ()"}){
   assertThrows(ResponseStatusException.class,()->monthly.sell(new MonthlyService.SaleInput(Tier.GOLD,"Khách",phone,"",List.of(LocalDate.of(2027,1,4))),"staff"));
   String value=phone==null?"null":"\""+phone+"\"";
   mvc.perform(post("/api/staff/monthly/orders").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"tier\":\"GOLD\",\"holderName\":\"Test\",\"phone\":"+value+",\"dates\":[\"2027-01-04\"]}"))
    .andExpect(status().is4xxClientError());
  }
  var b=monthly.sell(new MonthlyService.SaleInput(Tier.GOLD,"Khách","+84 (901) 234-567","",DEFAULT_DATES.stream().map(LocalDate::parse).toList()),"staff");
  assertEquals("0901234567",monthly.view(b.id).phone());
 }
 @Test void monthlyPdfHasPhoneCorrectMonthBenefitsAndDistinctBackgrounds()throws Exception{
  var colors=new HashSet<Integer>();
  for(var tier:Tier.values()){
   var b=sale(tier,"2027-01-04");service.cash(b.id,"staff");
   try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf.generate(b.accessToken))){
    assertEquals(1,doc.getNumberOfPages());
    assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A5.getWidth(),doc.getPage(0).getMediaBox().getWidth(),0.01f);
    assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A5.getHeight(),doc.getPage(0).getMediaBox().getHeight(),0.01f);
    var text=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
    assertTrue(text.contains("Họ tên: Khách tháng"));assertTrue(text.contains("SĐT: 0901234567"));assertTrue(text.contains("tháng 1 - 2027"));assertFalse(text.contains("VTicket"));assertFalse(text.contains("vật lý mỗi lần đến"));
    if(tier==Tier.PLATINUM)assertTrue(text.contains("+ 01 phiếu ăn"));else assertFalse(text.contains("phiếu ăn"));
    var image=new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,100);colors.add(image.getRGB(5,5));
    var qrCrop=image.getSubimage((int)(120*100.0/72),(int)((doc.getPage(0).getMediaBox().getHeight()-445)*100.0/72),(int)(180*100.0/72),(int)(180*100.0/72));
    var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(qrCrop)));
    assertEquals(monthly.view(b.id).qrToken(),new com.google.zxing.MultiFormatReader().decode(bitmap).getText());
   }
  }
  assertEquals(3,colors.size());
 }
 @Test void unifiedScanRecognizesMonthlyWithoutConsumingAndStillChecksInSingleTickets()throws Exception{
  var b=sale(Tier.PLATINUM,"2027-01-04");service.cash(b.id,"staff");var token=monthly.view(b.id).qrToken();
  mvc.perform(post("/api/staff/scan").contentType("application/json").content("{\"qrToken\":\""+token+"\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/staff/scan").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"qrToken\":\" "+token+" \"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("MONTHLY_PASS")).andExpect(jsonPath("$.monthlyPass.holderName").value("Khách tháng")).andExpect(jsonPath("$.monthlyPass.remaining").value(12)).andExpect(jsonPath("$.ticket").doesNotExist());
  assertTrue(service.ticketList(b.id).isEmpty());assertFalse(monthly.view(b.id).days().getFirst().physicalMealIssued());
  mvc.perform(post("/api/staff/check-in").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"qrToken\":\""+token+"\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("thẻ tháng")));
  var issued=monthly.redeem(new MonthlyService.RedeemInput(token,true,true),"staff");var ticket=issued.tickets().getFirst();
  mvc.perform(post("/api/staff/scan").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"qrToken\":\""+ticket.qrToken+"\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("TICKET")).andExpect(jsonPath("$.ticket.id").value(ticket.id)).andExpect(jsonPath("$.ticket.checkedInAt").isNotEmpty());
  mvc.perform(post("/api/staff/scan").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"qrToken\":\""+ticket.qrToken+"\"}")).andExpect(status().isConflict());
  mvc.perform(post("/api/staff/scan").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content("{\"qrToken\":\"invalid-token\"}")).andExpect(status().isNotFound());
 }
 @Test void thirtySelectedDaysMaximumIsEnforcedByServiceAndHttp()throws Exception{
  var dates=java.util.stream.IntStream.range(0,31).mapToObj(i->LocalDate.of(2027,1,4).plusDays(i)).toList();
  assertThrows(ResponseStatusException.class,()->monthly.sell(new MonthlyService.SaleInput(Tier.GOLD,"Khách","0901234567","",dates),"staff"));
  var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
  mvc.perform(post("/api/staff/monthly/orders").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(new MonthlyService.SaleInput(Tier.GOLD,"Khách","0901234567","",dates)))).andExpect(status().isBadRequest());
  var thirty=new ArrayList<>(dates.subList(0,28));thirty.add(LocalDate.of(2027,2,6));thirty.add(LocalDate.of(2027,2,7));
  var b=monthly.sell(new MonthlyService.SaleInput(Tier.GOLD,"Khách","0901234567","",thirty),"staff");assertEquals(30,monthly.view(b.id).days().size());
 }
 @Test void minimumDaysWeekendAndCrossMonthCrossYearPricing()throws Exception{
  assertThrows(ResponseStatusException.class,()->rawSale(Tier.SILVER,"2027-01-04"));
  for(var tier:List.of(Tier.GOLD,Tier.PLATINUM)){
   assertThrows(ResponseStatusException.class,()->rawSale(tier,"2027-01-04","2027-01-05"));
   assertThrows(ResponseStatusException.class,()->rawSale(tier,"2027-11-24")); // Weekday holiday is not a Saturday/Sunday.
   var b=rawSale(tier,"2027-01-25","2027-01-26","2027-01-27","2027-01-28","2027-01-29","2027-01-30","2027-02-01","2027-02-02","2027-02-03","2027-02-04","2027-02-06","2027-02-07");assertEquals(0,b.total.compareTo(BigDecimal.valueOf(1350)));
   service.cash(b.id,"staff");assertEquals(12,monthly.view(b.id).days().size());
   try(var document=org.apache.pdfbox.Loader.loadPDF(pdf.generate(b.accessToken))){
    assertEquals(1,document.getNumberOfPages());var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
    for(var date:List.of("tháng 1 - 2027: 25, 26, 27, 28, 29, 30","tháng 2 - 2027: 01, 02, 03, 04, 06, 07"))assertTrue(text.contains(date));
   }
  }
  var silver=rawSale(Tier.SILVER,"2027-01-25","2027-01-26","2027-01-27","2027-01-28","2027-01-29","2027-02-01","2027-02-02","2027-02-03","2027-02-04","2027-02-05","2027-02-11","2027-02-12");
  assertEquals(0,silver.total.compareTo(BigDecimal.valueOf(1200)));assertEquals(12,monthly.view(silver.id).days().size());
 }
 @Test void eachSelectedWeekNeedsItsOwnWeekendIncludingMonthAndYearBoundaries(){
  for(var tier:List.of(Tier.GOLD,Tier.PLATINUM)){
   assertThrows(ResponseStatusException.class,()->rawSale(tier,withDefault("2027-01-18")));
   assertThrows(ResponseStatusException.class,()->rawSale(tier,withDefault("2027-01-30","2027-02-01")));
   assertThrows(ResponseStatusException.class,()->rawSale(tier,withDefault("2027-12-27","2028-01-01","2028-01-03")));
   assertNotNull(rawSale(tier,withDefault()));
   assertNotNull(rawSale(tier,"2027-12-27","2027-12-28","2027-12-29","2027-12-30","2027-12-31","2028-01-01","2028-01-02","2028-01-03","2028-01-04","2028-01-05","2028-01-06","2028-01-08"));
   assertNotNull(rawSale(tier,withDefault("2027-02-06","2027-02-20"))); // Empty intervening weeks do not require visits.
  }
 }
 @Test void thirtyDaysInTwoMonthsFitOneA5PageWithCenteredQrAndNearbyInstructions()throws Exception{
  var dates=new ArrayList<LocalDate>();for(int i=0;i<28;i++)dates.add(LocalDate.of(2027,1,4).plusDays(i));dates.add(LocalDate.of(2027,2,6));dates.add(LocalDate.of(2027,2,7));
  var b=monthly.sell(new MonthlyService.SaleInput(Tier.GOLD,"Khách","0901234567","",dates),"staff");service.cash(b.id,"staff");
  try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf.generate(b.accessToken))){
   assertEquals(1,doc.getNumberOfPages());
   assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A5.getWidth(),doc.getPage(0).getMediaBox().getWidth(),0.01f);
   assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A5.getHeight(),doc.getPage(0).getMediaBox().getHeight(),0.01f);
   var text=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);assertTrue(text.contains("tháng 1 - 2027:"));assertTrue(text.contains("tháng 2 - 2027: 06, 07"));
   var rows=new HashMap<String,Float>();var monthFontSizes=new HashSet<Float>();
   var stripper=new org.apache.pdfbox.text.PDFTextStripper(){@Override protected void writeString(String value,List<org.apache.pdfbox.text.TextPosition> positions)throws java.io.IOException{if(!positions.isEmpty()){rows.put(value,positions.getFirst().getYDirAdj());if(value.contains("tháng "))for(var pos:positions)monthFontSizes.add(pos.getFontSizeInPt());}super.writeString(value,positions);}};
   stripper.getText(doc);assertEquals(1,monthFontSizes.size(),"Các dòng tháng phải có cùng cỡ chữ");
   var schedule=rows.entrySet().stream().filter(e->e.getKey().contains("tháng 2 - 2027")).findFirst().orElseThrow().getValue();
   var instruction=rows.entrySet().stream().filter(e->e.getKey().contains("Mỗi ngày đăng ký")).findFirst().orElseThrow().getValue();
   assertTrue(instruction>schedule&&instruction-schedule<50,"Hướng dẫn phải nằm sát lịch đăng ký");
  }
 }
 @Test void rejectThirdOrNonAdjacentMonthAndAllowDecemberJanuary()throws Exception{
  var invalid=withDefault("2027-02-06","2027-03-06");
  var error=assertThrows(ResponseStatusException.class,()->rawSale(Tier.GOLD,invalid));assertTrue(error.getReason().contains("2 tháng"));
  assertThrows(ResponseStatusException.class,()->rawSale(Tier.GOLD,withDefault("2027-03-06")));
  var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
  mvc.perform(post("/api/staff/monthly/orders").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(new MonthlyService.SaleInput(Tier.GOLD,"Khách","0901234567","",Arrays.stream(invalid).map(LocalDate::parse).toList())))).andExpect(status().isConflict());
 }
 @Test void allTiersRejectElevenDaysAndAcceptTwelveThroughHttp()throws Exception{
  var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
  for(var tier:Tier.values()){
   var chosen=new ArrayList<LocalDate>();
   if(tier==Tier.SILVER){for(var d=LocalDate.of(2027,1,4);chosen.size()<12;d=d.plusDays(1))if(d.getDayOfWeek().getValue()<=5)chosen.add(d);}
   else chosen.addAll(DEFAULT_DATES.stream().map(LocalDate::parse).toList());
   var eleven=new MonthlyService.SaleInput(tier,"Khách","0901234567","",chosen.subList(0,11));
   assertThrows(ResponseStatusException.class,()->monthly.sell(eleven,"staff"));
   mvc.perform(post("/api/staff/monthly/orders").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(eleven))).andExpect(status().isConflict());
   mvc.perform(post("/api/staff/monthly/orders").with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(new MonthlyService.SaleInput(tier,"Khách","0901234567","",chosen)))).andExpect(status().isOk());
  }
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
