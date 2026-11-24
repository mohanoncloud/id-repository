package io.mosip.idrepository.paas;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.idrepository.paas.repository.AppConfigRepository;
import io.mosip.idrepository.paas.service.impl.AppConfigServiceImplTest;

/**
 * The app config rate limit is keyed on the client IP the ingress saw, not one the client puts in
 * {@code X-Forwarded-For} (MP-243). Runs on a real Tomcat, since {@code RemoteIpValve} sets the client IP. The
 * test client connects from 127.0.0.1, a trusted internal proxy, like the ingress does.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
				+ "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
		// As in bootstrap.properties.
		"server.forward-headers-strategy=native",
		"mosip.idpaas.app-config.rate-limit.requests-per-minute=2" })
public class AppConfigClientIpTest {

	@LocalServerPort
	private int port;

	@MockBean
	private AppConfigRepository repository;

	private int get(String xForwardedFor) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(
				URI.create("http://127.0.0.1:" + port + "/v1/enrolment/app/config?platform=android"))
				.header("X-Forwarded-For", xForwardedFor).GET().build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	@Test
	public void addressesTheClientAddsToXForwardedForAreIgnored() throws Exception {
		when(repository.findByPlatform(any())).thenReturn(Optional.of(AppConfigServiceImplTest.android(1)));

		// The client sends a different X-Forwarded-For each time; the ingress appends the address it saw.
		assertEquals(200, get("198.51.100.1, 203.0.113.7"));
		assertEquals(200, get("198.51.100.2, 203.0.113.7"));
		assertEquals(429, get("198.51.100.3, 203.0.113.7"));

		// Another real client has its own count.
		assertEquals(200, get("198.51.100.3, 203.0.113.8"));
	}
}
