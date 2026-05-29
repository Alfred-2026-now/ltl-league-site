package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.RewardRuleRequest;
import com.ltl.league.admin.dto.RewardRuleVO;

import java.util.List;

public interface AdminRewardRuleService {

    List<RewardRuleVO> list(String format);

    RewardRuleVO create(RewardRuleRequest request);

    RewardRuleVO update(Long id, RewardRuleRequest request);

    void delete(Long id);
}
