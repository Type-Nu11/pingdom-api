package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostCommentListResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostDetailResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.domain.CommunityCountry;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostCategory;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityCommentLikeRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostMediaRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 게시글·댓글을 조회하고 이미지·통계·작성자·장소 표시 정보를 조합. */
@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {
    private static final String DELETED_PLACE_NAME = "삭제된 장소입니다";
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;
    private final CommunityPostCommentRepository communityPostCommentRepository;
    private final UserRepository userRepository;
    private final CommunityPostMediaRepository mediaRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final PlaceAdministrativeRegionRepository regionRepository;

    @Transactional(readOnly = true)
    public CommunityPostListResponse findByCategory(String categoryId, int page, int limit, String country) {
        CommunityPostCategory.findEnabledById(categoryId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));
        return findPostPage(categoryId, country, page, limit);
    }

    @Transactional(readOnly = true)
    public CommunityPostListResponse findAll(int page, int limit, String country) {
        return findPostPage(null, country, page, limit);
    }

    private CommunityPostListResponse findPostPage(String categoryId, String countryCode, int page, int limit) {
        CommunityCountry country = CommunityCountry.parseFilter(countryCode);
        Page<CommunityPostRepository.ListItem> result = communityPostRepository.findListItems(categoryId, country != null,
                country == null ? List.of("__NO_FILTER__") : country.getAliases(), PageRequest.of(page - 1, limit));
        List<CommunityPostListResponse.Item> posts = result.getContent().stream().map(item ->
                new CommunityPostListResponse.Item(item.getPostId(), item.getTitle(), preview(item.getContent()),
                        item.getThumbnailUrl(), item.getImageCount(), item.getRegion(), item.getCreatedAt(),
                        item.getViewCount(), item.getLikeCount(), item.getCommentCount(), category(item.getCategoryId()),
                        CommunityCountry.normalize(item.getCountry()))).toList();
        return new CommunityPostListResponse(posts, page, limit, result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    private String preview(String content) {
        if (content == null) { return ""; }
        String text = content.replaceAll("\\s+", " ").trim();
        int length = Math.min(120, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, length));
    }

    private CommunityPostDetailResponse.Category category(String categoryId) {
        return CommunityPostCategory.findEnabledById(categoryId)
                .map(value -> new CommunityPostDetailResponse.Category(value.getId(), value.getDisplayName())).orElse(null);
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponse findDetail(long postId) {
        CommunityPost post = requirePost(postId);
        List<CommunityPostPlace> links = communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(postId);
        List<String> regionCodes = links.stream().map(CommunityPostPlace::getMapPlace).filter(java.util.Objects::nonNull)
                .map(MapPlace::getRegionCode).filter(java.util.Objects::nonNull).distinct().toList();
        Map<String, PlaceAdministrativeRegion> regions = regionCodes.isEmpty() ? Map.of()
                : regionRepository.findAllById(regionCodes).stream().collect(Collectors.toMap(PlaceAdministrativeRegion::getCode, Function.identity()));
        List<CommunityPostDetailResponse.Place> places = links.stream().map(link -> toPlace(link, regions)).toList();
        List<CommunityPostDetailResponse.Image> images = mediaRepository.findByCommunityPost_IdOrderByDisplayOrderAsc(postId)
                .stream().map(media -> new CommunityPostDetailResponse.Image(media.getId(), media.getImageUrl(), media.getDisplayOrder())).toList();
        User author = post.getUserId() == null ? null : userRepository.findById(post.getUserId()).orElse(null);
        return new CommunityPostDetailResponse(post.getId(), post.getTitle(), post.getContent(), places,
                toAuthor(post.getUserId(), author), category(post.getCategoryId()), post.getCreatedAt(), images,
                places.isEmpty() ? null : places.getFirst().region(), post.getViewCount());
    }

    private CommunityPostDetailResponse.Author toAuthor(Long authorId, User author) {
        if (author == null) { return new CommunityPostDetailResponse.Author(authorId, "알 수 없는 사용자", null); }
        if (author.isWithdrawn()) { return new CommunityPostDetailResponse.Author(authorId, User.WITHDRAWN_DISPLAY_NAME, null); }
        return new CommunityPostDetailResponse.Author(authorId, author.getUsername(), author.getProfileImageUrl());
    }

    @Transactional(readOnly = true)
    public CommunityPostCommentListResponse findComments(long postId, int page, int limit, long userId, boolean rootOnly) {
        requirePost(postId);
        return commentPage(communityPostCommentRepository.findVisibleComments(postId, rootOnly, commentPageable(page, limit)),
                page, limit, userId);
    }

    @Transactional(readOnly = true)
    public CommunityPostCommentListResponse findReplies(long postId, long parentId, int page, int limit, long userId) {
        CommunityPostComment parent = communityPostCommentRepository.findVisibleComment(postId, parentId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (parent.getParentComment() != null) { throw new CommunityException(CommunityErrorCode.INVALID_COMMENT_PARENT); }
        return commentPage(communityPostCommentRepository.findByCommunityPost_IdAndParentComment_IdAndHiddenFalse(
                postId, parentId, commentPageable(page, limit)), page, limit, userId);
    }

    private PageRequest commentPageable(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private CommunityPostCommentListResponse commentPage(Page<CommunityPostComment> result, int page, int limit, long userId) {
        List<Long> authorIds = result.getContent().stream().map(CommunityPostComment::getUserId).distinct().toList();
        Map<Long, User> usersById = authorIds.isEmpty() ? Map.of() : userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> ids = result.getContent().stream().map(CommunityPostComment::getId).toList();
        Map<Long, Long> likes = ids.isEmpty() ? Map.of() : commentLikeRepository.countForComments(ids).stream()
                .collect(Collectors.toMap(CommunityCommentLikeRepository.LikeCount::getCommentId, CommunityCommentLikeRepository.LikeCount::getLikeCount));
        Set<Long> liked = ids.isEmpty() ? Set.of() : Set.copyOf(commentLikeRepository.findLikedCommentIds(ids, userId));
        List<CommunityPostCommentListResponse.Item> comments = result.getContent().stream().map(comment -> {
            User author = usersById.get(comment.getUserId());
            String name = author == null ? "알 수 없는 사용자" : author.isWithdrawn() ? User.WITHDRAWN_DISPLAY_NAME : author.getUsername();
            return new CommunityPostCommentListResponse.Item(comment.getId(), comment.getContent(), comment.getUserId(), name,
                    comment.getCreatedAt(), comment.getParentComment() == null ? null : comment.getParentComment().getId(),
                    likes.getOrDefault(comment.getId(), 0L), liked.contains(comment.getId()));
        }).toList();
        return new CommunityPostCommentListResponse(comments, page, limit, result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    private CommunityPost requirePost(long postId) {
        return communityPostRepository.findByIdAndHiddenFalse(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private CommunityPostDetailResponse.Place toPlace(CommunityPostPlace link, Map<String, PlaceAdministrativeRegion> regions) {
        MapPlace place = link.getMapPlace();
        if (place == null) { return new CommunityPostDetailResponse.Place(link.getMapPlaceId(), DELETED_PLACE_NAME, true, null, null, null); }
        PlaceAdministrativeRegion region = place.getRegionCode() == null ? null : regions.get(place.getRegionCode());
        return new CommunityPostDetailResponse.Place(place.getId(), place.getName(), false, place.getImageUrl(), place.getCategory(),
                region == null ? place.getAddress() : region.getRegionName());
    }
}
