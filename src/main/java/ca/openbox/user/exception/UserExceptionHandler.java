package ca.openbox.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class UserExceptionHandler {
    @ExceptionHandler(UsernameAlreadyTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)  // 409
    public Map<String, String> handleUsernameTaken(UsernameAlreadyTakenException ex) {
        return Map.of(
                "error", "USERNAME_ALREADY_EXISTS",
                "message", ex.getMessage()
        );
    }
    @ExceptionHandler(VerificationCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleVerification(VerificationCodeException ex) {
        return Map.of(
                "error", "INVALID_VERIFICATION_CODE",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleExistingUser(UserAlreadyExistsException ex) {
        return Map.of(
                "error", "EMAIL_ALREADY_REGISTERED",
                "message", ex.getMessage()
        );
    }
    //TODO:InvalidBirthdateException;InvalidSinException;InvalidPhoneNumberException;InvalidPostalCodeException;InvalidProvinceException;PasswordTooWeakException;DuplicateSINException
}

