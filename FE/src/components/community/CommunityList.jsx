import { useRecoilValue } from "recoil";
import { communityListState } from "../../recoil/atoms/communityState";
import CommunityItem from "./CommunityItem";

export default function CommunityList() {
  const posts = useRecoilValue(communityListState);

  return (
    <div className="space-y-4">
      {posts.length > 0 && posts.map((post) => (
        <CommunityItem key={post.id || post.postId} post={post} />
      ))}

      {posts.length === 0 && (
        <p className="text-center text-gray-500">검색 결과가 없습니다.</p>
      )}
    </div>
  );
}
