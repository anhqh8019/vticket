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
 private final Products products; private final TicketBatches batches; private final TicketStock stock; private final Tickets tickets; private final Audits audits; private final Clock clock; private final TicketIdentifierCounters counters;private final SmileProperties smile;private final SmileSyncQueue smileQueue;
 InventoryService(Products p,TicketBatches b,TicketStock s,Tickets t,Audits a,Clock c,TicketIdentifierCounters ids,SmileProperties sm,SmileSyncQueue queue){products=p;batches=b;stock=s;tickets=t;audits=a;clock=c;counters=ids;smile=sm;smileQueue=queue;}
 private void require(boolean c,String m){BookingService.require(c,m);}
 public void lockProducts(Collection<Long> ids){ids.stream().distinct().sorted().forEach(id->products.lockId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy vé/dịch vụ")));}
 public void lockBooking(Booking b){var ids=new ArrayList<Long>();for(var l:b.lines){ids.add(l.productId);if(l.ticketProductId!=null)ids.add(l.ticketProductId);}lockProducts(ids);}
 private void reset(StockCode s){s.state=StockState.AVAILABLE;s.reservedOrderId=null;s.reservedUntil=null;}
 public TicketBatch createBatch(BatchInput in,String actor){
  lockProducts(List.of(in.productId()));Product p=products.findById(in.productId()).orElseThrow();
  require(p.kind==Kind.TICKET,"Chỉ tạo lô cho vé; combo dùng kho của loại vé tham chiếu");require(!in.validUntil().isBefore(in.validFrom()),"Ngày hết hạn phải từ ngày bắt đầu trở đi");require(!in.validUntil().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))),"Lô vé đã hết hạn");
  TicketBatch b=new TicketBatch();b.id=UUID.randomUUID().toString();b.code=in.code();b.productId=p.id;b.quantity=in.quantity();b.validFrom=in.validFrom();b.validUntil=in.validUntil();b.smileTicketTypeCode=in.smileTicketTypeCode();b.smileTicketTypeName=in.smileTicketTypeName();b.createdAt=clock.instant();b.createdBy=actor;batches.saveAndFlush(b);
  var counter=counters.acquire();
  for(int i=1;i<=in.quantity();i++){StockCode s=new StockCode();s.id=UUID.randomUUID().toString();s.batchId=b.id;s.productId=p.id;s.code=b.code+"-"+String.format(Locale.ROOT,"%06d",i);s.qrToken=UUID.randomUUID().toString();assignIdentifiers(s,counter);stock.save(s);}
  stock.flush();smileQueue.created(b);
  Audit a=new Audit();a.happenedAt=clock.instant();a.actor=actor;a.action="BATCH_CREATED";a.target=b.code;audits.save(a);return b;
 }
 /** Import exact Smile identifiers; never infer barcodes from serial numbers. New imports are paused for review. */
 public TicketBatch importSmile(SmileBatchInput in,String actor){
  require(in.codes()!=null&&!in.codes().isEmpty()&&in.codes().size()<=10000,"Nhập từ 1 đến 10.000 cặp Seri / barcode");
  Set<String> serials=new HashSet<>(),barcodes=new HashSet<>();
  for(var row:in.codes()){
   require(row!=null&&row.serial()!=null&&row.serial().matches("[A-Za-z0-9._/-]{1,64}")&&row.barcode()!=null&&row.barcode().matches("[A-Za-z0-9._/-]{1,128}"),"Seri / barcode không hợp lệ; giữ nguyên mã gốc, không có khoảng trắng");
   require(!Boolean.TRUE.equals(row.isCancel())&&!Boolean.TRUE.equals(row.isLoss()),"Không nhập mã đã hủy hoặc báo mất trên Smile");
   require(row.pkgCode()==null||row.pkgCode().equals(in.smileTicketTypeCode()),"PkgCode không khớp loại vé của lô");
   require((row.fromDate()==null||row.fromDate().equals(in.validFrom()))&&(row.toDate()==null||row.toDate().equals(in.validUntil())),"FromDate / ToDate không khớp thời hạn lô; tách các lô khác thời hạn");
   require((row.isWeekday()==null||row.isWeekday()==in.smileIsWeekday())&&(row.isChild()==null||row.isChild()==in.smileIsChild()),"IsWeekday / IsChild không khớp cấu hình lô");
   require(row.seqNo()==null||row.seqNo().matches("[0-9]{1,19}"),"SeqNo không hợp lệ");
   require(serials.add(row.serial().toUpperCase(Locale.ROOT)),"Seri bị trùng trong danh sách: "+row.serial());require(barcodes.add(row.barcode().toUpperCase(Locale.ROOT)),"Barcode bị trùng trong danh sách: "+row.barcode());
  }
  lockProducts(List.of(in.productId()));Product p=products.findById(in.productId()).orElseThrow();
  require(p.kind==Kind.TICKET,"Chỉ tạo lô cho vé; combo dùng kho của loại vé tham chiếu");require(!in.validUntil().isBefore(in.validFrom()),"Ngày hết hạn phải từ ngày bắt đầu trở đi");require(!in.validUntil().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))),"Lô vé đã hết hạn");
  TicketBatch b=new TicketBatch();b.id=UUID.randomUUID().toString();b.code=in.code();b.productId=p.id;b.quantity=in.codes().size();b.validFrom=in.validFrom();b.validUntil=in.validUntil();b.source="SMILE";b.active=false;b.smileIsWeekday=in.smileIsWeekday();b.smileIsChild=in.smileIsChild();b.smileTicketTypeCode=in.smileTicketTypeCode();b.smileTicketTypeName=in.smileTicketTypeName().strip();b.createdAt=clock.instant();b.createdBy=actor;batches.saveAndFlush(b);
  int index=0;for(var row:in.codes()){StockCode code=new StockCode();code.id=UUID.randomUUID().toString();code.batchId=b.id;code.productId=p.id;code.code=b.code+"-"+String.format(Locale.ROOT,"%06d",++index);code.qrToken=UUID.randomUUID().toString();code.smileSeqNo=row.seqNo();code.smileSerial=row.serial();code.smileBarcode=row.barcode();stock.save(code);}
  stock.flush();Audit a=new Audit();a.happenedAt=clock.instant();a.actor=actor;a.action="SMILE_BATCH_IMPORTED";a.target=b.code;audits.save(a);return b;
 }
 private void assignIdentifiers(StockCode code,TicketIdentifierCounter counter){
  while(true){require(counter.nextValue<=999999999L,"Đã hết dải mã nội bộ. Liên hệ quản trị để mở dải mới.");long number=counter.nextValue++;String serial="VN"+String.format(Locale.ROOT,"%010d",number),barcode=Long.toString(1000000000L+number);
   if(stock.existsBySmileSerial(serial)||stock.existsBySmileBarcode(barcode))continue;
   code.smileSerial=serial;code.smileBarcode=barcode;return;
  }
 }
 public TicketBatch generateIdentifiers(String batchId,String actor){
  var b=batches.findById(batchId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));lockProducts(List.of(b.productId));require("VTICKET".equals(b.source),"Lô Smile phải dùng nguyên Seri / barcode nguồn, không tự sinh thay thế");var counter=counters.acquire();int count=0;
  for(var code:stock.findByBatchIdOrderByCodeAsc(batchId)){require((code.smileSerial==null)==(code.smileBarcode==null),"Mã đang thiếu một phần Seri / barcode, cần kiểm tra dữ liệu");if(code.smileSerial==null){assignIdentifiers(code,counter);stock.saveAndFlush(code);tickets.fillIdentifiers(code.id,code.smileSerial,code.smileBarcode);count++;}}
  Audit audit=new Audit();audit.happenedAt=clock.instant();audit.actor=actor;audit.action="BATCH_IDENTIFIERS_GENERATED";audit.target=b.code;audits.save(audit);return b;
 }
 public void reserve(Booking b){
  int total=b.lines.stream().filter(l->l.kind!=Kind.SERVICE).mapToInt(this::ticketCount).sum();require(total<=1000,"Tối đa 1.000 vé trong một đơn, gồm vé tặng");
  for(BookingLine l:b.lines)if(l.kind!=Kind.SERVICE){
   for(StockCode old:stock.expired(ticketProduct(l),StockState.RESERVED,clock.instant()))reset(old);
   stock.flush();var available=stock.available(ticketProduct(l),StockState.AVAILABLE,b.visitDate,smile.enabled(),PageRequest.of(0,ticketCount(l)));
   require(available.size()==ticketCount(l),"Không đủ mã vé cho "+l.name+" vào ngày đã chọn. Vui lòng tạo/bổ sung lô vé hoặc giảm số lượng.");
   for(var s:available){s.state=StockState.RESERVED;s.reservedOrderId=b.id;s.reservedUntil=b.expiresAt;}
  }
 }
 public void release(Booking b){for(var s:stock.reserved(b.id))reset(s);}
 private Long ticketProduct(BookingLine l){return l.ticketProductId==null?l.productId:l.ticketProductId;}
 private int ticketCount(BookingLine l){return l.ticketQuantity==null?l.quantity:l.ticketQuantity;}
 private Map<Long,Long> expected(Booking b){Map<Long,Long> counts=new HashMap<>();for(var l:b.lines)if(l.kind!=Kind.SERVICE)counts.merge(ticketProduct(l),(long)ticketCount(l),Long::sum);return counts;}
 public boolean reservationValid(Booking b,Instant at){Map<Long,Long> actual=new HashMap<>();for(var row:stock.reserved(b.id)){if(row.state!=StockState.RESERVED||row.reservedUntil==null||!row.reservedUntil.isAfter(at))return false;if(smile.enabled()){var batch=batches.findById(row.batchId).orElseThrow();if(!"SMILE".equals(batch.source)&&!"SYNCED".equals(batch.smileSyncState))return false;}actual.merge(row.productId,1L,Long::sum);}return expected(b).equals(actual);}
 public void issue(Booking b,Instant at){
  var reserved=stock.reserved(b.id);Map<Long,Long> actual=new HashMap<>();Map<Long,Deque<StockCode>> queues=new HashMap<>();for(var row:reserved){actual.merge(row.productId,1L,Long::sum);queues.computeIfAbsent(row.productId,key->new ArrayDeque<>()).add(row);}
  require(expected(b).equals(actual),"Đơn không còn đủ mã vé đang giữ. Vui lòng hủy đơn và đặt lại.");
  for(var l:b.lines)if(l.kind!=Kind.SERVICE)for(int n=0;n<ticketCount(l);n++){var s=queues.get(ticketProduct(l)).removeFirst();require(s.state==StockState.RESERVED&&s.reservedUntil.isAfter(at),"Mã vé đã hết thời gian giữ");var batch=batches.findById(s.batchId).orElseThrow();s.state=StockState.SOLD;s.soldOrderId=b.id;s.soldAt=at;s.reservedOrderId=null;s.reservedUntil=null;
   Ticket t=new Ticket();t.id=UUID.randomUUID().toString();t.stockId=s.id;t.ticketCode=s.code;t.batchCode=batch.code;t.source=batch.source;t.smileSeqNo=s.smileSeqNo;t.smileGateBarcode=s.smileGateBarcode;t.smileIsWeekday=batch.smileIsWeekday;t.smileIsChild=batch.smileIsChild;t.smileSerial=s.smileSerial;t.smileBarcode=s.smileBarcode;t.smileTicketTypeCode=batch.smileTicketTypeCode;t.smileTicketTypeName=batch.smileTicketTypeName;t.qrToken=s.qrToken;t.bookingId=b.id;t.productName=l.ticketProductName==null?l.name:l.ticketProductName;t.includedServices=l.includedServices;t.visitDate=b.visitDate;tickets.save(t);
  }
 }
 public void used(Ticket t){if(t.stockId!=null){var s=stock.lockId(t.stockId).orElseThrow();require(s.state==StockState.SOLD&&t.bookingId.equals(s.soldOrderId),"Trạng thái mã vé không hợp lệ");s.state=StockState.USED;s.usedAt=clock.instant();}}
 @Transactional(readOnly=true) public List<Map<String,Object>> list(){
  var all=batches.findTop200ByOrderByCreatedAtDesc();Map<String,Map<String,Long>> counts=new HashMap<>();if(!all.isEmpty())for(Object[] row:stock.counts(all.stream().map(b->b.id).toList()))counts.computeIfAbsent((String)row[0],k->new HashMap<>()).put(row[1].toString(),(Long)row[2]);
  return all.stream().map(b->{Map<String,Object> m=new LinkedHashMap<>();m.put("batch",b);m.put("productName",products.findById(b.productId).map(p->p.name).orElse(""));Map<String,Long> c=new LinkedHashMap<>();for(var state:StockState.values())c.put(state.name(),counts.getOrDefault(b.id,Map.of()).getOrDefault(state.name(),0L));m.put("counts",c);return m;}).toList();
 }
 @Transactional(readOnly=true) public Map<String,Object> codes(String batchId,StockState state,int page){require(page>=0,"Trang không hợp lệ");var batch=batches.findById(batchId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lô vé"));var rows=state==null?stock.findByBatchIdOrderByCodeAsc(batchId,PageRequest.of(page,100)):stock.findByBatchIdAndStateOrderByCodeAsc(batchId,state,PageRequest.of(page,100));return Map.of("batch",batch,"items",rows.getContent(),"page",page,"totalPages",rows.getTotalPages(),"total",rows.getTotalElements());}
 public TicketBatch active(String id,boolean active,String actor){var b=batches.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));lockProducts(List.of(b.productId));require(!active||!smile.enabled()||"SMILE".equals(b.source)||"SYNCED".equals(b.smileSyncState),"Lô chưa đồng bộ thành công sang Smile");require(!active||!"SMILE".equals(b.source)||(!Boolean.TRUE.equals(b.smileIsChild)&&!Boolean.TRUE.equals(b.smileIsWeekday)),"Chưa mở bán vé Smile trẻ em / giới hạn ngày thường trước khi cấu hình quy tắc sử dụng");b.active=active;Audit a=new Audit();a.happenedAt=clock.instant();a.actor=actor;a.action=active?"BATCH_ENABLED":"BATCH_DISABLED";a.target=b.code;audits.save(a);return b;}
}
