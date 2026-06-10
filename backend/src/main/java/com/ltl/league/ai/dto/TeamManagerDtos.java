package com.ltl.league.ai.dto;

import com.ltl.league.admin.dto.RuleParameterVO;
import com.ltl.league.dto.UserInfoVO;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.SettlementRewardRule;
import com.ltl.league.entity.Team;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamManagerDtos {

    private TeamManagerDtos() {
    }

    @Data
    public static class ContextResponse {
        private UserInfoVO currentUser;
        private Team currentTeam;
        private List<Team> teams = new ArrayList<>();
        private List<Player> players = new ArrayList<>();
        private List<RuleParameterVO> ruleParameters = new ArrayList<>();
        private List<SettlementRewardRule> rewardRules = new ArrayList<>();
        private String knowledgeSummary;
    }

    @Data
    public static class SimulationRequest {
        private Long opponentTeamId;
        private String format;
        private List<Long> lineupPlayerIds = new ArrayList<>();
        private List<Long> opponentLineupPlayerIds = new ArrayList<>();
        private String strategy;
        private Map<String, Integer> recommendationBatches = new LinkedHashMap<>();
    }

    @Data
    public static class SimulationResponse {
        private Long currentTeamId;
        private Long opponentTeamId;
        private String format;
        private String strategy;
        private Integer lineValue;
        private Integer rosterSize;
        private Integer luxuryTax;
        private Integer loanFeePaid;
        private Integer balanceBefore;
        private Integer opponentLineValue;
        private Boolean opponentHasLoans;
        private List<ScenarioSimulation> scenarios = new ArrayList<>();
        private List<LoanRecommendation> loanRecommendations = new ArrayList<>();
        private List<StrategyRecommendation> strategyRecommendations = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class ScenarioSimulation {
        private String scorePattern;
        private Integer matchReward;
        private Integer luxuryTax;
        private Integer loanFeePaid;
        private Integer loanFeeReceived;
        private Integer netPChange;
        private Integer balanceBefore;
        private Integer balanceAfter;
        private List<String> breakdown = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class LoanRecommendation {
        private Long playerId;
        private String playerName;
        private Long sourceTeamId;
        private String sourceTeamState;
        private String sourceType;
        private Long replacedPlayerId;
        private String replacedPlayerName;
        private Integer playerValue;
        private Integer loanFee;
        private Integer worstNetPChange;
        private Integer bestNetPChange;
        private List<Long> lineupPlayerIdsAfterLoan = new ArrayList<>();
        private List<ScenarioSimulation> scenarios = new ArrayList<>();
    }

    @Data
    public static class StrategyRecommendation {
        private String strategy;
        private Integer batchIndex;
        private String candidateRange;
        private List<LoanRecommendation> recommendations = new ArrayList<>();
    }

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Data
    public static class ChatRequest {
        private SimulationRequest input;
        private SimulationResponse simulation;
        private List<ChatMessage> messages = new ArrayList<>();
    }

    @Data
    public static class ChatResponse {
        private String reply;
    }
}
