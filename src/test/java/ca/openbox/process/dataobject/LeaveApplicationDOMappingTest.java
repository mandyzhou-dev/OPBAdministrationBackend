package ca.openbox.process.dataobject;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LeaveApplicationDOMappingTest {

    @Test
    void leaveApplicationDoesNotMapCreatedAtColumn() {
        boolean hasCreatedAt = Arrays.stream(LeaveApplicationDO.class.getDeclaredFields())
                .anyMatch(field -> "createdAt".equals(field.getName()));

        assertFalse(hasCreatedAt);
    }
}
