package ca.openbox.shift.exception;

import ca.openbox.user.exception.UserAlreadyExistsException;
import ca.openbox.user.exception.UsernameAlreadyTakenException;
import ca.openbox.user.exception.VerificationCodeException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
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
}

