package com.garret.dreammoa.domain.service.search;

import com.garret.dreammoa.domain.model.BoardEntity;
import com.garret.dreammoa.domain.model.SearchClickLogEntity;
import com.garret.dreammoa.domain.model.SearchLogEntity;
import com.garret.dreammoa.domain.model.SearchSynonymCandidateEntity;
import com.garret.dreammoa.domain.repository.BoardRepository;
import com.garret.dreammoa.domain.repository.SearchClickLogRepository;
import com.garret.dreammoa.domain.repository.SearchLogRepository;
import com.garret.dreammoa.domain.repository.SearchSynonymCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchSynonymCandidateService {

    private static final Pattern TERM_PATTERN = Pattern.compile("[가-힣A-Za-z0-9]{2,20}");
    private static final Set<String> STOPWORDS = Set.of(
            "모집", "구함", "구합니다", "정보", "방법", "가능", "가능한가요", "질문", "자유",
            "후기", "정리", "게시판", "참여", "함께", "도전", "프로젝트", "관련", "문의"
    );

    private final SearchClickLogRepository searchClickLogRepository;
    private final SearchLogRepository searchLogRepository;
    private final SearchSynonymCandidateRepository searchSynonymCandidateRepository;
    private final BoardRepository boardRepository;

    @Value("${search.synonym-candidate.reformulation-window-minutes:10}")
    private long reformulationWindowMinutes;

    @Transactional
    public int generateCandidates(LocalDateTime since) throws IOException {
        Set<Set<String>> existingSynonymGroups = loadExistingSynonymGroups();
        Map<CandidateKey, CandidateAggregate> aggregates = new LinkedHashMap<>();

        collectClickEvidence(since, existingSynonymGroups, aggregates);
        collectReformulationEvidence(since, existingSynonymGroups, aggregates);

        return saveQualifiedCandidates(aggregates);
    }

    /**
     * 시그널 1: 키워드 검색 0건 → 의미 기반 검색 → 게시글 클릭
     */
    private void collectClickEvidence(
            LocalDateTime since,
            Set<Set<String>> existingSynonymGroups,
            Map<CandidateKey, CandidateAggregate> aggregates
    ) {
        List<SearchClickLogEntity> semanticClicks = searchClickLogRepository.findSemanticClicksSince(
                since,
                SearchLogEntity.SearchType.SEMANTIC,
                SearchLogEntity.SearchType.SEMANTIC
        );

        Set<Long> previousIds = semanticClicks.stream()
                .map(click -> click.getSearchLog().getPreviousSearchLogId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SearchLogEntity> previousLogs = searchLogRepository.findAllByIdIn(previousIds).stream()
                .collect(Collectors.toMap(SearchLogEntity::getId, log -> log));

        for (SearchClickLogEntity click : semanticClicks) {
            SearchLogEntity semanticLog = click.getSearchLog();
            SearchLogEntity keywordLog = previousLogs.get(semanticLog.getPreviousSearchLogId());
            if (keywordLog == null || keywordLog.getResultCount() > 0) {
                continue;
            }

            String sourceTerm = keywordLog.getNormalizedQuery();
            if (sourceTerm == null || sourceTerm.contains(" ")) {
                continue;
            }

            BoardEntity board = boardRepository.findById(click.getPostId()).orElse(null);
            if (board == null) {
                continue;
            }

            List<String> targetTerms = extractTargetTerms(board);
            for (String targetTerm : targetTerms) {
                if (sourceTerm.equals(targetTerm) || alreadyGrouped(sourceTerm, targetTerm, existingSynonymGroups)) {
                    continue;
                }

                addEvidence(aggregates, sourceTerm, targetTerm, keywordLog.getCategory(),
                        buildActorKey(keywordLog), board.getTitle());
            }
        }
    }

    /**
     * 시그널 2: 같은 세션에서 검색어 A로 0건 → 검색어 B로 재검색해서 결과를 찾음
     */
    private void collectReformulationEvidence(
            LocalDateTime since,
            Set<Set<String>> existingSynonymGroups,
            Map<CandidateKey, CandidateAggregate> aggregates
    ) {
        List<SearchLogEntity> logs = searchLogRepository
                .findBySessionIdIsNotNullAndCreatedAtAfterOrderBySessionIdAscCreatedAtAsc(since);

        Map<String, List<SearchLogEntity>> bySession = new LinkedHashMap<>();
        for (SearchLogEntity log : logs) {
            bySession.computeIfAbsent(log.getSessionId(), ignored -> new ArrayList<>()).add(log);
        }

        for (List<SearchLogEntity> sessionLogs : bySession.values()) {
            for (int i = 0; i < sessionLogs.size() - 1; i++) {
                SearchLogEntity failed = sessionLogs.get(i);
                SearchLogEntity retried = sessionLogs.get(i + 1);

                if (failed.getResultCount() > 0 || retried.getResultCount() <= 0) {
                    continue;
                }

                String sourceTerm = failed.getNormalizedQuery();
                String targetTerm = retried.getNormalizedQuery();
                if (sourceTerm == null || targetTerm == null
                        || sourceTerm.isBlank() || targetTerm.isBlank()
                        || sourceTerm.contains(" ") || targetTerm.contains(" ")
                        || sourceTerm.equals(targetTerm)) {
                    continue;
                }

                if (failed.getCreatedAt() == null || retried.getCreatedAt() == null
                        || Duration.between(failed.getCreatedAt(), retried.getCreatedAt())
                                .compareTo(Duration.ofMinutes(reformulationWindowMinutes)) > 0) {
                    continue;
                }

                if (alreadyGrouped(sourceTerm, targetTerm, existingSynonymGroups)) {
                    continue;
                }

                addEvidence(aggregates, sourceTerm, targetTerm, failed.getCategory(),
                        buildActorKey(failed), "\"" + sourceTerm + "\" → \"" + targetTerm + "\"");
            }
        }
    }

    private void addEvidence(
            Map<CandidateKey, CandidateAggregate> aggregates,
            String sourceTerm, String targetTerm, String category,
            String actorKey, String sampleText
    ) {
        CandidateKey key = new CandidateKey(sourceTerm, targetTerm, category);
        CandidateAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new CandidateAggregate());
        aggregate.evidenceCount++;
        aggregate.userOrSessionKeys.add(actorKey);
        aggregate.sampleTitles.add(sampleText);
    }

    private int saveQualifiedCandidates(Map<CandidateKey, CandidateAggregate> aggregates) {
        int savedCount = 0;
        for (Map.Entry<CandidateKey, CandidateAggregate> entry : aggregates.entrySet()) {
            CandidateKey key = entry.getKey();
            CandidateAggregate aggregate = entry.getValue();
            if (aggregate.evidenceCount < 3 || aggregate.userOrSessionKeys.size() < 2) {
                continue;
            }

            BigDecimal confidence = calculateConfidence(aggregate.evidenceCount, aggregate.userOrSessionKeys.size());
            SearchSynonymCandidateEntity entity = searchSynonymCandidateRepository
                    .findBySourceTermAndTargetTermAndCategory(key.sourceTerm(), key.targetTerm(), key.category())
                    .orElseGet(() -> SearchSynonymCandidateEntity.builder()
                            .sourceTerm(key.sourceTerm())
                            .targetTerm(key.targetTerm())
                            .category(key.category())
                            .status(SearchSynonymCandidateEntity.Status.PENDING)
                            .build());

            entity.setEvidenceCount(aggregate.evidenceCount);
            entity.setDistinctUserCount(aggregate.userOrSessionKeys.size());
            entity.setSampleTitles(String.join("\n", aggregate.sampleTitles.stream().limit(5).toList()));
            entity.setConfidenceScore(confidence);
            searchSynonymCandidateRepository.save(entity);
            savedCount++;
        }

        return savedCount;
    }

    private List<String> extractTargetTerms(BoardEntity board) {
        Set<String> terms = new LinkedHashSet<>();
        terms.addAll(extractTerms(board.getTitle()));
        if (board.getBoardTags() != null) {
            board.getBoardTags().forEach(boardTag -> {
                if (boardTag.getTag() != null && boardTag.getTag().getTagName() != null) {
                    String normalized = normalizeTerm(boardTag.getTag().getTagName());
                    if (normalized != null) {
                        terms.add(normalized);
                    }
                }
            });
        }
        return new ArrayList<>(terms);
    }

    private List<String> extractTerms(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        Matcher matcher = TERM_PATTERN.matcher(text);
        while (matcher.find()) {
            String normalized = normalizeTerm(matcher.group());
            if (normalized != null) {
                results.add(normalized);
            }
        }
        return results;
    }

    private String normalizeTerm(String term) {
        if (term == null) {
            return null;
        }
        String normalized = term.trim().toLowerCase(Locale.ROOT);
        normalized = stripCommonPostposition(normalized);
        if (normalized.length() < 2 || STOPWORDS.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private String stripCommonPostposition(String token) {
        String[] postfixes = {"으로", "에서", "에게", "한테", "과", "와", "을", "를", "이", "가", "은", "는", "의", "에", "로"};
        for (String postfix : postfixes) {
            if (token.length() > postfix.length() + 1 && token.endsWith(postfix)) {
                return token.substring(0, token.length() - postfix.length());
            }
        }
        return token;
    }

    private boolean alreadyGrouped(String left, String right, Set<Set<String>> groups) {
        for (Set<String> group : groups) {
            if (group.contains(left) && group.contains(right)) {
                return true;
            }
        }
        return false;
    }

    private Set<Set<String>> loadExistingSynonymGroups() throws IOException {
        ClassPathResource resource = new ClassPathResource("elasticsearch/board-synonyms.txt");
        if (!resource.exists()) {
            return Set.of();
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .map(line -> Arrays.stream(line.split("\\s*,\\s*"))
                            .map(this::normalizeTerm)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()))
                    .filter(set -> !set.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    private String buildActorKey(SearchLogEntity log) {
        if (log.getUserId() != null) {
            return "USER:" + log.getUserId();
        }
        return "SESSION:" + (log.getSessionId() == null ? "UNKNOWN" : log.getSessionId());
    }

    private BigDecimal calculateConfidence(int evidenceCount, int distinctUserCount) {
        double score = Math.min(0.99, evidenceCount * 0.1 + distinctUserCount * 0.15);
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    record CandidateKey(String sourceTerm, String targetTerm, String category) {}

    static class CandidateAggregate {
        int evidenceCount = 0;
        Set<String> userOrSessionKeys = new HashSet<>();
        Set<String> sampleTitles = new LinkedHashSet<>();
    }
}
