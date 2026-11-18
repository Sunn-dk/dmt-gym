package vn.edu.gym.exception;

import java.util.Collections;
import java.util.List;

/**
 * Domain/business exception carrying a canonical ErrorCode, clear Vietnamese message,
 * and optional conflict details (e.g., pending BOOKED sessions).
 */
public class GymException extends RuntimeException {

    private final ErrorCode code;
    private final List<String> details;
    private final Long relatedId;

    public GymException(ErrorCode code) {
        this(code, code.getDefaultMessage(), Collections.emptyList(), null, null);
    }

    public GymException(ErrorCode code, String message) {
        this(code, message, Collections.emptyList(), null, null);
    }

    public GymException(ErrorCode code, String message, List<String> details) {
        this(code, message, details, null, null);
    }

    public GymException(ErrorCode code, String message, Long relatedId) {
        this(code, message, Collections.emptyList(), relatedId, null);
    }

    public GymException(ErrorCode code, String message, Throwable cause) {
        this(code, message, Collections.emptyList(), null, cause);
    }

    public GymException(ErrorCode code, String message, List<String> details, Long relatedId, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? Collections.emptyList() : List.copyOf(details);
        this.relatedId = relatedId;
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }

    public Long getRelatedId() {
        return relatedId;
    }
}
