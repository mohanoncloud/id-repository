package io.mosip.idrepository.paas.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.compare.PrintedVsMrzComparator;
import io.mosip.idrepository.paas.passport.mrz.PassportMrz;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.passport.upload.PassportImageValidator;
import io.mosip.idrepository.paas.passport.upload.SanitisedImage;
import io.mosip.idrepository.paas.passport.validation.PassportExpiryCheck;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.service.PassportScanService;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.spi.PassportImageReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;

/**
 * Runs the passport checks in the spec's order (API spec section 3); the first failure stops the rest.
 *
 * <ol>
 *   <li><b>File checks on both images: MP-256</b>, ENR-PSP-001 (HTTP 413 if too large). The image quality
 *       check (blur, glare, whole page: ENR-PSP-002) waits for the OCR / image library decision (MP-397).</li>
 *   <li><b>MRZ read and check digits: MP-261</b>, ENR-PSP-003 / ENR-PSP-004</li>
 *   <li>Issuing state and passport number format: MP-262 (on hold)</li>
 *   <li><b>Printed details vs MRZ: MP-257</b>, ENR-PSP-007 (mismatch) / ENR-PSP-002 (printed details
 *       unreadable). Setting the session to ASSISTED_DESK on a mismatch waits for the session store
 *       (MP-246–249).</li>
 *   <li><b>Expiry: MP-258</b>, ENR-PSP-008</li>
 *   <li>Already enrolled, draft create/update, record: MP-258 / MP-259 (on hold)</li>
 * </ol>
 *
 * Until the remaining checks exist, a scan that passes the expiry check answers ENR-GEN-003 (HTTP 503). With
 * the stub OCR, every scan stops at the image read with the same answer.
 */
@Service
public class PassportScanServiceImpl implements PassportScanService {

	private final PassportImageValidator imageValidator;
	private final PassportImageReader imageReader;
	private final Td3MrzReader mrzReader;
	private final PrintedVsMrzComparator printedVsMrz;
	private final PassportExpiryCheck expiryCheck;

	public PassportScanServiceImpl(PassportImageValidator imageValidator, PassportImageReader imageReader,
			Td3MrzReader mrzReader, PrintedVsMrzComparator printedVsMrz, PassportExpiryCheck expiryCheck) {
		this.imageValidator = imageValidator;
		this.imageReader = imageReader;
		this.mrzReader = mrzReader;
		this.printedVsMrz = printedVsMrz;
		this.expiryCheck = expiryCheck;
	}

	@Override
	public PassportScanResponse scan(EnrolmentSession session, PassportScanInput input) {
		SanitisedImage front = imageValidator.validate("frontImage", input.frontImage());
		imageValidator.validate("backImage", input.backImage()); // checked, not OCR'd (spec needs both)
		PassportImageReading reading = imageReader.read(front);
		PassportMrz mrz = mrzReader.read(reading.mrzText());
		printedVsMrz.requireMatch(reading.printedFields(), mrz);
		expiryCheck.requireNotExpired(mrz.expiryDate());
		return remainingChecks(mrz);
	}

	/** Steps after the expiry check: already enrolled, draft create/update (MP-258 / MP-259, on hold). */
	private PassportScanResponse remainingChecks(PassportMrz mrz) {
		throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
				"Passport checks after the expiry check are not implemented yet");
	}
}
