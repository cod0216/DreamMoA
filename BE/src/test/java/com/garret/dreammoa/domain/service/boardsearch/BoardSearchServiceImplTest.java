package com.garret.dreammoa.domain.service.boardsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoardSearchServiceImplTest {

    @Test
    void resolveMinimumShouldMatchReturnsNullForSingleToken() {
        assertNull(BoardSearchServiceImpl.resolveMinimumShouldMatch("면접"));
    }

    @Test
    void resolveMinimumShouldMatchReturnsFiftyPercentForTwoTokens() {
        assertEquals("50%", BoardSearchServiceImpl.resolveMinimumShouldMatch("면접 준비"));
    }

    @Test
    void resolveMinimumShouldMatchReturnsSeventyPercentForThreeTokens() {
        assertEquals("70%", BoardSearchServiceImpl.resolveMinimumShouldMatch("자소서 면접 준비"));
    }

    @Test
    void resolveMinimumShouldMatchReturnsSeventyFivePercentForFourOrMoreTokens() {
        assertEquals("75%", BoardSearchServiceImpl.resolveMinimumShouldMatch("스프링 게시판 검색 기능"));
    }
}
