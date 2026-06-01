package com.ltl.league.service;

import com.ltl.league.dto.ChangePasswordRequest;
import com.ltl.league.dto.PrizeExchangeDetailVO;
import com.ltl.league.dto.UserInfoVO;

import java.util.List;

public interface UserService {

    UserInfoVO getUserInfo(Long playerId);

    List<PrizeExchangeDetailVO> getUserPrizeExchanges(Long playerId);

    void changePassword(Long playerId, ChangePasswordRequest request);
}
