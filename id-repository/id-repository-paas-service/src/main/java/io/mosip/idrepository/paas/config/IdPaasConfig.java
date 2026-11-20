package io.mosip.idrepository.paas.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.httpfilter.EnrolmentSessionFilter;
import io.mosip.idrepository.paas.session.SessionVerifier;

@Configuration
public class IdPaasConfig {

	/** Session check on every path of this service (the filter itself skips actuator). */
	@Bean
	public FilterRegistrationBean<EnrolmentSessionFilter> enrolmentSessionFilter(SessionVerifier verifier,
			SessionProperties properties, ObjectMapper mapper) {
		FilterRegistrationBean<EnrolmentSessionFilter> registration = new FilterRegistrationBean<>(
				new EnrolmentSessionFilter(verifier, properties, mapper));
		registration.addUrlPatterns("/*");
		return registration;
	}
}
