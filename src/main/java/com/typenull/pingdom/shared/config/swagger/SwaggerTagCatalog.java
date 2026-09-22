package com.typenull.pingdom.shared.config.swagger;

import com.typenull.pingdom.shared.config.swagger.ApiAudience.Group;
import java.util.List;

/** 사용자 업무 흐름에 따른 표시 순서와 설명. Controller 수와 독립적으로 분류한다. */
public final class SwaggerTagCatalog {
    public static final String ACCOUNT = "내 정보·계정 연동";
    public static final String TRAVEL = "여행";
    public static final String PLACE_DISCOVERY = "장소 탐색";
    public static final String PLACE_DETAIL = "장소 상세·메뉴";
    public static final String BOOKMARK = "즐겨찾기";
    public static final String MY_REVIEW = "내 리뷰";
    public static final String INFORMATION_REPORT = "장소 정보 제보";
    public static final String VISIT = "방문 인증";
    public static final String SCOUT = "스카우트";
    public static final String COMMUNITY_POST = "커뮤니티 게시글";
    public static final String COMMUNITY_REACTION = "댓글·좋아요";
    public static final String REPORT = "신고";
    public static final String RESERVATION = "예약";
    public static final String PAYMENT = "결제";
    public static final String OFFER = "쿠폰·혜택";
    public static final String EVENT = "행사·팝업";
    public static final String NOTIFICATION = "알림";
    public static final String ANALYSIS = "상권 분석";
    public static final String BUSINESS = "사업자 정보";
    public static final String TEAM = "팀 관리";
    public static final String APPLICATION = "장소 등록 신청";
    public static final String PLACE_MANAGEMENT = "장소 정보 관리";
    public static final String MENU_NOTICE = "메뉴·운영 공지";
    public static final String REVIEW_MANAGEMENT = "리뷰 관리";
    public static final String PRODUCT = "예약 상품";
    public static final String AVAILABILITY = "예약 가능 시간";
    public static final String MERCHANT_RESERVATION = "예약 관리";
    public static final String MERCHANT_PAYMENT = "결제";
    public static final String CAMPAIGN = "캠페인·혜택";
    public static final String MERCHANT_BOOST = "Verified Boost";
    public static final String PERFORMANCE = "실적";
    public static final String DASHBOARD = "대시보드";
    public static final String MEMBER = "회원·권한";
    public static final String MERCHANT_MANAGEMENT = "사업자 관리";
    public static final String APPLICATION_REVIEW = "장소 등록 심사";
    public static final String PLACE_LOOKUP = "장소 조회";
    public static final String PLACE_QUALITY = "장소 정보·품질";
    public static final String DUPLICATE_RECOMMENDATION = "중복·추천 관리";
    public static final String REPORT_REVERIFICATION = "제보·재검증";
    public static final String EVENT_NOTICE = "행사·운영 공지";
    public static final String REVIEW_DELETION = "리뷰 삭제 심사";
    public static final String COMMUNITY_MANAGEMENT = "커뮤니티 관리";
    public static final String REPORT_APPEAL = "신고·이의신청";
    public static final String TRUST = "신뢰 점수";
    public static final String VISIT_SCOUT = "방문 인증·스카우트";
    public static final String ADMIN_RESERVATION = "예약";
    public static final String ADMIN_BOOST = "Verified Boost";
    public static final String ADMIN_NOTIFICATION = "알림·발송 이력";
    public static final String AUDIT = "감사·개인정보 처리 이력";
    public static final String OPERATIONS = "Outbox·스토리지 운영";
    public static final String LOGIN = "로그인·세션";
    public static final String SIGNUP = "회원가입·이메일 인증";
    public static final String PASSWORD = "비밀번호 재설정";
    public static final String PLACE_REVIEW = "장소 리뷰";
    public static final String REVIEW_MEDIA = "리뷰 미디어";
    public static final String SERVICE = "서비스 기본";
    public static final String INTRO = "상담 시작";

    private SwaggerTagCatalog() {
    }

    public record Section(Group audience, String name, String description) {
    }

    public static final List<Section> SECTIONS = List.of(
            new Section(Group.APP, ACCOUNT, "내 정보 조회·변경, 계정 연동 및 탈퇴"),
            new Section(Group.APP, TRAVEL, "여행 목적 선호, 여행 일정 및 활동 의향"),
            new Section(Group.APP, PLACE_DISCOVERY, "지도·검색·추천과 인기 장소 탐색"),
            new Section(Group.APP, PLACE_DETAIL, "장소 상세, 메뉴, 운영 공지 및 미디어"),
            new Section(Group.APP, BOOKMARK, "관심 장소 저장 및 조회"),
            new Section(Group.APP, MY_REVIEW, "내가 작성한 장소 리뷰 조회"),
            new Section(Group.APP, INFORMATION_REPORT, "장소 정보 제보 및 이의 제기"),
            new Section(Group.APP, VISIT, "위치 체크인, 방문 증빙 및 검증 보고"),
            new Section(Group.APP, SCOUT, "스카우트 프로필 및 현장 보고"),
            new Section(Group.APP, COMMUNITY_POST, "게시글 작성·탐색 및 연결 장소 조회"),
            new Section(Group.APP, COMMUNITY_REACTION, "게시글 댓글 및 좋아요"),
            new Section(Group.APP, REPORT, "커뮤니티 게시글 및 댓글 신고"),
            new Section(Group.APP, RESERVATION, "예약 생성·조회 및 취소"),
            new Section(Group.APP, PAYMENT, "결제 내역 및 상세 조회"),
            new Section(Group.APP, OFFER, "혜택 탐색, 쿠폰 발급 및 조회"),
            new Section(Group.APP, EVENT, "행사 및 팝업 캠페인 탐색"),
            new Section(Group.APP, NOTIFICATION, "알림 수신 설정 및 기기 토큰 관리"),
            new Section(Group.APP, ANALYSIS, "상권 분석 보고서 생성·조회·다운로드"),
            new Section(Group.MERCHANT, BUSINESS, "사업자 프로필 등록 및 조회·변경"),
            new Section(Group.MERCHANT, TEAM, "장소 팀원 초대 및 권한 관리"),
            new Section(Group.MERCHANT, APPLICATION, "장소 등록 신청 및 첨부 자료 관리"),
            new Section(Group.MERCHANT, PLACE_MANAGEMENT, "장소 정보·미디어·운영 상태 및 재검증 응답"),
            new Section(Group.MERCHANT, MENU_NOTICE, "메뉴와 장소 운영 공지 관리"),
            new Section(Group.MERCHANT, REVIEW_MANAGEMENT, "장소 리뷰 확인 및 삭제 심사 요청"),
            new Section(Group.MERCHANT, PRODUCT, "예약 상품 등록 및 활성 상태 관리"),
            new Section(Group.MERCHANT, AVAILABILITY, "예약 가능 시간 등록·변경 및 활성 상태 관리"),
            new Section(Group.MERCHANT, MERCHANT_RESERVATION, "사업자 예약 조회 및 취소"),
            new Section(Group.MERCHANT, MERCHANT_PAYMENT, "사업자 결제·정산 조회 및 환불"),
            new Section(Group.MERCHANT, CAMPAIGN, "캠페인·브랜드·혜택 및 쿠폰 사용 관리"),
            new Section(Group.MERCHANT, MERCHANT_BOOST, "Verified Boost 상품 조회·선택 및 실행"),
            new Section(Group.MERCHANT, PERFORMANCE, "사업자 운영 실적 조회"),
            new Section(Group.ADMIN, DASHBOARD, "운영 현황, 최근 활동 및 처리 대기 항목"),
            new Section(Group.ADMIN, MEMBER, "회원 제재 및 관리자 역할 관리"),
            new Section(Group.ADMIN, MERCHANT_MANAGEMENT, "사업자 심사 및 연결 장소 관리"),
            new Section(Group.ADMIN, APPLICATION_REVIEW, "장소 등록 신청과 증빙 확인 및 심사"),
            new Section(Group.ADMIN, PLACE_LOOKUP, "운영 대상 장소 목록 및 상세 조회"),
            new Section(Group.ADMIN, PLACE_QUALITY, "장소 정보 보정, 근거 검토 및 노출 상태 관리"),
            new Section(Group.ADMIN, DUPLICATE_RECOMMENDATION, "중복 장소 검토·병합 및 추천 운영"),
            new Section(Group.ADMIN, REPORT_REVERIFICATION, "장소 정보 제보 심사 및 재검증 요청 관리"),
            new Section(Group.ADMIN, EVENT_NOTICE, "행사 발행 및 운영 공지 관리"),
            new Section(Group.ADMIN, REVIEW_DELETION, "장소 리뷰 삭제 요청 확인 및 심사"),
            new Section(Group.ADMIN, COMMUNITY_MANAGEMENT, "게시글과 댓글 운영 조회"),
            new Section(Group.ADMIN, REPORT_APPEAL, "신고 및 이의신청 검토·처리, 신고 콘텐츠 삭제"),
            new Section(Group.ADMIN, TRUST, "신뢰 점수·이상 징후 및 개입 규칙 관리"),
            new Section(Group.ADMIN, VISIT_SCOUT, "방문 보고 심사 및 스카우트 자격 관리"),
            new Section(Group.ADMIN, ADMIN_RESERVATION, "예약 조회 및 승인·거절"),
            new Section(Group.ADMIN, ADMIN_BOOST, "Verified Boost 상품 등록 및 활성 상태 관리"),
            new Section(Group.ADMIN, ADMIN_NOTIFICATION, "운영 알림 확인 및 발송 이력 조회"),
            new Section(Group.ADMIN, AUDIT, "관리자 작업 감사 및 개인정보 처리 이력 조회"),
            new Section(Group.ADMIN, OPERATIONS, "Outbox 재처리 및 스토리지 고아 객체 관리"),
            new Section(Group.COMMON, LOGIN, "로그인, 토큰 갱신 및 로그아웃"),
            new Section(Group.COMMON, SIGNUP, "회원가입 및 이메일 인증"),
            new Section(Group.COMMON, PASSWORD, "비밀번호 재설정 요청 및 확인"),
            new Section(Group.COMMON, PLACE_REVIEW, "장소 리뷰 작성 및 목록 조회"),
            new Section(Group.COMMON, REVIEW_MEDIA, "장소 리뷰 첨부 미디어 업로드 및 삭제"),
            new Section(Group.COMMON, SERVICE, "서비스 기본 응답"),
            new Section(Group.CONSULTING, INTRO, "상권 상담 안내 및 초기 상담")
    );

    public static List<Section> sections(Group audience) {
        return SECTIONS.stream().filter(section -> section.audience() == audience).toList();
    }
}
