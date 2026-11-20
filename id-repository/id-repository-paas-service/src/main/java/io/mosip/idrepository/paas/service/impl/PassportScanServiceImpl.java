package io.mosip.idrepository.paas.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.service.PassportScanService;
import io.mosip.idrepository.paas.session.EnrolmentSession;

/**
 * Scaffold (MP-379): the passport checks are added by their own tasks, in the spec's order:
 * file and quality checks (MP-256), MRZ read (MP-261), printed vs MRZ (MP-257) and expiry (MP-258).
 * Until then every scan answers ENR-GEN-003 with HTTP 503.
 */
@Service
public class PassportScanServiceImpl implements PassportScanService {

	@Override
	public PassportScanResponse scan(EnrolmentSession session, PassportScanInput input) {
		throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
				"Passport checks are not implemented yet");
	}
}
