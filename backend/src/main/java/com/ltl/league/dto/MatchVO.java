package com.ltl.league.dto;

import com.ltl.league.entity.Attachment;
import com.ltl.league.entity.PLedger;
import com.ltl.league.entity.Player;
import com.ltl.league.entity.ValuationChange;
import lombok.Data;

import java.util.List;

@Data
public class MatchVO {
    private Long id;
    private String matchId;
    private String season;
    private Integer round;
    private String roundLabel;
    private String matchDate;
    private String format;
    private String status;
    private String homeTeam;
    private String awayTeam;
    private Score score;
    private Live live;
    private List<GameVO> games;
    private List<PLedgerVO> pLedger;
    private List<ValuationChangeVO> valuationChanges;
    private List<AttachmentVO> attachments;
    private String notes;
    private String source;
    private String version;

    @Data
    public static class Score {
        private Integer home;
        private Integer away;
    }

    @Data
    public static class Live {
        private String label;
        private String url;
    }

    @Data
    public static class PLedgerVO {
        private String team;
        private String type;
        private Integer amount;
        private String reason;
    }

    @Data
    public static class ValuationChangeVO {
        private String playerName;
        private Integer before;
        private Integer objective;
        private Integer subjective;
        private String reason;
        private Integer after;
    }

    @Data
    public static class AttachmentVO {
        private String label;
        private String url;
    }
}
