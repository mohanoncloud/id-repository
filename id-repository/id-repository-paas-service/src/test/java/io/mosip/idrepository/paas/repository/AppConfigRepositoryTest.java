package io.mosip.idrepository.paas.repository;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import javax.sql.DataSource;

import org.junit.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.config.AppConfigProperties;

public class AppConfigRepositoryTest {

	private final NamedParameterJdbcTemplate shared = new NamedParameterJdbcTemplate(mock(DataSource.class));
	private final AppConfigProperties properties = new AppConfigProperties();

	@Test
	public void readsHaveAQueryTimeoutOfThreeSecondsByDefault() {
		assertEquals(3, new AppConfigRepository(shared, new ObjectMapper(), properties).queryTimeoutSeconds());
	}

	@Test
	public void timeoutIsRoundedUpToWholeSecondsAndIsAtLeastOne() {
		properties.setQueryTimeout(Duration.ofMillis(1500));
		assertEquals(2, new AppConfigRepository(shared, new ObjectMapper(), properties).queryTimeoutSeconds());

		properties.setQueryTimeout(Duration.ZERO);
		assertEquals(1, new AppConfigRepository(shared, new ObjectMapper(), properties).queryTimeoutSeconds());
	}

	@Test
	public void theSharedJdbcTemplateKeepsItsOwnSettings() {
		new AppConfigRepository(shared, new ObjectMapper(), properties);

		assertEquals("JdbcTemplate default: no timeout", -1, shared.getJdbcTemplate().getQueryTimeout());
	}
}
