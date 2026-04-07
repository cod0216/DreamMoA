import { useRecoilValue } from "recoil";
import { communityListState } from "../../recoil/atoms/communityState";
import CommunityItem from "./CommunityItem";

export default function CommunityList({ searchType = null }) {
  const posts = useRecoilValue(communityListState);

  return (
    <div className="space-y-4">
      {searchType === "RELATED" && posts.length > 0 && (
        <p className="text-center text-gray-600 text-lg font-semibold mt-10 mb-10">
          🔎 검색 결과가 없어 연관 검색 결과를 보여드립니다.
        </p>
      )}

      {posts.length > 0 && posts.map((post) => (
        <CommunityItem key={post.id || post.postId} post={post} />
      ))}

      {posts.length === 0 && (
        <p className="text-center text-gray-500">검색 결과가 없습니다.</p>
      )}
    </div>
  );
}
