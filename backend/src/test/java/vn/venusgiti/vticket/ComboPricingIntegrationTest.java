package vn.venusgiti.vticket;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:combopricing;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class ComboPricingIntegrationTest {
 @Autowired ComboPricing pricing;@Autowired Holidays holidays;@Autowired Products products;@Autowired InventoryService inventory;@Autowired BookingService bookings;@Autowired Bookings orders;@Autowired MockMvc mvc;
 Product combo;
 @BeforeEach void setup(){
  var base=new Product();base.code="B-"+UUID.randomUUID();base.name="Vé gốc";base.kind=Kind.TICKET;base.price=BigDecimal.valueOf(100);base=products.saveAndFlush(base);
  inventory.createBatch(new BatchInput("B-"+UUID.randomUUID().toString().substring(0,20).toUpperCase(Locale.ROOT),base.id,200,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),LocalDate.of(2030,12,31)),"admin");
  combo=new Product();combo.code="C-"+UUID.randomUUID();combo.name="Combo";combo.kind=Kind.COMBO;combo.price=BigDecimal.valueOf(100);combo.weekendPrice=BigDecimal.valueOf(150);combo.holidayPrice=BigDecimal.valueOf(200);combo.comboTicketId=base.id;combo.comboBuyQuantity=10;combo.comboGiftQuantity=1;combo=products.saveAndFlush(combo);
 }
 Booking order(LocalDate date,Channel channel){return bookings.create(new OrderInput("Test",null,"",date,null,List.of(new LineInput(combo.id,10))),channel,"test");}
 @Test void visitDateSetsPriceForOnlineAndCounterIncludingHolidayWeekend(){
  for(var channel:Channel.values())for(var item:Map.of("2027-01-04",100,"2027-01-09",150,"2027-01-10",150,"2027-01-02",200,"2027-11-24",200).entrySet()){
   var b=order(LocalDate.parse(item.getKey()),channel);assertEquals(0,b.total.compareTo(BigDecimal.valueOf(item.getValue()*10)));assertEquals(0,b.lines.getFirst().unitPrice.compareTo(BigDecimal.valueOf(item.getValue())));assertEquals(11,b.lines.getFirst().ticketQuantity);
  }
 }
 @Test void seededCalendarMatchesAllTwentyRequestedDates(){
  assertEquals(20,holidays.findByDateGreaterThanEqualAndDateLessThanOrderByDate(LocalDate.of(2027,1,1),LocalDate.of(2028,1,1)).size());
  for(String date:List.of("2027-01-01","2027-01-02","2027-01-03","2027-02-07","2027-02-08","2027-02-09","2027-02-10","2027-04-16","2027-04-17","2027-04-18","2027-04-30","2027-05-01","2027-05-02","2027-05-03","2027-09-02","2027-09-03","2027-09-04","2027-09-05","2027-11-24","2027-12-31"))assertEquals("HOLIDAY",pricing.dayType(LocalDate.parse(date)));
 }
 @Test void adminPersistsSpecialPricesAndRejectsNegativePrice()throws Exception{
  var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
  var input=new ProductInput("PRICE-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),"Combo giá","",Kind.COMBO,BigDecimal.valueOf(100),true,Set.of(),combo.comboTicketId,10,1,BigDecimal.valueOf(150),BigDecimal.valueOf(200));
  mvc.perform(post("/api/admin/products").with(csrf()).with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andExpect(jsonPath("$.weekendPrice").value(150)).andExpect(jsonPath("$.holidayPrice").value(200));
  var invalid=new ProductInput("BAD-PRICE","Combo giá","",Kind.COMBO,BigDecimal.valueOf(100),true,Set.of(),combo.comboTicketId,10,1,BigDecimal.valueOf(-1),BigDecimal.valueOf(200));
  mvc.perform(post("/api/admin/products").with(csrf()).with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content(mapper.writeValueAsString(invalid))).andExpect(status().isBadRequest());
 }
 @Test void ticketPricesPersistViaAdminAndApplyForBothChannels()throws Exception{
  var base=products.findById(combo.comboTicketId).orElseThrow();
  var update=new ProductInput(base.code.toUpperCase(Locale.ROOT),base.name,"",Kind.TICKET,BigDecimal.valueOf(250),true,Set.of(),null,null,null,BigDecimal.valueOf(300),BigDecimal.valueOf(400));
  var json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(update);
  mvc.perform(put("/api/admin/products/"+base.id).with(csrf()).with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.weekendPrice").value(300)).andExpect(jsonPath("$.holidayPrice").value(400));
  for(var channel:Channel.values())for(var item:Map.of("2027-01-04",250,"2027-01-09",300,"2027-01-10",300,"2027-01-02",400).entrySet()){
   var order=bookings.create(new OrderInput("Test",null,"",LocalDate.parse(item.getKey()),null,List.of(new LineInput(base.id,2))),channel,"test");
   assertEquals(0,order.total.compareTo(BigDecimal.valueOf(item.getValue()*2)));assertEquals(0,order.lines.getFirst().unitPrice.compareTo(BigDecimal.valueOf(item.getValue())));
   if(item.getKey().equals("2027-01-02")){
    // Base ticket rates must not override the independent combo rates.
    assertEquals(0,order(LocalDate.parse(item.getKey()),channel).total.compareTo(BigDecimal.valueOf(2000)));
   }
  }
 }
 @Test void ticketOrderKeepsPriceAfterLaterPriceChange(){
  var base=products.findById(combo.comboTicketId).orElseThrow();base.weekendPrice=BigDecimal.valueOf(300);products.saveAndFlush(base);
  var b=bookings.create(new OrderInput("Test",null,"",LocalDate.of(2027,1,9),null,List.of(new LineInput(base.id,1))),Channel.ONLINE,"test");
  base.weekendPrice=BigDecimal.valueOf(999);products.saveAndFlush(base);
  assertEquals(0,orders.findById(b.id).orElseThrow().total.compareTo(BigDecimal.valueOf(300)));
 }
 @Test void nullPricesFallBackAndServicesUseBase(){
  combo.weekendPrice=null;combo.holidayPrice=null;assertEquals(combo.price,pricing.price(combo,"HOLIDAY"));assertEquals(combo.price,pricing.price(combo,"WEEKEND"));
  combo.holidayPrice=BigDecimal.ZERO;assertEquals(BigDecimal.ZERO,pricing.price(combo,"HOLIDAY"));combo.kind=Kind.SERVICE;assertEquals(combo.price,pricing.price(combo,"HOLIDAY"));
 }
 @Test void existingOrderPriceDoesNotChangeWhenPricesOrCalendarChange(){
  var b=order(LocalDate.of(2027,1,2),Channel.COUNTER);combo.holidayPrice=BigDecimal.valueOf(999);products.saveAndFlush(combo);
  assertEquals(0,orders.findById(b.id).orElseThrow().total.compareTo(BigDecimal.valueOf(2000)));
  assertEquals(0,order(LocalDate.of(2027,1,2),Channel.COUNTER).total.compareTo(BigDecimal.valueOf(9990)));
 }
 @Test void adminCanEditYearAndStaffCannotAndBadInputIsRejected()throws Exception{
  mvc.perform(put("/api/admin/holidays/2029").with(csrf()).with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content("{\"days\":[{\"date\":\"2029-01-02\",\"name\":\"Ngày thử\"}]}" )).andExpect(status().isOk());
  assertEquals("HOLIDAY",pricing.dayType(LocalDate.of(2029,1,2)));
  mvc.perform(put("/api/admin/holidays/2029").with(csrf()).with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content("{\"days\":[{\"date\":\"2029-01-02\",\"name\":\"Tên sửa\"},{\"date\":\"2029-01-03\",\"name\":\"Thêm\"}]}" )).andExpect(status().isOk());
  mvc.perform(get("/api/public/holidays?year=2029")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Tên sửa"));
  mvc.perform(put("/api/admin/holidays/2029").with(csrf()).with(httpBasic("staff","demo-staff-change-me")).contentType("application/json").content("{\"days\":[]}")).andExpect(status().isForbidden());
  for(String body:List.of("{\"days\":[{\"date\":\"2028-01-01\",\"name\":\"Sai năm\"}]}","{\"days\":[{\"date\":\"2029-01-01\",\"name\":\"A\"},{\"date\":\"2029-01-01\",\"name\":\"B\"}]}"))mvc.perform(put("/api/admin/holidays/2029").with(csrf()).with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  assertEquals(2,holidays.findByDateGreaterThanEqualAndDateLessThanOrderByDate(LocalDate.of(2029,1,1),LocalDate.of(2030,1,1)).size());
 }
}
