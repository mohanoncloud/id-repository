package io.mosip.idrepository.paas.config;

import java.time.Clock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.httpfilter.PublicEndpointRateLimitFilter;
import io.mosip.idrepository.paas.httpfilter.EnrolmentSessionFilter;
import io.mosip.idrepository.paas.session.SessionVerifier;

@Configuration
public class IdPaasConfig {

	/** System clock (UTC). Injected so date rules (DOB century, expiry) can be tested with a fixed clock. */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/** Session check on every path of this service (the filter itself skips actuator). */
	@Bean
	public FilterRegistrationBean<EnrolmentSessionFilter> enrolmentSessionFilter(SessionVerifier verifier,
			SessionProperties properties, ObjectMapper mapper) {
		FilterRegistrationBean<EnrolmentSessionFilter> registration = new FilterRegistrationBean<>(
				new EnrolmentSessionFilter(verifier, properties, mapper));
		registration.addUrlPatterns("/*");
		return registration;
	}

	/** Per-IP limit on the public app config endpoint (the filter itself skips every other path). */
	@Bean
	public FilterRegistrationBean<PublicEndpointRateLimitFilter> appConfigRateLimitFilter(AppConfigProperties properties,
			ObjectMapper mapper, Clock clock) {
		FilterRegistrationBean<PublicEndpointRateLimitFilter> registration = new FilterRegistrationBean<>(
				new PublicEndpointRateLimitFilter(properties, mapper, clock));
		registration.addUrlPatterns("/*");
		return registration;
	}

	/** Per-IP limit on device registration (MP-247), counted apart from the app config's. */
	@Bean
	public FilterRegistrationBean<PublicEndpointRateLimitFilter> deviceRegisterRateLimitFilter(
			DeviceProperties properties, ObjectMapper mapper, Clock clock) {
		FilterRegistrationBean<PublicEndpointRateLimitFilter> registration = new FilterRegistrationBean<>(
				new PublicEndpointRateLimitFilter(EnrolmentApi::isDeviceRegister, EnrolmentApi.DEVICE_REGISTER,
						() -> properties.getRateLimit().getRequestsPerMinute(),
						() -> properties.getRateLimit().getMaxTrackedClients(), mapper, clock));
		registration.addUrlPatterns("/*");
		return registration;
	}
}
