package vn.venusgiti.vticket;

import jakarta.servlet.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final AuthenticationManager authentication;
    AuthController(SecurityContextRepository contexts,CsrfTokenRepository csrf,AuthenticationManager authentication){this.contexts=contexts;this.csrf=csrf;this.authentication=authentication;}
    record LoginInput(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=1000) String password) {}

    /** Credentials are verified once; subsequent browser requests use HttpOnly session cookies. */
    @PostMapping("/login")
    public Map<String,Object> login(@Valid @RequestBody LoginInput input,HttpServletRequest request,HttpServletResponse response){
        final org.springframework.security.core.Authentication auth;
        try{auth=authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username(),input.password()));}
        catch(AuthenticationException e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Tài khoản hoặc mật khẩu không đúng");}
        if(request.getSession(false)!=null)request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(auth,request,response);
        contexts.saveContext(context,request,response);
        response.setHeader("Cache-Control","no-store");
        return Map.of("username",auth.getName(),"roles",auth.getAuthorities().stream().map(Object::toString).toList());
    }
}
