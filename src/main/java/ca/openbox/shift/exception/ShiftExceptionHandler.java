package ca.openbox.shift.exception;

import ca.openbox.user.exception.UserAlreadyExistsException;
import ca.openbox.user.exception.UsernameAlreadyTakenException;
import ca.openbox.user.exception.VerificationCodeException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ShiftExceptionHandler {
    @ExceptionHandler(InvalidScheduleRangeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleScheduleRange(InvalidScheduleRangeException ex) {
        return Map.of(
                "error", "INVALID_SCHEDULE_RANGE",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDuplicateKey(DuplicateKeyException ex) {
        return Map.of(
                "error", "SHIFT_ALREADY_EXISTS",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of(
                "error", "INVALID_SHIFT_REQUEST",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalState(IllegalStateException ex) {
        if (isMultipartFileSizeException(ex)) {
            return fileSizeLimitExceeded(ex);
        }
        return Map.of(
                "error", "SHIFT_STATUS_NOT_ALLOWED",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleMultipartException(Exception ex) {
        return fileSizeLimitExceeded(ex);
    }

    private Map<String, String> fileSizeLimitExceeded(Exception ex) {
        return Map.of(
                "error", "FILE_SIZE_LIMIT_EXCEEDED",
                "message", ex.getMessage()
        );
    }

    private boolean isMultipartFileSizeException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("FileSizeLimitExceededException")
                    || current instanceof MaxUploadSizeExceededException
                    || current instanceof MultipartException
                    || (message != null && message.contains("FileSizeLimitExceededException"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
