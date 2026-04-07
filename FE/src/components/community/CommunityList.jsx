import { useState } from "react";
import { useRecoilValue } from "recoil";
import { communityListState } from "../../recoil/atoms/communityState";
import CommunityItem from "./CommunityItem";
import Pagination from "./Pagination";

export default function CommunityList({ aiRecommended, aiPosts = [] }) {
  const posts = useRecoilValue(communityListState);
  const [aiPage, setAiPage] = useState(1);
  const aiPageSize = 5;

  // ✅ AI 추천 게시글에서 중복 제거 (이미 일반 게시글에 있는 데이터는 제외)
  const displayedAiPosts = aiPosts.slice((aiPage - 1) * aiPageSize, aiPage * aiPageSize);


  return (
    <div className="space-y-4">
      {/* 일반 게시글 (AI 추천과 중복 X) */}
      {posts.length > 0 && !aiRecommended && posts.map((post) => (
        <CommunityItem key={post.id || post.postId} post={post} />
      ))}

      {/* 검색 결과 없음 문구 (AI 추천 전에 표시) */}
      {posts.length === 0 && aiRecommended && (
        <p className="text-center text-gray-600 text-lg font-semibold mt-10 mb-10">
          ❌ 검색 결과가 없습니다. 대신 AI 추천 게시글을 보여드립니다.
        </p>
      )}

      {/* AI 추천 게시글 (일반 게시글과 별도로 렌더링) */}
      {aiRecommended && displayedAiPosts.length > 0 && (
        <div className="border border-yellow-200 p-4 rounded-lg bg-yellow-50">
          <p className="text-yellow-600 font-semibold text-lg mt-2 mb-2">✨ AI 기반 추천글</p>
          {displayedAiPosts.map((post) => (
            <CommunityItem key={post.id || post.postId} post={post} />
          ))}
          {Math.ceil(aiPosts.length / aiPageSize) > 1 && (
            <Pagination 
              currentPage={aiPage} 
              totalPages={Math.ceil(aiPosts.length / aiPageSize)} 
              onPageChange={setAiPage} 
            />
          )}
        </div>
      )}

      {/* 검색 결과 없을 때 */}
      {posts.length === 0 && !aiRecommended && (
        <p className="text-center text-gray-500">검색 결과가 없습니다.</p>
      )}
    </div>
  );
}
