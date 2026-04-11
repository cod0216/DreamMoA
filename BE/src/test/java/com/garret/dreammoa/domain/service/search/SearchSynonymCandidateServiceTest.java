package com.garret.dreammoa.domain.service.search;

import com.garret.dreammoa.domain.model.BoardEntity;
import com.garret.dreammoa.domain.model.SearchClickLogEntity;
import com.garret.dreammoa.domain.model.SearchLogEntity;
import com.garret.dreammoa.domain.model.SearchSynonymCandidateEntity;
import com.garret.dreammoa.domain.repository.BoardRepository;
import com.garret.dreammoa.domain.repository.SearchClickLogRepository;
import com.garret.dreammoa.domain.repository.SearchLogRepository;
import com.garret.dreammoa.domain.repository.SearchSynonymCandidateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SearchSynonymCandidateServiceTest {

    @Test
    void generateCandidatesCreatesPendingCandidateFromRepeatedZeroResultSemanticClicks() throws IOException {
        SearchClickLogRepository clickRepository = mock(SearchClickLogRepository.class);
        SearchLogRepository logRepository = mock(SearchLogRepository.class);
        SearchSynonymCandidateRepository candidateRepository = mock(SearchSynonymCandidateRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);

        SearchSynonymCandidateService service = new SearchSynonymCandidateService(
                clickRepository,
                logRepository,
                candidateRepository,
                boardRepository
        );

        SearchLogEntity keyword1 = SearchLogEntity.builder()
                .id(1L)
                .normalizedQuery("백앤드")
                .category("자유")
                .resultCount(0)
                .userId(10L)
                .searchType(SearchLogEntity.SearchType.KEYWORD)
                .build();
        SearchLogEntity keyword2 = SearchLogEntity.builder()
                .id(2L)
                .normalizedQuery("백앤드")
                .category("자유")
                .resultCount(0)
                .userId(20L)
                .searchType(SearchLogEntity.SearchType.KEYWORD)
                .build();
        SearchLogEntity keyword3 = SearchLogEntity.builder()
                .id(3L)
                .normalizedQuery("백앤드")
                .category("자유")
                .resultCount(0)
                .sessionId("guest-1")
                .searchType(SearchLogEntity.SearchType.KEYWORD)
                .build();

        SearchClickLogEntity click1 = semanticClick(101L, 1L, 8L);
        SearchClickLogEntity click2 = semanticClick(102L, 2L, 8L);
        SearchClickLogEntity click3 = semanticClick(103L, 3L, 8L);

        BoardEntity board = BoardEntity.builder()
                .postId(8L)
                .title("백엔드 개발자 팀원을 모집합니다")
                .category(BoardEntity.Category.자유)
                .build();

        given(clickRepository.findSemanticClicksSince(any(LocalDateTime.class), any(SearchLogEntity.SearchType.class), any(SearchLogEntity.SearchType.class)))
                .willReturn(List.of(click1, click2, click3));
        given(logRepository.findAllByIdIn(anyCollection()))
                .willReturn(List.of(keyword1, keyword2, keyword3));
        given(boardRepository.findById(8L)).willReturn(Optional.of(board));
        given(candidateRepository.findBySourceTermAndTargetTermAndCategory("백앤드", "백엔드", "자유"))
                .willReturn(Optional.empty());
        given(candidateRepository.save(any(SearchSynonymCandidateEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        int generatedCount = service.generateCandidates(LocalDateTime.now().minusDays(7));

        assertEquals(1, generatedCount);
        ArgumentCaptor<SearchSynonymCandidateEntity> captor = ArgumentCaptor.forClass(SearchSynonymCandidateEntity.class);
        verify(candidateRepository).save(captor.capture());
        SearchSynonymCandidateEntity saved = captor.getValue();
        assertEquals("백앤드", saved.getSourceTerm());
        assertEquals("백엔드", saved.getTargetTerm());
        assertEquals(3, saved.getEvidenceCount());
        assertEquals(3, saved.getDistinctUserCount());
        assertEquals(SearchSynonymCandidateEntity.Status.PENDING, saved.getStatus());
        assertTrue(saved.getSampleTitles().contains("백엔드 개발자 팀원을 모집합니다"));
    }

    private SearchClickLogEntity semanticClick(Long semanticLogId, Long previousSearchLogId, Long postId) {
        SearchLogEntity semanticLog = SearchLogEntity.builder()
                .id(semanticLogId)
                .searchType(SearchLogEntity.SearchType.SEMANTIC)
                .previousSearchLogId(previousSearchLogId)
                .build();

        return SearchClickLogEntity.builder()
                .searchLog(semanticLog)
                .postId(postId)
                .postTitle("백엔드 개발자 팀원을 모집합니다")
                .clickedRank(1)
                .resultType(SearchLogEntity.SearchType.SEMANTIC)
                .clickedAt(LocalDateTime.now())
                .build();
    }
}
