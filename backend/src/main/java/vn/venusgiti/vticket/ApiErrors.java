package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Yêu cầu không hợp lệ":e.getReason()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("message","Dữ liệu không hợp lệ: "+e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+" "+f.getDefaultMessage()).findFirst().orElse("")));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("message","Dữ liệu trùng hoặc đang được sử dụng. Vui lòng kiểm tra lại."));}
}
