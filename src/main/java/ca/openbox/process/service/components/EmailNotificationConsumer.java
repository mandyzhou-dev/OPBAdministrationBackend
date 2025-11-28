package ca.openbox.process.service.components;

import ca.openbox.infrastructure.email.service.WebhookEmailService;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class EmailNotificationConsumer {
    private final WebhookEmailService emailService;
    private final UserRepository userRepository;

    public EmailNotificationConsumer(WebhookEmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        startConsumer();
    }

    private void startConsumer() {
        new Thread(() -> {
            while (true) {
                try {
                    LeaveApplication leaveApplication = ApplicationStatusChangeMessageQueue.take();
                    String[] handlers = leaveApplication.getCurrentHandler().split(",");
                    //1.Retreive the name of applicant
                    String applicant = leaveApplication.getApplicant();
                    //2.Construct the subject
                    String subject =  String.format("Leave Request Submitted – %s", applicant);
                    //3.Construct the body
                    //3.1Formatter
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);
                    //3.2 the body
                    String body = String.format(
                            "%s submitted a leave request from %s to %s. Please log on to https://openbox.brimon.ca/ to review it.",
                            applicant,
                            leaveApplication.getStart().format(formatter),
                            leaveApplication.getEnd().format(formatter)
                    );
                    for (String handler : handlers) {
                        String email = userRepository.getUserDOByUsernameAndActiveIsTrue(handler).getEmail();
                        emailService.sendEmail(
                                email,
                                subject,
                                body
                        );
                        Thread.sleep(20000);//sleep 20s
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
