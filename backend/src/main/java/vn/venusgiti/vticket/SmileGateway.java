package vn.venusgiti.vticket;
import java.time.LocalDate;
import java.util.*;
public interface SmileGateway {
 static String serialForSeq(String seq){
  if(seq==null||!seq.matches("[0-9]{1,10}"))throw new IllegalStateException("SeqNo Smile phải có tối đa 10 chữ số để tạo Seri VN chuẩn");
  return "VN"+String.format(Locale.ROOT,"%010d",Long.parseLong(seq));
 }
 record Code(String stockId,String barcode,String serial,boolean serialFromSeq) {public Code(String stockId,String barcode,String serial){this(stockId,barcode,serial,false);}}
 record Batch(String code,String pkgCode,LocalDate fromDate,LocalDate toDate,boolean weekday,boolean child,List<Code> codes) {}
 record Synced(String stockId,String seqNo,String gateBarcode) {}
 record Column(String name,String type,int size,boolean nullable,boolean identity,boolean hasDefault) {}
 List<Column> inspect();
 List<Synced> sync(Batch batch);
}
