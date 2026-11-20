package io.mosip.idrepository.paas;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.idrepository.paas.controller.PassportController;
import io.mosip.idrepository.paas.session.SessionVerifier;
import io.mosip.idrepository.paas.session.StubSessionVerifier;

/** The service starts with the stubs, without a database, config server or auth adapter. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class IdPaasBootApplicationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	public void contextStartsWithTheStubs() {
		assertNotNull(context.getBean(PassportController.class));
		assertTrue(context.getBean(SessionVerifier.class) instanceof StubSessionVerifier);
		assertNotNull(context.getBean("enrolmentSessionFilter", FilterRegistrationBean.class));
	}
}
