package com.ltl.league.admin.service;

import com.ltl.league.admin.dto.RuleParameterHistoryVO;
import com.ltl.league.admin.dto.RuleParameterUpdateRequest;
import com.ltl.league.admin.dto.RuleParameterVO;

import java.util.List;

public class CatalogRuleParameterService implements RuleParameterService {

    @Override
    public double getDecimal(String key) {
        return Double.parseDouble(RuleParameterCatalog.defaultValue(key));
    }

    @Override
    public int getInt(String key) {
        return (int) Math.round(getDecimal(key));
    }

    @Override
    public List<RuleParameterVO> listParameters(String groupKey) {
        return List.of();
    }

    @Override
    public RuleParameterVO updateParameter(String key, RuleParameterUpdateRequest request, String operator) {
        throw new UnsupportedOperationException("Catalog parameters are read-only.");
    }

    @Override
    public List<RuleParameterHistoryVO> listHistory(String groupKey, Integer limit) {
        return List.of();
    }

    @Override
    public void recordHistory(String groupKey, String groupName, String paramKey, String paramName,
            String oldValue, String newValue, String operator, String reason) {
    }
}
