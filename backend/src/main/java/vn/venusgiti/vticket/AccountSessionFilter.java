package vn.venusgiti.vticket;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;

/** Revoke existing sessions immediately after an account is locked, reassigned or reset. */
final class AccountSessionFilter extends OncePerRequestFilter {
 private final UserAccountService users;
 AccountSessionFilter(UserAccountService users){this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.getPrincipal() instanceof UserAccountService.Principal principal&&!users.current(principal)){
   SecurityContextHolder.clearContext();var session=request.getSession(false);if(session!=null)session.invalidate();
   response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.\"}");return;
  }
  chain.doFilter(request,response);
 }
}
