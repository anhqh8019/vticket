package vn.venusgiti.vticket;
import java.time.LocalDate;
import java.util.*;
public interface SmileGateway {
 record Code(String stockId,String barcode,String serial) {}
 record Batch(String code,String pkgCode,LocalDate fromDate,LocalDate toDate,boolean weekday,boolean child,List<Code> codes) {}
 record Synced(String stockId,String seqNo,String gateBarcode) {}
 record Column(String name,String type,int size,boolean nullable,boolean identity,boolean hasDefault) {}
 List<Column> inspect();
 List<Synced> sync(Batch batch);
}
