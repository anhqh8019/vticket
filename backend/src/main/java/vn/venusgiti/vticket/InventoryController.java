package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;

@RestController @RequestMapping("/api")
public class InventoryController {
 private final InventoryService inventory; private final TicketPdfService pdf;
 InventoryController(InventoryService i,TicketPdfService p){inventory=i;pdf=p;}
 @GetMapping("/admin/ticket-batches") public List<Map<String,Object>> batches(){return inventory.list();}
 @PostMapping("/admin/ticket-batches") public TicketBatch create(@Valid @RequestBody BatchInput in,Authentication a){return inventory.createBatch(in,a.getName());}
 @GetMapping("/admin/ticket-batches/{id}/codes") public Map<String,Object> codes(@PathVariable String id,@RequestParam(required=false) StockState state,@RequestParam(defaultValue="0") int page){return inventory.codes(id,state,page);}
 @PutMapping("/admin/ticket-batches/{id}/active") public TicketBatch active(@PathVariable String id,@Valid @RequestBody BatchActiveInput in,Authentication a){return inventory.active(id,in.active(),a.getName());}
 @GetMapping(value="/public/orders/{token}/tickets.pdf",produces=MediaType.APPLICATION_PDF_VALUE)
 public ResponseEntity<byte[]> tickets(@PathVariable String token)throws IOException{return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"Venusgiti-tickets.pdf\"").cacheControl(CacheControl.noStore()).body(pdf.generate(token));}
}
