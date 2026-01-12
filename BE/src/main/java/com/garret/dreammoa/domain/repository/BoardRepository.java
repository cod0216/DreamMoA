package com.garret.dreammoa.domain.repository;

import com.garret.dreammoa.domain.model.BoardEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoardRepository extends JpaRepository<BoardEntity, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.viewCount = :viewCount WHERE b.postId = :postId")
    void updateViewCount(Long postId, Long viewCount);

    long countByCategory(BoardEntity.Category category);

    // 최신순 정렬 : 카테고리 필터링 후 생성일자 내림차순 정렬 및 페이징
    Page<BoardEntity> findAllByCategoryOrderByCreatedAtDesc(BoardEntity.Category category, Pageable pageable);

    // category 필드로 필터링한 후, viewCount 내림차순 정렬 및 페이징 처리
    Page<BoardEntity> findAllByCategoryOrderByViewCountDesc(BoardEntity.Category category, Pageable pageable);

    // 좋아요순 정렬 : 카테고리별로 likecount 컬럼 기준 내림차순 정렬 및 페이징
    Page<BoardEntity> findAllByCategoryOrderByLikeCountDesc(BoardEntity.Category category, Pageable pageable);

    // 좋아요 수 증가/감소를 위한 업데이트 메서드
    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.likeCount = b.likeCount + 1 WHERE b.postId = :postId")
    void incrementLikeCount(Long postId);

    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.likeCount = b.likeCount - 1 WHERE b.postId = :postId")
    void decrementLikeCount(Long postId);


    // 댓글순 정렬 : 카테고리별로 commentcount 컬럼 기준 내림차순 정렬
    Page<BoardEntity> findAllByCategoryOrderByCommentCountDesc(BoardEntity.Category category, Pageable pageable);

    // 댓글 수 증가/감소를 위한 업데이트 메서드
    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.commentCount = b.commentCount + 1 WHERE b.postId = :postId")
    void incrementCommentCount(Long postId);

    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.commentCount = b.commentCount - 1 WHERE b.postId = :postId")
    void decrementCommentCount(Long postId);

    // Redis에서 DB로 댓글 개수 동기화할 때 사용하는 메서드
    @Modifying
    @Transactional
    @Query("UPDATE BoardEntity b SET b.commentCount = :commentCount WHERE b.postId = :postId")
    void updateCommentCount(@Param("postId") Long postId, @Param("commentCount") int commentCount);
    List<BoardEntity> findTop20ByOrderByViewCountDesc();

    // ID 리스트를 기반으로 페이징된 게시글 목록 조회
    @Query("SELECT b FROM BoardEntity b WHERE b.postId IN :postIds")
    Page<BoardEntity> findByPostIdIn(@Param("postIds") List<Long> postIds, Pageable pageable);

    @Query("SELECT DISTINCT b FROM BoardEntity b LEFT JOIN FETCH b.boardTags bt LEFT JOIN FETCH bt.tag WHERE b.category = :category ORDER BY b.createdAt DESC")
    Page<BoardEntity> findAllByCategoryWithTags(@Param("category") BoardEntity.Category category, Pageable pageable);

    @Query("select b.postId from BoardEntity b order by b.createdAt desc")
    Page<Long> findPostIdsOrderByCreatedAtDesc(Pageable pageable);

    @Query("select b.postId from BoardEntity b where b.category = :category order by b.createdAt desc")
    Page<Long> findPostIdsByCategoryOrderByCreatedAtDesc(@Param("category") BoardEntity.Category category, Pageable pageable);

    @Query("""
    select distinct b
    from BoardEntity b
    left join fetch b.boardTags bt
    left join fetch bt.tag t
    where b.postId in :ids
    """)
    List<BoardEntity> findAllByPostIdInWithTags(@Param("ids") List<Long> ids);


    // DB의 viewCount 컬럼을 기준으로 내림차순 정렬 및 페이징
//    Page<BoardEntity> findAllByOrderByViewCountDesc(BoardEntity.Category category, Pageable pageable);

    @Query("""
    select distinct b
    from BoardEntity b
    join fetch b.user u
    left join fetch b.boardTags bt
    left join fetch bt.tag t
    where b.postId in :ids
    """)
    List<BoardEntity> findAllByPostIdInWithUserAndTags(@Param("ids") List<Long> ids);

}
