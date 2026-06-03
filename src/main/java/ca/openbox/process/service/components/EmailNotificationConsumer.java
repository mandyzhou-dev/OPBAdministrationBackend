package ca.openbox.process.service.components;

import ca.openbox.infrastructure.email.service.WebhookEmailService;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class EmailNotificationConsumer {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Vancouver");
    private static final DateTimeFormatter LEAVE_EMAIL_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);
    static final long EMAIL_SEND_DELAY_MILLIS = 20000;
    private static final List<String> SICK_PROOF_HR_USERNAMES = List.of("raynold", "agnes");
    private final WebhookEmailService emailService;
    private final UserRepository userRepository;
    private final EmailDelay emailDelay;

    @Autowired
    public EmailNotificationConsumer(WebhookEmailService emailService, UserRepository userRepository) {
        this(emailService, userRepository, true, Thread::sleep);
    }

    EmailNotificationConsumer(WebhookEmailService emailService, UserRepository userRepository, boolean startConsumer) {
        this(emailService, userRepository, startConsumer, ignored -> {});
    }

    EmailNotificationConsumer(WebhookEmailService emailService, UserRepository userRepository, boolean startConsumer, EmailDelay emailDelay) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.emailDelay = emailDelay;
        if (startConsumer) {
            startConsumer();
        }
    }

    private void startConsumer() {
        new Thread(() -> {
            while (true) {
                try {
                    sendEmail(ApplicationStatusChangeMessageQueue.take());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendEmail(LeaveApplicationEmailEvent event) throws InterruptedException {
        if (event == null || event.getLeaveApplication() == null || event.getType() == null) {
            return;
        }
        switch (event.getType()) {
            case LEAVE_SUBMITTED -> sendLeaveSubmittedEmails(event.getLeaveApplication());
            case SICK_PROOF_UPLOADED -> sendSickProofUploadedEmails(event);
        }
    }

    private void sendLeaveSubmittedEmails(LeaveApplication leaveApplication) throws InterruptedException {
        sendHandlerReviewEmails(leaveApplication);
        if (isSickLeave(leaveApplication)) {
            sendEmployeeSickProofReminder(leaveApplication);
        }
    }

    private void sendHandlerReviewEmails(LeaveApplication leaveApplication) throws InterruptedException {
        String applicant = leaveApplication.getApplicant();
        String subject = String.format("Time Off Request Submitted – %s", applicant);
        sendToHandlers(leaveApplication, subject, buildHandlerReviewBody(leaveApplication), true);
    }

    private void sendEmployeeSickProofReminder(LeaveApplication leaveApplication) {
        UserDO applicant = findActiveUserByUsernameToken(leaveApplication.getApplicant());
        if (applicant == null || applicant.getEmail() == null || applicant.getEmail().isBlank()) {
            return;
        }
        try {
            emailService.sendEmail(applicant.getEmail(), "Sick Leave Proof Required", buildEmployeeSickProofReminderBody(leaveApplication));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSickProofUploadedEmails(LeaveApplicationEmailEvent event) throws InterruptedException {
        LeaveApplication leaveApplication = event.getLeaveApplication();
        String subject = String.format("Sick Leave Proof Submitted - %s", leaveApplication.getApplicant());
        sendToSickProofHrRecipients(subject, buildSickProofUploadedBody(leaveApplication, event.getSickProofStoredPath()));
    }

    void sendToHandlers(LeaveApplication leaveApplication, String subject, String body, boolean delayBetweenRecipients) throws InterruptedException {
        String currentHandler = leaveApplication.getCurrentHandler();
        if (currentHandler == null || currentHandler.isBlank()) {
            return;
        }
        sendToUsernameTokens(Arrays.asList(currentHandler.split(",")), subject, body, delayBetweenRecipients);
    }

    private void sendToSickProofHrRecipients(String subject, String body) throws InterruptedException {
        sendToUsernameTokens(SICK_PROOF_HR_USERNAMES, subject, body, true);
    }

    private void sendToUsernameTokens(List<String> usernameTokens, String subject, String body, boolean delayBetweenRecipients) throws InterruptedException {
        for (String usernameToken : usernameTokens) {
            try {
                UserDO user = findActiveUserByUsernameToken(usernameToken);
                if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                    emailService.sendEmail(user.getEmail(), subject, body);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (delayBetweenRecipients) {
                emailDelay.sleep(EMAIL_SEND_DELAY_MILLIS);//sleep 20s
            }
        }
    }

    private UserDO findActiveUserByUsernameToken(String usernameToken) {
        if (usernameToken == null || usernameToken.isBlank()) {
            return null;
        }
        UserDO user = userRepository.getUserDOByUsernameAndActiveIsTrue(usernameToken);
        String trimmedUsername = usernameToken.trim();
        if (user == null && !trimmedUsername.equals(usernameToken)) {
            user = userRepository.getUserDOByUsernameAndActiveIsTrue(trimmedUsername);
        }
        return user;
    }

    static String formatDate(LeaveApplication leaveApplication) {
        return formatLeaveTime(leaveApplication.getStart());
    }

    static String formatEndDate(LeaveApplication leaveApplication) {
        return formatLeaveTime(leaveApplication.getEnd());
    }

    static String buildHandlerReviewBody(LeaveApplication leaveApplication) {
        return String.format(
                "%s requested time off from %s to %s. Please log on to https://openbox.brimon.ca/ to review it.",
                leaveApplication.getApplicant(),
                formatDate(leaveApplication),
                formatEndDate(leaveApplication)
        );
    }

    static String buildEmployeeSickProofReminderBody(LeaveApplication leaveApplication) {
        return String.format(
                "Please submit proof within 3 days for your sick leave from %s to %s. Please log on to https://openbox.brimon.ca/ to upload your proof.",
                formatDate(leaveApplication),
                formatEndDate(leaveApplication)
        );
    }

    static String buildSickProofUploadedBody(LeaveApplication leaveApplication) {
        return buildSickProofUploadedBody(leaveApplication, null);
    }

    static String buildSickProofUploadedBody(LeaveApplication leaveApplication, String sickProofStoredPath) {
        String body = String.format(
                "%s has submitted proof for sick leave from %s to %s.",
                leaveApplication.getApplicant(),
                formatDate(leaveApplication),
                formatEndDate(leaveApplication)
        );
        if (sickProofStoredPath == null || sickProofStoredPath.isBlank()) {
            return body;
        }
        return String.format(
                "%s%nThe proof file has been stored at %s.",
                body,
                sickProofStoredPath
        );
    }

    private static String formatLeaveTime(ZonedDateTime leaveTime) {
        return leaveTime.withZoneSameInstant(BUSINESS_ZONE).format(LEAVE_EMAIL_TIME_FORMATTER);
    }

    private boolean isSickLeave(LeaveApplication leaveApplication) {
        return leaveApplication.getLeaveType() != null && "SICK".equalsIgnoreCase(leaveApplication.getLeaveType().trim());
    }

    interface EmailDelay {
        void sleep(long millis) throws InterruptedException;
    }
}
