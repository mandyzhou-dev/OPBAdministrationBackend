package ca.openbox.shift.exception;

public class InvalidScheduleRangeException extends RuntimeException {
    public InvalidScheduleRangeException(String message) {
        super(message);//call the RuntimeException to save the message.
    }
}
