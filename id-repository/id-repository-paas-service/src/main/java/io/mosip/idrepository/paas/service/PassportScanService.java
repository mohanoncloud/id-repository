package io.mosip.idrepository.paas.service;

import io.mosip.idrepository.paas.dto.passport.PassportScanResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.session.EnrolmentSession;

/**
 * Step 3 of enrolment (API spec section 3): validates the passport, creates the ID Repository draft and
 * returns the Application ID. Checks run in the spec's order and the first failure stops the rest.
 */
public interface PassportScanService {

	/** @throws EnrolmentException with the spec's {@code ENR-PSP-*} code when a check fails */
	PassportScanResponse scan(EnrolmentSession session, PassportScanInput input);
}
