package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import java.util.*;
import vn.venusgiti.vticket.MonthlyDomain.*;
@RestController @RequestMapping("/api")
public class MonthlyController {
 private final MonthlyService monthly;
 private final BookingService bookings;
 MonthlyController(MonthlyService monthly,BookingService bookings){this.monthly=monthly;this.bookings=bookings;}
 public record ScanResult(String type,MonthlyService.View monthlyPass,vn.venusgiti.vticket.Domain.Ticket ticket) {}
 @PostMapping("/staff/scan") public ScanResult scan(@Valid @RequestBody Requests.CheckInInput input,Authentication a){
  var pass=monthly.recognize(input.qrToken());
  if(pass!=null)return new ScanResult("MONTHLY_PASS",pass,null);
  return new ScanResult("TICKET",null,bookings.checkIn(input.qrToken(),a.getName()));
 }
 @GetMapping("/staff/monthly/plans") public Object plans(){return monthly.plans();}
 @PutMapping("/admin/monthly/plans/{tier}") public Object save(@PathVariable Tier tier,@Valid @RequestBody MonthlyService.PlanInput input,Authentication a){return monthly.save(tier,input,a.getName());}
 @PostMapping("/staff/monthly/orders") public Object sell(@Valid @RequestBody MonthlyService.SaleInput input,Authentication a){return monthly.sell(input,a.getName());}
 @GetMapping("/staff/monthly/passes") public Object list(){return monthly.list();}
 @PostMapping("/staff/monthly/lookup") public Object lookup(@RequestBody Map<String,String> in){return monthly.lookup(in.getOrDefault("qrToken",""));}
 @PostMapping("/staff/monthly/redeem") public Object redeem(@Valid @RequestBody MonthlyService.RedeemInput in,Authentication a){return monthly.redeem(in,a.getName());}
}
