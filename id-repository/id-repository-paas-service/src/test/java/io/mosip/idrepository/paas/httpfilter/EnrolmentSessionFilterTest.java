package io.mosip.idrepository.paas.httpfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.config.SessionProperties;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.session.SessionVerifier;
import io.mosip.idrepository.paas.session.StubSessionVerifier;

public class EnrolmentSessionFilterTest {

	private final EnrolmentSessionFilter filter = new EnrolmentSessionFilter(new StubSessionVerifier(),
			new SessionProperties(), new ObjectMapper());

	private static MockHttpServletRequest request(String servletPath) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/enrolment" + servletPath);
		request.setContextPath("/v1/enrolment");
		request.setServletPath(servletPath);
		return request;
	}

	@Test
	public void actuatorAndApiDocsAreNotFiltered() {
		assertTrue(filter.shouldNotFilter(request("/actuator/health")));
		assertTrue(filter.shouldNotFilter(request("/swagger-ui/index.html")));
		assertTrue(filter.shouldNotFilter(request("/v3/api-docs")));
		assertFalse(filter.shouldNotFilter(request("/passport")));
	}

	/** The app reads its config before it has a session (MP-243); only that exact path is public. */
	@Test
	public void onlyTheExactAppConfigPathSkipsTheSessionCheck() {
		assertTrue(filter.shouldNotFilter(request("/app/config")));
		assertFalse(filter.shouldNotFilter(request("/app/config/../passport")));
		assertFalse(filter.shouldNotFilter(request("/app/configx")));
		assertFalse(filter.shouldNotFilter(request("/app")));
	}

	@Test
	public void validHeadersSetTheSessionAndContinue() throws Exception {
		MockHttpServletRequest request = request("/passport");
		request.addHeader("Authorization", "Bearer token");
		request.addHeader("X-Device-Signature", "sig");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertTrue(request.getAttribute(EnrolmentSessionFilter.SESSION_ATTRIBUTE) instanceof EnrolmentSession);
		assertEquals(request, chain.getRequest());
	}

	@Test
	public void headerNamesComeFromConfig() throws Exception {
		SessionProperties properties = new SessionProperties();
		properties.setTokenHeader("X-Session-Token");
		properties.setDeviceSignatureHeader("X-Device-JWS");
		EnrolmentSessionFilter custom = new EnrolmentSessionFilter(new StubSessionVerifier(), properties,
				new ObjectMapper());
		MockHttpServletRequest request = request("/passport");
		request.addHeader("X-Session-Token", "Bearer token");
		request.addHeader("X-Device-JWS", "sig");
		MockFilterChain chain = new MockFilterChain();

		custom.doFilter(request, new MockHttpServletResponse(), chain);

		assertEquals(request, chain.getRequest());
	}

	@Test
	public void blankBearerTokenIsRejected() throws Exception {
		MockHttpServletRequest request = request("/passport");
		request.addHeader("Authorization", "Bearer   ");
		request.addHeader("X-Device-Signature", "sig");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("ENR-SES-001"));
		assertEquals(null, chain.getRequest());
	}

	@Test
	public void tokenSchemeComesFromConfig() throws Exception {
		SessionProperties properties = new SessionProperties();
		properties.setTokenScheme("DPoP");
		EnrolmentSessionFilter dpop = new EnrolmentSessionFilter(new StubSessionVerifier(), properties,
				new ObjectMapper());

		MockHttpServletRequest accepted = request("/passport");
		accepted.addHeader("Authorization", "DPoP token");
		accepted.addHeader("X-Device-Signature", "sig");
		MockFilterChain chain = new MockFilterChain();
		dpop.doFilter(accepted, new MockHttpServletResponse(), chain);
		assertEquals(accepted, chain.getRequest());

		MockHttpServletRequest bearer = request("/passport");
		bearer.addHeader("Authorization", "Bearer token");
		bearer.addHeader("X-Device-Signature", "sig");
		MockHttpServletResponse response = new MockHttpServletResponse();
		dpop.doFilter(bearer, response, new MockFilterChain());
		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("ENR-SES-001"));
	}

	@Test
	public void unexpectedVerifierErrorIsEnrGen003WithErrors() throws Exception {
		SessionVerifier failing = (token, signature, req) -> {
			throw new IllegalStateException("cache timeout");
		};
		EnrolmentSessionFilter custom = new EnrolmentSessionFilter(failing, new SessionProperties(),
				new ObjectMapper());
		MockHttpServletRequest request = request("/passport");
		request.addHeader("Authorization", "Bearer token");
		request.addHeader("X-Device-Signature", "sig");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		custom.doFilter(request, response, chain);

		assertEquals(500, response.getStatus());
		assertTrue(response.getContentAsString().contains("\"errors\""));
		assertTrue(response.getContentAsString().contains("ENR-GEN-003"));
		assertFalse(response.getContentAsString().contains("cache timeout"));
		assertEquals(null, chain.getRequest());
	}
}
