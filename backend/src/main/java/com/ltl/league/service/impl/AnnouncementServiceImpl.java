package com.ltl.league.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ltl.league.entity.Announcement;
import com.ltl.league.mapper.AnnouncementMapper;
import com.ltl.league.service.AnnouncementService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement> implements AnnouncementService {

    @Override
    public List<Announcement> getAllAnnouncements() {
        return lambdaQuery()
                .orderByDesc(Announcement::getAnnounceDate)
                .list();
    }
}
