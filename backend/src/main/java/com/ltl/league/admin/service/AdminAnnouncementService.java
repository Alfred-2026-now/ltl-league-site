package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.AnnouncementRequest;
import com.ltl.league.entity.Announcement;

import java.util.List;

public interface AdminAnnouncementService {
    List<Announcement> listAnnouncements(Integer isActive);

    Announcement createAnnouncement(AnnouncementRequest request);

    Announcement updateAnnouncement(Long id, AnnouncementRequest request);

    void deleteAnnouncement(Long id);
}
