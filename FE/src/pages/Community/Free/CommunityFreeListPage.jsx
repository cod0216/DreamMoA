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

export default function CommunityFreeListPage() {
  const setPosts = useSetRecoilState(communityListState);
  const location = useLocation();
  const navigate = useNavigate();

  // URL에서 검색어, 페이지, 정렬 기준 추출
  const queryParams = new URLSearchParams(location.search);
  const currentPage = Number(queryParams.get("page")) || 1;
  const currentSort = queryParams.get("sort") || "최신순";
  const searchQuery = queryParams.get("search") || "";
  const tagQuery = queryParams.get("tag") || "";

  const [sortOption, setSortOption] = useState(currentSort);
  const [totalPages, setTotalPages] = useState(1);
  const [searchType, setSearchType] = useState(null);

  // 정렬 옵션 변경 시 URL 업데이트
  const handleSortChange = (newSort) => {
    setSortOption(newSort);
    navigate(
      `/community/free?page=1&sort=${newSort}&search=${searchQuery}&size=5`
    );
  };

  // 페이지 변경 시 URL 업데이트
  const handlePageChange = (newPage) => {
    navigate(
      `/community/free?page=${newPage}&sort=${sortOption}&search=${searchQuery}&size=5`
    );
  };

  // fetchPosts() 호출
  useEffect(() => {
    fetchPosts(
      "자유",
      setPosts,
      sortOption,
      currentPage,
      setTotalPages,
      setSearchType,
      searchQuery,
      tagQuery
    );
  }, [sortOption, currentPage, searchQuery, tagQuery]);

  // 검색 실행 시 URL 업데이트
  const handleSearch = (query, tag) => {
    navigate(`/community/free?page=1&sort=${sortOption}&search=${query}&tag=${tag}`);
  };

  return (
    <div className="bg-gray-100 mt-8">
      <div className="max-w-4xl mx-auto p-4 min-h-screen bg-white rounded-2xl">

        <CommunityTab />
        
        <div className="flex justify-between mb-4">
          <h1
            className="text-2xl   cursor-pointer"
            style={{ fontFamily: "mbc" }}
            onClick={() => {
              navigate("/community/free?page=1&sort=최신순", { replace: true }); // ✅ URL 변경
              setTimeout(() => {
                window.location.reload(); // 강제 리렌더링
              }, 100);
            }}
          >
            자유게시판
          </h1>
          <Link
            to="/community/free/write"
            style={{ fontFamily: "mbc" }}
            className="px-4 py-2 bg-my-blue-1 text-white rounded"
          >
            글쓰기
          </Link>
        </div>

        {/* 🔍 검색 바 */}
        <SearchBar onSearch={handleSearch} />

        {/* 정렬 버튼 */}
        <SortButtons sortOption={sortOption} setSortOption={handleSortChange} />

        <CommunityList searchType={searchType} />

        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
        />
      </div>
    </div>
  );
}
