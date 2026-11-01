package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;

/** Durable outbox. Enqueue joins the payment transaction; SMTP never runs inside it. */
@Service @Transactional
public class TicketEmailQueue {
    private final TicketEmailJobs jobs;
    private final Bookings bookings;
    private final TicketEmailProperties props;
    private final Audits audits;
    private final Clock clock;
    TicketEmailQueue(TicketEmailJobs jobs, Bookings bookings, TicketEmailProperties props, Audits audits, Clock clock) {
        this.jobs=jobs;this.bookings=bookings;this.props=props;this.audits=audits;this.clock=clock;
    }
    public void paid(Booking booking) {
        if (!props.enabled() || booking.email == null || booking.email.isBlank()) return;
        BookingService.require(booking.status == Status.PAID,"Chỉ gửi email cho đơn đã thanh toán");
        if (jobs.existsById(booking.id)) return;
        var job=new TicketEmailJob();job.bookingId=booking.id;job.recipient=booking.email.strip();
        job.state="PENDING";job.createdAt=clock.instant();job.nextAttemptAt=job.createdAt;
        jobs.save(job);
    }
    public record Claim(String bookingId,String attemptId,String recipient,String accessToken) {}
    public Claim claim(String id) {
        if (!props.enabled()) return null;
        var job=jobs.lockId(id).orElse(null);var now=clock.instant();
        if (job==null || "SENT".equals(job.state) || "FAILED".equals(job.state)
                || ("SENDING".equals(job.state) && now.isBefore(job.leaseUntil))
                || (!"SENDING".equals(job.state) && now.isBefore(job.nextAttemptAt))) return null;
        if (job.attempts>=props.maxAttempts()) {job.state="FAILED";job.leaseUntil=null;job.lastError="ATTEMPTS_EXHAUSTED";return null;}
        var order=bookings.findById(id).orElseThrow();
        if (order.status!=Status.PAID) {job.state="FAILED";job.lastError="ORDER_NOT_PAID";job.leaseUntil=null;return null;}
        job.state="SENDING";job.attempts++;job.attemptId=UUID.randomUUID().toString();job.leaseUntil=now.plusSeconds(900);
        return new Claim(id,job.attemptId,job.recipient,order.accessToken);
    }
    public void sent(Claim claim) {
        var job=jobs.lockId(claim.bookingId()).orElseThrow();
        if (!"SENDING".equals(job.state)||!Objects.equals(job.attemptId,claim.attemptId())) return;
        job.state="SENT";job.sentAt=clock.instant();job.leaseUntil=null;job.lastError=null;
        audit("system","TICKET_EMAIL_SENT",job.bookingId);
    }
    public void failed(Claim claim) { failed(claim,TicketEmailFailure.DELIVERY_FAILED_CHECK_SMTP); }
    public void failed(Claim claim,TicketEmailFailure failure) {
        var job=jobs.lockId(claim.bookingId()).orElseThrow();
        if (!"SENDING".equals(job.state)||!Objects.equals(job.attemptId,claim.attemptId())) return;
        job.state=job.attempts>=props.maxAttempts()?"FAILED":"RETRY";
        job.nextAttemptAt=clock.instant().plusSeconds(Math.min(3600,60L*(1L<<Math.min(job.attempts-1,6))));
        job.leaseUntil=null;job.lastError=failure.name();
        // Do not persist exception messages, which may include SMTP credentials or bearer links.
        audit("system","TICKET_EMAIL_FAILED",job.bookingId);
    }
    public void retry(String id,String actor) {
        BookingService.require(props.enabled(),"Chưa bật gửi email vé");
        var job=jobs.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        BookingService.require(Set.of("FAILED","RETRY").contains(job.state),"Chỉ thử lại email gửi lỗi");
        job.state="PENDING";job.attempts=0;job.nextAttemptAt=clock.instant();job.leaseUntil=null;job.lastError=null;
        audit(actor,"TICKET_EMAIL_RETRY",id);
    }
    public record View(String state,int attempts,Instant sentAt) {}
    @Transactional(readOnly=true) public View view(String id) {
        return jobs.findById(id).map(j->new View(j.state,j.attempts,j.sentAt)).orElse(null);
    }
    @Transactional(readOnly=true) public List<TicketEmailJob> list(){return jobs.findTop200ByOrderByCreatedAtDesc();}
    private void audit(String actor,String action,String id){var a=new Audit();a.actor=actor;a.action=action;a.target=id;a.happenedAt=clock.instant();audits.save(a);}
}
