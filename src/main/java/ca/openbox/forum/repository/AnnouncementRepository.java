package ca.openbox.forum.repository;

import ca.openbox.forum.dataobject.AnnouncementDO;
import ca.openbox.forum.entities.AnnouncementReadLog;
import org.springframework.data.repository.Repository;

import java.time.ZonedDateTime;
import java.util.List;

public interface AnnouncementRepository extends Repository<AnnouncementDO,Integer> {
    AnnouncementDO save(AnnouncementDO announcementDO);
    List<AnnouncementDO> findByExpiryDateAfterOrExpiryDateIsNullOrderByCreatedTimeDesc(ZonedDateTime expiryDate);
    AnnouncementDO getAnnouncementDOById(Integer id);
    void deleteAnnouncementDOById(Integer id);
}

