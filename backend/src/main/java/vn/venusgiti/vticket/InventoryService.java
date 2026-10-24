package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;

@Service @Transactional
public class InventoryService {
 private final Products products; private final TicketBatches batches; private final TicketStock stock; private final Tickets tickets; private final Audits audits; private final Clock clock;
 InventoryService(Products p,TicketBatches b,TicketStock s,Tickets t,Audits a,Clock c){products=p;batches=b;stock=s;tickets=t;audits=a;clock=c;}
 private void require(boolean c,String m){BookingService.require(c,m);}
 public void lockProducts(Collection<Long> ids){ids.stream().distinct().sorted().forEach(id->products.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy vé/dịch vụ")));}
 public void lockBooking(Booking b){lockProducts(b.lines.stream().map(l->l.productId).toList());}
 private void reset(StockCode s){s.state=StockState.AVAILABLE;s.reservedOrderId=null;s.reservedUntil=null;}
 public TicketBatch createBatch(BatchInput in,String actor){
  lockProducts(List.of(in.productId()));Product p=products.findById(in.productId()).orElseThrow();
  require(p.kind!=Kind.SERVICE,"Dịch vụ không quản lý lô vé");require(!in.validUntil().isBefore(in.validFrom()),"Ngày hết hạn phải từ ngày bắt đầu trở đi");require(!in.validUntil().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))),"Lô vé đã hết hạn");
  TicketBatch b=new TicketBatch();b.id=UUID.randomUUID().toString();b.code=in.code();b.productId=p.id;b.quantity=in.quantity();b.validFrom=in.validFrom();b.validUntil=in.validUntil();b.createdAt=clock.instant();b.createdBy=actor;batches.saveAndFlush(b);
  for(int i=1;i<=in.quantity();i++){StockCode s=new StockCode();s.id=UUID.randomUUID().toString();s.batchId=b.id;s.productId=p.id;s.code=b.code+"-"+String.format(Locale.ROOT,"%06d",i);s.qrToken=UUID.randomUUID().toString();stock.save(s);}
  Audit a=new Audit();a.happenedAt=clock.instant();a.actor=actor;a.action="BATCH_CREATED";a.target=b.code;audits.save(a);return b;
 }
 public void reserve(Booking b){
  int total=b.lines.stream().filter(l->l.kind!=Kind.SERVICE).mapToInt(l->l.quantity).sum();require(total<=500,"Tối đa 500 vé trong một đơn");
  for(BookingLine l:b.lines)if(l.kind!=Kind.SERVICE){
   for(StockCode old:stock.expired(l.productId,StockState.RESERVED,clock.instant()))reset(old);
   stock.flush();var available=stock.available(l.productId,StockState.AVAILABLE,b.visitDate,PageRequest.of(0,l.quantity));
   require(available.size()==l.quantity,"Không đủ mã vé cho "+l.name+" vào ngày đã chọn. Vui lòng tạo/bổ sung lô vé hoặc giảm số lượng.");
   for(var s:available){s.state=StockState.RESERVED;s.reservedOrderId=b.id;s.reservedUntil=b.expiresAt;}
  }
 }
 public void release(Booking b){for(var s:stock.reserved(b.id))reset(s);}
 public void issue(Booking b){
  var reserved=stock.reserved(b.id);Map<Long,BookingLine> lines=new HashMap<>();for(var l:b.lines)if(l.kind!=Kind.SERVICE)lines.put(l.productId,l);
  Map<Long,Long> actual=new HashMap<>();for(var s:reserved)actual.merge(s.productId,1L,Long::sum);
  require(lines.entrySet().stream().allMatch(e->actual.getOrDefault(e.getKey(),0L)==e.getValue().quantity)&&reserved.size()==lines.values().stream().mapToInt(l->l.quantity).sum(),"Đơn không còn đủ mã vé đang giữ. Vui lòng hủy đơn và đặt lại.");
  for(var s:reserved){require(s.state==StockState.RESERVED&&s.reservedUntil.isAfter(clock.instant()),"Mã vé đã hết thời gian giữ");var l=lines.get(s.productId);var batch=batches.findById(s.batchId).orElseThrow();
   s.state=StockState.SOLD;s.soldOrderId=b.id;s.soldAt=clock.instant();s.reservedOrderId=null;s.reservedUntil=null;
   Ticket t=new Ticket();t.id=UUID.randomUUID().toString();t.stockId=s.id;t.ticketCode=s.code;t.batchCode=batch.code;t.qrToken=s.qrToken;t.bookingId=b.id;t.productName=l.name;t.includedServices=l.includedServices;t.visitDate=b.visitDate;tickets.save(t);
  }
 }
 public void used(Ticket t){if(t.stockId!=null){var s=stock.lockId(t.stockId).orElseThrow();require(s.state==StockState.SOLD&&t.bookingId.equals(s.soldOrderId),"Trạng thái mã vé không hợp lệ");s.state=StockState.USED;s.usedAt=clock.instant();}}
 @Transactional(readOnly=true) public List<Map<String,Object>> list(){
  var all=batches.findTop200ByOrderByCreatedAtDesc();Map<String,Map<String,Long>> counts=new HashMap<>();if(!all.isEmpty())for(Object[] row:stock.counts(all.stream().map(b->b.id).toList()))counts.computeIfAbsent((String)row[0],k->new HashMap<>()).put(row[1].toString(),(Long)row[2]);
  return all.stream().map(b->{Map<String,Object> m=new LinkedHashMap<>();m.put("batch",b);m.put("productName",products.findById(b.productId).map(p->p.name).orElse(""));Map<String,Long> c=new LinkedHashMap<>();for(var state:StockState.values())c.put(state.name(),counts.getOrDefault(b.id,Map.of()).getOrDefault(state.name(),0L));m.put("counts",c);return m;}).toList();
 }
 @Transactional(readOnly=true) public Map<String,Object> codes(String batchId,StockState state,int page){require(page>=0,"Trang không hợp lệ");var batch=batches.findById(batchId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lô vé"));var rows=state==null?stock.findByBatchIdOrderByCodeAsc(batchId,PageRequest.of(page,100)):stock.findByBatchIdAndStateOrderByCodeAsc(batchId,state,PageRequest.of(page,100));return Map.of("batch",batch,"items",rows.getContent(),"page",page,"totalPages",rows.getTotalPages(),"total",rows.getTotalElements());}
 public TicketBatch active(String id,boolean active,String actor){var b=batches.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));lockProducts(List.of(b.productId));b.active=active;Audit a=new Audit();a.happenedAt=clock.instant();a.actor=actor;a.action=active?"BATCH_ENABLED":"BATCH_DISABLED";a.target=b.code;audits.save(a);return b;}
}
