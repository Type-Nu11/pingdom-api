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
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
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
 * 숨김 게시글·댓글을 제외해 목록과 상세를 읽고 연결 장소 및 댓글 작성자 정보를 조합.
 * 삭제된 장소 연결은 보존된 ID와 삭제 안내로, 조회되지 않는 작성자는 대체 이름으로 반환.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private static final String DELETED_PLACE_NAME = "삭제된 장소입니다";

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;
    private final CommunityPostCommentRepository communityPostCommentRepository;
    private final UserRepository userRepository;
    private final CommunityPostImageRepository imageRepository;

    private final PlaceAdministrativeRegionRepository regionRepository;

    /**
     * 활성 카테고리인지 확인한 뒤 숨김되지 않은 게시글의 ID·제목을 최신 생성 시각·ID 순의 페이지로 반환.
     * 잘못된 카테고리는 거절하며 외부의 1부터 시작하는 페이지를 저장소 조회용 인덱스로 변환.
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
                enrich(result.getContent()),
                page,
                limit,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    /**
     * 숨김되지 않은 게시글의 제목·본문과 연결 장소를 반환하고 없는 글은 POST_NOT_FOUND로 거절.
     * 이미 삭제된 장소 연결은 보존된 ID와 삭제 안내를 포함해 반환.
     */
    @Transactional
    public CommunityPostDetailResponse findDetail(long postId) {
        // 원자적 증가 후 조회하므로 동시 상세 요청의 조회수 갱신이 유실되지 않습니다.
        communityPostRepository.incrementViewCount(postId);
        CommunityPost post = requirePost(postId);
        List<CommunityPostPlace> linkedPlaces = communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(postId);
        List<String> regionCodes = linkedPlaces.stream().filter(link -> link.getMapPlace() != null)
                .map(link -> link.getMapPlace().getRegionCode()).filter(java.util.Objects::nonNull).distinct().toList();
        Map<String, String> regionNames = regionCodes.isEmpty() ? Map.of()
                : regionRepository.findAllById(regionCodes).stream().collect(java.util.stream.Collectors.toMap(
                    PlaceAdministrativeRegion::getCode,
                    PlaceAdministrativeRegion::getRegionName));
        List<CommunityPostDetailResponse.Place> places = linkedPlaces.stream()
                .map(link -> toPlace(link, regionNames)).toList();

        User author = post.getUserId() == null ? null : userRepository.findById(post.getUserId()).orElse(null);
        CommunityPostCategory category = CommunityPostCategory.findEnabledById(post.getCategoryId()).orElse(null);
        return new CommunityPostDetailResponse(post.getId(), post.getTitle(), post.getContent(), places,
                toAuthor(post.getUserId(), author),
                category == null ? null : new CommunityPostDetailResponse.Category(category.getId(), category.getDisplayName()),
                post.getCreatedAt(), imageRepository.findByPostIdOrderByPositionAsc(postId).stream()
                        .map(image -> new CommunityPostDetailResponse.Image(image.getId(), image.getImageUrl(), image.getPosition()))
                        .toList(), post.getCountryCode(), post.getRegion(), post.getViewCount());
    }

    @Transactional(readOnly = true)
    public CommunityPostListResponse findPosts(String categoryId, String countryCode, int page, int limit) {
        if (categoryId != null) CommunityPostCategory.findEnabledById(categoryId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));
        CommunityCountryCodes.validate(countryCode);
        Page<CommunityPostListResponse.Item> result = communityPostRepository.findListItems(categoryId, countryCode,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new CommunityPostListResponse(enrich(result.getContent()), page, limit,
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    private List<CommunityPostListResponse.Item> enrich(List<CommunityPostListResponse.Item> items) {
        if (items.isEmpty()) return items;
        Map<Long, CommunityPostRepository.Summary> summaries = communityPostRepository
                .findSummaries(items.stream().map(CommunityPostListResponse.Item::postId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(CommunityPostRepository.Summary::getPostId, Function.identity()));
        return items.stream().map(item -> {
            var summary = summaries.get(item.postId());
            if (summary == null) return item;
            var category = CommunityPostCategory.findEnabledById(summary.getCategoryId()).orElse(null);
            return new CommunityPostListResponse.Item(item.postId(), item.title(), summary.getContentPreview(),
                    summary.getImageUrl(), summary.getImageCount(), summary.getCountryCode(), summary.getRegion(),
                    summary.getCreatedAt(), summary.getViewCount(), summary.getLikeCount(), summary.getCommentCount(),
                    category == null ? null : new CommunityPostDetailResponse.Category(category.getId(), category.getDisplayName()));
        }).toList();
    }

    private CommunityPostDetailResponse.Author toAuthor(Long authorId, User author) {
        if (author == null) {
            return new CommunityPostDetailResponse.Author(authorId, "알 수 없는 사용자", null);
        }
        if (author.isWithdrawn()) {
            return new CommunityPostDetailResponse.Author(authorId, User.WITHDRAWN_DISPLAY_NAME, null);
        }
        return new CommunityPostDetailResponse.Author(authorId, author.getUsername(), author.getProfileImageUrl());
    }

    /**
     * 공개 게시글의 숨김되지 않은 댓글을 최신순으로 조회하고 작성자를 일괄 조회해 페이지 응답에 결합.
     * 숨김·없는 게시글은 거절하며 조회되지 않는 작성자는 대체 이름으로 표시.
     */
    @Transactional(readOnly = true)
    public CommunityPostCommentListResponse findComments(long postId, int page, int limit) {
        return findComments(postId, page, limit, null);
    }

    @Transactional(readOnly = true)
    public CommunityPostCommentListResponse findComments(long postId, int page, int limit, Long userId) {
        return findComments(postId, page, limit, userId, null);
    }

    @Transactional(readOnly = true)
    public CommunityPostCommentListResponse findComments(long postId, int page, int limit, Long userId, Long parentCommentId) {
        requirePost(postId);
        if (parentCommentId != null) {
            var parent = communityPostCommentRepository
                    .findByIdAndCommunityPost_IdAndHiddenFalseAndCommunityPost_HiddenFalse(parentCommentId, postId)
                    .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
            if (parent.getParentCommentId() != null) throw new CommunityException(CommunityErrorCode.INVALID_PARENT_COMMENT);
        }
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<CommunityPostComment> result = parentCommentId != null
                ? communityPostCommentRepository.findReplies(postId, parentCommentId, pageable)
                : communityPostCommentRepository.findByCommunityPost_IdAndHiddenFalse(postId, pageable);
        List<Long> authorIds = result.getContent().stream()
                .map(CommunityPostComment::getUserId)
                .distinct()
                .toList();
        Map<Long, User> usersById = authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, Function.identity()));
        Map<Long, CommunityPostCommentRepository.Reaction> reactions = result.isEmpty() ? Map.of()
                : communityPostCommentRepository.findReactions(result.getContent().stream()
                    .map(CommunityPostComment::getId).toList(), userId).stream()
                    .collect(java.util.stream.Collectors.toMap(CommunityPostCommentRepository.Reaction::getCommentId, Function.identity()));
        List<CommunityPostCommentListResponse.Item> comments = result.getContent().stream()
                .map(comment -> toCommentItem(comment, usersById.get(comment.getUserId()), reactions.get(comment.getId())))
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

    private CommunityPostCommentListResponse.Item toCommentItem(CommunityPostComment comment, User author, CommunityPostCommentRepository.Reaction reaction) {
        return new CommunityPostCommentListResponse.Item(
                comment.getId(),
                comment.getContent(),
                comment.getUserId(),
                toAuthor(comment.getUserId(), author).authorName(),
                comment.getCreatedAt(), comment.getParentCommentId(),
                reaction == null ? 0 : reaction.getLikeCount(), reaction != null && reaction.getLiked()
        );
    }

    private CommunityPostDetailResponse.Place toPlace(CommunityPostPlace postPlace, Map<String, String> regionNames) {
        if (postPlace.getMapPlace() == null) {
            return new CommunityPostDetailResponse.Place(postPlace.getMapPlaceId(), DELETED_PLACE_NAME, true);
        }
        return new CommunityPostDetailResponse.Place(
                postPlace.getMapPlace().getId(),
                postPlace.getMapPlace().getName(),
                false, postPlace.getMapPlace().getImageUrl(), postPlace.getMapPlace().getCategory(),
                postPlace.getMapPlace().getRegionCode(), postPlace.getMapPlace().getAddress(),
                postPlace.getMapPlace().getRegionCode() == null ? null : regionNames.get(postPlace.getMapPlace().getRegionCode())
        );
    }
}
