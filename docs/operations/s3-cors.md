# S3 브라우저 업로드 CORS 설정

장소 미디어 업로드는 서버가 발급한 presigned URL로 브라우저가 S3에 직접 `PUT`합니다.
따라서 Spring의 API CORS 설정과 별도로 해당 S3 버킷에 CORS 규칙이 있어야 합니다.

## 적용

배포 환경의 AWS 자격 증명으로 다음 명령을 실행합니다. `CORS_ALLOWED_ORIGINS`는
Spring API에 사용하는 값과 동일하게 설정하고, origin 뒤에 경로 또는 `/`를 붙이지 않습니다.
`put-bucket-cors`는 기존 버킷 CORS 전체를 대체하므로 필요한 모든 origin을 한 번에 지정합니다.

```bash
export AWS_S3_BUCKET=pingdom-production
export CORS_ALLOWED_ORIGINS=http://localhost:5173,https://<frontend-domain>

./scripts/configure-s3-cors.sh --dry-run
./scripts/configure-s3-cors.sh
```

실행 주체에는 `s3:PutBucketCORS` 권한이 필요합니다. 실제 적용 여부는 다음 명령으로 확인합니다.

```bash
aws s3api get-bucket-cors --bucket "${AWS_S3_BUCKET}"
```

스크립트는 브라우저 업로드에 필요한 `PUT`, 이미지 조회를 위한 `GET`/`HEAD`,
`Content-Type` 및 AWS 헤더를 허용하고 `ETag`를 노출합니다. origin은 S3에서 요청의
`Origin`과 정확히 비교되므로 로컬·스테이징·운영 도메인을 각각 명시해야 합니다.

## 장애 확인

1. 브라우저 개발자 도구에서 S3 `OPTIONS` 요청의 `Access-Control-Allow-Origin`,
   `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`를 확인합니다.
2. `OPTIONS`가 실패하거나 CORS 응답 헤더가 없으면 버킷 CORS 설정 문제입니다.
3. `OPTIONS`가 성공한 뒤 `PUT`이 `403`으로 실패하면 presigned URL 만료, 리전, 권한 또는
   발급 시 지정한 `Content-Type`과 실제 요청 헤더 불일치를 확인합니다.

AWS 자격 증명이나 버킷 이름은 저장소에 커밋하지 않습니다.
