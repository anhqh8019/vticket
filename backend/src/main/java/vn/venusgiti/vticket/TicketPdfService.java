package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.io.*;
import java.awt.Color;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;

@Service
public class TicketPdfService {
 private final BookingService bookings;
 TicketPdfService(BookingService b){bookings=b;}
 public record PaidTickets(Booking order,List<Ticket> tickets) {}
 public PaidTickets prepare(String accessToken){
  Booking b=bookings.getPublic(accessToken);BookingService.require(b.status==Status.PAID,"Chỉ tải vé khi đơn đã thanh toán");
  var tickets=bookings.ticketList(b.id).stream().sorted(Comparator.comparing((Ticket t)->t.ticketCode==null?t.id:t.ticketCode).thenComparing(t->t.id)).toList();BookingService.require(!tickets.isEmpty(),"Đơn chưa có vé được cấp");return new PaidTickets(b,tickets);
 }
 public byte[] generate(String accessToken) throws IOException {var paid=prepare(accessToken);return render(paid.order(),paid.tickets());}
 /** Generate one PDF at a time directly into the response; never hold the entire archive in memory. */
 public void writeZip(PaidTickets paid,OutputStream output) throws IOException {
  try(var zip=new java.util.zip.ZipOutputStream(output,java.nio.charset.StandardCharsets.UTF_8)){
   int index=0;for(var ticket:paid.tickets()){
    String serial=ticket.smileSerial==null?ticket.id:ticket.smileSerial;serial=serial.replaceAll("[^A-Za-z0-9_-]","_");serial=serial.substring(0,Math.min(serial.length(),64));
    String name=String.format(Locale.ROOT,"%04d-Venusgiti-%s.pdf",++index,serial);
    zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(render(paid.order(),List.of(ticket)));zip.closeEntry();
   }
   zip.finish();
  }
 }
 private byte[] render(Booking b,List<Ticket> tickets) throws IOException {
  try(var document=new PDDocument();var fontStream=new ClassPathResource("fonts/VenusgitiSans.ttf").getInputStream();var output=new ByteArrayOutputStream()){
   var font=PDType0Font.load(document,fontStream,true);
   for(Ticket t:tickets){
    var page=new PDPage(new PDRectangle(297.64f,419.53f));document.addPage(page);
    try(var canvas=new PDPageContentStream(document,page)){
     canvas.setNonStrokingColor(new Color(35,89,75));canvas.addRect(0,365,298,55);canvas.fill();
     text(canvas,font,22,24,386,"Venusgiti",Color.WHITE);
     text(canvas,font,10,24,348,"VÉ ĐIỆN TỬ - "+t.visitDate,Color.DARK_GRAY);
     wrap(canvas,font,t.productName,13,24,325,250,2);
     var matrix=new MultiFormatWriter().encode(t.qrToken,BarcodeFormat.QR_CODE,600,600,Map.of(EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M));
     var image=LosslessFactory.createFromImage(document,MatrixToImageWriter.toBufferedImage(matrix));canvas.drawImage(image,74,157,150,150);
     String code=t.ticketCode==null?t.id:t.ticketCode;
     if(t.smileBarcode!=null){
      var bars=new MultiFormatWriter().encode(t.smileBarcode,BarcodeFormat.CODE_128,1200,180,Map.of(EncodeHintType.MARGIN,20));
      canvas.drawImage(LosslessFactory.createFromImage(document,MatrixToImageWriter.toBufferedImage(bars)),24,104,250,37);
      wrap(canvas,font,"Barcode: "+t.smileBarcode,8,24,92,250,1);
     }
     wrap(canvas,font,"Seri: "+(t.smileSerial==null?code:t.smileSerial),9,24,77,250,1);
     wrap(canvas,font,"Mã vé: "+code,8,24,63,250,1);
     wrap(canvas,font,"Đơn: "+b.id.substring(0,8).toUpperCase(Locale.ROOT)+(t.batchCode==null?"":" | Lô: "+t.batchCode),8,24,51,250,1);
     text(canvas,font,8,24,39,"Mỗi mã vé sử dụng một lần, đúng ngày ghi trên vé.",Color.DARK_GRAY);
     text(canvas,font,8,24,24,"Quét tại hệ thống Venusgiti Onsen",Color.DARK_GRAY);
    }
   }
   document.getDocumentInformation().setTitle("Vé điện tử Venusgiti");document.save(output);return output.toByteArray();
  }catch(WriterException e){throw new IOException("Không tạo được mã QR",e);}
 }
 private void text(PDPageContentStream c,PDType0Font f,float size,float x,float y,String value,Color color)throws IOException{c.beginText();c.setFont(f,size);c.setNonStrokingColor(color);c.newLineAtOffset(x,y);c.showText(clean(f,value));c.endText();}
 private String clean(PDType0Font font,String value){StringBuilder out=new StringBuilder();for(int cp:value.codePoints().toArray()){String s=new String(Character.toChars(cp));try{font.encode(s);out.append(s);}catch(Exception e){out.append(' ');}}return out.toString();}
 private void wrap(PDPageContentStream c,PDType0Font f,String value,float size,float x,float y,float width,int maxLines)throws IOException{
  String remaining=clean(f,value).strip();
  for(int line=0;line<maxLines&&!remaining.isEmpty();line++){
   int fit=0;while(fit<remaining.length()&&f.getStringWidth(remaining.substring(0,fit+1))/1000*size<=width)fit++;
   if(fit==remaining.length()){text(c,f,size,x,y-line*(size+4),remaining,Color.DARK_GRAY);break;}
   int cut=remaining.lastIndexOf(' ',fit);if(cut<=0)cut=Math.max(1,fit);
   String part=remaining.substring(0,cut).strip();if(line==maxLines-1)part=part.substring(0,Math.max(0,part.length()-3))+"...";
   text(c,f,size,x,y-line*(size+4),part,Color.DARK_GRAY);remaining=remaining.substring(cut).strip();
  }
 }
}

