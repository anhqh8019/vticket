package vn.venusgiti.vticket;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Clock;
import vn.venusgiti.vticket.Domain.Status;
@Component
public class ExpiryJob {
 private final Bookings bookings; private final BookingService service; private final Clock clock;
 ExpiryJob(Bookings b,BookingService s,Clock c){bookings=b;service=s;clock=c;}
 @Scheduled(fixedDelay=60000) public void expire(){for(var b:bookings.findByStatusAndExpiresAtBefore(Status.PENDING,clock.instant()))service.cancel(b.id,"system",true);}
}
