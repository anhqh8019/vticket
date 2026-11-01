package vn.venusgiti.vticket;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

interface TicketEmailJobs extends JpaRepository<TicketEmailJob,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from TicketEmailJob j where j.bookingId=:id")
    Optional<TicketEmailJob> lockId(@Param("id") String id);
    @Query("select j from TicketEmailJob j where (j.state in ('PENDING','RETRY') and j.nextAttemptAt<=:now) or (j.state='SENDING' and j.leaseUntil<=:now) order by j.nextAttemptAt,j.createdAt")
    List<TicketEmailJob> due(@Param("now") Instant now, Pageable page);
    List<TicketEmailJob> findTop200ByOrderByCreatedAtDesc();
}
