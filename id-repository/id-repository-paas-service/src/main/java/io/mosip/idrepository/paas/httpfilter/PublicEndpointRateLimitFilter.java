package io.mosip.idrepository.paas.httpfilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

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
 * Limits a public endpoint (one called before the app has a session) per client IP, answering ENR-GEN-002
 * (429) above the limit. Used for the app config ({@code mosip.idpaas.app-config.rate-limit.*}, MP-243) and for
 * device registration ({@code mosip.idpaas.device.rate-limit.*}, MP-247), each with its own instance and so its own
 * counts. Counts are per pod and per calendar minute, so this is a backstop; a limit at the ingress can sit in
 * front of it.
 *
 * <p>The client IP is {@code getRemoteAddr()}. With {@code server.forward-headers-strategy=native}, Tomcat's
 * {@code RemoteIpValve} sets it to the right-most {@code X-Forwarded-For} entry that isn't a trusted internal
 * proxy, i.e. the address the ingress saw. Entries the client adds itself sit further left and are ignored.
 */
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

	private static final long MINUTE_MILLIS = 60_000L;

	private final Predicate<HttpServletRequest> applies;
	private final String apiId;
	private final IntSupplier requestsPerMinute;
	private final IntSupplier maxTrackedClients;
	private final ObjectMapper mapper;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public PublicEndpointRateLimitFilter(AppConfigProperties properties, ObjectMapper mapper, Clock clock) {
		this(EnrolmentApi::isAppConfig, EnrolmentApi.APP_CONFIG,
				() -> properties.getRateLimit().getRequestsPerMinute(),
				() -> properties.getRateLimit().getMaxTrackedClients(), mapper, clock);
	}

	/**
	 * The same per-IP limit for another public endpoint (device registration, MP-247). Each filter instance
	 * keeps its own counts, so one endpoint's traffic doesn't use up another's allowance.
	 *
	 * @param applies the requests to limit; every other request passes untouched
	 * @param apiId   envelope id of the endpoint, echoed in the ENR-GEN-002 body
	 */
	public PublicEndpointRateLimitFilter(Predicate<HttpServletRequest> applies, String apiId,
			IntSupplier requestsPerMinute, IntSupplier maxTrackedClients, ObjectMapper mapper, Clock clock) {
		this.applies = applies;
		this.apiId = apiId;
		this.requestsPerMinute = requestsPerMinute;
		this.maxTrackedClients = maxTrackedClients;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !applies.test(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long minute = clock.millis() / MINUTE_MILLIS;
		if (count(String.valueOf(request.getRemoteAddr()), minute) > requestsPerMinute.getAsInt()) {
			long secondsLeft = Math.max(1, ((minute + 1) * MINUTE_MILLIS - clock.millis() + 999) / 1000);
			response.setStatus(EnrolmentErrorCode.GEN_002.getHttpStatus().value());
			response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondsLeft));
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			mapper.writeValue(response.getOutputStream(),
					EnrolmentResponse.error(apiId, EnrolmentErrorCode.GEN_002));
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
		int max = maxTrackedClients.getAsInt();
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
