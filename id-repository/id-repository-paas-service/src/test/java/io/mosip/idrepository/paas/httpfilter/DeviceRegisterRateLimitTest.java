package io.mosip.idrepository.paas.httpfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.config.DeviceProperties;
import io.mosip.idrepository.paas.constant.EnrolmentApi;

/** The per-IP limit on {@code POST /device/register} (MP-247) and that it is counted apart from the app config. */
public class DeviceRegisterRateLimitTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:45Z"), ZoneOffset.UTC);

	private static PublicEndpointRateLimitFilter filter(int perMinute) {
		DeviceProperties properties = new DeviceProperties();
		properties.getRateLimit().setRequestsPerMinute(perMinute);
		return new PublicEndpointRateLimitFilter(EnrolmentApi::isDeviceRegister, EnrolmentApi.DEVICE_REGISTER,
				() -> properties.getRateLimit().getRequestsPerMinute(),
				() -> properties.getRateLimit().getMaxTrackedClients(), new ObjectMapper(), CLOCK);
	}

	private static MockHttpServletRequest request(String servletPath, String ip) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/enrolment" + servletPath);
		request.setContextPath("/v1/enrolment");
		request.setServletPath(servletPath);
		request.setRemoteAddr(ip);
		return request;
	}

	@Test
	public void onlyDeviceRegistrationIsLimited() {
		PublicEndpointRateLimitFilter filter = filter(1);
		assertFalse(filter.shouldNotFilter(request("/device/register", "1.1.1.1")));
		assertTrue(filter.shouldNotFilter(request("/app/config", "1.1.1.1")));
		assertTrue(filter.shouldNotFilter(request("/passport", "1.1.1.1")));
	}

	@Test
	public void defaultIsStricterThanTheAppConfigLimit() {
		assertEquals(30, new DeviceProperties().getRateLimit().getRequestsPerMinute());
	}

	@Test
	public void requestsOverTheLimitAreEnrGen002WithTheDeviceEnvelopeId() throws Exception {
		PublicEndpointRateLimitFilter filter = filter(2);
		for (int i = 0; i < 2; i++) {
			MockFilterChain chain = new MockFilterChain();
			filter.doFilter(request("/device/register", "1.1.1.1"), new MockHttpServletResponse(), chain);
			assertTrue("request " + (i + 1) + " passes", chain.getRequest() != null);
		}
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request("/device/register", "1.1.1.1"), response, chain);

		assertNull(chain.getRequest());
		assertEquals(429, response.getStatus());
		assertTrue(response.getContentAsString().contains("\"errorCode\":\"ENR-GEN-002\""));
		assertTrue(response.getContentAsString().contains("\"id\":\"mosip.enrolment.device.register\""));
	}

	@Test
	public void bothPublicEndpointsSkipTheSessionCheck() {
		assertTrue(EnrolmentApi.isPublic(request("/device/register", "1.1.1.1")));
		assertTrue(EnrolmentApi.isPublic(request("/app/config", "1.1.1.1")));
		assertFalse(EnrolmentApi.isPublic(request("/passport", "1.1.1.1")));
		assertEquals(EnrolmentApi.DEVICE_REGISTER, EnrolmentApi.forRequest(request("/device/register", "1.1.1.1")));
	}
}
