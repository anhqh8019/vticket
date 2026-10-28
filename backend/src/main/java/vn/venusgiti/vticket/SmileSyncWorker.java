package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.time.Clock;
@Service
public class SmileSyncWorker {
 private final SmileProperties props;private final SmileJobs jobs;private final SmileSyncQueue queue;private final SmileGateway gateway;private final Clock clock;
 SmileSyncWorker(SmileProperties p,SmileJobs j,SmileSyncQueue q,SmileGateway g,Clock c){props=p;jobs=j;queue=q;gateway=g;clock=c;}
 public void process(String batchId){if(!props.enabled())return;var claim=queue.claim(batchId);if(claim==null)return;try{queue.complete(claim,gateway.sync(claim.payload()));}catch(Exception e){queue.fail(claim,e instanceof IllegalStateException&&e.getMessage()!=null?e.getMessage():"Không hoàn tất đồng bộ Smile; kiểm tra cấu hình và kết nối.");}}
 @Scheduled(fixedDelay=5000,initialDelay=10000) public void run(){if(!props.enabled()||!props.workerEnabled())return;for(String id:jobs.due(clock.instant(),PageRequest.of(0,1)))try{process(id);}catch(Exception ignored){/* A claim may lose its lease or be blocked by stock state; never expose credentials in logs. */}}
}
