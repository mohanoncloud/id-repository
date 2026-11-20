package io.mosip.idrepository.paas.dto.passport;

import lombok.Data;

/** {@code response} of {@code POST /v1/enrolment/passport} (API spec section 3). */
@Data
public class PassportScanResponse {

	/** Application ID; also the {@code registrationId} of the ID Repository draft. */
	private String applicationId;
	private String status;
	private PassportDetailsDto passport;
	private String nextStep;
}
