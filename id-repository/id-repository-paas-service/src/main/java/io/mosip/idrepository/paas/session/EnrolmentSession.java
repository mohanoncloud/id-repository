package io.mosip.idrepository.paas.session;

/**
 * The verified enrolment session behind a request.
 *
 * @param sessionId identifier of the session (never the raw token)
 * @param status    current status, or {@code null} if the session API doesn't report one yet
 */
public record EnrolmentSession(String sessionId, String status) {
}
