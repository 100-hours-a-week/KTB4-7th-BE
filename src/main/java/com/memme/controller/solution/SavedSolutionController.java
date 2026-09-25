package com.memme.controller.solution;

import java.util.List;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.solution.SavedSolutionDeleteResponse;
import com.memme.dto.solution.SavedSolutionDetailResponse;
import com.memme.dto.solution.SavedSolutionListResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.solution.SavedSolutionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v1/saved-solutions")
public class SavedSolutionController {

    private final SavedSolutionService savedSolutionService;

    public SavedSolutionController(SavedSolutionService savedSolutionService) {
        this.savedSolutionService = savedSolutionService;
    }

    @GetMapping
    public SavedSolutionListResponse list(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        requireAuthentication(user);
        return savedSolutionService.list(user.userId(), user.storeId(), cursor, size);
    }

    @GetMapping("/{savedId}")
    public SavedSolutionDetailResponse detail(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @PathVariable Long savedId
    ) {
        requireAuthentication(user);
        return savedSolutionService.detail(user.userId(), user.storeId(), savedId);
    }

    @DeleteMapping
    public SavedSolutionDeleteResponse delete(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @RequestParam List<Long> savedIds
    ) {
        requireAuthentication(user);
        int deletedCount = savedSolutionService.delete(user.userId(), user.storeId(), savedIds);
        return new SavedSolutionDeleteResponse(
                "삭제되었습니다.",
                new SavedSolutionDeleteResponse.Data(deletedCount)
        );
    }

    private void requireAuthentication(AuthenticatedUserSession user) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
    }
}
