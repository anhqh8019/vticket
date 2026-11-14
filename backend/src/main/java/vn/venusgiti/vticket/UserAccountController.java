package vn.venusgiti.vticket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/admin/users")
public class UserAccountController {
 private final UserAccountService users;
 UserAccountController(UserAccountService users){this.users=users;}
 record Create(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=150) String displayName,@NotBlank @Size(min=12,max=72) String password,@NotNull @Pattern(regexp="ADMIN|STAFF") String role){}
 record Update(@NotBlank @Size(max=150) String displayName,@NotNull @Pattern(regexp="ADMIN|STAFF") String role,@NotNull Boolean enabled){}
 record Password(@NotBlank @Size(min=12,max=72) String password){}
 @GetMapping public List<UserAccountService.View> list(){return users.list();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserAccountService.View create(@Valid @RequestBody Create in,Authentication a){return users.create(in.username(),in.displayName(),in.password(),in.role(),a.getName());}
 @PutMapping("/{id}") public UserAccountService.View update(@PathVariable long id,@Valid @RequestBody Update in,Authentication a){return users.update(id,in.displayName(),in.role(),in.enabled(),a.getName());}
 @PostMapping("/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void password(@PathVariable long id,@Valid @RequestBody Password in,Authentication a){users.resetPassword(id,in.password(),a.getName());}
}
