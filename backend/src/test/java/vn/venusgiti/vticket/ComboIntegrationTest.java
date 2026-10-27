package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:combo;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false","app.vietqr.enabled=false"})
@ActiveProfiles("demo")
class ComboIntegrationTest {
 @Autowired Products products;@Autowired InventoryService inventory;@Autowired BookingService service;@Autowired TicketStock stock;@Autowired TicketPdfService pdf;@Autowired ApiController api;
 LocalDate date(){return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
 Product product(Kind kind){var p=new Product();p.code="C-"+UUID.randomUUID().toString().substring(0,20);p.name="Vé Hoàng Kim";p.kind=kind;p.price=BigDecimal.valueOf(500000);return products.saveAndFlush(p);}
 Product base(int quantity){var p=product(Kind.TICKET);inventory.createBatch(new BatchInput("B-"+UUID.randomUUID().toString().substring(0,20).toUpperCase(Locale.ROOT),p.id,quantity,date(),date().plusYears(1)),"admin");return p;}
 Product combo(Product base){var p=product(Kind.COMBO);p.name="Mua 10 tặng 1";p.price=BigDecimal.valueOf(450000);p.comboTicketId=base.id;p.comboBuyQuantity=10;p.comboGiftQuantity=1;return products.saveAndFlush(p);}
 OrderInput input(LineInput... lines){return new OrderInput("Khách combo","0900000000","",date(),"",List.of(lines));}
 @Test void fullComboChargesBoughtQuantityAndIssuesGiftFromBaseStock() throws Exception{var base=base(11);var combo=combo(base);var b=service.create(input(new LineInput(combo.id,10)),Channel.COUNTER,"staff");assertEquals(0,b.total.compareTo(BigDecimal.valueOf(4500000)));assertEquals(11,b.lines.getFirst().ticketQuantity);service.cash(b.id,"staff");var issued=service.ticketList(b.id);assertEquals(11,issued.size());assertTrue(issued.stream().allMatch(t->base.name.equals(t.productName)&&t.smileSerial!=null));assertEquals(11,issued.stream().map(t->t.smileBarcode).distinct().count());try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf.generate(b.accessToken))){assertEquals(11,doc.getNumberOfPages());}}
 @Test void partialOrNonMultipleQuantityRejectedForBothChannels(){var base=base(30);var combo=combo(base);for(var channel:Channel.values())for(int n:List.of(1,9,11,19))assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(input(new LineInput(combo.id,n)),channel,"test"));}
 @Test void twoComboGroupsProduce22Tickets(){var base=base(22);var combo=combo(base);var b=service.create(input(new LineInput(combo.id,20)),Channel.COUNTER,"staff");service.cash(b.id,"staff");assertEquals(22,service.ticketList(b.id).size());}
 @Test void regularAndTwoCombosShareOnePoolWithoutLosingLineSnapshots(){var base=base(24);var first=combo(base);var second=combo(base);second.includedServiceIds=Set.of();products.saveAndFlush(second);var b=service.create(input(new LineInput(base.id,2),new LineInput(first.id,10),new LineInput(second.id,10)),Channel.COUNTER,"staff");service.cash(b.id,"staff");assertEquals(24,service.ticketList(b.id).size());assertEquals(24,service.ticketList(b.id).stream().map(t->t.stockId).distinct().count());}
 @Test void giftStockIsRequiredAndFailureReleasesAllCodes(){var base=base(10);var combo=combo(base);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(input(new LineInput(combo.id,10)),Channel.COUNTER,"staff"));assertNotNull(service.create(input(new LineInput(base.id,10)),Channel.COUNTER,"staff"));}
 @Test void cancelRestoresBaseStock(){var base=base(11);var combo=combo(base);var b=service.create(input(new LineInput(combo.id,10)),Channel.ONLINE,"guest");service.cancel(b.id,"guest",false);assertNotNull(service.create(input(new LineInput(base.id,11)),Channel.ONLINE,"guest"));}
 @Test void orderKeepsTicketCountsAfterComboChanges(){var base=base(11);var combo=combo(base);var b=service.create(input(new LineInput(combo.id,10)),Channel.COUNTER,"staff");combo.comboGiftQuantity=5;products.saveAndFlush(combo);service.cash(b.id,"staff");assertEquals(11,service.ticketList(b.id).size());}
 @Test void comboCannotHaveOwnBatch(){var base=base(1);var combo=combo(base);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->inventory.createBatch(new BatchInput("NO-COMBO",combo.id,10,date(),date().plusYears(1)),"admin"));}
 @Test void inactiveBaseAndUnconfiguredComboCannotSell(){var base=base(11);var combo=combo(base);base.active=false;products.saveAndFlush(base);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(input(new LineInput(combo.id,10)),Channel.ONLINE,"guest"));var unconfigured=product(Kind.COMBO);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(input(new LineInput(unconfigured.id,10)),Channel.ONLINE,"guest"));}
 @Test void comboCannotReferenceServiceViaAdmin(){var p=product(Kind.SERVICE);var in=new ProductInput("INVALID-COMBO","Combo","",Kind.COMBO,BigDecimal.valueOf(450000),true,Set.of(),p.id,10,1);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->api.add(in,new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin","unused")));}
}
