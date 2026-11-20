package io.mosip.idrepository.paas.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.mrz.PassportMrz;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.service.PassportScanService;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.spi.PassportImageReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;

/**
 * Runs the passport checks in the spec's order (API spec section 3); the first failure stops the rest.
 *
 * <ol>
 *   <li>File and quality checks: MP-256 (not yet)</li>
 *   <li><b>MRZ read and check digits: MP-261</b>, ENR-PSP-003 / ENR-PSP-004</li>
 *   <li>Issuing state and passport number format: MP-262 (on hold)</li>
 *   <li>Printed details vs MRZ: MP-257 (not yet)</li>
 *   <li>Expiry: MP-258 (not yet)</li>
 *   <li>Already enrolled, draft create/update, record: MP-258 / MP-259 (on hold)</li>
 * </ol>
 *
 * Until the remaining checks exist, a scan that passes the MRZ read answers ENR-GEN-003 (HTTP 503). With
 * the stub OCR, every scan stops at the image read with the same answer.
 */
@Service
public class PassportScanServiceImpl implements PassportScanService {

	private final PassportImageReader imageReader;
	private final Td3MrzReader mrzReader;

	public PassportScanServiceImpl(PassportImageReader imageReader, Td3MrzReader mrzReader) {
		this.imageReader = imageReader;
		this.mrzReader = mrzReader;
	}

	@Override
	public PassportScanResponse scan(EnrolmentSession session, PassportScanInput input) {
		PassportImageReading reading = imageReader.read(input.frontImage());
		PassportMrz mrz = mrzReader.read(reading.mrzText());
		return remainingChecks(mrz);
	}

	/** Steps after the MRZ read; added by MP-257 and MP-258. */
	private PassportScanResponse remainingChecks(PassportMrz mrz) {
		throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
				"Passport checks after the MRZ read are not implemented yet");
	}
}
