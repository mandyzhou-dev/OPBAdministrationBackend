package ca.openbox.process.service.components;

import ca.openbox.process.entities.LeaveApplication;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmailNotificationConsumerTimezoneTest {

    @Test
    void formatsUtcLeaveTimesInVancouverBusinessTimezone() throws Exception {
        LeaveApplication leaveApplication = utcLeaveApplication();

        assertEquals("May 28, 2026 11:00 AM", EmailNotificationConsumer.formatDate(leaveApplication));
        assertEquals("May 28, 2026 6:00 PM", EmailNotificationConsumer.formatEndDate(leaveApplication));
        assertNotEquals("May 28, 2026 6:00 PM", EmailNotificationConsumer.formatDate(leaveApplication));
    }

    @Test
    void handlerReviewEmailBodyUsesVancouverBusinessTimezone() {
        String body = EmailNotificationConsumer.buildHandlerReviewBody(utcLeaveApplication());

        assertContainsVancouverTime(body);
    }

    @Test
    void employeeSickProofReminderEmailBodyUsesVancouverBusinessTimezone() {
        String body = EmailNotificationConsumer.buildEmployeeSickProofReminderBody(utcLeaveApplication());

        assertContainsVancouverTime(body);
    }

    @Test
    void sickProofUploadedEmailBodyUsesVancouverBusinessTimezone() {
        String body = EmailNotificationConsumer.buildSickProofUploadedBody(utcLeaveApplication());

        assertContainsVancouverTime(body);
    }

    @Test
    void sickProofUploadedEmailBodyIncludesStoredPathAndRemovesCheckStatusPrompt() {
        String body = EmailNotificationConsumer.buildSickProofUploadedBody(
                utcLeaveApplication(),
                "/configured/sick-proof-dir/77/20260601120000000_test.pdf"
        );

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("The proof file has been stored at /configured/sick-proof-dir/77/20260601120000000_test.pdf."));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("Please log on"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("check the leave application status"));
    }

    private LeaveApplication utcLeaveApplication() {
        LeaveApplication leaveApplication = new LeaveApplication();
        leaveApplication.setApplicant("alice");
        leaveApplication.setStart(ZonedDateTime.parse("2026-05-28T18:00:00Z"));
        leaveApplication.setEnd(ZonedDateTime.parse("2026-05-29T01:00:00Z"));
        return leaveApplication;
    }

    private void assertContainsVancouverTime(String body) {
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("May 28, 2026 11:00 AM"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("May 28, 2026 6:00 PM to May 29, 2026 1:00 AM"));
    }
}
