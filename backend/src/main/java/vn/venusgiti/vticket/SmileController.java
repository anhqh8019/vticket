package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController @RequestMapping("/api/admin/smile")
public class SmileController {
 private final SmileProperties props;private final SmileGateway gateway;private final SmileSyncQueue queue;
 SmileController(SmileProperties p,SmileGateway g,SmileSyncQueue q){props=p;gateway=g;queue=q;}
 @GetMapping("/status") public Map<String,Object> status(){return Map.of("enabled",props.enabled(),"configured",props.configured(),"schema",props.schema(),"table",props.table(),"mapping",props.codeMapping(),"trigger","BATCH_CREATED");}
 @PostMapping("/connection-test") public Map<String,Object> test(){try{return Map.of("ok",true,"columns",gateway.inspect());}catch(IllegalStateException e){return Map.of("ok",false,"message",e.getMessage());}}
 @GetMapping("/jobs") public Object jobs(){return queue.list();}
 public record SyncInput(@NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9._/-]+") String pkgCode,@Size(max=150) String typeName) {}
 @PostMapping("/batches/{id}/sync") public Object sync(@PathVariable String id,@Valid @RequestBody SyncInput input,Authentication a){return queue.configureAndQueue(id,input.pkgCode(),input.typeName(),a.getName());}
}
