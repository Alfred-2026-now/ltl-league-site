package com.ltl.league.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ltl.league.admin.service.AdminAssetService;
import com.ltl.league.admin.service.RuleParameterService;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.PlayerDepositLedger;
import com.ltl.league.mapper.PlayerDepositLedgerMapper;
import com.ltl.league.mapper.PlayerMapper;
import com.ltl.league.service.impl.DailyMaintenanceFeeServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyMaintenanceFeeServiceImplTest {

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private PlayerDepositLedgerMapper depositLedgerMapper;

    @Mock
    private RuleParameterService ruleParameterService;

    @Mock
    private AdminAssetService adminAssetService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Player.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PlayerDepositLedger.class);
    }

    @Test
    void skipsWhenFeatureDisabled() {
        DailyMaintenanceFeeServiceImpl service = newService();
        when(ruleParameterService.getInt("daily_fee.enabled")).thenReturn(0);

        assertEquals(0, service.runDailyDeduction());
        verify(playerMapper, never()).selectList(any());
    }

    @Test
    void chargesEligiblePlayersAndSkipsBelowThreshold() {
        DailyMaintenanceFeeServiceImpl service = newService();
        when(ruleParameterService.getInt("daily_fee.enabled")).thenReturn(1);
        when(ruleParameterService.getInt("daily_fee.amount")).thenReturn(50);
        when(ruleParameterService.getInt("daily_fee.min_balance")).thenReturn(1000);

        Player idRow = new Player();
        idRow.setId(1L);
        Player idRow2 = new Player();
        idRow2.setId(2L);
        when(playerMapper.selectList(any())).thenReturn(List.of(idRow, idRow2));
        when(depositLedgerMapper.selectCount(any())).thenReturn(0L);

        Player rich = new Player();
        rich.setId(1L);
        rich.setName("高分");
        rich.setDeposit(1200);
        rich.setDeleted(0);
        Player poor = new Player();
        poor.setId(2L);
        poor.setName("低分");
        poor.setDeposit(999);
        poor.setDeleted(0);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(rich);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(poor);

        assertEquals(1, service.runDailyDeduction());

        ArgumentCaptor<PlayerDepositLedger> ledgerCaptor = ArgumentCaptor.forClass(PlayerDepositLedger.class);
        verify(depositLedgerMapper).insert(ledgerCaptor.capture());
        PlayerDepositLedger ledger = ledgerCaptor.getValue();
        assertEquals(-50, ledger.getAmount());
        assertEquals(1200, ledger.getBalanceBefore());
        assertEquals(1150, ledger.getBalanceAfter());
        assertEquals("daily_maintenance_fee", ledger.getType());

        verify(playerMapper).updateById(rich);
        assertEquals(1150, rich.getDeposit());
        verify(adminAssetService).recordIncome(
                eq(50),
                eq("daily_maintenance_fee"),
                anyString(),
                eq("daily_fee"),
                eq("player_deposit_ledger"),
                isNull(),
                isNull(),
                isNull(),
                eq("system"));
        verify(playerMapper, never()).updateById(poor);
    }

    private DailyMaintenanceFeeServiceImpl newService() {
        return new DailyMaintenanceFeeServiceImpl(
                playerMapper,
                depositLedgerMapper,
                ruleParameterService,
                adminAssetService);
    }
}
