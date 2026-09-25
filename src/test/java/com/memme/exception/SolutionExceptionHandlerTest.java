package com.memme.exception;

import com.memme.dto.solution.SolutionNavigationErrorResponse;
import com.memme.exception.solution.SolutionRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void expiredBundleReturnsGoneAndTodayNavigation() {
        var response = handler.handleSolutionRequest(new SolutionRequestException(
                SolutionRequestException.Reason.BUNDLE_EXPIRED
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isEqualTo(new SolutionNavigationErrorResponse(
                "오늘의 솔루션이 만료되었습니다.",
                "SOL-01-01",
                null
        ));
    }

    @Test
    void missingSavedSolutionReturnsNotFoundAndListNavigation() {
        var response = handler.handleSolutionRequest(new SolutionRequestException(
                SolutionRequestException.Reason.SAVED_SOLUTION_NOT_FOUND
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new SolutionNavigationErrorResponse(
                "삭제되었거나 존재하지 않는 저장 솔루션입니다.",
                "SOL-03",
                null
        ));
    }
}
