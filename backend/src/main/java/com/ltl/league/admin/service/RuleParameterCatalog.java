package com.ltl.league.admin.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleParameterCatalog {

    public static final String GROUP_REWARD = "reward";
    public static final String GROUP_LUXURY_TAX = "luxury_tax";
    public static final String GROUP_LOAN_FEE = "loan_fee";
    public static final String GROUP_PLAYER_TRANSFER = "player_transfer";
    public static final String GROUP_SALARY = "salary";

    private static final Map<String, Spec> SPECS = buildSpecs();

    private RuleParameterCatalog() {
    }

    public static Collection<Spec> all() {
        return SPECS.values();
    }

    public static Spec get(String key) {
        return SPECS.get(key);
    }

    public static String defaultValue(String key) {
        Spec spec = get(key);
        return spec != null ? spec.defaultValue() : "0";
    }

    private static Map<String, Spec> buildSpecs() {
        Map<String, Spec> specs = new LinkedHashMap<>();
        int order = 100;

        add(specs, "luxury.tax_line_factor", GROUP_LUXURY_TAX, "奢侈税", "税线系数", "0.92", "decimal", "倍",
                "税线 = 联盟标准身价 R × 本系数；值越低越容易触发奢侈税。", order++);
        add(specs, "luxury.standard_roster_size", GROUP_LUXURY_TAX, "奢侈税", "标准出场人数", "5", "int", "人",
                "联盟标准身价 R = 当前在职选手平均身价 × 本人数。", order++);
        add(specs, "luxury.taxable_floor", GROUP_LUXURY_TAX, "奢侈税", "应税额下限", "0", "int", "P",
                "应税额 X = max(本值, 修正后 L - 税线)。通常保持为 0，避免低于税线时产生负税。", order++);
        add(specs, "luxury.roster_factor.le5", GROUP_LUXURY_TAX, "奢侈税", "名单人数≤5修正因子", "1", "decimal", "倍",
                "队伍在职人数不超过 5 人时，出场总身价 L 的放大倍率。", order++);
        add(specs, "luxury.roster_factor.eq6", GROUP_LUXURY_TAX, "奢侈税", "名单人数6修正因子", "1.1", "decimal", "倍",
                "队伍在职人数为 6 人时，出场总身价 L 的放大倍率。", order++);
        add(specs, "luxury.roster_factor.eq7", GROUP_LUXURY_TAX, "奢侈税", "名单人数7修正因子", "1.25", "decimal", "倍",
                "队伍在职人数为 7 人时，出场总身价 L 的放大倍率。", order++);
        add(specs, "luxury.roster_factor.eq8", GROUP_LUXURY_TAX, "奢侈税", "名单人数8修正因子", "1.45", "decimal", "倍",
                "队伍在职人数为 8 人时，出场总身价 L 的放大倍率。", order++);
        add(specs, "luxury.roster_factor.eq9", GROUP_LUXURY_TAX, "奢侈税", "名单人数9修正因子", "1.7", "decimal", "倍",
                "队伍在职人数为 9 人时，出场总身价 L 的放大倍率。", order++);
        add(specs, "luxury.roster_factor.ge10", GROUP_LUXURY_TAX, "奢侈税", "名单人数≥10修正因子", "2", "decimal", "倍",
                "队伍在职人数为 10 人及以上时，出场总身价 L 的放大倍率。", order++);
        add(specs, "luxury.tier_width", GROUP_LUXURY_TAX, "奢侈税", "累进税档宽度", "1000", "int", "P",
                "奢侈税累进分段的单档宽度；默认每 1000P 进入下一档。", order++);

        double[] bo2Rates = {0.8, 1.1, 1.4, 1.8, 2.3};
        double[] bo3Rates = {1.0, 1.3, 1.7, 2.2, 2.8};
        for (int i = 0; i < bo2Rates.length; i++) {
            int tier = i + 1;
            add(specs, "luxury.bo2.rate.tier" + tier, GROUP_LUXURY_TAX, "奢侈税", "BO2第" + tier + "档税率",
                    trimDouble(bo2Rates[i]), "decimal", "倍", "BO2 奢侈税第 " + tier + " 档应税额使用的倍率。", order++);
        }
        for (int i = 0; i < bo3Rates.length; i++) {
            int tier = i + 1;
            add(specs, "luxury.bo3.rate.tier" + tier, GROUP_LUXURY_TAX, "奢侈税", "BO3第" + tier + "档税率",
                    trimDouble(bo3Rates[i]), "decimal", "倍", "BO3 奢侈税第 " + tier + " 档应税额使用的倍率。", order++);
        }

        order = 300;
        add(specs, "loan.bo2.rate", GROUP_LOAN_FEE, "租借费", "BO2租借费率", "0.45", "decimal", "倍",
                "BO2 租借费 = 结算身价 × 本费率。", order++);
        add(specs, "loan.bo3.rate", GROUP_LOAN_FEE, "租借费", "BO3租借费率", "0.6", "decimal", "倍",
                "BO3 租借费 = 结算身价 × 本费率。", order++);
        add(specs, "loan.player_share", GROUP_LOAN_FEE, "租借费", "选手分成比例", "0.4", "decimal", "倍",
                "租借费中进入被租借选手个人账户的比例。", order++);
        add(specs, "loan.original_team_share", GROUP_LOAN_FEE, "租借费", "原队分成比例", "0.4", "decimal", "倍",
                "非自由人租借时，租借费中进入原队 P 币账户的比例。", order++);
        add(specs, "loan.league_share", GROUP_LOAN_FEE, "租借费", "联盟回收比例", "0.2", "decimal", "倍",
                "非自由人租借时，租借费中由联盟回收的比例；用于说明和差额校验。", order++);
        add(specs, "loan.free_agent_source_share", GROUP_LOAN_FEE, "租借费", "自由人原队分成比例", "0", "decimal", "倍",
                "自由人租借没有原队时，原队分成按本比例计算。", order++);
        add(specs, "loan.free_agent_league_share", GROUP_LOAN_FEE, "租借费", "自由人联盟回收比例", "0.6", "decimal", "倍",
                "自由人租借时，租借费中由联盟回收的比例。", order++);

        order = 400;
        add(specs, "transfer.min_amount", GROUP_PLAYER_TRANSFER, "选手转赠", "单次最小金额", "10", "int", "P",
                "选手转赠金额不得低于本值。", order++);
        add(specs, "transfer.max_amount", GROUP_PLAYER_TRANSFER, "选手转赠", "单次最大金额", "100000", "int", "P",
                "选手转赠金额不得高于本值。", order++);
        add(specs, "transfer.amount_step", GROUP_PLAYER_TRANSFER, "选手转赠", "金额步进", "10", "int", "P",
                "选手转赠金额必须是本值的整数倍。", order++);
        add(specs, "transfer.personal_cooldown_days", GROUP_PLAYER_TRANSFER, "选手转赠", "个人转赠冷却", "3", "int", "天",
                "同一赠与人对个人转赠的冷却天数。", order++);
        add(specs, "transfer.team_fee_rate", GROUP_PLAYER_TRANSFER, "选手转赠", "转给战队手续费率", "0.1", "decimal", "倍",
                "转给自己战队时，手续费 = 转赠金额 × 本费率。", order++);
        add(specs, "transfer.player_base_fee", GROUP_PLAYER_TRANSFER, "选手转赠", "转给个人基础手续费", "100", "int", "P",
                "转给个人时额外收取的固定基础手续费。", order++);
        add(specs, "transfer.player_tier_width", GROUP_PLAYER_TRANSFER, "选手转赠", "个人转赠档位宽度", "1000", "int", "P",
                "个人转赠手续费前两档的分段宽度。", order++);
        add(specs, "transfer.player_tier1_rate", GROUP_PLAYER_TRANSFER, "选手转赠", "个人转赠第1档费率", "0.2", "decimal", "倍",
                "个人转赠第 1 档金额使用的手续费率。", order++);
        add(specs, "transfer.player_tier2_rate", GROUP_PLAYER_TRANSFER, "选手转赠", "个人转赠第2档费率", "0.4", "decimal", "倍",
                "个人转赠第 2 档金额使用的手续费率。", order++);
        add(specs, "transfer.player_tier3_rate", GROUP_PLAYER_TRANSFER, "选手转赠", "个人转赠第3档费率", "0.6", "decimal", "倍",
                "个人转赠超过前两档后的剩余金额使用的手续费率。", order++);

        order = 500;
        add(specs, "salary.min_rate", GROUP_SALARY, "工资发放", "工资比例最小值", "1", "int", "%",
                "后台工资发放允许输入的最低比例。", order++);
        add(specs, "salary.max_rate", GROUP_SALARY, "工资发放", "工资比例最大值", "100", "int", "%",
                "后台工资发放允许输入的最高比例。", order++);
        add(specs, "salary.default_rate", GROUP_SALARY, "工资发放", "默认工资比例", "10", "int", "%",
                "后台工资发放输入框默认值；工资 = 选手身价 × 比例。", order);

        return specs;
    }

    private static void add(Map<String, Spec> specs, String key, String groupKey, String groupName, String name,
            String defaultValue, String valueType, String unit, String description, int sortOrder) {
        specs.put(key, new Spec(key, groupKey, groupName, name, defaultValue, valueType, unit, description, sortOrder));
    }

    private static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    public record Spec(
            String key,
            String groupKey,
            String groupName,
            String name,
            String defaultValue,
            String valueType,
            String unit,
            String description,
            int sortOrder) {
    }
}
