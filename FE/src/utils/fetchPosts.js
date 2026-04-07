// src/utils/fetchPosts.js
import communityApi from "../services/api/communityApi";

/**
 * 게시글 데이터를 가져오는 함수
 *
 * @param {string} category - 예: "자유"
 * @param {Function} setPosts - 게시글 목록 상태 업데이트 함수
 * @param {string} sortOption - "최신순", "조회순", "인기순" 등
 * @param {number} currentPage - 현재 페이지 (1부터 시작)
 * @param {Function} setTotalPages - 전체 페이지 수 상태 업데이트 함수 (조회순일 때 사용)
 * @param {string} [searchQuery=""] - 제목 검색어
 * @param {string} [tagQuery=""] - 태그 검색어
 */
export const fetchPosts = async (
  category,
  setPosts,
  sortOption,
  currentPage = 1,
  setTotalPages = null,
  searchQuery = "",
  setAiRecommended = null,
  setAiPosts = null, //AI 게시글 목록 상태 추가
  tagQuery = ""
) => {
  console.log(`${category} 게시판 데이터를 불러옵니다...`);
  try {
    let response;
    let posts = [];
    let totalPages = 1; // 기본값

    if (tagQuery.trim()) {
      // 태그 검색 실행
      console.log("🔍 태그 검색 실행:", tagQuery);
      response = await communityApi.searchByTag(tagQuery, currentPage - 1, 5);

      if (response && response.content && response.content.length > 0) {
        posts = response.content;
        totalPages = response.totalPages || 1;
        console.log(`✅ 태그 검색 결과 ${posts.length}개 발견`);
      } else {
        console.log("❌ 태그 검색 결과 없음.");
      }
    } else if (searchQuery.trim()) {
      // 🔹 1. 기본 키워드 검색 실행
      console.log("🔍 키워드 검색 실행:", searchQuery);
      response = await communityApi.searchPosts(searchQuery, currentPage - 1, 5);

      console.log("✅ 키워드 검색 응답 데이터:", response);

      if (response && response.content && response.content.length > 0) {
        // 🔹 일반 검색 결과 업데이트 (AI 데이터 포함 X)
        posts = response.content;
        totalPages = response.totalPages || 1;

        console.log(`✅ 키워드 검색 결과 ${posts.length}개 발견`);
        if (setAiRecommended) setAiRecommended(false);
        setPosts(posts);
        if (setTotalPages) setTotalPages(totalPages);
      } else {
        console.log("⚠️ 키워드 검색 결과 없음, AI 추천 검색 실행...");

        // AI 추천 검색 실행 (AI 데이터는 일반 데이터에 포함하지 않음)
        const aiResponse = await communityApi.searchSemanticPosts(
          searchQuery,
          currentPage - 1,
          5,
          true
        );

        console.log("✅ AI 추천 검색 응답 데이터:", aiResponse);

        if (aiResponse && aiResponse.content && aiResponse.content.length > 0) {
          // AI 검색 결과는 `setAiPosts()`에만 저장 (일반 데이터에는 포함 X)
          const aiPosts = aiResponse.content;
          totalPages = aiResponse.totalPages || 1;

          console.log(`🔥 AI 추천 검색 결과 ${aiPosts.length}개 발견`);
          if (setAiRecommended) setAiRecommended(true);
          if (setAiPosts) {
            setAiPosts(aiPosts); // AI 검색 결과는 여기만 업데이트
            if (setTotalPages) setTotalPages(totalPages);
          }
        } else {
          console.log("❌ AI 검색 결과도 없음. 빈 배열 유지.");
          setAiPosts([]);
          if (setTotalPages) setTotalPages(1);
        }
      }
    } else {
      // 정렬 옵션에 따라 API 호출
      console.log(`[fetchPosts] 요청 - sortOption: ${sortOption}, page: ${currentPage}, size: 7, search: ${searchQuery}, tag: ${tagQuery}`);

      if (sortOption === "조회순") {
        response = await communityApi.getSortedByViews(currentPage - 1, 7);
      } else if (sortOption === "최신순") {
        response = await communityApi.getSortedByNewest(currentPage - 1, 7, category);
      } else if (sortOption === "좋아요순") {
        response = await communityApi.getSortedByLikes(currentPage - 1, 7, category);
      } else if (sortOption === "댓글순") {
        response = await communityApi.getSortedByComments(currentPage - 1, 7, category);
      }

      console.log("[fetchPosts] 조회순 API 응답 데이터:", response);

      if (response && response.content) {
        posts = response.content;
        totalPages = response.totalPages || 1;
      }
    }

    // 상태 업데이트 (AI 데이터와 일반 데이터 분리)
    setPosts(posts);
    if (setTotalPages) setTotalPages(totalPages);
  } catch (error) {
    console.error("📌 게시글 데이터 가져오기 에러:", error);
  }
};