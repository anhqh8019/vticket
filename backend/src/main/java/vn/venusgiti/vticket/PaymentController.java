package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.io.*;

@RestController @RequestMapping("/api")
public class PaymentController {
 private final VietQrPaymentService payments;private final BookingService bookings;
 PaymentController(VietQrPaymentService p,BookingService b){payments=p;bookings=b;}
 @PostMapping("/public/orders/{token}/vietqr") public VietQrPaymentService.View initiate(@PathVariable String token){return payments.initiate(token);}
 @PostMapping("/public/orders/{token}/confirm-zero") public vn.venusgiti.vticket.Domain.Booking zero(@PathVariable String token){return bookings.confirmZero(token);}
 @GetMapping(value="/public/orders/{token}/vietqr/image",produces=MediaType.IMAGE_PNG_VALUE)
 public ResponseEntity<byte[]> image(@PathVariable String token)throws Exception{String providerPayload=payments.qrPayload(token);var qr=new MultiFormatWriter().encode(providerPayload,BarcodeFormat.QR_CODE,380,380);var out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(qr,"PNG",out);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out.toByteArray());}
 @GetMapping("/admin/payment-receipts") public Object receipts(){return payments.receipts();}
}
