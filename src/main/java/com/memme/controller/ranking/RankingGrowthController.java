package com.memme.controller.ranking;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.ranking.RankingGrowthResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.exception.ranking.UnsupportedRankingConditionException;
import com.memme.service.ranking.RankingGrowthQueryService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class RankingGrowthController {

    private final RankingGrowthQueryService queryService;

    public RankingGrowthController(RankingGrowthQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/v2/rankings/growth")
    public RankingGrowthResponse growth(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @RequestParam Map<String, String> queryParameters
    ) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
        if (!queryParameters.isEmpty()) {
            throw new UnsupportedRankingConditionException();
        }
        return queryService.query(user.userId(), user.storeId());
    }
}
