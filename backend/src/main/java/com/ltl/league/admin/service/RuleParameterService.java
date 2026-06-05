package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.RuleParameterHistoryVO;
import com.ltl.league.admin.dto.RuleParameterUpdateRequest;
import com.ltl.league.admin.dto.RuleParameterVO;

import java.util.List;

public interface RuleParameterService {

    double getDecimal(String key);

    int getInt(String key);

    List<RuleParameterVO> listParameters(String groupKey);

    RuleParameterVO updateParameter(String key, RuleParameterUpdateRequest request, String operator);

    List<RuleParameterHistoryVO> listHistory(String groupKey, Integer limit);

    void recordHistory(String groupKey, String groupName, String paramKey, String paramName,
            String oldValue, String newValue, String operator, String reason);
}
