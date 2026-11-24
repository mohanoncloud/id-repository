package io.mosip.idrepository.paas.passport.compare;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Result of comparing the printed details with the MRZ: a status per field and an overall outcome. Any
 * {@link FieldStatus#MISMATCH} gives {@link ComparisonOutcome#ASSISTED_DESK}; otherwise any
 * {@link FieldStatus#UNCERTAIN} gives {@link ComparisonOutcome#RETAKE}; otherwise {@link ComparisonOutcome#MATCH}.
 * Holds field names only, never values, so it is safe to log.
 */
public final class ComparisonResult {

	private final Map<VizField, FieldStatus> fields;
	private final ComparisonOutcome outcome;

	ComparisonResult(Map<VizField, FieldStatus> fields) {
		this.fields = Collections.unmodifiableMap(new EnumMap<>(fields));
		if (fields.containsValue(FieldStatus.MISMATCH)) {
			outcome = ComparisonOutcome.ASSISTED_DESK;
		} else if (fields.containsValue(FieldStatus.UNCERTAIN)) {
			outcome = ComparisonOutcome.RETAKE;
		} else {
			outcome = ComparisonOutcome.MATCH;
		}
	}

	public ComparisonOutcome outcome() {
		return outcome;
	}

	public FieldStatus status(VizField field) {
		return fields.get(field);
	}

	public Map<VizField, FieldStatus> fields() {
		return fields;
	}

	/** Fields that disagree with the MRZ (for the audit trail on ASSISTED_DESK). */
	public Set<VizField> mismatchedFields() {
		return withStatus(FieldStatus.MISMATCH);
	}

	/** Fields that couldn't be read reliably (the reason for a retake). */
	public Set<VizField> uncertainFields() {
		return withStatus(FieldStatus.UNCERTAIN);
	}

	private Set<VizField> withStatus(FieldStatus status) {
		Set<VizField> result = EnumSet.noneOf(VizField.class);
		fields.forEach((field, s) -> {
			if (s == status) {
				result.add(field);
			}
		});
		return Collections.unmodifiableSet(result);
	}

	@Override
	public String toString() {
		return "ComparisonResult[outcome=" + outcome + ", fields=" + fields + "]";
	}
}
