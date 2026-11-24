package io.mosip.idrepository.paas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

/**
 * ID PAAS: the My Pass enrolment API ({@code /v1/enrolment/*}).
 *
 * <p>Depends only on {@code id-repository-core}, never on identity-service classes, so it can move to its
 * own repository later. It reads {@code mypass_idpaas} with plain JDBC ({@code spring.datasource.*} from
 * the config server); the JPA auto-configurations pulled in through core stay excluded.
 */
@OpenAPIDefinition(info = @Info(title = "ID PAAS: My Pass Enrolment API", version = "1.0",
		description = "Enrolment endpoints the My Pass app calls. Contract: My Pass Enrolment API Specification."))
@SpringBootApplication(exclude = { HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class })
@ConfigurationPropertiesScan("io.mosip.idrepository.paas")
@ComponentScan(basePackages = { "io.mosip.idrepository.paas", "${mosip.auth.adapter.impl.basepackage}" })
public class IdPaasBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdPaasBootApplication.class, args);
	}
}
