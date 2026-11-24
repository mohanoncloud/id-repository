package io.mosip.idrepository.paas.httpfilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.config.AppConfigProperties;
import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limits the public app config endpoint to {@code mosip.idpaas.app-config.rate-limit.requests-per-minute} per
 * client IP, answering ENR-GEN-002 (429) above it (MP-243). Counts are per pod and per calendar minute, so
 * this is a backstop; a limit at the ingress can sit in front of it.
 *
 * <p>The client IP is {@code getRemoteAddr()}. With {@code server.forward-headers-strategy=native}, Tomcat's
 * {@code RemoteIpValve} sets it to the right-most {@code X-Forwarded-For} entry that isn't a trusted internal
 * proxy, i.e. the address the ingress saw. Entries the client adds itself sit further left and are ignored.
 */
public class AppConfigRateLimitFilter extends OncePerRequestFilter {

	private static final long MINUTE_MILLIS = 60_000L;

	private final AppConfigProperties properties;
	private final ObjectMapper mapper;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public AppConfigRateLimitFilter(AppConfigProperties properties, ObjectMapper mapper, Clock clock) {
		this.properties = properties;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !EnrolmentApi.isAppConfig(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long minute = clock.millis() / MINUTE_MILLIS;
		if (count(String.valueOf(request.getRemoteAddr()), minute) > properties.getRateLimit().getRequestsPerMinute()) {
			long secondsLeft = Math.max(1, ((minute + 1) * MINUTE_MILLIS - clock.millis() + 999) / 1000);
			response.setStatus(EnrolmentErrorCode.GEN_002.getHttpStatus().value());
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondsLeft));
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			mapper.writeValue(response.getOutputStream(),
					EnrolmentResponse.error(EnrolmentApi.APP_CONFIG, EnrolmentErrorCode.GEN_002));
			return;
		}
		chain.doFilter(request, response);
	}

	/**
	 * This request's number within the client's current minute, or 0 for a new client while every tracked
	 * client is active this minute. Such a client isn't counted (the map stays bounded) and passes; the counts
	 * already held are kept, so a flood of new addresses can't reset them.
	 */
	int count(String client, long minute) {
		int max = properties.getRateLimit().getMaxTrackedClients();
		if (windows.size() >= max && !windows.containsKey(client)) {
			windows.values().removeIf(window -> window.minute != minute);
			if (windows.size() >= max) {
				return 0;
			}
		}
		Window window = windows.compute(client,
				(key, existing) -> existing == null || existing.minute != minute ? new Window(minute) : existing);
		return window.count.incrementAndGet();
	}

	int trackedClients() {
		return windows.size();
	}

	private static final class Window {

		private final long minute;
		private final AtomicInteger count = new AtomicInteger();

		private Window(long minute) {
			this.minute = minute;
		}
	}
}
