package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.AnnouncementRequest;
import com.ltl.league.admin.service.AdminAnnouncementService;
import com.ltl.league.entity.Announcement;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAnnouncementServiceImpl implements AdminAnnouncementService {

    private final AnnouncementMapper announcementMapper;

    public AdminAnnouncementServiceImpl(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    @Override
    public List<Announcement> listAnnouncements(Integer isActive) {
        return announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(isActive != null, Announcement::getIsActive, isActive)
                .eq(Announcement::getDeleted, 0)
                .orderByDesc(Announcement::getAnnounceDate)
                .orderByDesc(Announcement::getCreatedAt));
    }

    @Override
    @Transactional
    public Announcement createAnnouncement(AnnouncementRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(400, "公告标题不能为空");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException(400, "公告内容不能为空");
        }
        if (request.getAnnounceDate() == null) {
            throw new BusinessException(400, "公告日期不能为空");
        }

        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(request.getContent().trim());
        announcement.setAnnounceDate(request.getAnnounceDate());
        announcement.setIsActive(request.getIsActive() != null ? request.getIsActive() : 1);
        announcementMapper.insert(announcement);
        return announcement;
    }

    @Override
    @Transactional
    public Announcement updateAnnouncement(Long id, AnnouncementRequest request) {
        if (id == null) {
            throw new BusinessException(400, "公告ID不能为空");
        }
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(404, "公告不存在");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            announcement.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            announcement.setContent(request.getContent().trim());
        }
        if (request.getAnnounceDate() != null) {
            announcement.setAnnounceDate(request.getAnnounceDate());
        }
        if (request.getIsActive() != null) {
            announcement.setIsActive(request.getIsActive());
        }

        announcementMapper.updateById(announcement);
        return announcement;
    }

    @Override
    @Transactional
    public void deleteAnnouncement(Long id) {
        if (id == null) {
            throw new BusinessException(400, "公告ID不能为空");
        }
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(404, "公告不存在");
        }
        announcementMapper.deleteById(id);
    }
}
