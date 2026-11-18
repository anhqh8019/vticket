package vn.venusgiti.vticket;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:email-test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
    "app.seed-data=false","app.ticket-email.enabled=true","app.ticket-email.worker-enabled=false",
    "app.ticket-email.from=tickets@venusgiti.test","app.ticket-email.public-base-url=https://tickets.venusgiti.test",
    "app.ticket-email.max-attempts=2"})
@ActiveProfiles("demo") @Import(TicketEmailIntegrationTest.Config.class) @AutoConfigureMockMvc
class TicketEmailIntegrationTest {
    @TestConfiguration static class Config {@Bean @Primary FakeSmtp fakeSmtp(){return new FakeSmtp();}}
    static class FakeSmtp extends JavaMailSenderImpl {
        final List<MimeMessage> messages=new ArrayList<>();boolean fail;
        @Override public void send(MimeMessage message){
            if(fail)throw new MailSendException("sensitive SMTP server response must not be persisted");
            try{message.saveChanges();var bytes=new ByteArrayOutputStream();message.writeTo(bytes);
                messages.add(new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(bytes.toByteArray())));
            }catch(Exception e){throw new MailSendException("Test serialization failed",e);}
        }
    }
    @Autowired BookingService service;@Autowired InventoryService inventory;@Autowired Products products;
    @Autowired Bookings bookings;@Autowired TicketEmailJobs jobs;@Autowired TicketEmailQueue queue;
    @Autowired TicketEmailWorker worker;@Autowired FakeSmtp smtp;@Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    @Autowired TicketEmailSender sender;
    LocalDate date(){return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
    @BeforeEach void reset(){smtp.messages.clear();smtp.fail=false;}
    Booking order(String email,Channel channel,int quantity,int price){
        var p=new Product();p.code="MAIL-"+UUID.randomUUID().toString().substring(0,15);p.name="Vé tắm khoáng";
        p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(price);p=products.saveAndFlush(p);
        inventory.createBatch(new BatchInput("MAIL-"+UUID.randomUUID().toString().substring(0,15).toUpperCase(Locale.ROOT),p.id,quantity,date(),date().plusYears(1)),"test");
        return service.create(new OrderInput("Khách hàng thử","0900000000",email,date(),"",List.of(new LineInput(p.id,quantity))),channel,"test");
    }
    Booking order(String email){return order(email,Channel.COUNTER,2,10000);}
    void readyAgain(String id){var job=jobs.findById(id).orElseThrow();job.nextAttemptAt=Instant.now().minusSeconds(1);jobs.saveAndFlush(job);}
    List<Part> attachments(Part part)throws Exception {
        var result=new ArrayList<Part>();if(part.getFileName()!=null)result.add(part);
        if(part.getContent() instanceof Multipart multi)for(int i=0;i<multi.getCount();i++)result.addAll(attachments(multi.getBodyPart(i)));
        return result;
    }
    String body(Part part)throws Exception {
        if(part.isMimeType("text/plain"))return (String)part.getContent();
        if(part.getContent() instanceof Multipart multi)for(int i=0;i<multi.getCount();i++){var text=body(multi.getBodyPart(i));if(!text.isEmpty())return text;}
        return "";
    }
    @Test void cashQueuesOnlyAfterPaymentAndSendsRealPdfOnce()throws Exception {
        var b=order("guest@example.test");assertFalse(jobs.existsById(b.id));
        service.cash(b.id,"staff");assertEquals("PENDING",queue.view(b.id).state());assertTrue(smtp.messages.isEmpty());
        service.cash(b.id,"staff");worker.process(b.id);worker.process(b.id);
        assertEquals("SENT",queue.view(b.id).state());assertEquals(1,smtp.messages.size());assertEquals(2,service.ticketList(b.id).size());
        var mail=smtp.messages.getFirst();assertEquals("guest@example.test",mail.getAllRecipients()[0].toString());
        assertTrue(mail.getSubject().contains("Venusgiti"));assertTrue(body(mail).contains("Số vé: 2"));
        assertTrue(body(mail).contains("https://tickets.venusgiti.test/api/public/orders/"+b.accessToken+"/tickets.zip"));
        var files=attachments(mail);assertEquals(1,files.size());assertTrue(files.getFirst().getFileName().endsWith(".pdf"));
        try(var document=org.apache.pdfbox.Loader.loadPDF(files.getFirst().getInputStream().readAllBytes())){assertEquals(2,document.getNumberOfPages());}
    }
    @Autowired MonthlyService monthly;
    @Test void monthlyBankPaymentSendsOneCardPdfWithTheCorrectQrAndDates()throws Exception {
        var p=new Product();p.code="MONTHMAIL-"+UUID.randomUUID().toString().substring(0,15);p.name="Vé tháng mail";p.kind=Kind.TICKET;p.price=BigDecimal.valueOf(100);p=products.saveAndFlush(p);
        monthly.save(MonthlyDomain.Tier.GOLD,new MonthlyService.PlanInput(p.id,BigDecimal.valueOf(100),BigDecimal.valueOf(150),true),"admin");
        var b=monthly.sell(new MonthlyService.SaleInput(MonthlyDomain.Tier.GOLD,"Khách tháng",null,"monthly@example.test",List.of(date())),"staff");
        assertFalse(jobs.existsById(b.id));new TransactionTemplate(tm).executeWithoutResult(t->bookings.lockId(b.id).orElseThrow().paymentMethod="VIETQR");
        assertTrue(service.tryBankPayment(b.id,"monthly-bank-ref").applied());assertFalse(service.tryBankPayment(b.id,"duplicate-monthly-ref").applied());
        worker.process(b.id);worker.process(b.id);assertEquals("SENT",queue.view(b.id).state());assertEquals(1,smtp.messages.size());assertTrue(service.ticketList(b.id).isEmpty());
        var mail=smtp.messages.getFirst();assertTrue(body(mail).contains("thẻ tháng GOLD"));assertTrue(body(mail).contains("không dùng trực tiếp tại cổng Smile"));
        var files=attachments(mail);assertEquals(1,files.size());
        try(var document=org.apache.pdfbox.Loader.loadPDF(files.getFirst().getInputStream().readAllBytes())){
            assertEquals(1,document.getNumberOfPages());assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(document).contains("THẺ THÁNG"));
            var resources=document.getPage(0).getResources();String decoded=null;
            for(var name:resources.getXObjectNames())if(resources.getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image){
                var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image.getImage())));
                decoded=new com.google.zxing.MultiFormatReader().decode(bitmap).getText();
            }
            assertEquals(monthly.view(b.id).qrToken(),decoded);
        }
    }
    @Test void noEmailOrBlankEmailDoesNotCreateJob(){
        for(String email:new String[]{null,"","   "}){var b=order(email);service.cash(b.id,"staff");assertFalse(jobs.existsById(b.id));}
        assertTrue(smtp.messages.isEmpty());
    }
    @Test void bankAndZeroTotalUseTheSameOutbox(){
        var bank=order("bank@example.test",Channel.ONLINE,1,10000);
        new TransactionTemplate(tm).executeWithoutResult(t->{var b=bookings.findById(bank.id).orElseThrow();b.paymentMethod="VIETQR";});
        assertTrue(service.tryBankPayment(bank.id,"bank-ref").applied());assertEquals("PENDING",queue.view(bank.id).state());
        assertFalse(service.tryBankPayment(bank.id,"duplicate-ref").applied());
        var zero=order("zero@example.test",Channel.ONLINE,1,0);service.confirmZero(zero.accessToken);service.confirmZero(zero.accessToken);
        assertEquals("PENDING",queue.view(zero.id).state());
        worker.process(bank.id);worker.process(zero.id);assertEquals(2,smtp.messages.size());
    }
    @Test void cancelledOrUnappliedPaymentNeverQueues(){
        var cancelled=order("cancel@example.test");service.cancel(cancelled.id,"test",false);assertFalse(jobs.existsById(cancelled.id));
        var pending=order("pending@example.test");assertFalse(service.tryBankPayment(pending.id,"wrong-method").applied());assertFalse(jobs.existsById(pending.id));
    }
    @Test void rollbackPaymentRollsBackEmailAndTickets(){
        var b=order("rollback@example.test");assertThrows(IllegalStateException.class,()->new TransactionTemplate(tm).executeWithoutResult(t->{service.cash(b.id,"staff");throw new IllegalStateException("rollback");}));
        assertEquals(Status.PENDING,bookings.findById(b.id).orElseThrow().status);assertFalse(jobs.existsById(b.id));assertTrue(service.ticketList(b.id).isEmpty());
    }
    @Test void smtpFailureRetriesWithoutChangingPaymentAndHasBoundedAttempts(){
        var b=order("retry@example.test");service.cash(b.id,"staff");smtp.fail=true;worker.process(b.id);
        assertEquals("RETRY",queue.view(b.id).state());assertEquals(Status.PAID,bookings.findById(b.id).orElseThrow().status);
        assertEquals(2,service.ticketList(b.id).size());assertEquals("SMTP_SEND_FAILED",jobs.findById(b.id).orElseThrow().lastError);
        worker.process(b.id);assertEquals(1,queue.view(b.id).attempts());readyAgain(b.id);worker.process(b.id);assertEquals("FAILED",queue.view(b.id).state());
        queue.retry(b.id,"admin");smtp.fail=false;worker.process(b.id);assertEquals("SENT",queue.view(b.id).state());assertEquals(1,smtp.messages.size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->queue.retry(b.id,"admin"));
    }
    @Test void leaseAndStaleAttemptProtectAgainstParallelWork(){
        var b=order("lease@example.test");service.cash(b.id,"staff");var first=queue.claim(b.id);assertNotNull(first);assertNull(queue.claim(b.id));
        var job=jobs.findById(b.id).orElseThrow();job.leaseUntil=Instant.now().minusSeconds(1);jobs.saveAndFlush(job);
        var second=queue.claim(b.id);assertNotNull(second);queue.sent(first);assertEquals("SENDING",queue.view(b.id).state());
        queue.sent(second);queue.failed(second);assertEquals("SENT",queue.view(b.id).state());
    }
    @Test void dealerOrderAttachesZipWithOnePdfPerTicket()throws Exception {
        var b=order("dealer@example.test",Channel.COUNTER,300,1000);service.cash(b.id,"staff");worker.process(b.id);
        assertEquals("SENT",queue.view(b.id).state());var files=attachments(smtp.messages.getFirst());assertEquals(1,files.size());
        assertTrue(files.getFirst().getFileName().endsWith(".zip"));int count=0;
        try(var zip=new ZipInputStream(files.getFirst().getInputStream())){while(zip.getNextEntry()!=null){
            try(var document=org.apache.pdfbox.Loader.loadPDF(zip.readAllBytes())){assertEquals(1,document.getNumberOfPages());}count++;
        }}assertEquals(300,count);
    }
    @Test void oversizedAttachmentFallsBackToPrivateDownloadLinks()throws Exception {
        var b=order("links@example.test");service.cash(b.id,"staff");
        var props=new TicketEmailProperties(true,"tickets@venusgiti.test","https://tickets.venusgiti.test",false,2,1024);
        var small=new TicketEmailSender(new org.springframework.beans.factory.support.StaticListableBeanFactory(Map.of("smtp",smtp)).getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class),props,
            new TicketPdfService(service));
        small.send(queue.claim(b.id));var mail=smtp.messages.getFirst();assertTrue(attachments(mail).isEmpty());
        assertTrue(body(mail).contains("vượt giới hạn"));assertTrue(body(mail).contains(b.accessToken+"/tickets.pdf"));
    }
    @Test void emailApisRequireAdminAndCsrf()throws Exception {
        var b=order("auth@example.test");service.cash(b.id,"staff");
        mvc.perform(get("/api/admin/ticket-emails")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ticket-emails").with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ticket-emails").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/ticket-emails/"+b.id+"/retry").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/orders/"+b.accessToken)).andExpect(status().isOk()).andExpect(jsonPath("$.emailDelivery.state").value("PENDING"));
    }
    @Test void invalidEmailAndUnsafePublicUrlAreRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->TicketEmailProperties.requireAddress("a@example.test,b@example.test"));
        assertThrows(IllegalArgumentException.class,()->TicketEmailProperties.requireAddress("a@example.test\r\nBcc:b@example.test"));
        assertThrows(IllegalArgumentException.class,()->new TicketEmailProperties(true,"sender@example.test","https://user:pass@example.test",true,5,8000000));
        mvc.perform(post("/api/public/orders").with(csrf()).contentType("application/json").content("{\"customerName\":\"Khách\",\"phone\":\"0900000000\",\"email\":\"not-an-email\",\"visitDate\":\""+date()+"\",\"lines\":[{\"productId\":1,\"quantity\":1}]}"))
            .andExpect(status().isBadRequest());
    }
}
