package io.mosip.idrepository.paas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code idpaas.device} (MP-247). {@code idpaasuser} can only SELECT and INSERT here (grants.sql): a device is
 * registered once and revoked by support through the {@code idpaasdeviceadmin} role, never by the service.
 *
 * <p>Uniqueness of the key thumbprint and the App Attest key ID (decision B1) is the database's job:
 * {@link #insert} lets the unique violation through as {@link DuplicateKeyException} for the service to turn
 * into an error code. There is no "check, then insert" here, because two simultaneous requests would both pass
 * the check.
 */
@Repository
public class DeviceRepository {

	private static final String COLUMNS = "device_id, jkt, public_key_jwk::text AS public_key_jwk,"
			+ " app_attest_key_id, platform, os_version, manufacturer, model, app_version, policy_version,"
			+ " attestation_summary::text AS attestation_summary, status_code, cr_dtimes";

	private static final String INSERT = "INSERT INTO idpaas.device (device_id, jkt, public_key_jwk,"
			+ " app_attest_key_id, platform, os_version, manufacturer, model, app_version, policy_version,"
			+ " attestation_summary, status_code, cr_by, cr_dtimes) VALUES (:deviceId, :jkt,"
			+ " CAST(:publicKeyJwk AS jsonb), :appAttestKeyId, :platform, :osVersion, :manufacturer, :model,"
			+ " :appVersion, :policyVersion, CAST(:attestationSummary AS jsonb), 'ACTIVE', :createdBy, :createdAt)";

	/** Value of {@code cr_by} for rows the service writes. */
	public static final String CREATED_BY = "idpaas-service";

	private final NamedParameterJdbcTemplate jdbc;

	public DeviceRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Adds an ACTIVE device.
	 *
	 * @throws DuplicateKeyException if the key thumbprint or the App Attest key ID is already registered
	 */
	public void insert(DeviceRecord device) {
		jdbc.update(INSERT, new MapSqlParameterSource().addValue("deviceId", device.deviceId())
				.addValue("jkt", device.jkt()).addValue("publicKeyJwk", device.publicKeyJwk())
				.addValue("appAttestKeyId", device.appAttestKeyId()).addValue("platform", device.platform())
				.addValue("osVersion", device.osVersion()).addValue("manufacturer", device.manufacturer())
				.addValue("model", device.model()).addValue("appVersion", device.appVersion())
				.addValue("policyVersion", device.policyVersion())
				.addValue("attestationSummary", device.attestationSummary()).addValue("createdBy", CREATED_BY)
				.addValue("createdAt", device.createdAt()));
	}

	public Optional<DeviceRecord> findByJkt(String jkt) {
		return one("WHERE jkt = :value", jkt);
	}

	public Optional<DeviceRecord> findByAppAttestKeyId(String keyId) {
		return one("WHERE app_attest_key_id = :value", keyId);
	}

	/** For the session and signature checks (MP-248, MP-249): the device, whatever its status. */
	public Optional<DeviceRecord> findById(String deviceId) {
		return one("WHERE device_id = :value", deviceId);
	}

	private Optional<DeviceRecord> one(String where, String value) {
		List<DeviceRecord> rows = jdbc.query("SELECT " + COLUMNS + " FROM idpaas.device " + where,
				new MapSqlParameterSource("value", value), (rs, rowNum) -> toRecord(rs));
		return rows.stream().findFirst();
	}

	private static DeviceRecord toRecord(ResultSet rs) throws SQLException {
		return new DeviceRecord(rs.getString("device_id"), rs.getString("jkt"), rs.getString("public_key_jwk"),
				rs.getString("app_attest_key_id"), rs.getString("platform"), rs.getString("os_version"),
				rs.getString("manufacturer"), rs.getString("model"), rs.getString("app_version"),
				rs.getString("policy_version"), rs.getString("attestation_summary"), rs.getString("status_code"),
				rs.getTimestamp("cr_dtimes").toLocalDateTime());
	}
}
