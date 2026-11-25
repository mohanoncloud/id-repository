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

import io.mosip.idrepository.paas.config.AppConfigProperties;

public class PublicEndpointRateLimitFilterTest {

	/** 10:00:45 UTC: 15 seconds left in the minute. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T10:00:45Z"), ZoneOffset.UTC);

	private static PublicEndpointRateLimitFilter filter(int perMinute, int maxClients) {
		AppConfigProperties properties = new AppConfigProperties();
		properties.getRateLimit().setRequestsPerMinute(perMinute);
		properties.getRateLimit().setMaxTrackedClients(maxClients);
		return new PublicEndpointRateLimitFilter(properties, new ObjectMapper(), CLOCK);
	}

	private static MockHttpServletRequest request(String servletPath, String ip) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/enrolment" + servletPath);
		request.setContextPath("/v1/enrolment");
		request.setServletPath(servletPath);
		request.setRemoteAddr(ip);
		return request;
	}

	@Test
	public void onlyTheAppConfigPathIsLimited() {
		PublicEndpointRateLimitFilter filter = filter(1, 10);
		assertFalse(filter.shouldNotFilter(request("/app/config", "1.1.1.1")));
		assertTrue(filter.shouldNotFilter(request("/passport", "1.1.1.1")));
		assertTrue(filter.shouldNotFilter(request("/actuator/health", "1.1.1.1")));
	}

	@Test
	public void requestsOverTheLimitAreEnrGen002WithRetryAfter() throws Exception {
		PublicEndpointRateLimitFilter filter = filter(2, 10);

		for (int i = 0; i < 2; i++) {
			MockFilterChain chain = new MockFilterChain();
			filter.doFilter(request("/app/config", "1.1.1.1"), new MockHttpServletResponse(), chain);
			assertTrue("request " + (i + 1) + " passes", chain.getRequest() != null);
		}
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request("/app/config", "1.1.1.1"), response, chain);

		assertNull("third request stops here", chain.getRequest());
		assertEquals(429, response.getStatus());
		assertEquals("15", response.getHeader("Retry-After"));
		assertTrue(response.getContentAsString().contains("\"errorCode\":\"ENR-GEN-002\""));
		assertTrue(response.getContentAsString().contains("\"id\":\"mosip.enrolment.app.config\""));
	}

	@Test
	public void eachClientHasItsOwnCount() throws Exception {
		PublicEndpointRateLimitFilter filter = filter(1, 10);
		filter.doFilter(request("/app/config", "1.1.1.1"), new MockHttpServletResponse(), new MockFilterChain());

		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request("/app/config", "2.2.2.2"), new MockHttpServletResponse(), chain);

		assertTrue(chain.getRequest() != null);
	}

	@Test
	public void countStartsAgainEachMinute() {
		PublicEndpointRateLimitFilter filter = filter(1, 10);
		assertEquals(1, filter.count("1.1.1.1", 100));
		assertEquals(2, filter.count("1.1.1.1", 100));
		assertEquals(1, filter.count("1.1.1.1", 101));
	}

	@Test
	public void trackedClientsStayBounded() {
		PublicEndpointRateLimitFilter filter = filter(1, 3);
		filter.count("a", 100);
		filter.count("b", 100);
		filter.count("c", 101);

		// Full: the counts from minute 100 are dropped first.
		filter.count("d", 101);
		assertEquals(2, filter.trackedClients());

		// Full again with everyone active this minute: new clients pass uncounted, the others keep their counts.
		filter.count("e", 101);
		assertEquals(0, filter.count("f", 101));
		assertEquals(3, filter.trackedClients());
		assertEquals(2, filter.count("c", 101));
	}
}
