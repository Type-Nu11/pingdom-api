# 빌드 산출물을 비특권 사용자로 실행하는 최소 Java 21 런타임 이미지입니다.
FROM azul/zulu-openjdk:21

RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system pingdom \
    && useradd --system --gid pingdom --home-dir /app --shell /usr/sbin/nologin pingdom \
    && mkdir -p /app \
    && chown pingdom:pingdom /app

WORKDIR /app
COPY --chown=pingdom:pingdom build/libs/*.jar app.jar

USER pingdom:pingdom

ENTRYPOINT ["java", "-jar", "app.jar"]
