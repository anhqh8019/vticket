package vn.venusgiti.vticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:authtest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class AuthSessionIntegrationTest {
 @Autowired MockMvc mvc;
 String credentials(String user,String password){return "{\"username\":\""+user+"\",\"password\":\""+password+"\"}";}
 MockHttpSession login(String user)throws Exception{
  return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(credentials(user,"demo-"+user+"-change-me"))).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(user)).andReturn().getRequest().getSession(false);
 }
 @Test void refreshRestoresAdminWithoutResendingPassword()throws Exception{
  var session=login("admin");assertNotNull(session);
  mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
  mvc.perform(get("/api/admin/products").session(session)).andExpect(status().isOk());
 }
 @Test void staffSessionRetainsPermissions()throws Exception{
  var session=login("staff");
  mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_STAFF"));
  mvc.perform(get("/api/staff/orders").session(session)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/products").session(session)).andExpect(status().isForbidden());
 }
 @Test void successfulLoginRotatesExistingSession()throws Exception{
  var session=new MockHttpSession();String old=session.getId();
  mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType("application/json").content(credentials("admin","demo-admin-change-me"))).andExpect(status().isOk());
  assertNotEquals(old,session.getId());
 }
 @Test void wrongPasswordIsRejectedEvenWithExistingAuthenticatedSession()throws Exception{
  var session=login("admin");
  mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType("application/json").content(credentials("admin","incorrect-password"))).andExpect(status().isUnauthorized());
 }
 @Test void wrongPasswordDoesNotCreateAuthenticatedSession()throws Exception{
  var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(credentials("admin","incorrect-password"))).andExpect(status().isUnauthorized()).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);
  var request=get("/api/me");if(session!=null)request.session(session);
  mvc.perform(request).andExpect(status().isUnauthorized());
 }
 @Test void loginAndLogoutRequireCsrf()throws Exception{
  mvc.perform(post("/api/auth/login").contentType("application/json").content(credentials("admin","demo-admin-change-me"))).andExpect(status().isForbidden());
  var session=login("admin");
  mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
  mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
 }
 @Test void logoutInvalidatesSessionAndAnonymousRefreshStaysLoggedOut()throws Exception{
  var session=login("admin");
  mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID",0));
  assertTrue(session.isInvalid());
  mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
 }
 @Test void freshCsrfTokenWorksAfterLogin()throws Exception{
  var session=login("admin");
  var token=mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
  var data=tools.jackson.databind.json.JsonMapper.builder().build().readTree(token.getResponse().getContentAsString());
  mvc.perform(post("/api/auth/logout").session(session).cookie(token.getResponse().getCookies()).header(data.get("headerName").asText(),data.get("token").asText())).andExpect(status().isNoContent());
 }
 @Test void basicApiClientsRemainCompatible()throws Exception{
  mvc.perform(get("/api/me").with(httpBasic("staff","demo-staff-change-me"))).andExpect(status().isOk());
  mvc.perform(get("/api/public/products")).andExpect(status().isOk());
 }
}
