package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:barcodeprefix;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false","app.ticket-barcode-prefix=7788"}) @ActiveProfiles("demo")
class TicketBarcodePrefixIntegrationTest {
 @Autowired InventoryService inventory;@Autowired Products products;@Autowired TicketStock stock;
 @Test void customPrefixUsesDurableIncreasingCounterAndDoesNotRegenerateExistingCodes(){
  var p=new Product();p.code="PREFIX";p.name="Prefix test";p.kind=Kind.TICKET;p.price=BigDecimal.ONE;p=products.saveAndFlush(p);var date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
  var one=inventory.createBatch(new BatchInput("PREFIX1",p.id,2,date,date.plusYears(1)),"admin");var rows=stock.findByBatchIdOrderByCodeAsc(one.id);
  assertEquals(List.of("7788000001","7788000002"),rows.stream().map(x->x.smileBarcode).toList());assertTrue(rows.stream().allMatch(x->x.smileSerialFromSeq&&x.smileSerial==null));
  inventory.generateIdentifiers(one.id,"admin");assertEquals(List.of("7788000001","7788000002"),stock.findByBatchIdOrderByCodeAsc(one.id).stream().map(x->x.smileBarcode).toList());
  var two=inventory.createBatch(new BatchInput("PREFIX2",p.id,1,date,date.plusYears(1)),"admin");assertEquals("7788000003",stock.findByBatchIdOrderByCodeAsc(two.id).getFirst().smileBarcode);
 }
}
