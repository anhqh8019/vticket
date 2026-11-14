package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @Transactional
public class UserAccountService implements UserDetailsService {
 private final UserAccounts accounts;private final Audits audits;private final Clock clock;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 UserAccountService(UserAccounts accounts,Audits audits,Clock clock){this.accounts=accounts;this.audits=audits;this.clock=clock;}
 public static class Principal extends User {
  public final long securityVersion;
  Principal(UserAccount u){super(u.username,u.passwordHash,u.enabled,true,true,true,List.of(new SimpleGrantedAuthority("ROLE_"+u.role)));securityVersion=u.securityVersion;}
 }
 public record View(Long id,String username,String displayName,String role,boolean enabled,Instant createdAt) {}
 private View view(UserAccount u){return new View(u.id,u.username,u.displayName,u.role,u.enabled,u.createdAt);}
 static String username(String value){return value.strip().toLowerCase(Locale.ROOT);}
 @Override @Transactional(readOnly=true) public UserDetails loadUserByUsername(String name){return new Principal(accounts.findByUsername(username(name)).orElseThrow(()->new UsernameNotFoundException("Unknown user")));}
 @Transactional(readOnly=true) public boolean current(Principal principal){return accounts.findByUsername(principal.getUsername()).map(u->u.enabled&&u.securityVersion==principal.securityVersion&&principal.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+u.role))).orElse(false);}
 @Transactional(readOnly=true) public List<View> list(){return accounts.findAll(org.springframework.data.domain.Sort.by("username")).stream().map(this::view).toList();}
 public void bootstrap(String admin,String password,String staff,String staffPassword){
  if(accounts.count()!=0)return;
  if(username(admin).equals(username(staff)))throw new IllegalStateException("Configure separate ADMIN/STAFF users");
  create(admin,admin,password,"ADMIN","system");create(staff,staff,staffPassword,"STAFF","system");
 }
 private String hash(String password){
  if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu cần ít nhất 12 ký tự và tối đa 72 byte UTF-8");
  return "{bcrypt}"+encoder.encode(password);
 }
 public View create(String name,String displayName,String password,String role,String actor){
  String login=username(name);
  if(!login.matches("[a-z0-9][a-z0-9._@-]{2,99}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên đăng nhập gồm 3–100 ký tự: chữ không dấu, số, dấu . _ @ -");
  if(accounts.findByUsername(login).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Tên đăng nhập đã tồn tại");
  checkRole(role);var u=new UserAccount();u.username=login;u.displayName=displayName.strip();u.passwordHash=hash(password);u.role=role;u.createdAt=clock.instant();accounts.saveAndFlush(u);audit(actor,"USER_CREATED",login);return view(u);
 }
 public View update(long id,String displayName,String role,boolean enabled,String actor){
  checkRole(role);
  var all=accounts.lockAccounts();var u=all.stream().filter(x->x.id==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản"));
  if(u.username.equals(actor)&&(!enabled||!role.equals("ADMIN")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể tự khóa hoặc hạ quyền tài khoản đang đăng nhập");
  if(u.enabled&&u.role.equals("ADMIN")&&(!enabled||!role.equals("ADMIN"))&&all.stream().filter(x->x.enabled&&x.role.equals("ADMIN")).count()<=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Phải còn ít nhất một quản trị viên hoạt động");
  if(u.enabled!=enabled||!u.role.equals(role))u.securityVersion++;
  u.displayName=displayName.strip();u.role=role;u.enabled=enabled;audit(actor,"USER_UPDATED",u.username);return view(u);
 }
 public void resetPassword(long id,String password,String actor){
  var u=accounts.lockAccounts().stream().filter(x->x.id==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản"));
  u.passwordHash=hash(password);u.securityVersion++;audit(actor,"USER_PASSWORD_RESET",u.username);
 }
 private void checkRole(String role){if(!Set.of("ADMIN","STAFF").contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quyền không hợp lệ");}
 private void audit(String actor,String action,String target){var a=new Domain.Audit();a.actor=actor;a.action=action;a.target=target;a.happenedAt=clock.instant();audits.save(a);}
}
