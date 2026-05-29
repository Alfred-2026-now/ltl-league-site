package com.ltl.league.admin.controller;

import com.ltl.league.admin.dto.AnnouncementRequest;
import com.ltl.league.admin.service.AdminAnnouncementService;
import com.ltl.league.common.Result;
import com.ltl.league.entity.Announcement;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminAnnouncementController {

    private final AdminAnnouncementService adminAnnouncementService;

    public AdminAnnouncementController(AdminAnnouncementService adminAnnouncementService) {
        this.adminAnnouncementService = adminAnnouncementService;
    }

    @GetMapping("/announcements")
    public Result<List<Announcement>> listAnnouncements(
            @RequestParam(required = false) Integer isActive) {
        return Result.success(adminAnnouncementService.listAnnouncements(isActive));
    }

    @PostMapping("/announcements")
    public Result<Announcement> createAnnouncement(@RequestBody AnnouncementRequest request) {
        return Result.success(adminAnnouncementService.createAnnouncement(request));
    }

    @PutMapping("/announcements/{id}")
    public Result<Announcement> updateAnnouncement(
            @PathVariable Long id,
            @RequestBody AnnouncementRequest request) {
        return Result.success(adminAnnouncementService.updateAnnouncement(id, request));
    }

    @DeleteMapping("/announcements/{id}")
    public Result<Void> deleteAnnouncement(@PathVariable Long id) {
        adminAnnouncementService.deleteAnnouncement(id);
        return Result.success();
    }
}
