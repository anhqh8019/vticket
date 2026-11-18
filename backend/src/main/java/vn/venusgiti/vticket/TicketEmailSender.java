package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.*;
import org.springframework.core.io.FileSystemResource;
import java.io.*;
import java.nio.file.*;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class TicketEmailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final TicketEmailProperties props;
    private final TicketPdfService pdf;
    TicketEmailSender(ObjectProvider<JavaMailSender> senders,TicketEmailProperties props,TicketPdfService pdf) {
        this.senders=senders;this.props=props;this.pdf=pdf;
    }
    static class AttachmentTooLarge extends IOException {}
    static final class LimitedOutput extends FilterOutputStream {
        private final long max;private long written;
        LimitedOutput(OutputStream out,long max){super(out);this.max=max;}
        @Override public void write(int value)throws IOException{if(++written>max)throw new AttachmentTooLarge();out.write(value);}
        @Override public void write(byte[] bytes,int offset,int length)throws IOException{if(written+length>max)throw new AttachmentTooLarge();written+=length;out.write(bytes,offset,length);}
    }
    public void send(TicketEmailQueue.Claim claim)throws Exception {
        TicketEmailProperties.requireAddress(claim.recipient());
        var sender=senders.getIfAvailable();
        if(sender==null)throw new IllegalStateException("SMTP chưa được cấu hình");
        var paid=pdf.prepare(claim.accessToken());var order=paid.order();
        boolean zip=paid.tickets().size()>=300;
        Path attachment=Files.createTempFile("vticket-email-",zip?".zip":".pdf");
        try {
            boolean attach=true;
            try(var output=new LimitedOutput(Files.newOutputStream(attachment),props.attachmentLimitBytes())){
                if(zip)pdf.writeZip(paid,output);else output.write(pdf.generate(claim.accessToken()));
            }catch(AttachmentTooLarge e){attach=false;}
            String root=props.publicBaseUrl().replaceAll("/+$","")+"/api/public/orders/"+claim.accessToken();
            String shortId=order.id.substring(0,8).toUpperCase(Locale.ROOT);
            String body="Xin chào "+order.customerName+",\n\nVenusgiti đã xác nhận thanh toán thành công.\n"
                    +"Mã đơn: "+shortId+"\nNgày sử dụng: "+order.visitDate+"\nSố vé: "+paid.tickets().size()
                    +"\nTổng đã thanh toán: "+NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(order.total)+" đ\n\n"
                    +(attach?"Vé được đính kèm email này.\n":"File vé vượt giới hạn đính kèm. Vui lòng tải bằng đường dẫn dưới đây.\n")
                    +"Tải PDF gộp: "+root+"/tickets.pdf\nTải ZIP các vé riêng: "+root+"/tickets.zip\n\n"
                    +"Mỗi vé dùng một lần, đúng ngày ghi trên vé. Quét tại hệ thống Venusgiti Onsen.\n"
                    +"Đường dẫn tải vé là riêng tư. Chỉ chuyển vé cho đúng người sử dụng và không chia sẻ công khai.\n\nCảm ơn bạn đã lựa chọn Venusgiti!";
            if(paid.monthlyPass()!=null)body="Xin chào "+order.customerName+",\n\nVenusgiti đã xác nhận thanh toán thẻ tháng "+paid.monthlyPass().tier()+".\n"
                    +"Số ngày đăng ký: "+paid.monthlyPass().days().size()+"\n"
                    +"Thẻ QR và lịch sử dụng được đính kèm. Tải lại: "+root+"/tickets.pdf\n\n"
                    +"Mỗi ngày đã đăng ký, vui lòng đưa QR thẻ tháng tại quầy để nhận vé lượt. QR thẻ tháng không dùng trực tiếp tại cổng Smile.\n"
                    +"Không chia sẻ QR hoặc đường dẫn tải thẻ công khai.\n\nCảm ơn bạn đã lựa chọn Venusgiti!";
            var message=sender.createMimeMessage();var helper=new MimeMessageHelper(message,attach,"UTF-8");
            helper.setFrom(props.from());helper.setTo(claim.recipient());helper.setSubject("Venusgiti - Vé điện tử đơn "+shortId);
            helper.setText(body,false);
            if(attach)helper.addAttachment("Venusgiti-"+shortId+(zip?".zip":".pdf"),new FileSystemResource(attachment),zip?"application/zip":"application/pdf");
            sender.send(message);
        }finally{try{Files.deleteIfExists(attachment);}catch(IOException ignored){/* Temp cleanup must not retry a message already accepted by SMTP. */}}
    }
}
