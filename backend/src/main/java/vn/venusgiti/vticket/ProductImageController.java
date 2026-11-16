package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
@RestController @RequestMapping("/api")
public class ProductImageController {
 private final Products products;
 ProductImageController(Products products){this.products=products;}
 @GetMapping({"/public/products/{id}/image","/admin/products/{id}/image","/public/products/{id}/images/{position}","/admin/products/{id}/images/{position}"})
 public ResponseEntity<byte[]> image(@PathVariable Long id,@PathVariable(required=false) Integer position,HttpServletRequest request){
  int slot=position==null?0:position;if(slot<0||slot>2)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  var p=products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(p.imageBytes(slot)==null||(!p.active&&request.getRequestURI().startsWith("/api/public/")))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.imageMime(slot))).header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noCache()).body(p.imageBytes(slot));
 }
}
