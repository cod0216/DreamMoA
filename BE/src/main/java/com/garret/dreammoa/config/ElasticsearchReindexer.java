package com.garret.dreammoa.config;

import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.model.BoardEntity;
import com.garret.dreammoa.domain.repository.BoardRepository;
import com.garret.dreammoa.domain.repository.BoardSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

@Component
@DependsOn("elasticsearchInitializer")
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchReindexer {
    private final BoardRepository boardRepository;
    private final BoardSearchRepository boardSearchRepository;

    /**
     * 애플리케이션 시작 시 DB에 저장된 모든 게시글을 Elasticsearch에 색인합니다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reindexAllBoards() {
        log.info("재동기화 작업 시작: DB의 모든 게시글을 Elasticsearch에 색인합니다.");
        List<BoardEntity> boards = boardRepository.findAll();
        int successCount = 0;
        for (BoardEntity board : boards) {
            try {
                BoardDocument boardDocument = BoardDocument.builder()
                        .id(board.getPostId())
                        .userId(board.getUser().getId())
                        .userNickname(board.getUser().getNickname())
                        .category(board.getCategory().name())
                        .title(board.getTitle())
                        .content(board.getContent())
                        .createdAt(board.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        .updatedAt(board.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        .viewCount(board.getViewCount().intValue())
                        .build();

                // Elasticsearch 색인 작업 수행
                boardSearchRepository.index(boardDocument);
                successCount++;
                log.info("게시글 {} 색인 완료", board.getPostId());
            } catch (Exception e) {
                log.error("게시글 {} 색인 중 오류 발생: {}", board.getPostId(), e.getMessage());
            }
        }
        log.info("재동기화 작업 완료: 총 {}개 게시글 중 {}개 색인 성공", boards.size(), successCount);
    }
}
