package com.typenull.pingdom.shared.config.postgis;

import java.sql.DatabaseMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

@Configuration
public class PostgisExtensionInitializer {

    private static final Logger log = LoggerFactory.getLogger(PostgisExtensionInitializer.class);

    @Bean
    public ApplicationRunner postgisExtensionRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!isPostgreSql(jdbcTemplate)) {
                log.info("Skipping PostGIS initializer for non-PostgreSQL datasource.");
                return;
            }

            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");
                log.info("PostGIS extension ensured (CREATE EXTENSION IF NOT EXISTS postgis).");
                jdbcTemplate.execute("""
                        DO $$
                        BEGIN
                          IF NOT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'map_place'
                              AND column_name = 'location'
                          ) THEN
                            ALTER TABLE map_place
                              ADD COLUMN location geometry(Point,4326);
                          END IF;
                        END
                        $$;
                        """);
                log.info("map_place.location ensured (geometry(Point,4326)).");
            } catch (Exception e) {
                log.warn("Failed to ensure PostGIS extension. If 'geometry' type errors occur, run `CREATE EXTENSION postgis;` with sufficient DB privileges.", e);
            }

            try {
                jdbcTemplate.execute("""
                        DO $$
                        BEGIN
                          IF NOT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'map_place'
                              AND column_name = 'registrant'
                          ) THEN
                            ALTER TABLE map_place
                              ADD COLUMN registrant varchar(255) DEFAULT 'unknown' NOT NULL;
                          ELSIF EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_name = 'map_place'
                              AND column_name = 'registrant'
                              AND is_nullable = 'YES'
                          ) THEN
                            UPDATE map_place
                            SET registrant = 'unknown'
                            WHERE registrant IS NULL;

                            ALTER TABLE map_place
                              ALTER COLUMN registrant SET NOT NULL;
                          END IF;
                        END
                        $$;
                        """);
                log.info("map_place.registrant ensured and backfilled.");
            } catch (Exception e) {
                log.error("Failed to migrate map_place.registrant column. Application may fail to function correctly.", e);
                throw e;
            }
        };
    }

    private boolean isPostgreSql(JdbcTemplate jdbcTemplate) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData != null && "PostgreSQL".equalsIgnoreCase(metaData.getDatabaseProductName());
        }));
    }
}
