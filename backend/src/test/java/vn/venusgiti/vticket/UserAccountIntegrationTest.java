package vn.venusgiti.vticket;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:useraccounts;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class UserAccountIntegrationTest {
 @Autowired MockMvc mvc;@Autowired UserAccountService users;@Autowired UserAccounts accounts;
 static final String PASSWORD="test-user-password-2026";
 String unique(){return "user-"+UUID.randomUUID();}
 UserAccountService.View create(String role){String name=unique();return users.create(name,"Nhân viên thử",PASSWORD,role,"admin");}
 MockHttpSession login(String name,String password)throws Exception{
  return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
 }
 String json(Object value){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);}
 @Test void adminCanCreateListUpdateAndReset()throws Exception{
  var name=unique();var result=mvc.perform(post("/api/admin/users").with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(json(Map.of("username",name,"displayName","Nhân viên mới","password",PASSWORD,"role","STAFF")))).andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
  var id=tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(get("/api/admin/users").with(httpBasic("admin","demo-admin-change-me"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))));
  mvc.perform(put("/api/admin/users/"+id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(json(Map.of("displayName","Tên cập nhật","role","STAFF","enabled",true)))).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Tên cập nhật"));
  mvc.perform(post("/api/admin/users/"+id+"/password").with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(json(Map.of("password","new-user-password-2026")))).andExpect(status().isNoContent());
  login(name,"new-user-password-2026");
  assertTrue(accounts.findById(id).orElseThrow().passwordHash.startsWith("{bcrypt}"));
 }
 @Test void staffAndAnonymousCannotManageUsers()throws Exception{
  mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/users").with(httpBasic("staff","demo-staff-change-me"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/users").with(csrf()).with(httpBasic("staff","demo-staff-change-me")).contentType("application/json").content(json(Map.of("username",unique(),"displayName","Test","password",PASSWORD,"role","ADMIN")))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/users").with(httpBasic("admin","demo-admin-change-me")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void duplicateCaseInsensitiveAndWeakPasswordRejected()throws Exception{
  var u=create("STAFF");
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->users.create(u.username().toUpperCase(Locale.ROOT),"Test",PASSWORD,"STAFF","admin"));
  for(String password:List.of("short","密".repeat(25))){
   mvc.perform(post("/api/admin/users").with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(json(Map.of("username",unique(),"displayName","Test","password",password,"role","STAFF")))).andExpect(status().isBadRequest());
  }
 }
 @Test void lockRevokesExistingSessionAndUnlockAllowsLogin()throws Exception{
  var u=create("STAFF");var session=login(u.username(),PASSWORD);
  users.update(u.id(),u.displayName(),u.role(),false,"admin");
  mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());assertTrue(session.isInvalid());
  mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json(Map.of("username",u.username(),"password",PASSWORD)))).andExpect(status().isUnauthorized());
  users.update(u.id(),u.displayName(),u.role(),true,"admin");login(u.username(),PASSWORD);
 }
 @Test void resettingPasswordRevokesExistingSessionAndOldPassword()throws Exception{
  var u=create("STAFF");var session=login(u.username(),PASSWORD);users.resetPassword(u.id(),"new-user-password-2026","admin");
  mvc.perform(get("/api/staff/orders").session(session)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json(Map.of("username",u.username(),"password",PASSWORD)))).andExpect(status().isUnauthorized());
  login(u.username(),"new-user-password-2026");
 }
 @Test void changingRoleRevokesOldAdminPrivileges()throws Exception{
  var u=create("ADMIN");var session=login(u.username(),PASSWORD);users.update(u.id(),u.displayName(),"STAFF",true,"admin");
  mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isUnauthorized());
  var fresh=login(u.username(),PASSWORD);mvc.perform(get("/api/admin/users").session(fresh)).andExpect(status().isForbidden());
 }
 @Test void selfLockAndDemotionRejectedAndRestartBootstrapDoesNotOverwrite(){
  var admin=accounts.findByUsername("admin").orElseThrow();
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->users.update(admin.id,"Admin","ADMIN",false,"admin"));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->users.update(admin.id,"Admin","STAFF",true,"admin"));
  var u=create("STAFF");users.resetPassword(u.id(),"new-user-password-2026","admin");var saved=accounts.findById(u.id()).orElseThrow().passwordHash;
  users.bootstrap("admin","different-password","staff","different-password");assertEquals(saved,accounts.findById(u.id()).orElseThrow().passwordHash);
 }
}
