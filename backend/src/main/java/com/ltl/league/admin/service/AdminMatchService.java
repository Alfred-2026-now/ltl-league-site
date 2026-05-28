package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.AdminMatchListItemVO;
import com.ltl.league.admin.dto.MatchCreateRequest;
import com.ltl.league.admin.dto.MatchUpdateRequest;
import com.ltl.league.entity.Match;

import java.util.List;

public interface AdminMatchService {

    List<AdminMatchListItemVO> list(
            String season,
            Integer round,
            Long teamId,
            String format,
            Integer schedulePublished,
            String status
    );

    Match getByIdOrThrow(Long id);

    Match create(MatchCreateRequest request);

    Match update(Long id, MatchUpdateRequest request);

    void publishSchedule(Long id);

    void unpublishSchedule(Long id);
}

