package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostDetailResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentListResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostCategory;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 숨김 게시글·댓글을 제외해 목록과 상세를 읽고 연결 장소 및 댓글 작성자 정보를 조합합니다.
 * 삭제된 장소 연결은 보존된 ID와 삭제 안내로, 조회되지 않는 작성자는 대체 이름으로 반환합니다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private static final String DELETED_PLACE_NAME = "삭제된 장소입니다";

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;
    private final CommunityPostCommentRepository communityPostCommentRepository;
    private final UserRepository userRepository;

    /**
     * 활성 카테고리인지 확인한 뒤 숨김되지 않은 게시글의 ID·제목을 최신 생성 시각·ID 순의 페이지로 반환합니다.
     * 잘못된 카테고리는 거절하며 외부의 1부터 시작하는 페이지를 저장소 조회용 인덱스로 변환합니다.
     */
    @Transactional(readOnly = true)
    public CommunityPostListResponse findByCategory(String categoryId, int page, int limit) {
        CommunityPostCategory.findEnabledById(categoryId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));

        Page<CommunityPostListResponse.Item> result = communityPostRepository.findListItemsByCategoryId(
                categoryId,
                PageRequest.of(page - 1, limit, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                ))
        );

        return new CommunityPostListResponse(
                result.getContent(),
                page,
                limit,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    /**
     * 숨김되지 않은 게시글의 제목·본문과 연결 장소를 반환하고 없는 글은 POST_NOT_FOUND로 거절합니다.
     * 이미 삭제된 장소 연결은 보존된 ID와 삭제 안내를 포함해 반환합니다.
     */
    @Transactional(readOnly = true)
    public CommunityPostDetailResponse findDetail(long postId) {
        CommunityPost post = requirePost(postId);
        List<CommunityPostDetailResponse.Place> places = communityPostPlaceRepository
                .findAllWithMapPlaceByCommunityPostId(postId)
                .stream()
                .map(this::toPlace)
                .toList();

        return new CommunityPostDetailResponse(post.getId(), post.getTitle(), post.getContent(), places);
    }

    /**
     * 공개 게시글의 숨김되지 않은 댓글을 최신순으로 조회하고 작성자를 일괄 조회해 페이지 응답에 결합합니다.
     * 숨김·없는 게시글은 거절하며 조회되지 않는 작성자는 대체 이름으로 표시합니다.
     */
    @Transactional(readOnly = true)
    public CommunityPostCommentListResponse findComments(long postId, int page, int limit) {
        requirePost(postId);
        Page<CommunityPostComment> result = communityPostCommentRepository.findByCommunityPost_IdAndHiddenFalse(
                postId,
                PageRequest.of(page - 1, limit, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                ))
        );
        List<Long> authorIds = result.getContent().stream()
                .map(CommunityPostComment::getUserId)
                .distinct()
                .toList();
        Map<Long, User> usersById = authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, Function.identity()));
        List<CommunityPostCommentListResponse.Item> comments = result.getContent().stream()
                .map(comment -> toCommentItem(comment, usersById.get(comment.getUserId())))
                .toList();

        return new CommunityPostCommentListResponse(
                comments,
                page,
                limit,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private CommunityPost requirePost(long postId) {
        return communityPostRepository.findByIdAndHiddenFalse(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private CommunityPostCommentListResponse.Item toCommentItem(CommunityPostComment comment, User author) {
        return new CommunityPostCommentListResponse.Item(
                comment.getId(),
                comment.getContent(),
                comment.getUserId(),
                author == null ? "알 수 없는 사용자" : author.getUsername(),
                comment.getCreatedAt()
        );
    }

    private CommunityPostDetailResponse.Place toPlace(CommunityPostPlace postPlace) {
        if (postPlace.getMapPlace() == null) {
            return new CommunityPostDetailResponse.Place(postPlace.getMapPlaceId(), DELETED_PLACE_NAME, true);
        }
        return new CommunityPostDetailResponse.Place(
                postPlace.getMapPlace().getId(),
                postPlace.getMapPlace().getName(),
                false
        );
    }
}
