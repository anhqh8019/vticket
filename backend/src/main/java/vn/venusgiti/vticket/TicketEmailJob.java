package vn.venusgiti.vticket;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="ticket_email_jobs")
public class TicketEmailJob {
    @Id @Column(length=36) public String bookingId;
    @Column(nullable=false,length=254) public String recipient;
    @Column(nullable=false,length=16) public String state;
    @Column(nullable=false) public int attempts;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant nextAttemptAt;
    public Instant leaseUntil;
    public Instant sentAt;
    @Column(length=36) public String attemptId;
    @Column(length=100) public String lastError;
}
