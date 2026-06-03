package ca.openbox.process.service.components;

import ca.openbox.infrastructure.email.service.WebhookEmailService;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationConsumerHandlerLookupTest {

    private WebhookEmailService emailService;
    private UserRepository userRepository;
    private EmailNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        emailService = mock(WebhookEmailService.class);
        userRepository = mock(UserRepository.class);
        consumer = new EmailNotificationConsumer(emailService, userRepository, false);
    }

    @Test
    void sendToHandlersLooksUpRawUsernameBeforeTrimFallback() throws Exception {
        UserDO user = user("harsim@example.com");
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("Harsimranjit Kaur ")).thenReturn(user);

        consumer.sendToHandlers(applicationWithHandlers("Harsimranjit Kaur "), "subject", "body", false);

        verify(userRepository).getUserDOByUsernameAndActiveIsTrue("Harsimranjit Kaur ");
        verify(userRepository, never()).getUserDOByUsernameAndActiveIsTrue("Harsimranjit Kaur");
        verify(emailService).sendEmail("harsim@example.com", "subject", "body");
    }

    @Test
    void sendToHandlersFallsBackToTrimmedUsernameForSeparatorWhitespace() throws Exception {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("raynold")).thenReturn(user("raynold@example.com"));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue(" agnes")).thenReturn(null);
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("agnes")).thenReturn(user("agnes@example.com"));

        consumer.sendToHandlers(applicationWithHandlers("raynold, agnes"), "subject", "body", false);

        verify(emailService).sendEmail("raynold@example.com", "subject", "body");
        verify(emailService).sendEmail("agnes@example.com", "subject", "body");
    }

    @Test
    void sendToHandlersSkipsMissingUsernameWithoutThrowing() throws Exception {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("missing")).thenReturn(null);

        consumer.sendToHandlers(applicationWithHandlers("missing"), "subject", "body", false);

        verify(emailService, never()).sendEmail("missing@example.com", "subject", "body");
    }

    @Test
    void sickProofUploadedEmailGoesToFixedHrRecipientsWithoutCurrentHandler() throws Exception {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("raynold")).thenReturn(user("raynold@example.com"));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("agnes")).thenReturn(user("agnes@example.com"));

        invokeSendEmail(LeaveApplicationEmailEvent.sickProofUploaded(sickProofApplication(null)));

        verify(emailService).sendEmail(eq("raynold@example.com"), eq("Sick Leave Proof Submitted - Harsimranjit Kaur "), contains("May 28, 2026 11:00 AM"));
        verify(emailService).sendEmail(eq("agnes@example.com"), eq("Sick Leave Proof Submitted - Harsimranjit Kaur "), contains("May 28, 2026 11:00 AM"));
    }

    @Test
    void sickProofUploadedEmailDoesNotUseApplicantCurrentHandler() throws Exception {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("raynold")).thenReturn(user("raynold@example.com"));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("agnes")).thenReturn(user("agnes@example.com"));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("Harsimranjit Kaur ")).thenReturn(user("applicant@example.com"));

        invokeSendEmail(LeaveApplicationEmailEvent.sickProofUploaded(sickProofApplication("Harsimranjit Kaur ")));

        verify(emailService).sendEmail(eq("raynold@example.com"), anyString(), anyString());
        verify(emailService).sendEmail(eq("agnes@example.com"), anyString(), anyString());
        verify(emailService, never()).sendEmail(eq("applicant@example.com"), anyString(), anyString());
    }

    @Test
    void sickProofUploadedEmailContinuesAfterOneHrEmailFails() throws Exception {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("raynold")).thenReturn(user("raynold@example.com"));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("agnes")).thenReturn(user("agnes@example.com"));
        doThrow(new RuntimeException("webhook failed"))
                .when(emailService)
                .sendEmail(eq("raynold@example.com"), anyString(), anyString());

        invokeSendEmail(LeaveApplicationEmailEvent.sickProofUploaded(sickProofApplication(null)));

        verify(emailService).sendEmail(eq("raynold@example.com"), anyString(), anyString());
        verify(emailService).sendEmail(eq("agnes@example.com"), anyString(), anyString());
    }

    @Test
    void sickProofUploadedEmailUsesExistingDelayBetweenHrRecipients() throws Exception {
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("raynold")).thenReturn(user("raynold@example.com"));
        when(userRepository.getUserDOByUsernameAndActiveIsTrue("agnes")).thenReturn(user("agnes@example.com"));
        long[] delayCalls = {0};
        long[] delayedMillis = {0};
        EmailNotificationConsumer delayedConsumer = new EmailNotificationConsumer(
                emailService,
                userRepository,
                false,
                millis -> {
                    delayCalls[0]++;
                    delayedMillis[0] = millis;
                }
        );

        invokeSendEmail(delayedConsumer, LeaveApplicationEmailEvent.sickProofUploaded(sickProofApplication(null)));

        assertEquals(2, delayCalls[0]);
        assertEquals(EmailNotificationConsumer.EMAIL_SEND_DELAY_MILLIS, delayedMillis[0]);
    }

    private LeaveApplication applicationWithHandlers(String currentHandler) {
        LeaveApplication leaveApplication = new LeaveApplication();
        leaveApplication.setCurrentHandler(currentHandler);
        return leaveApplication;
    }

    private LeaveApplication sickProofApplication(String currentHandler) {
        LeaveApplication leaveApplication = applicationWithHandlers(currentHandler);
        leaveApplication.setApplicant("Harsimranjit Kaur ");
        leaveApplication.setLeaveType("SICK");
        leaveApplication.setStart(ZonedDateTime.parse("2026-05-28T18:00:00Z"));
        leaveApplication.setEnd(ZonedDateTime.parse("2026-05-29T01:00:00Z"));
        return leaveApplication;
    }

    private void invokeSendEmail(LeaveApplicationEmailEvent event) throws Exception {
        invokeSendEmail(consumer, event);
    }

    private void invokeSendEmail(EmailNotificationConsumer targetConsumer, LeaveApplicationEmailEvent event) throws Exception {
        Method sendEmail = EmailNotificationConsumer.class.getDeclaredMethod("sendEmail", LeaveApplicationEmailEvent.class);
        sendEmail.setAccessible(true);
        sendEmail.invoke(targetConsumer, event);
    }

    private UserDO user(String email) {
        UserDO user = new UserDO();
        user.setEmail(email);
        return user;
    }
}
