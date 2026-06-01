package com.ltl.league.dto;

import lombok.Data;

@Data
public class CreatePrizeRequest {
    private String name;
    private String description;
    private String imageUrl;
    private Integer costPoints;
    private Integer stock;
}
