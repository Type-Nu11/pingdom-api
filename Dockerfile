FROM azul/zulu-openjdk:21

RUN groupadd --system pingdom \
    && useradd --system --gid pingdom --home-dir /app --shell /usr/sbin/nologin pingdom \
    && mkdir -p /app \
    && chown pingdom:pingdom /app

WORKDIR /app
COPY --chown=pingdom:pingdom build/libs/*.jar app.jar

USER pingdom:pingdom

ENTRYPOINT ["java", "-jar", "app.jar"]
