package io.mosip.idrepository.paas.session;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The stub accepts any token, so it must only load when explicitly switched on. */
public class StubSessionVerifierTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(StubSessionVerifier.class);

	@Test
	public void notLoadedByDefault() {
		runner.run(context -> assertEquals(0, context.getBeansOfType(SessionVerifier.class).size()));
	}

	@Test
	public void notLoadedWhenFalse() {
		runner.withPropertyValues(StubSessionVerifier.ENABLED_PROPERTY + "=false")
				.run(context -> assertEquals(0, context.getBeansOfType(SessionVerifier.class).size()));
	}

	@Test
	public void loadedOnlyWhenTrue() {
		runner.withPropertyValues(StubSessionVerifier.ENABLED_PROPERTY + "=true")
				.run(context -> assertEquals(1, context.getBeansOfType(SessionVerifier.class).size()));
	}
}
