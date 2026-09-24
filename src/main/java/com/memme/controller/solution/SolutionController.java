package com.memme.controller.solution;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.solution.SolutionBundleDetailResponse;
import com.memme.dto.solution.SolutionSaveResponse;
import com.memme.dto.solution.SolutionTodayResponse;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.service.solution.SavedSolutionService;
import com.memme.service.solution.SolutionQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
public class SolutionController {

    private final SolutionQueryService queryService;
    private final SavedSolutionService savedSolutionService;

    public SolutionController(
            SolutionQueryService queryService,
            SavedSolutionService savedSolutionService
    ) {
        this.queryService = queryService;
        this.savedSolutionService = savedSolutionService;
    }

    @GetMapping("/v1/solutions/today")
    public SolutionTodayResponse today(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user
    ) {
        requireAuthentication(user);
        return queryService.today(user.userId(), user.storeId());
    }

    @GetMapping("/v1/solution-bundles/{bundleId}")
    public SolutionBundleDetailResponse detail(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @PathVariable Long bundleId
    ) {
        requireAuthentication(user);
        return queryService.detail(user.userId(), user.storeId(), bundleId);
    }

    @PostMapping("/v1/solution-bundles/{bundleId}/saves")
    public ResponseEntity<SolutionSaveResponse> save(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @PathVariable Long bundleId
    ) {
        requireAuthentication(user);
        SavedSolutionService.SaveResult result = savedSolutionService.saveBundle(
                user.userId(),
                user.storeId(),
                bundleId
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    private void requireAuthentication(AuthenticatedUserSession user) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
    }
}
