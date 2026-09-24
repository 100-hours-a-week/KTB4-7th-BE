package com.memme.dto.solution;

public record SavedSolutionDeleteResponse(String message, Data data) {

    public record Data(int deletedCount) {}
}
