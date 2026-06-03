package ca.openbox.shift.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftExceptionHandlerMultipartTest {

    private final ShiftExceptionHandler handler = new ShiftExceptionHandler();

    @Test
    void fileSizeLimitIllegalStateUsesFileUploadErrorCode() {
        IllegalStateException exception = new IllegalStateException(
                "org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException: " +
                        "The field proof exceeds its maximum permitted size of 1048576 bytes."
        );

        Map<String, String> response = handler.handleIllegalState(exception);

        assertEquals("FILE_SIZE_LIMIT_EXCEEDED", response.get("error"));
        assertEquals(exception.getMessage(), response.get("message"));
    }

    @Test
    void nonMultipartIllegalStateKeepsShiftStatusErrorCode() {
        IllegalStateException exception = new IllegalStateException("Cannot transition this shift");

        Map<String, String> response = handler.handleIllegalState(exception);

        assertEquals("SHIFT_STATUS_NOT_ALLOWED", response.get("error"));
        assertEquals(exception.getMessage(), response.get("message"));
    }
}
