import { useEffect, useState } from "react";
import { useSetRecoilState } from "recoil";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { communityListState } from "../../../recoil/atoms/communityState";
import CommunityList from "../../../components/community/CommunityList";
import SearchBar from "../../../components/community/SearchBar";
import SortButtons from "../../../components/community/SortButtons";
import Pagination from "../../../components/community/Pagination";
import { fetchPosts } from "../../../utils/fetchPosts";
import CommunityTab from "../../../components/community/CommunityTab";

export default function CommunityQnAListPage() {
  const setPosts = useSetRecoilState(communityListState);
  const location = useLocation();
  const navigate = useNavigate();

  // 현재 URL에서 `page`와 `sortOption` 값을 가져옴 (기본값: 최신순, 1페이지)
  const queryParams = new URLSearchParams(location.search);
  const currentPage = Number(queryParams.get("page")) || 1;
  const currentSort = queryParams.get("sort") || "최신순";
  const searchQuery = queryParams.get("search") || "";
  const tagQuery = queryParams.get("tag") || "";

  const [sortOption, setSortOption] = useState(currentSort);
  const [totalPages, setTotalPages] = useState(1);
  const [aiRecommended, setAiRecommended] = useState(false); // AI 추천 여부 상태 추가
  const [aiPosts, setAiPosts] = useState([]); // AI 추천 게시글 상태 추가
  const [keywordSearchLogId, setKeywordSearchLogId] = useState(null);
  const [semanticSearchLogId, setSemanticSearchLogId] = useState(null);

  console.log(
    "📌 현재 URL에서 가져온 페이지 번호:",
    currentPage,
    "정렬 기준:",
    sortOption
  ); // 디버깅 로그 추가

  // 정렬 옵션이 변경되면 `URL` 업데이트
  const handleSortChange = (newSort) => {
    setSortOption(newSort);
    navigate(`/community/qna?page=1&sort=${newSort}&search=${searchQuery}&size=5`); // 정렬 변경 시 1페이지로 이동
  };

  // 페이지 변경 시 `URL` 업데이트
  const handlePageChange = (newPage) => {
    console.log("📌 페이지 변경:", newPage, "현재 정렬:", sortOption); // 디버깅 로그 추가
    navigate(`/community/qna?page=${newPage}&sort=${sortOption}&search=${searchQuery}&size=5`);
  };

  // `sortOption`이나 `currentPage`가 변경될 때 API 호출
  useEffect(() => {
    console.log(
      "📌 useEffect 실행됨 - 페이지:",
      currentPage,
      "정렬 기준:",
      sortOption
    );
    fetchPosts(
      "질문",
      setPosts,
      sortOption,
      currentPage,
      setTotalPages,
      searchQuery,
      setAiRecommended, // AI 추천 여부 전달
      setAiPosts,
      setKeywordSearchLogId,
      setSemanticSearchLogId,
      tagQuery
    );
  }, [sortOption, currentPage, searchQuery, tagQuery]); // currentPage 의존성 추가

  // 검색 실행 시 URL 업데이트
  const handleSearch = (query, tag) => {
    navigate(`/community/qna?page=1&sort=${sortOption}&search=${query}&tag=${tag}`);
  };

  return (
    <div className="bg-gray-100 mt-8">
      <div className="max-w-4xl mx-auto p-4 min-h-screen bg-white rounded-2xl ">
      <CommunityTab />
        <div className="flex justify-between mb-4 ">
        <h1
            className="text-2xl  cursor-pointer"
            onClick={() => {
              navigate("/community/qna?page=1&sort=최신순", { replace: true }); // ✅ URL 변경
              setTimeout(() => {
                window.location.reload(); // 강제 리렌더링
              }, 100);
            }}
          >
            질문/답변 게시판
          </h1>
          <Link
            to="/community/qna/write"
            className="px-4 py-2 bg-my-blue-1 text-white rounded"
          >
            글쓰기
          </Link>
        </div>

        {/* 🔍 검색 바 */}
        <SearchBar onSearch={handleSearch} />

        {/* ✅ 정렬 버튼 (정렬 옵션 변경 시 `handleSortChange` 실행) */}
        <SortButtons sortOption={sortOption} setSortOption={handleSortChange} />

        <CommunityList
          sortOption={sortOption}
          aiRecommended={aiRecommended}
          aiPosts={aiPosts}
          keywordSearchLogId={keywordSearchLogId}
          semanticSearchLogId={semanticSearchLogId}
          searchQuery={searchQuery}
        />

        {/* Pagination에서 onPageChange를 `handlePageChange`로 전달 */}
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
        />
      </div>
    </div>
  );
}
