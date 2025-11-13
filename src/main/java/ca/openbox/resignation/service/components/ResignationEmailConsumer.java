package ca.openbox.resignation.service.components;

import ca.openbox.infrastructure.email.service.WebhookEmailService;
import ca.openbox.resignation.entities.ResignationApplication;
import ca.openbox.user.presentation.UserPresentation;
import ca.openbox.user.repository.UserPresentationRepository;
import ca.openbox.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ResignationEmailConsumer {
    private final WebhookEmailService emailService;
    private final UserPresentationRepository  userPresentationRepository;

    public ResignationEmailConsumer(WebhookEmailService emailService, UserPresentationRepository userPresentationRepository) {
        this.emailService = emailService;
        this.userPresentationRepository = userPresentationRepository;
        startConsumer();
    }

    private void startConsumer() {
        new Thread(() -> {
            while (true) {
                try {
                    ResignationApplication resignation = ResignationMessageQueue.take();
                    String applicant = resignation.getApplicant();
                    LocalDate lastWorkingDay = resignation.getLastWorkingDay();
                    String subject = String.format("Resignation Submitted – %s", applicant);
                    String body = String.format("%s submitted a resignation request for %s. Please log on to https://openbox.brimon.ca/ to review.", applicant, lastWorkingDay.toString());
                    // Send to Manager
                    List<UserPresentation> userPresentations = userPresentationRepository.findByRolesLikeAndActiveIsTrue("manager");
                    for(UserPresentation userPresentation : userPresentations) {
                        String email = userPresentation.getEmail();
                        emailService.sendEmail(email, subject, body);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}

