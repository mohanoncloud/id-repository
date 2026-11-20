package io.mosip.idrepository.paas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * ID PAAS: the My Pass enrolment API ({@code /v1/enrolment/*}).
 *
 * <p>Depends only on {@code id-repository-core}, never on identity-service classes, so it can move to its
 * own repository later. The {@code mypass_idpaas} database is not wired in yet: the JPA/DataSource
 * auto-configurations pulled in through core are excluded until the first task that persists data.
 */
@OpenAPIDefinition(info = @Info(title = "ID PAAS: My Pass Enrolment API", version = "1.0",
		description = "Enrolment endpoints the My Pass app calls. Contract: My Pass Enrolment API Specification."))
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@ConfigurationPropertiesScan("io.mosip.idrepository.paas")
@ComponentScan(basePackages = { "io.mosip.idrepository.paas", "${mosip.auth.adapter.impl.basepackage}" })
public class IdPaasBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdPaasBootApplication.class, args);
	}
}
