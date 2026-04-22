package ca.openbox.forum.service;

import ca.openbox.forum.dataobject.AnnouncementDO;
import ca.openbox.forum.entities.Announcement;
import ca.openbox.forum.entities.AnnouncementReadLog;
import ca.openbox.forum.repository.AnnouncementRepository;
import ca.openbox.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AnnouncementService {
    @Autowired
    AnnouncementRepository announcementRepository;
    @Autowired
    private UserRepository userRepository;

    public Announcement addAnnouncement(Announcement announcement){
        AnnouncementDO announcementDO = announcementRepository.save(announcement.toDO());
        return Announcement.fromDO(announcementDO);
    }
    public List<Announcement> getAnnouncementAfter(ZonedDateTime expiryDate, String username) {
        List<AnnouncementDO> announcementListDO = new ArrayList<>();
        List<Announcement> announcements = new ArrayList<>();
        announcementListDO = announcementRepository.findByExpiryDateAfterOrExpiryDateIsNullOrderByCreatedTimeDesc(expiryDate);
        //new
        String groupName=userRepository.getUserDOByUsernameAndActiveIsTrue(username).getGroupName();
        if (Objects.equals(groupName, "manager")){
            // add all announcements if groupName is manager.
            for (int i = 0;i<announcementListDO.size();++i){
                Announcement announcement = Announcement.fromDO(announcementListDO.get(i));
                announcements.add(announcement);
            }
        }else{
            for (int i = 0;i<announcementListDO.size();++i){
                Announcement announcement = Announcement.fromDO(announcementListDO.get(i));
                if (announcement.getGroupName().equals(groupName)|| announcement.getGroupName().equals("public")) {
                    // only add announcements that match the groupName.
                    announcements.add(announcement);
                }

            }
        }

        return announcements;


    }
    public Announcement getAnnouncementById(Integer announcementId){
        AnnouncementDO announcementDO = announcementRepository.getAnnouncementDOById(announcementId);
        return Announcement.fromDO(announcementDO);

    }
    public void deleteAnnouncementById(Integer announcementId){
        announcementRepository.deleteAnnouncementDOById(announcementId);
    }


}
