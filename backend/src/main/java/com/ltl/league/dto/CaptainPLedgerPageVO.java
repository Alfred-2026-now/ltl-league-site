package com.ltl.league.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class CaptainPLedgerPageVO {
    private List<CaptainPLedgerVO> records = Collections.emptyList();
    private long total;
    private int page;
    private int pageSize;

    public int getTotalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) ((total + pageSize - 1) / pageSize);
    }
}
