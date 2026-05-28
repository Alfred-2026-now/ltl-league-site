package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.MatchResultAttachmentVO;
import com.ltl.league.admin.dto.MatchResultDraftRequest;
import com.ltl.league.admin.dto.MatchResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface AdminMatchResultService {

    MatchResultVO getResultContext(Long matchId);

    MatchResultVO createDraft(Long matchId, MatchResultDraftRequest request);

    MatchResultVO updateDraft(Long matchId, Long resultId, MatchResultDraftRequest request);

    void publish(Long matchId, Long resultId);

    MatchResultVO withdraw(Long matchId, Long resultId, String withdrawReason);

    MatchResultAttachmentVO uploadScreenshot(Long matchId, Long resultId, Integer gameIndex, MultipartFile file);

    void deleteAttachment(Long attachmentId);
}
