package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
@RestController @RequestMapping("/api")
public class ProductImageController {
 private final Products products;
 ProductImageController(Products products){this.products=products;}
 @GetMapping({"/public/products/{id}/image","/admin/products/{id}/image"})
 public ResponseEntity<byte[]> image(@PathVariable Long id,HttpServletRequest request){
  var p=products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(p.detailImage==null||(!p.active&&request.getRequestURI().startsWith("/api/public/")))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.detailImageType)).header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noCache()).body(p.detailImage);
 }
}
