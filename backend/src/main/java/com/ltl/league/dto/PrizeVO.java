package com.ltl.league.dto;

import lombok.Data;

@Data
public class PrizeVO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Integer costPoints;
    private Integer stock;
    private Integer isActive;
    private String createdAt;
}
