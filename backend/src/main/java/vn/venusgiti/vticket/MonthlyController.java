package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import java.util.*;
import vn.venusgiti.vticket.MonthlyDomain.*;
@RestController @RequestMapping("/api")
public class MonthlyController {
 private final MonthlyService monthly;
 MonthlyController(MonthlyService monthly){this.monthly=monthly;}
 @GetMapping("/staff/monthly/plans") public Object plans(){return monthly.plans();}
 @PutMapping("/admin/monthly/plans/{tier}") public Object save(@PathVariable Tier tier,@Valid @RequestBody MonthlyService.PlanInput input,Authentication a){return monthly.save(tier,input,a.getName());}
 @PostMapping("/staff/monthly/orders") public Object sell(@Valid @RequestBody MonthlyService.SaleInput input,Authentication a){return monthly.sell(input,a.getName());}
 @GetMapping("/staff/monthly/passes") public Object list(){return monthly.list();}
 @PostMapping("/staff/monthly/lookup") public Object lookup(@RequestBody Map<String,String> in){return monthly.lookup(in.getOrDefault("qrToken",""));}
 @PostMapping("/staff/monthly/redeem") public Object redeem(@Valid @RequestBody MonthlyService.RedeemInput in,Authentication a){return monthly.redeem(in,a.getName());}
}
