package ca.openbox.user.exception;

public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException() {
        super("This username is already taken.");
    }

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
