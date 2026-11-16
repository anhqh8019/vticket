package vn.venusgiti.vticket;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import vn.venusgiti.vticket.Domain.*;
import vn.venusgiti.vticket.Requests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:productdetails;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.seed-data=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class ProductDetailsIntegrationTest {
 @Autowired MockMvc mvc;@Autowired Products products;
 final tools.jackson.databind.json.JsonMapper mapper=tools.jackson.databind.json.JsonMapper.builder().build();
 Product base;
 @BeforeEach void setup(){base=new Product();base.code="DETAIL-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);base.name="Vé mẫu";base.kind=Kind.TICKET;base.price=BigDecimal.valueOf(100);base=products.saveAndFlush(base);}
 byte[] image()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
 String data()throws Exception{return "data:image/png;base64,"+Base64.getEncoder().encodeToString(image());}
 ProductInput input(Kind kind,String content,String image,boolean active){return new ProductInput(base.code,base.name,"Mô tả ngắn",kind,BigDecimal.valueOf(100),active,Set.of(),kind==Kind.COMBO?base.id:null,kind==Kind.COMBO?10:null,kind==Kind.COMBO?1:null,null,null,null,null,null,content,image);}
 @Test void ticketAndComboExposeDetailsAndServeImageWithoutInliningBinary()throws Exception{
  for(var kind:List.of(Kind.TICKET,Kind.COMBO)){
   var in=input(kind,"Dịch vụ bao gồm\nĐiều kiện sử dụng",data(),true);
   if(kind==Kind.TICKET)mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(in))).andExpect(status().isOk()).andExpect(jsonPath("$.detailContent").value(in.detailContent())).andExpect(jsonPath("$.detailImage").doesNotExist());
   else {in=new ProductInput("COMBO-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),"Combo mẫu","",kind,BigDecimal.valueOf(100),true,Set.of(),base.id,10,1,null,null,null,null,null,in.detailContent(),in.detailImageData());var result=mvc.perform(post("/api/admin/products").with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(in))).andExpect(status().isOk()).andReturn();var id=mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();assertNotNull(products.findById(id).orElseThrow().detailImage);}
  }
  mvc.perform(get("/api/public/products/"+base.id+"/image")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(image())).andExpect(header().string("X-Content-Type-Options","nosniff"));
  mvc.perform(get("/api/public/products")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+base.id+")].detailImageUrl").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("/api/public/products/")))).andExpect(jsonPath("$[0].detailImage").doesNotExist());
 }
 @Test void omittedDetailFieldsPreserveExistingDataAndBlankImageRemovesIt()throws Exception{
  mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(input(Kind.TICKET,"Nội dung nhiều dòng\nDòng hai",data(),true)))).andExpect(status().isOk());
  var old=products.findById(base.id).orElseThrow().detailImageVersion;
  var legacy=new ProductInput(base.code,base.name,"Mô tả khác",Kind.TICKET,BigDecimal.valueOf(100),true,Set.of());
  mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(legacy))).andExpect(status().isOk());
  assertEquals(old,products.findById(base.id).orElseThrow().detailImageVersion);assertTrue(products.findById(base.id).orElseThrow().detailContent.contains("Dòng hai"));
  mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(input(Kind.TICKET,"","",true)))).andExpect(status().isOk());
  mvc.perform(get("/api/public/products/"+base.id+"/image")).andExpect(status().isNotFound());
 }
 @Test void invalidImageAndLongContentRejectedWithoutChangingProduct()throws Exception{
  for(String bad:List.of("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=","data:image/png;base64,bm90LWFuLWltYWdl","https://example.com/picture.png","data:image/png;base64,"+"A".repeat(2800000))){
   mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(input(Kind.TICKET,"Nội dung",bad,true)))).andExpect(status().isBadRequest());
  }
  mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(input(Kind.TICKET,"X".repeat(12001),null,true)))).andExpect(status().isBadRequest());
  assertNull(products.findById(base.id).orElseThrow().detailImage);
 }
 @Test void staffCannotConfigureAndInactiveImageIsAdminOnly()throws Exception{
  mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("staff","demo-staff-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(input(Kind.TICKET,"Test",data(),true)))).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/products/"+base.id).with(httpBasic("admin","demo-admin-change-me")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(input(Kind.TICKET,"Test",data(),false)))).andExpect(status().isOk());
  mvc.perform(get("/api/public/products/"+base.id+"/image")).andExpect(status().isNotFound());
  mvc.perform(get("/api/admin/products/"+base.id+"/image").with(httpBasic("admin","demo-admin-change-me"))).andExpect(status().isOk());
 }
}
