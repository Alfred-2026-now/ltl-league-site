package com.ltl.league.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginRequest {

    @NotBlank(message = "选手名称不能为空")
    private String playerName;

    @NotBlank(message = "密码不能为空")
    private String password;
}
