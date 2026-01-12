package com.garret.dreammoa.domain.service.board;

import com.garret.dreammoa.domain.dto.board.requestdto.BoardRequestDto;
import com.garret.dreammoa.domain.dto.board.responsedto.BoardResponseDto;
import com.garret.dreammoa.domain.model.BoardEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BoardService {

    // 작성
    BoardResponseDto createBoard(BoardRequestDto dto);

    // 조회
    BoardResponseDto getBoard(Long postId);
//    List<BoardResponseDto> getBoardList();
    List<BoardResponseDto> getBoardListSortedByViews();

    // 수정
    BoardResponseDto updateBoard(Long postId, BoardRequestDto dto);

    // 삭제
    void deleteBoard(Long postId);

    //전체 게시글 개수 조회
    int getTotalBoardCount();

    Page<BoardResponseDto> getBoardList(Pageable pageable);


    //카테고리별 게시글 개수 조회
    int getBoardCountByCategory(String category);

    BoardResponseDto getBoardDtoFromCache(Long postId);

    int getCommentCountFromCache(Long postId);

    //최신순 정렬 + 페이징
    Page<BoardResponseDto> getBoardListSortedByNewest(Pageable pageable, BoardEntity.Category category);

    //조회수 기준 정렬(내림차순) + 페이징
    Page<BoardResponseDto> getBoardListSortedByViewCount(Pageable pageable, BoardEntity.Category category);

    //좋아요수 기준 정렬(내림차순) + 페이징
    Page<BoardResponseDto> getBoardListSortedByLikeCount(Pageable pageable, BoardEntity.Category category);

    //댓글수 기준 정렬(내림차순) + 페이징
    Page<BoardResponseDto> getBoardListSortedByCommentCount(Pageable pageable, BoardEntity.Category category);

    // 태그 검색
    Page<BoardResponseDto> searchByTag(String tag, Pageable pageable);

    //DB의 실제 게시글 개수로 Redis 카운터를 재초기
    void reinitializeCounters();


}
