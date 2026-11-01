package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.time.Clock;

@Service
public class TicketEmailWorker {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(TicketEmailWorker.class);
    private final TicketEmailProperties props;private final TicketEmailJobs jobs;private final TicketEmailQueue queue;
    private final TicketEmailSender sender;private final Clock clock;
    TicketEmailWorker(TicketEmailProperties props,TicketEmailJobs jobs,TicketEmailQueue queue,TicketEmailSender sender,Clock clock){
        this.props=props;this.jobs=jobs;this.queue=queue;this.sender=sender;this.clock=clock;
    }
    @Scheduled(fixedDelay=5000,initialDelay=15000)
    public void tick(){
        if(!props.enabled()||!props.workerEnabled())return;
        for(var job:jobs.due(clock.instant(),PageRequest.of(0,1))){
            try{process(job.bookingId);}catch(Exception e){log.warn("Không xử lý được hàng đợi email vé; kiểm tra DB và cấu hình SMTP");}
        }
    }
    public void process(String id){
        var claim=queue.claim(id);if(claim==null)return;
        try{sender.send(claim);}catch(Exception e){var failure=TicketEmailFailure.classify(e);log.warn("Gửi email vé thất bại: bookingId={}, code={}",id,failure.name());queue.failed(claim,failure);return;}
        // A DB failure after SMTP accepted must leave the lease recoverable, not mark the payment failed.
        queue.sent(claim);
    }
}
