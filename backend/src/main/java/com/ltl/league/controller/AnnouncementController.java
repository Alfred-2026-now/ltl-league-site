package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.entity.Announcement;
import com.ltl.league.service.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public Result<List<Announcement>> getAllAnnouncements() {
        return Result.success(announcementService.getAllAnnouncements());
    }
}
