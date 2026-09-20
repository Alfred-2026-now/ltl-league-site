package com.ltl.league.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ltl.league.entity.LeagueAssetLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeagueAssetLedgerMapper extends BaseMapper<LeagueAssetLedger> {
    @Select("SELECT * FROM league_asset_ledger WHERE deleted = 0 ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE")
    LeagueAssetLedger selectLatestForUpdate();
}
