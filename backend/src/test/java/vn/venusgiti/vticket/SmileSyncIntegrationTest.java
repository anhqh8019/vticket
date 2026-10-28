package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:smile-sync;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false","app.smile.enabled=true","app.smile.worker-enabled=false"})
@ActiveProfiles("demo") @Import(SmileSyncIntegrationTest.Config.class)
class SmileSyncIntegrationTest {
 @TestConfiguration static class Config {@Bean @Primary FakeSmile fakeSmile(){return new FakeSmile();}}
 static class FakeSmile implements SmileGateway {
  final Map<String,Synced> remote=new HashMap<>();boolean failAfterCommit;
  public List<Column> inspect(){return List.of();}
  public synchronized List<Synced> sync(Batch batch){var out=batch.codes().stream().map(c->remote.computeIfAbsent(c.barcode(),k->new Synced(c.stockId(),Integer.toString(remote.size()+1),c.barcode()))).toList();if(failAfterCommit){failAfterCommit=false;throw new IllegalStateException("Mất kết nối sau commit thử nghiệm");}return out;}
 }
 @Autowired InventoryService inventory;@Autowired Products products;@Autowired TicketBatches batches;@Autowired TicketStock stock;@Autowired SmileJobs jobs;@Autowired SmileSyncWorker worker;@Autowired SmileSyncQueue queue;@Autowired FakeSmile remote;@Autowired BookingService bookings;@Autowired TicketPdfService pdf;
 LocalDate date(){return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
 Product product(){var p=new Product();p.code="SS-"+UUID.randomUUID().toString().substring(0,20);p.name="Vé đồng bộ";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(100000);return products.saveAndFlush(p);}
 TicketBatch batch(){var p=product();return inventory.createBatch(new BatchInput("SS-"+UUID.randomUUID().toString().substring(0,20).toUpperCase(Locale.ROOT),p.id,2,date(),date().plusYears(1),"HK","Hoàng Kim"),"admin");}
 @Test void createQueuesImmediatelyButCannotSellBeforeSync(){var b=batch();assertFalse(b.active);assertEquals("PENDING",jobs.findById(b.id).orElseThrow().state);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->inventory.active(b.id,true,"admin"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->bookings.create(new OrderInput("Khách","0900000000","",date(),"",List.of(new LineInput(b.productId,1))),Channel.COUNTER,"staff"));}
 @Test void successfulSyncSetsExternalSeqAndBarcodeThenAllowsSaleAndPdf()throws Exception{var b=batch();worker.process(b.id);var after=batches.findById(b.id).orElseThrow();assertEquals("SYNCED",after.smileSyncState);assertFalse(after.active);var rows=stock.findByBatchIdOrderByCodeAsc(b.id);assertTrue(rows.stream().allMatch(c->c.smileSeqNo!=null&&c.smileGateBarcode.equals(c.smileSerial)));inventory.active(b.id,true,"admin");var order=bookings.create(new OrderInput("Khách","0900000000","",date(),"",List.of(new LineInput(b.productId,1))),Channel.COUNTER,"staff");bookings.cash(order.id,"staff");var ticket=bookings.ticketList(order.id).getFirst();assertEquals(ticket.smileSerial,ticket.smileGateBarcode);try(var document=org.apache.pdfbox.Loader.loadPDF(pdf.generate(order.accessToken))){String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertTrue(text.contains("Barcode: "+ticket.smileGateBarcode));}bookings.checkIn(ticket.smileGateBarcode,"staff");}
 @Test void retryAfterRemoteCommitDoesNotDuplicate(){var b=batch();int before=remote.remote.size();remote.failAfterCommit=true;worker.process(b.id);assertEquals("FAILED",jobs.findById(b.id).orElseThrow().state);assertEquals(before+2,remote.remote.size());queue.configureAndQueue(b.id,"HK","Hoàng Kim","admin");worker.process(b.id);assertEquals(before+2,remote.remote.size());assertEquals("SYNCED",jobs.findById(b.id).orElseThrow().state);worker.process(b.id);assertEquals(before+2,remote.remote.size());}
 @Test void missingPkgRollsBackWholeBatch(){var p=product();long before=batches.count();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->inventory.createBatch(new BatchInput("NOPKG-"+UUID.randomUUID().toString().substring(0,10).toUpperCase(Locale.ROOT),p.id,2,date(),date().plusYears(1)),"admin"));assertEquals(before,batches.count());}
 @Test void mappingIsSnapshottedBeforeExternalWork(){var b=batch();var claim=queue.claim(b.id);assertEquals("VN_AS_BARCODE",batches.findById(b.id).orElseThrow().smileCodeMapping);var local=stock.findById(claim.payload().codes().getFirst().stockId()).orElseThrow();assertEquals(local.smileSerial,claim.payload().codes().getFirst().barcode());assertEquals(local.smileBarcode,claim.payload().codes().getFirst().serial());assertNull(queue.claim(b.id));}
 @Test void cannotChangePkgAfterFirstAttempt(){var b=batch();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->queue.configureAndQueue(b.id,"OTHER","Other","admin"));assertEquals("HK",batches.findById(b.id).orElseThrow().smileTicketTypeCode);}
}
