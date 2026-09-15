package com.typenull.pingdom.moderation.application.service.community;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityCommentPageResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityCommentResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityPostPageResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityPostResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCommunityContentQueryService {

    private static final String DELETED_PLACE_NAME = "삭제된 장소입니다";

    private final CommunityPostRepository postRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityPostPlaceRepository postPlaceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminCommunityPostPageResponse findPosts(String categoryId, Boolean hidden, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Page<CommunityPost> result = postRepository.findAllForAdmin(
                categoryId,
                hidden,
                PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        Map<Long, User> authors = authors(result.getContent().stream().map(CommunityPost::getUserId).toList());
        return new AdminCommunityPostPageResponse(
                result.getContent().stream().map(post -> postItem(post, authors.get(post.getUserId()))).toList(),
                safePage, safeLimit, result.getTotalElements(), result.getTotalPages(), result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminCommunityPostResponse findPost(Long postId) {
        CommunityPost post = requirePost(postId);
        User author = authors(List.of(post.getUserId())).get(post.getUserId());
        List<AdminCommunityPostResponse.Place> places = postPlaceRepository.findAllWithMapPlaceByCommunityPostId(postId)
                .stream().map(this::place).toList();
        return new AdminCommunityPostResponse(
                post.getId(), post.getCategoryId(), post.getTitle(), post.getContent(), post.getUserId(), username(author),
                post.isHidden(), post.getHiddenByAdminUserId(), post.getHiddenAt(), post.getCreatedAt(), places
        );
    }

    @Transactional(readOnly = true)
    public AdminCommunityCommentPageResponse findComments(Long postId, Boolean hidden, int page, int limit) {
        requirePost(postId);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Page<CommunityPostComment> result = commentRepository.findAllForAdmin(
                postId,
                hidden,
                PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        Map<Long, User> authors = authors(result.getContent().stream().map(CommunityPostComment::getUserId).toList());
        return new AdminCommunityCommentPageResponse(
                result.getContent().stream().map(comment -> commentItem(comment, authors.get(comment.getUserId()))).toList(),
                safePage, safeLimit, result.getTotalElements(), result.getTotalPages(), result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminCommunityCommentResponse findComment(Long postId, Long commentId) {
        requirePost(postId);
        CommunityPostComment comment = commentRepository.findByIdAndCommunityPost_Id(commentId, postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        User author = authors(List.of(comment.getUserId())).get(comment.getUserId());
        return commentResponse(comment, author);
    }

    private CommunityPost requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private Map<Long, User> authors(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, Function.identity()));
    }

    private AdminCommunityPostPageResponse.Item postItem(CommunityPost post, User author) {
        return new AdminCommunityPostPageResponse.Item(
                post.getId(), post.getCategoryId(), post.getTitle(), post.getUserId(), username(author),
                post.isHidden(), post.getHiddenByAdminUserId(), post.getHiddenAt(), post.getCreatedAt()
        );
    }

    private AdminCommunityCommentPageResponse.Item commentItem(CommunityPostComment comment, User author) {
        return new AdminCommunityCommentPageResponse.Item(
                comment.getId(), comment.getCommunityPost().getId(), comment.getUserId(), username(author), comment.getContent(),
                comment.isHidden(), comment.getHiddenByAdminUserId(), comment.getHiddenAt(), comment.getCreatedAt()
        );
    }

    private AdminCommunityCommentResponse commentResponse(CommunityPostComment comment, User author) {
        return new AdminCommunityCommentResponse(
                comment.getId(), comment.getCommunityPost().getId(), comment.getUserId(), username(author), comment.getContent(),
                comment.isHidden(), comment.getHiddenByAdminUserId(), comment.getHiddenAt(), comment.getCreatedAt()
        );
    }

    private AdminCommunityPostResponse.Place place(CommunityPostPlace postPlace) {
        if (postPlace.getMapPlace() == null) {
            return new AdminCommunityPostResponse.Place(postPlace.getMapPlaceId(), DELETED_PLACE_NAME, true);
        }
        return new AdminCommunityPostResponse.Place(postPlace.getMapPlace().getId(), postPlace.getMapPlace().getName(), false);
    }

    private String username(User user) {
        return user == null ? null : user.getUsername();
    }
}
