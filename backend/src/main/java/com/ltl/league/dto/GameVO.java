package com.ltl.league.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GameVO {
    private Long id;
    private Long matchId;
    private Integer index;
    private String winner;
    private String blueTeam;
    private String redTeam;
    private String homeTeam;
    private String awayTeam;
    private Integer durationSeconds;
    private String sourceGameId;
    private String gameVersion;
    private Lineups lineups;

    @Data
    public static class Lineups {
        private List<Participant> home;
        private List<Participant> away;
    }

    @Data
    public static class Participant {
        private MappedPlayer mappedPlayer;
        private String position;
        private RosterContext rosterContext;
        private Loadout loadout;
        private CombatStats combatStats;
        private EconomyStats economyStats;
        private VisionStats visionStats;
        private DerivedStats derivedStats;
    }

    @Data
    public static class MappedPlayer {
        private String playerName;
    }

    @Data
    public static class RosterContext {
        private String representingTeam;
        private String sourceTeam;
        private Boolean isLoan;
        private Boolean isSubstitute;
    }

    @Data
    public static class Loadout {
        private Champion champion;
    }

    @Data
    public static class Champion {
        private String name;
    }

    @Data
    public static class CombatStats {
        private Integer kills;
        private Integer deaths;
        private Integer assists;
        private Integer damageDealtToChampions;
        private Integer totalDamageTaken;
    }

    @Data
    public static class EconomyStats {
        private Integer totalMinionsKilled;
        private Integer goldEarned;
    }

    @Data
    public static class VisionStats {
        private Integer visionScore;
    }

    @Data
    public static class DerivedStats {
        private BigDecimal killParticipation;
    }
}
