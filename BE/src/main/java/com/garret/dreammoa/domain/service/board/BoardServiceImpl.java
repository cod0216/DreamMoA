package com.garret.dreammoa.domain.service.board;

import com.garret.dreammoa.domain.document.BoardDocument;
import com.garret.dreammoa.domain.dto.board.requestdto.BoardRequestDto;
import com.garret.dreammoa.domain.dto.board.responsedto.BoardResponseDto;
import com.garret.dreammoa.domain.dto.user.CustomUserDetails;
import com.garret.dreammoa.domain.model.*;
import com.garret.dreammoa.domain.repository.*;
import com.garret.dreammoa.domain.service.file.FileService;
import com.garret.dreammoa.domain.service.embedding.EmbeddingService;
import com.garret.dreammoa.domain.service.like.LikeService;
import com.garret.dreammoa.domain.service.tag.TagService;
import com.garret.dreammoa.domain.service.viewcount.ViewCountService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardServiceImpl implements BoardService {

    private final FileService fileService;
    private final ViewCountService viewCountService;
    private final LikeService likeService;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final Logger logger = LoggerFactory.getLogger(BoardServiceImpl.class);
    private final @Qualifier("boardDtoRedisTemplate") RedisTemplate<String, BoardResponseDto> boardDtoRedisTemplate;
    // 문자열 전용 RedisTemplate (댓글 수와 같은 단순 값을 위한 캐싱)
    private final RedisTemplate<String, String> redisTemplate;
    private final BoardSearchRepository boardSearchRepository;
    private final EmbeddingService embeddingService;
    private final TagService tagService;
    private final BoardTagRepository boardTagRepository;
    private final LikeRepository likeRepository;

    @PostConstruct
    public void initializeBoardCount() {
        long totalCount = boardRepository.count();
        redisTemplate.opsForValue().set("board:count", String.valueOf(totalCount));

        long freeCount = boardRepository.countByCategory(BoardEntity.Category.자유);
        long questionCount = boardRepository.countByCategory(BoardEntity.Category.질문);
        redisTemplate.opsForValue().set("board:count:자유", String.valueOf(freeCount));
        redisTemplate.opsForValue().set("board:count:질문", String.valueOf(questionCount));

        logger.info("게시글 카운터 초기화 완료: 전체={}, 자유={}, 질문={}", totalCount, freeCount, questionCount);
    }

    /**
     * CREATE
     */
    @Override
    @Transactional
    public BoardResponseDto createBoard(BoardRequestDto dto) {

        // 작성자 인증 및 사용자 조회
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("사용자가 인증되지 않았습니다.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();


        UserEntity user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("해당 사용자 없음: id=" + userDetails.getId()));

        // 카테고리(문자열 "질문" or "자유") -> Enum 변환
        BoardEntity.Category category = BoardEntity.Category.valueOf(dto.getCategory());

        // 엔티티 생성 (content는 일단 빈 문자열)
        BoardEntity board = BoardEntity.builder()
                .user(user)
                .category(category)
                .title(dto.getTitle())
                .content("")
                .build();

        // 게시글을 먼저 저장하여 postId를 확보 (저장 후엔 board.getPostId()가 생성됨)
        BoardEntity savedBoard = boardRepository.saveAndFlush(board);

        // 태그 저장
        saveTagsForBoard(savedBoard, dto.getTags());

        // 확보된 postId를 사용해 Quill 본문 내의 Base64 이미지를 S3 업로드하고 URL로 치환
        String finalContent = parseAndUploadBase64Images(dto.getContent(), savedBoard.getPostId());

        // 치환된 최종 HTML을 다시 board 객체에 반영한 후 UPDATE
        savedBoard.setContent(finalContent);
        BoardEntity updatedBoard = boardRepository.saveAndFlush(savedBoard);

        // Elasticsearch에 동기화
        syncToElasticsearch(updatedBoard);

        // 트랜잭션 커밋 후 Redis 업데이트
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Long newTotalCount = redisTemplate.opsForValue().increment("board:count", 1);
                logger.debug("전체 게시글 카운터 업데이트 후 새 값: {}", newTotalCount);

                String categoryKey = "board:count:" + updatedBoard.getCategory().name();
                Long newCategoryCount = redisTemplate.opsForValue().increment(categoryKey, 1);
                logger.debug("게시글 생성 후 카테고리별 카운터 업데이트, 키: {}, 새 값: {}", categoryKey, newCategoryCount);
            }
            @Override public void suspend() {}
            @Override public void resume() {}
            @Override public void flush() {}
            @Override public void beforeCommit(boolean readOnly) {}
            @Override public void beforeCompletion() {}
            @Override public void afterCompletion(int status) {}
        });

        return convertToResponseDto(updatedBoard, 0);
    }

    /**
     * Base64 이미지를 S3 URL로 치환하는 메서드 (postId를 사용)
     */
    private String parseAndUploadBase64Images(String originalHtml, Long postId) {
        if (originalHtml == null || originalHtml.trim().isEmpty()) {
            return originalHtml;
        }

        Document doc = Jsoup.parseBodyFragment(originalHtml);
        Elements imgTags = doc.select("img");
        for (Element img : imgTags) {
            String src = img.attr("src");
            if (src != null && src.startsWith("data:image")) {
                String[] parts = src.split(",", 2);
                if (parts.length == 2) {
                    String base64 = parts[1];
                    try {
                        // 실제 postId를 사용하여 S3에 업로드
                        String s3Url = fileService.saveBase64FileS3(base64, postId, FileEntity.RelatedType.POST);
                        img.attr("src", s3Url);
                    } catch (Exception e) {
                        log.error("S3 업로드 실패: ", e);
                    }
                }
            }
        }
        return doc.body().html();
    }

    private void saveTagsForBoard(BoardEntity board, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        List<TagEntity> tags = tagService.getOrCreateTags(tagNames);

        List<BoardTagEntity> boardTags = tags.stream()
                .map(tag -> BoardTagEntity.builder().board(board).tag(tag).build())
                .collect(Collectors.toList());

        boardTagRepository.saveAll(boardTags);
    }

    /**
     * UPDATE
     */
    @Override
    public BoardResponseDto updateBoard(Long postId, BoardRequestDto dto) {
        BoardEntity board = boardRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글이 존재하지 않습니다. id=" + postId));

        Long currentUserId = getCurrentUserId();
        if (!board.getUser().getId().equals(currentUserId)) {
            throw new RuntimeException("본인이 작성한 글만 수정할 수 있습니다.");
        }

        if (dto.getTitle() != null) {
            board.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            String finalContent = parseAndUploadBase64Images(dto.getContent());
            log.debug("▶ updateBoard() 최종 치환된 content 길이: {}",
                    finalContent != null ? finalContent.length() : 0);

            board.setContent(finalContent);
        }

        // 기존 태그를 유지하면서 추가/삭제 반영
        updateTagsForBoard(board, dto.getTags());

        BoardEntity updated = boardRepository.save(board);

        // Elasticsearch에 동기화
        syncToElasticsearch(updated);

        // 캐시 삭제
        String cacheKey = "board:" + postId;
        boardDtoRedisTemplate.delete(cacheKey);

        int viewCount = viewCountService.getViewCount(postId);
        return convertToResponseDto(updated, viewCount);
    }

    //기존 태그를 유지하면서 추가/삭제 반영하는 메서드
    private void updateTagsForBoard(BoardEntity board, List<String> newTagNames) {
        if (newTagNames == null) {
            newTagNames = List.of(); // Null 방지
        }

        List<String> newTagNamesList = new ArrayList<>(newTagNames);

        // 현재 게시글에 연결된 태그 리스트 가져오기
        List<BoardTagEntity> existingBoardTags = boardTagRepository.findByBoard(board);
        List<String> existingTagNames = existingBoardTags.stream()
                .map(bt -> bt.getTag().getTagName())
                .collect(Collectors.toList());

        // 추가해야 할 태그 찾기 (기존에 없던 태그만 추가)
        List<String> tagsToAdd = newTagNames.stream()
                .filter(tag -> !existingTagNames.contains(tag))
                .collect(Collectors.toList());

        // 삭제해야 할 태그 찾기 (새로운 태그 목록에 없는 기존 태그 삭제)
        List<BoardTagEntity> tagsToRemove = existingBoardTags.stream()
                .filter(bt -> !newTagNamesList.contains(bt.getTag().getTagName()))
                .collect(Collectors.toList());

        // 태그 추가
        saveTagsForBoard(board, tagsToAdd);

        // 태그 삭제
        boardTagRepository.deleteAll(tagsToRemove);
    }

    //==============================================================================
    /**
     * base64 -> URL 치환 (에디터 HTML에서 <img src="data:image/...">를 찾아 업로드)
     */
    private String parseAndUploadBase64Images(String originalHtml) {
        if (originalHtml == null || originalHtml.trim().isEmpty()) {
            return originalHtml;
        }

        Document doc = Jsoup.parseBodyFragment(originalHtml);
        Elements imgTags = doc.select("img");
        for (Element img : imgTags) {
            String src = img.attr("src");
            if (src != null && src.startsWith("data:image")) {
                String[] parts = src.split(",", 2);
                if (parts.length == 2) {
                    String base64 = parts[1];
                    try {
                        // **여기서 fileService.saveBase64FileS3(...) 호출**
                        String s3Url = fileService.saveBase64FileS3(base64, 0L, FileEntity.RelatedType.POST);
                        // 치환
                        img.attr("src", s3Url);
                    } catch (Exception e) {
                        log.error("S3 업로드 실패: ", e);
                    }
                }
            }
        }
        return doc.body().html();
    }
    //==============================================================================

    /**
     * 게시글 상세조회
     */
    @Override
    public BoardResponseDto getBoard(Long postId) {
        BoardResponseDto dto = getBoardDtoFromCache(postId);
        int commentCount = getCommentCountFromCache(postId);
        dto.setCommentCount(commentCount);

        int updatedViewCount = viewCountService.getViewCount(postId);
        dto.setViewCount(updatedViewCount);

        return dto;
    }

    /**
     * 게시글 전체 조회
     */
    @Override
    public Page<BoardResponseDto> getBoardList(Pageable pageable) {

        // 1) 페이징은 ID만 가져오기 (정렬/페이지 정확)
        Page<Long> idPage = boardRepository.findPostIdsOrderByCreatedAtDesc(pageable);
        List<Long> ids = idPage.getContent();

        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2) ID 목록 기준으로 user + tags를 fetch join으로 한 번에 로딩
        List<BoardEntity> boards = boardRepository.findAllByPostIdInWithUserAndTags(ids);

        // IN 조회는 DB가 순서를 보장하지 않으므로, 원래 ids 순서로 메모리 정렬
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            orderMap.put(ids.get(i), i);
        }
        boards.sort(Comparator.comparingInt(b -> orderMap.getOrDefault(b.getPostId(), Integer.MAX_VALUE)));

        // DTO 변환 (tags/user는 이미 로딩되어 있으므로 추가 쿼리 없이 처리 가능)
        List<BoardResponseDto> content = boards.stream()
                .map(this::convertToResponseDtoLoaded)
                .toList();

        // totalElements는 idPage에서 가져와야 정확합니다.
        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    @Override
    public List<BoardResponseDto> getBoardListSortedByViews() {
        List<BoardEntity> list = boardRepository.findAll();
        return list.stream()
                .map(board -> {
                    int viewCount = viewCountService.getViewCount(board.getPostId());
                    int commentCount = getCommentCountFromCache(board.getPostId());
                    return convertToResponseDto(board, viewCount, commentCount);
                })
                .sorted((a, b) -> Integer.compare(b.getViewCount(), a.getViewCount()))
                .collect(Collectors.toList());
    }

    /**
     * DELETE
     */
    @Override
    public void deleteBoard(Long postId) {
        BoardEntity board = boardRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글이 존재하지 않습니다. id=" + postId));

        Long currentUserId = getCurrentUserId();
        if (!board.getUser().getId().equals(currentUserId)) {
            throw new RuntimeException("본인이 작성한 글만 삭제할 수 있습니다.");
        }

        commentRepository.deleteChildCommentsByBoard(postId);

        commentRepository.deleteByBoard(board);

        likeRepository.deleteByBoard(board);

        // Elasticsearch에서 해당 게시글 삭제
        boardSearchRepository.deleteByDocumentId(postId);

        boardRepository.delete(board);

        redisTemplate.opsForValue().decrement("board:count", 1);
        String categoryKey = "board:count:" + board.getCategory().name();
        redisTemplate.opsForValue().decrement(categoryKey, 1);
    }

    //==============================================================================
    @Override
    public int getTotalBoardCount() {
        String countStr = redisTemplate.opsForValue().get("board:count");
        return (countStr != null) ? Integer.parseInt(countStr) : 0;
    }

    @Override
    public int getBoardCountByCategory(String category) {
        String key = "board:count:" + category;
        String countStr = redisTemplate.opsForValue().get(key);
        return (countStr != null) ? Integer.parseInt(countStr) : 0;
    }

    @Override
    public BoardResponseDto getBoardDtoFromCache(Long postId) {
        String key = "board:" + postId;
        BoardResponseDto cachedDto = boardDtoRedisTemplate.opsForValue().get(key);
        if (cachedDto != null) {
            log.info("📌 Redis에서 게시글 DTO (postId={}) 를 가져옴", postId);
            return cachedDto;
        }
        BoardEntity boardEntity = boardRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("❌ 게시글이 존재하지 않습니다. postId=" + postId));

        int viewCount = viewCountService.getViewCount(postId);
        BoardResponseDto dto = convertToResponseDto(boardEntity, viewCount, 0);
        boardDtoRedisTemplate.opsForValue().set(key, dto);
        boardDtoRedisTemplate.expire(key, 10, TimeUnit.MINUTES);
        return dto;
    }

    public int getCommentCountFromCache(Long postId) {
        String key = "commentCount:" + postId;
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr != null) {
            try {
                return Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                // 파싱 실패 시 DB에서 재계산
            }
        }
        BoardEntity board = boardRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글이 존재하지 않습니다."));
        int count = commentRepository.countByBoard(board);
        redisTemplate.opsForValue().set(key, String.valueOf(count));
        redisTemplate.expire(key, 5, TimeUnit.MINUTES);
        return count;
    }

    @Override
    public Page<BoardResponseDto> getBoardListSortedByNewest(Pageable pageable, BoardEntity.Category category) {
        // Repository에서 카테고리 필터링 후 생성일자(createdAt) 내림차순 정렬 및 페이징 처리
//        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByCreatedAtDesc(category, pageable);

        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryWithTags(category, pageable);

        return boardPage.map(board -> {
            int viewCount = board.getViewCount().intValue();
            int likeCount = likeService.getLikeCount(board.getPostId());

            // ✅ 여기서는 이미 태그를 가져왔으므로 `boardTagRepository`를 호출할 필요 없음!
            List<String> tags = board.getBoardTags().stream()
                    .map(bt -> bt.getTag().getTagName())
                    .collect(Collectors.toList());

            // BoardEntity -> BoardResponseDto 변환
//        Page<BoardResponseDto> dtoPage = boardPage.map(board -> {
            // 만약 DB의 viewCount가 최신값이라고 가정 (또는 필요 시 viewCountService를 호출)
//            int viewCount = board.getViewCount().intValue();
            return BoardResponseDto.builder()
                    .postId(board.getPostId())
                    .userId(board.getUser().getId())
                    .userNickname(board.getUser().getNickname())
                    .category(board.getCategory())
                    .title(board.getTitle())
                    .content(board.getContent())
                    .createdAt(board.getCreatedAt())
                    .updatedAt(board.getUpdatedAt())
                    .viewCount(viewCount)
                    .likeCount(likeService.getLikeCount(board.getPostId()))
                    .commentCount(0)  // 댓글 수 처리는 필요 시 구현
                    .tags(tags)
                    .build();
        });
//        return dtoPage;
    }

    @Override
    public Page<BoardResponseDto> getBoardListSortedByViewCount(Pageable pageable, BoardEntity.Category category) {
        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByViewCountDesc(category, pageable);

        return boardPage.map(board -> {
            int viewCount = board.getViewCount().intValue();

            // ✅ 태그 리스트 가져오기
            List<String> tags = board.getBoardTags().stream()
                    .map(bt -> bt.getTag().getTagName())
                    .collect(Collectors.toList());

            return BoardResponseDto.builder()
                    .postId(board.getPostId())
                    .userId(board.getUser().getId())
                    .userNickname(board.getUser().getNickname())
                    .category(board.getCategory())
                    .title(board.getTitle())
                    .content(board.getContent())
                    .createdAt(board.getCreatedAt())
                    .updatedAt(board.getUpdatedAt())
                    .viewCount(viewCount)
                    .likeCount(likeService.getLikeCount(board.getPostId()))
                    .commentCount(0)
                    .tags(tags) // ✅ 태그 추가됨!
                    .build();
        });
    }

    @Override
    public Page<BoardResponseDto> getBoardListSortedByLikeCount(Pageable pageable, BoardEntity.Category category) {
        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByLikeCountDesc(category, pageable);

        return boardPage.map(board -> {
            // ✅ 태그 리스트 가져오기
            List<String> tags = board.getBoardTags().stream()
                    .map(bt -> bt.getTag().getTagName())
                    .collect(Collectors.toList());

            return BoardResponseDto.builder()
                    .postId(board.getPostId())
                    .userId(board.getUser().getId())
                    .userNickname(board.getUser().getNickname())
                    .category(board.getCategory())
                    .title(board.getTitle())
                    .content(board.getContent())
                    .createdAt(board.getCreatedAt())
                    .updatedAt(board.getUpdatedAt())
                    .viewCount(board.getViewCount().intValue())
                    .likeCount(board.getLikeCount())
                    .commentCount(0)
                    .tags(tags) // ✅ 태그 추가됨!
                    .build();
        });
    }

    @Override
    public Page<BoardResponseDto> getBoardListSortedByCommentCount(Pageable pageable, BoardEntity.Category category) {
        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByCommentCountDesc(category, pageable);

        return boardPage.map(board -> {
            // ✅ 태그 리스트 가져오기
            List<String> tags = board.getBoardTags().stream()
                    .map(bt -> bt.getTag().getTagName())
                    .collect(Collectors.toList());

            return BoardResponseDto.builder()
                    .postId(board.getPostId())
                    .userId(board.getUser().getId())
                    .userNickname(board.getUser().getNickname())
                    .category(board.getCategory())
                    .title(board.getTitle())
                    .content(board.getContent())
                    .createdAt(board.getCreatedAt())
                    .updatedAt(board.getUpdatedAt())
                    .viewCount(board.getViewCount().intValue())
                    .likeCount(board.getLikeCount())
                    .commentCount(board.getCommentCount())
                    .tags(tags) // ✅ 태그 추가됨!
                    .build();
        });
    }

//    @Override
//    public Page<BoardResponseDto> getBoardListSortedByViewCount(Pageable pageable, BoardEntity.Category category) {
//        // 올바른 Repository 메서드를 호출합니다.
//        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByViewCountDesc(category, pageable);
//
//        Page<BoardResponseDto> dtoPage = boardPage.map(board -> {
//            int viewCount = board.getViewCount().intValue();
//            return BoardResponseDto.builder()
//                    .postId(board.getPostId())
//                    .userId(board.getUser().getId())
//                    .userNickname(board.getUser().getNickname())
//                    .category(board.getCategory())
//                    .title(board.getTitle())
//                    .content(board.getContent())
//                    .createdAt(board.getCreatedAt())
//                    .updatedAt(board.getUpdatedAt())
//                    .viewCount(viewCount)
//                    .likeCount(likeService.getLikeCount(board.getPostId()))
//                    .commentCount(0) // 댓글 수 처리는 필요에 따라 구현
//                    .build();
//        });
//        return dtoPage;
//    }

//    @Override
//    public Page<BoardResponseDto> getBoardListSortedByLikeCount(Pageable pageable, BoardEntity.Category category) {
//        // DB에서 해당 카테고리의 게시글을 좋아요 수(likeCount) 기준 내림차순 정렬 및 페이징 처리
//        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByLikeCountDesc(category, pageable);
//
//        Page<BoardResponseDto> dtoPage = boardPage.map(board -> {
//            return BoardResponseDto.builder()
//                    .postId(board.getPostId())
//                    .userId(board.getUser().getId())
//                    .userNickname(board.getUser().getNickname())
//                    .category(board.getCategory())
//                    .title(board.getTitle())
//                    .content(board.getContent())
//                    .createdAt(board.getCreatedAt())
//                    .updatedAt(board.getUpdatedAt())
//                    .viewCount(board.getViewCount().intValue())
//                    .likeCount(board.getLikeCount())  // DB 컬럼의 likeCount 사용
//                    .commentCount(0)  // 댓글 수는 필요에 따라 구현
//                    .build();
//        });
//        return dtoPage;
//    }
//
//    @Override
//    public Page<BoardResponseDto> getBoardListSortedByCommentCount(Pageable pageable, BoardEntity.Category category) {
//        // DB에서 commentCount를 기준으로 정렬하여 페이징 처리
//        Page<BoardEntity> boardPage = boardRepository.findAllByCategoryOrderByCommentCountDesc(category, pageable);
//
//        Page<BoardResponseDto> dtoPage = boardPage.map(board -> {
//            return BoardResponseDto.builder()
//                    .postId(board.getPostId())
//                    .userId(board.getUser().getId())
//                    .userNickname(board.getUser().getNickname())
//                    .category(board.getCategory())
//                    .title(board.getTitle())
//                    .content(board.getContent())
//                    .createdAt(board.getCreatedAt())
//                    .updatedAt(board.getUpdatedAt())
//                    .viewCount(board.getViewCount().intValue())
//                    .likeCount(board.getLikeCount())
//                    .commentCount(board.getCommentCount())
//                    .build();
//        });
//        return dtoPage;
//    }

    @Override
    public Page<BoardResponseDto> searchByTag(String tag, Pageable pageable) {
        //태그가 포함된 게시글 id 리스트 조회
        List<Long> boardIds = boardTagRepository.findBoardIdsByTagName(tag);

        // ID가 없으면 빈 페이지 반환
        if (boardIds.isEmpty()) {
            return Page.empty(pageable);
        }

        //해당 id를 가진 게시글을 페이지네이션 처리하여 조회
        Page<BoardEntity> boardPage = boardRepository.findByPostIdIn(boardIds, pageable);

        //BoardEntity -> BoardResponseDto 변화 후 반환
        return boardPage.map(board -> convertToResponseDto(board, viewCountService.getViewCount(board.getPostId())));

    }


    @Override
    public void reinitializeCounters() {
        long totalCount = boardRepository.count();
        redisTemplate.opsForValue().set("board:count", String.valueOf(totalCount));

        long freeCount = boardRepository.countByCategory(BoardEntity.Category.자유);
        long questionCount = boardRepository.countByCategory(BoardEntity.Category.질문);

        try {
            String freeKey = "board:count:" + URLEncoder.encode(BoardEntity.Category.자유.name(), StandardCharsets.UTF_8);
            String questionKey = "board:count:" + URLEncoder.encode(BoardEntity.Category.질문.name(), StandardCharsets.UTF_8);
            redisTemplate.opsForValue().set(freeKey, String.valueOf(freeCount));
            redisTemplate.opsForValue().set(questionKey, String.valueOf(questionCount));

            logger.info("Redis 카운터 재초기화 완료: 전체={}, 자유={}, 질문={}", totalCount, freeCount, questionCount);
        } catch (Exception e) {
            logger.error("Redis 카운터 재초기화 중 오류 발생", e);
        }
    }

    /**
     * Elasticsearch 동기화 (MySQL -> Elasticsearch)
     */
    private void syncToElasticsearch(BoardEntity board) {
        try {
            // 게시글 제목과 내용을 결합하여 임베딩 계산
            String textForEmbedding = board.getTitle() + " " + board.getContent();
            float[] embedding = embeddingService.getEmbedding(textForEmbedding);

            // 임베딩 계산이 실패했거나 빈 배열이면 기본 384차원 0.0 배열 사용 (Java에서는 new float[384]가 0.0으로 초기화됨)
            if (embedding == null || embedding.length == 0) {
                embedding = new float[384];
            }

            // float[]를 List<Double>로 변환 (JSON 직렬화를 위해)
            List<Double> embeddingList = new ArrayList<>();
            for (float value : embedding) {
                embeddingList.add((double) value);
            }

            // BoardDocument 객체에 임베딩 필드 추가
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
                    .embedding(embeddingList)  // ← 임베딩 값 추가 (기본값도 포함됨)
                    .build();

            boardSearchRepository.index(boardDocument);
            log.info("✅ Elasticsearch 동기화 완료 - 게시글 ID: {}", board.getPostId());
        } catch (Exception e) {
            log.error("❌ Elasticsearch 동기화 중 오류 발생: ", e);
        }
    }




    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("사용자가 인증되지 않았습니다.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }

    /**
     * BoardEntity -> BoardResponseDto 변환
     */
    private BoardResponseDto convertToResponseDto(BoardEntity board, int viewCount) {
        return convertToResponseDto(board, viewCount, 0);
    }

    private BoardResponseDto convertToResponseDto(BoardEntity board, int viewCount, int commentCount) {
        //해당 게시글에 연결된 태그 목록 가져오기
        List<String> tags = boardTagRepository.findByBoard(board).stream()
                .map(boardTag -> boardTag.getTag().getTagName()) //태그 이름 리스트 변환
                .collect(Collectors.toList());

        return BoardResponseDto.builder()
                .postId(board.getPostId())
                .userId(board.getUser().getId())
                .userNickname(board.getUser().getNickname())
                .category(board.getCategory())
                .title(board.getTitle())
                .content(board.getContent())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .viewCount(viewCount)
                .commentCount(commentCount)
                .tags(tags)
                .build();
    }

    private BoardResponseDto convertToResponseDtoLoaded(BoardEntity board) {
        List<String> tags = board.getBoardTags().stream()
                .map(bt -> bt.getTag().getTagName())
                .toList();

        return BoardResponseDto.builder()
                .postId(board.getPostId())
                .userId(board.getUser().getId())
                .userNickname(board.getUser().getNickname())
                .category(board.getCategory())
                .title(board.getTitle())
                .content(board.getContent())
                .createdAt(board.getCreatedAt())
                .updatedAt(board.getUpdatedAt())
                .viewCount(board.getViewCount().intValue())      //  DB 컬럼 사용 (Redis N회 호출 방지)
                .likeCount(board.getLikeCount())                 //  DB 컬럼 사용
                .commentCount(board.getCommentCount())           //  DB 컬럼 사용
                .tags(tags)
                .build();
    }


}
