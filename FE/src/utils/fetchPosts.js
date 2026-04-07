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
 * @param {Function} setSearchType - 검색 결과 유형 상태 업데이트 함수
 * @param {string} [searchQuery=""] - 제목 검색어
 * @param {string} [tagQuery=""] - 태그 검색어
 */
export const fetchPosts = async (
  category,
  setPosts,
  sortOption,
  currentPage = 1,
  setTotalPages = null,
  setSearchType = null,
  searchQuery = "",
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
        posts = response.content;
        totalPages = response.totalPages || 1;
        if (setSearchType) setSearchType(response.searchType || "KEYWORD");

        console.log(`✅ 키워드 검색 결과 ${posts.length}개 발견`);
        setPosts(posts);
        if (setTotalPages) setTotalPages(totalPages);
      } else {
        console.log("❌ 키워드 검색 결과 없음.");
        if (setSearchType) setSearchType(response?.searchType || null);
        if (setTotalPages) setTotalPages(1);
      }
    } else {
      if (setSearchType) setSearchType(null);
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

    setPosts(posts);
    if (setTotalPages) setTotalPages(totalPages);
  } catch (error) {
    if (setSearchType) setSearchType(null);
    console.error("📌 게시글 데이터 가져오기 에러:", error);
  }
};
