package vn.venusgiti.vticket;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import java.time.Clock;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@Configuration @EnableConfigurationProperties(VietQrProperties.class)
public class SecurityConfig {
 @Bean Clock clock() { return Clock.systemUTC(); }
 @Bean UserDetailsService users(@Value("${app.admin-user}") String admin,@Value("${app.admin-password}") String password,@Value("${app.staff-user}") String staff,@Value("${app.staff-password}") String staffPassword) {
  if(password.length()<12||staffPassword.length()<12||admin.equals(staff)) throw new IllegalStateException("Configure separate ADMIN/STAFF users and passwords of at least 12 characters");
  var encoder=new BCryptPasswordEncoder();
  return new InMemoryUserDetailsManager(User.withUsername(admin).password("{bcrypt}"+encoder.encode(password)).roles("ADMIN").build(),User.withUsername(staff).password("{bcrypt}"+encoder.encode(staffPassword)).roles("STAFF").build());
 }
 @Bean @Order(1) SecurityFilterChain callbacks(HttpSecurity http) throws Exception {return http.securityMatcher("/vietqr/**","/api/vietqr/**","/api-vietqr-callback/**").csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).build();}
 @Bean @Order(2) SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.csrf(c->c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
   .authorizeHttpRequests(a->a.requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/staff/**").hasAnyRole("ADMIN","STAFF").requestMatchers("/api/me").authenticated().requestMatchers("/api/public/**","/api/csrf","/actuator/health","/error").permitAll().anyRequest().denyAll())
   .httpBasic(b->b.authenticationEntryPoint((req,res,e)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Vui lòng đăng nhập\"}");}))
   .sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).build();
 }
}
