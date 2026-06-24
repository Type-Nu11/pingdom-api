package com.typenull.pingdom.global.config.map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Slf4j
public class MapImageTitleMigrationConfig {

    private static final String DEFAULT_TITLE = "제목 없음";

    @Bean
    public ApplicationRunner mapImageTitleMigrationRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                Integer titleColumnCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = 'map_image'
                          AND column_name = 'title'
                        """, Integer.class);

                if (titleColumnCount == null || titleColumnCount == 0) {
                    log.info("map_image.title 컬럼이 아직 없어 title 백필을 건너뜁니다.");
                    return;
                }

                int updatedCount = jdbcTemplate.update("""
                        UPDATE map_image
                        SET title = ?
                        WHERE title IS NULL OR TRIM(title) = ''
                        """, DEFAULT_TITLE);

                if (updatedCount > 0) {
                    log.info("map_image.title 백필 완료. updatedCount={}", updatedCount);
                }

                jdbcTemplate.execute("ALTER TABLE map_image ALTER COLUMN title SET NOT NULL");
                log.info("map_image.title NOT NULL 제약을 적용했습니다.");
            } catch (Exception exception) {
                log.warn("map_image.title 백필 또는 NOT NULL 제약 적용에 실패했습니다. 운영 DB 상태를 확인해주세요.", exception);
            }
        };
    }
}
