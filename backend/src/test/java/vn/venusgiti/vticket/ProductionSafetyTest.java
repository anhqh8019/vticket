package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:safety;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","app.demo-payment=false","app.seed-data=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class ProductionSafetyTest {
 @Autowired MockMvc mvc;
 @Test void fakePaymentEndpointDisabled() throws Exception {mvc.perform(post("/api/public/orders/anything/demo-pay").with(csrf())).andExpect(status().isNotFound());}
}
