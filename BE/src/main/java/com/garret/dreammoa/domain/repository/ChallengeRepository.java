package com.garret.dreammoa.domain.repository;

import com.garret.dreammoa.domain.model.ChallengeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChallengeEntity c WHERE c.challengeId = :challengeId")
    java.util.Optional<ChallengeEntity> findByIdForUpdate(@Param("challengeId") Long challengeId);

    List<ChallengeEntity> findTop20ByStartDateAfterOrderByStartDateAsc(LocalDateTime now);

    // ⏳ 진행 중인 챌린지 조회 (startDate ~ expireDate 사이 + 참가 가능)
    @Query("SELECT c FROM ChallengeEntity c " +
            "WHERE c.id IN :challengeIds " +
            "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.description LIKE %:keyword%) " +
            "AND c.startDate <= :now AND c.expireDate >= :now " +
            "AND SIZE(c.challengeParticipants) < c.maxParticipants " +
            "ORDER BY SIZE(c.challengeParticipants) DESC")
    Page<ChallengeEntity> findRunningChallenges(
            @Param("challengeIds") List<Long> challengeIds,
            @Param("keyword") String keyword,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    // 📢 모집 중인 챌린지 조회 (startDate 이전 + 참가 가능)
    @Query("SELECT c FROM ChallengeEntity c " +
            "WHERE c.id IN :challengeIds " +
            "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.description LIKE %:keyword%) " +
            "AND c.startDate > :now " +
            "AND SIZE(c.challengeParticipants) < c.maxParticipants " +
            "ORDER BY SIZE(c.challengeParticipants) DESC")
    Page<ChallengeEntity> findRecruitingChallenges(
            @Param("challengeIds") List<Long> challengeIds,
            @Param("keyword") String keyword,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    // 🌟 인기 챌린지 조회 (참가자 많은 순)
    @Query("SELECT c FROM ChallengeEntity c " +
            "WHERE c.id IN :challengeIds " +  // 이미 필터링된 challengeIds만 사용
            "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.description LIKE %:keyword%) " +
            "ORDER BY SIZE(c.challengeTags) DESC, SIZE(c.challengeParticipants) DESC")
    Page<ChallengeEntity> findPopularChallenges(
            @Param("challengeIds") List<Long> challengeIds,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT c FROM ChallengeEntity c WHERE c.id IN :challengeIds")
    List<ChallengeEntity> findTagChallenges(@Param("challengeIds") List<Long> challengeIds);

    @Query("SELECT c.id FROM ChallengeEntity c")
    List<Long> findAllChallengeIds(Pageable pageable);

    // 태그 없을 때 전체 챌린지 ID 조회 (페이징 없음)
    @Query("SELECT c.id FROM ChallengeEntity c")
    List<Long> findAllChallengeIds();
}
