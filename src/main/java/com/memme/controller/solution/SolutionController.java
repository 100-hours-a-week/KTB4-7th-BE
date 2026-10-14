package com.memme.controller.solution;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.solution.SolutionBundleDetailResponse;
import com.memme.dto.solution.SolutionManualRetryResponse;
import com.memme.dto.solution.SolutionSaveResponse;
import com.memme.dto.solution.SolutionTodayResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.solution.SavedSolutionService;
import com.memme.service.solution.SolutionManualRetryService;
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
    private final SolutionManualRetryService manualRetryService;

    public SolutionController(
            SolutionQueryService queryService,
            SavedSolutionService savedSolutionService,
            SolutionManualRetryService manualRetryService
    ) {
        this.queryService = queryService;
        this.savedSolutionService = savedSolutionService;
        this.manualRetryService = manualRetryService;
    }

    @GetMapping("/v1/solutions/today")
    public SolutionTodayResponse today(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user
    ) {
        requireAuthentication(user);
        return queryService.today(user.userId(), user.storeId());
    }

    @PostMapping("/v1/solutions/today/retries")
    public ResponseEntity<SolutionManualRetryResponse> retryToday(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user
    ) {
        requireAuthentication(user);
        SolutionManualRetryService.Result result = manualRetryService.retry(user.userId(), user.storeId());

        return switch (result.status()) {
            case COMPLETED -> ResponseEntity.ok(new SolutionManualRetryResponse(
                    "솔루션 재시도가 완료되었습니다.",
                    "COMPLETED",
                    new SolutionManualRetryResponse.Data(result.solutionBundleId())));
            case IN_PROGRESS -> ResponseEntity.status(HttpStatus.CONFLICT).body(new SolutionManualRetryResponse(
                    "오늘의 솔루션을 이미 생성하고 있습니다.",
                    "IN_PROGRESS",
                    new SolutionManualRetryResponse.Data(result.solutionBundleId())));
            case FAILED -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new SolutionManualRetryResponse(
                    "솔루션 재시도에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    "FAILED",
                    null));
        };
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

    @PostMapping("/v1/solutions/{solutionId}/saves")
    public ResponseEntity<SolutionSaveResponse> save(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @PathVariable Long solutionId
    ) {
        requireAuthentication(user);
        SavedSolutionService.SaveResult result = savedSolutionService.save(
                user.userId(),
                user.storeId(),
                solutionId
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
