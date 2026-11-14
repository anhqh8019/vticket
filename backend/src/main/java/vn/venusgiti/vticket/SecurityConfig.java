package vn.venusgiti.vticket;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import java.time.Clock;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@Configuration @EnableConfigurationProperties({VietQrProperties.class,SmileProperties.class,TicketEmailProperties.class})
public class SecurityConfig {
 @Bean Clock clock() { return Clock.systemUTC(); }
 @Bean org.springframework.boot.ApplicationRunner initialAccounts(UserAccountService users,@Value("${app.admin-user}") String admin,@Value("${app.admin-password}") String password,@Value("${app.staff-user}") String staff,@Value("${app.staff-password}") String staffPassword){return args->users.bootstrap(admin,password,staff,staffPassword);}
 @Bean @Order(1) SecurityFilterChain callbacks(HttpSecurity http) throws Exception {return http.securityMatcher("/vietqr/**","/api/vietqr/**","/api-vietqr-callback/**").csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).build();}
 @Bean org.springframework.security.authentication.AuthenticationManager authenticationManager(org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration configuration) throws Exception {return configuration.getAuthenticationManager();}
 @Bean org.springframework.security.web.context.SecurityContextRepository staffContexts(){return new org.springframework.security.web.context.HttpSessionSecurityContextRepository();}
 @Bean org.springframework.security.web.csrf.CsrfTokenRepository staffCsrf(){return CookieCsrfTokenRepository.withHttpOnlyFalse();}
 @Bean @Order(2) SecurityFilterChain security(HttpSecurity http,org.springframework.security.web.context.SecurityContextRepository staffContexts,org.springframework.security.web.csrf.CsrfTokenRepository staffCsrf,UserAccountService users) throws Exception {
  return http.csrf(c->c.csrfTokenRepository(staffCsrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
   .authorizeHttpRequests(a->a.requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/staff/**").hasAnyRole("ADMIN","STAFF").requestMatchers("/api/me").authenticated().requestMatchers("/api/auth/login","/api/public/**","/api/csrf","/actuator/health","/error").permitAll().anyRequest().denyAll())
   .httpBasic(b->b.authenticationEntryPoint((req,res,e)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Vui lòng đăng nhập\"}");}))
   .securityContext(c->c.securityContextRepository(staffContexts))
   .addFilterBefore(new AccountSessionFilter(users),org.springframework.security.web.access.intercept.AuthorizationFilter.class)
   .requestCache(c->c.disable())
   .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID").logoutSuccessHandler((req,res,a)->{res.setHeader("Cache-Control","no-store");res.setStatus(204);}))
   .sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)).build();
 }
}
