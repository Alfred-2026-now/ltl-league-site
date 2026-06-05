package com.ltl.league.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.admin.dto.RuleParameterHistoryVO;
import com.ltl.league.admin.dto.RuleParameterUpdateRequest;
import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.admin.service.RuleParameterCatalog;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.RuleParameter;
import com.ltl.league.entity.RuleParameterHistory;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.RuleParameterHistoryMapper;
import com.ltl.league.mapper.RuleParameterMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RuleParameterServiceImpl implements RuleParameterService {

    private final RuleParameterMapper parameterMapper;
    private final RuleParameterHistoryMapper historyMapper;

    public RuleParameterServiceImpl(RuleParameterMapper parameterMapper, RuleParameterHistoryMapper historyMapper) {
        this.parameterMapper = parameterMapper;
        this.historyMapper = historyMapper;
    }

    @Override
    public double getDecimal(String key) {
        String value = activeValueOrDefault(key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.parseDouble(RuleParameterCatalog.defaultValue(key));
        }
    }

    @Override
    public int getInt(String key) {
        return (int) Math.round(getDecimal(key));
    }

    @Override
    @Transactional
    public List<RuleParameterVO> listParameters(String groupKey) {
        ensureDefaults();
        LambdaQueryWrapper<RuleParameter> query = new LambdaQueryWrapper<RuleParameter>()
                .eq(RuleParameter::getDeleted, 0)
                .orderByAsc(RuleParameter::getSortOrder)
                .orderByAsc(RuleParameter::getId);
        if (groupKey != null && !groupKey.isBlank()) {
            query.eq(RuleParameter::getGroupKey, groupKey.trim());
        }
        return parameterMapper.selectList(query).stream()
                .sorted(Comparator.comparing(RuleParameter::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RuleParameterVO updateParameter(String key, RuleParameterUpdateRequest request, String operator) {
        if (request == null) {
            throw new BusinessException(400, "请求参数不能为空");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写修改原因");
        }
        RuleParameter row = ensureOne(key);
        String oldValue = row.getValueText();
        Integer oldActive = row.getIsActive();
        String nextValue = request.getValueText() != null ? request.getValueText().trim() : row.getValueText();
        validateValue(row.getValueType(), nextValue);
        row.setValueText(nextValue);
        row.setIsActive(request.getIsActive() != null ? normalizeActive(request.getIsActive()) : row.getIsActive());
        parameterMapper.updateById(row);

        String oldText = valueWithState(oldValue, oldActive);
        String newText = valueWithState(row.getValueText(), row.getIsActive());
        if (!Objects.equals(oldText, newText)) {
            recordHistory(row.getGroupKey(), row.getGroupName(), row.getParamKey(), row.getName(),
                    oldText, newText, operator, request.getReason());
        }
        return toVO(row);
    }

    @Override
    public List<RuleParameterHistoryVO> listHistory(String groupKey, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        LambdaQueryWrapper<RuleParameterHistory> query = new LambdaQueryWrapper<RuleParameterHistory>()
                .orderByDesc(RuleParameterHistory::getCreatedAt)
                .orderByDesc(RuleParameterHistory::getId)
                .last("LIMIT " + normalizedLimit);
        if (groupKey != null && !groupKey.isBlank()) {
            query.eq(RuleParameterHistory::getGroupKey, groupKey.trim());
        }
        return historyMapper.selectList(query).stream().map(this::toHistoryVO).collect(Collectors.toList());
    }

    @Override
    public void recordHistory(String groupKey, String groupName, String paramKey, String paramName,
            String oldValue, String newValue, String operator, String reason) {
        RuleParameterHistory history = new RuleParameterHistory();
        RuleParameter parameter = findByKey(paramKey);
        history.setParameterId(parameter != null ? parameter.getId() : null);
        history.setParamKey(paramKey);
        history.setParamName(paramName);
        history.setGroupKey(groupKey);
        history.setGroupName(groupName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setOperator(operator == null || operator.isBlank() ? "admin" : operator);
        history.setReason(reason == null || reason.isBlank() ? "未填写" : reason.trim());
        historyMapper.insert(history);
    }

    private String activeValueOrDefault(String key) {
        RuleParameter row = findByKey(key);
        if (row == null || row.getIsActive() == null || row.getIsActive() != 1 || row.getValueText() == null || row.getValueText().isBlank()) {
            return RuleParameterCatalog.defaultValue(key);
        }
        return row.getValueText().trim();
    }

    private RuleParameter findByKey(String key) {
        return parameterMapper.selectOne(new LambdaQueryWrapper<RuleParameter>()
                .eq(RuleParameter::getParamKey, key)
                .eq(RuleParameter::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private void ensureDefaults() {
        for (RuleParameterCatalog.Spec spec : RuleParameterCatalog.all()) {
            ensureOne(spec.key());
        }
    }

    private RuleParameter ensureOne(String key) {
        RuleParameter row = findByKey(key);
        if (row != null) {
            return row;
        }
        RuleParameterCatalog.Spec spec = RuleParameterCatalog.get(key);
        if (spec == null) {
            throw new BusinessException(404, "规则参数不存在: " + key);
        }
        row = new RuleParameter();
        row.setParamKey(spec.key());
        row.setGroupKey(spec.groupKey());
        row.setGroupName(spec.groupName());
        row.setName(spec.name());
        row.setDescription(spec.description());
        row.setValueType(spec.valueType());
        row.setValueText(spec.defaultValue());
        row.setUnit(spec.unit());
        row.setSortOrder(spec.sortOrder());
        row.setIsActive(1);
        parameterMapper.insert(row);
        return row;
    }

    private void validateValue(String valueType, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "参数值不能为空");
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            if ("int".equalsIgnoreCase(valueType) && parsed != Math.rint(parsed)) {
                throw new BusinessException(400, "该参数必须填写整数");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "参数值必须是数字");
        }
    }

    private Integer normalizeActive(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private String valueWithState(String value, Integer active) {
        return (active != null && active == 1 ? "启用" : "停用") + " / " + (value == null ? "" : value);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 200));
    }

    private RuleParameterVO toVO(RuleParameter row) {
        RuleParameterVO vo = new RuleParameterVO();
        vo.setId(row.getId());
        vo.setParamKey(row.getParamKey());
        vo.setGroupKey(row.getGroupKey());
        vo.setGroupName(row.getGroupName());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription());
        vo.setValueType(row.getValueType());
        vo.setValueText(row.getValueText());
        vo.setUnit(row.getUnit());
        vo.setSortOrder(row.getSortOrder());
        vo.setIsActive(row.getIsActive());
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        vo.setUpdatedAt(row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
        return vo;
    }

    private RuleParameterHistoryVO toHistoryVO(RuleParameterHistory row) {
        RuleParameterHistoryVO vo = new RuleParameterHistoryVO();
        vo.setId(row.getId());
        vo.setParamKey(row.getParamKey());
        vo.setParamName(row.getParamName());
        vo.setGroupKey(row.getGroupKey());
        vo.setGroupName(row.getGroupName());
        vo.setOldValue(row.getOldValue());
        vo.setNewValue(row.getNewValue());
        vo.setOperator(row.getOperator());
        vo.setReason(row.getReason());
        vo.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        return vo;
    }
}
