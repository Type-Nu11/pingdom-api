package com.typenull.pingdom.shared.support;

/** 서버 경유 업로드와 S3 직접 업로드 완료 검증에 공통 적용하는 사진 파일 크기 상한. */
public final class ImageUploadLimits {

    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;

    private ImageUploadLimits() {
    }
}
