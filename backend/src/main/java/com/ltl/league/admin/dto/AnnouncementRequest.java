package com.ltl.league.admin.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AnnouncementRequest {
    private String title;
    private String content;
    private LocalDate announceDate;
    private Integer isActive;
}
