package io.mosip.idrepository.paas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry of {@code errors[]}. {@code message} is shown to the user word for word. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrolmentError {

	private String errorCode;
	private String message;
}
