package io.mosip.idrepository.paas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.config.AppConfigProperties;
import io.mosip.idrepository.paas.constant.Platform;

/**
 * Reads {@code idpaas.app_config}. Read-only on purpose: {@code idpaasuser} only has SELECT on this table
 * (grants.sql), and config changes are made by the config editors, with the history kept by the table's
 * triggers.
 *
 * <p>Plain JDBC rather than JPA: one read-only query with jsonb columns, and the JPA auto-configuration stays
 * off in this service.
 */
@Repository
public class AppConfigRepository {

	// jsonb read as text and parsed here, so no driver-specific type (PGobject) leaks into the code.
	private static final String SELECT_BY_PLATFORM = "SELECT platform, minimum_version, latest_version, store_url,"
			+ " maintenance_enabled, maintenance_message::text AS maintenance_message, maintenance_ends_dtimes,"
			+ " features::text AS features, device_policy::text AS device_policy, config_version"
			+ " FROM idpaas.app_config WHERE platform = :platform";

	private final NamedParameterJdbcTemplate jdbc;
	private final ObjectMapper mapper;

	public AppConfigRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper,
			AppConfigProperties properties) {
		// A JdbcTemplate of its own, so the query timeout applies to this read only and not to anything else
		// that uses the shared one.
		JdbcTemplate template = new JdbcTemplate(jdbc.getJdbcTemplate().getDataSource());
		template.setQueryTimeout((int) Math.max(1, (properties.getQueryTimeout().toMillis() + 999) / 1000));
		this.jdbc = new NamedParameterJdbcTemplate(template);
		this.mapper = mapper;
	}

	int queryTimeoutSeconds() {
		return jdbc.getJdbcTemplate().getQueryTimeout();
	}

	/** The platform's config, or empty if the table has no row for it. */
	public Optional<AppConfigRecord> findByPlatform(Platform platform) {
		List<AppConfigRecord> rows = jdbc.query(SELECT_BY_PLATFORM,
				new MapSqlParameterSource("platform", platform.code()), (rs, rowNum) -> toRecord(rs));
		return rows.stream().findFirst();
	}

	private AppConfigRecord toRecord(ResultSet rs) throws SQLException {
		Timestamp endsAt = rs.getTimestamp("maintenance_ends_dtimes");
		return new AppConfigRecord(rs.getString("platform"), rs.getString("minimum_version"),
				rs.getString("latest_version"), rs.getString("store_url"), rs.getBoolean("maintenance_enabled"),
				messages(json(rs.getString("maintenance_message"))), endsAt == null ? null : endsAt.toLocalDateTime(),
				json(rs.getString("features")), json(rs.getString("device_policy")), rs.getInt("config_version"));
	}

	private JsonNode json(String text) throws SQLException {
		if (text == null) {
			return null;
		}
		try {
			return mapper.readTree(text);
		} catch (JsonProcessingException e) {
			// Can't happen for a jsonb column; reported as a data error rather than a 500 with a stack trace.
			throw new SQLException("Invalid JSON in idpaas.app_config");
		}
	}

	/** Language code → message. Non-text values are ignored; the table's checks only require an object. */
	private static Map<String, String> messages(JsonNode node) {
		Map<String, String> messages = new LinkedHashMap<>();
		if (node != null && node.isObject()) {
			node.fields().forEachRemaining(entry -> {
				if (entry.getValue().isTextual() && !entry.getValue().asText().isBlank()) {
					messages.put(entry.getKey(), entry.getValue().asText());
				}
			});
		}
		return messages;
	}
}
