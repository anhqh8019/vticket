package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController @RequestMapping("/api/admin/ticket-emails")
public class TicketEmailController {
    private final TicketEmailQueue queue;
    TicketEmailController(TicketEmailQueue queue){this.queue=queue;}
    @GetMapping public Object jobs(){return queue.list();}
    @PostMapping("/{id}/retry") public Object retry(@PathVariable String id,Authentication auth){queue.retry(id,auth.getName());return queue.view(id);}
}
