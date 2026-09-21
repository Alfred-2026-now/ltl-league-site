package com.ltl.league.admin.service.impl;

import com.ltl.league.admin.dto.RuleParameterUpdateRequest;
import com.ltl.league.entity.RuleParameter;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.RuleParameterHistoryMapper;
import com.ltl.league.mapper.RuleParameterMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleParameterServiceImplTest {

    @Test
    void anonymousFeeRateMustStayBetweenZeroAndOneHundred() {
        RuleParameterMapper parameterMapper = mock(RuleParameterMapper.class);
        RuleParameterHistoryMapper historyMapper = mock(RuleParameterHistoryMapper.class);
        RuleParameter row = new RuleParameter();
        row.setParamKey("event_task.anonymous_fee_rate");
        row.setValueType("int");
        row.setValueText("10");
        row.setIsActive(1);
        when(parameterMapper.selectOne(any())).thenReturn(row);
        RuleParameterUpdateRequest request = new RuleParameterUpdateRequest();
        request.setValueText("101");
        request.setReason("边界校验");

        BusinessException error = assertThrows(BusinessException.class,
                () -> new RuleParameterServiceImpl(parameterMapper, historyMapper)
                        .updateParameter("event_task.anonymous_fee_rate", request, "管理员"));

        assertTrue(error.getMessage().contains("0到100"));
        verify(parameterMapper, never()).updateById(any());
    }
}
