package io.mosip.idrepository.paas;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger UI and the OpenAPI document load and list the passport and app config endpoints, without a session.
 * Spring Security's default chain is excluded here: in an environment the kernel auth adapter provides
 * the security chain and lets these paths through ({@code mosip.service.end-points=/**}).
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
		+ "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@AutoConfigureMockMvc
public class SwaggerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	public void openApiDocumentListsTheEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("ID PAAS: My Pass Enrolment API"))
				.andExpect(jsonPath("$.paths['/passport'].post").exists())
				.andExpect(jsonPath("$.paths['/app/config'].get").exists());
	}

	@Test
	public void swaggerUiLoadsWithoutASession() throws Exception {
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Swagger UI")));
	}
}
