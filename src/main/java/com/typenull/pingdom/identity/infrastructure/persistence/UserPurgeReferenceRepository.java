package com.typenull.pingdom.identity.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 현재 스키마의 FK를 확인해 승인되지 않은 CASCADE/SET NULL까지 최종 삭제 전에 보류한다. */
@Repository
@RequiredArgsConstructor
public class UserPurgeReferenceRepository {

    private static final long PURGE_LOCK_KEY = 27420260622L;
    private static final Set<String> AUTH_REFERENCES = Set.of(
            "oauth_accounts.user_id", "password_reset_token.user_id",
            "fcm_device_token.user_id", "notification_settings.user_id", "user_purge_retry.user_id"
    );

    // FK 없는 소유권/신고 이력도 정책 확정 전에는 귀속 사용자를 유지한다.
    private static final List<Reference> LOGICAL_REFERENCES = List.of(
            new Reference(null, "location_analysis_report", "owner_user_id"),
            new Reference(null, "post_report", "reporter_user_id"),
            new Reference(null, "post_report", "reported_user_id"),
            new Reference(null, "report_appeal", "appellant_user_id"),
            new Reference(null, "report_appeal", "target_user_id"),
            new Reference(null, "report_appeal", "admin_user_id"),
            new Reference(null, "community_report", "reporter_user_id"),
            new Reference(null, "community_report", "processed_by_admin_user_id"),
            new Reference(null, "place_information_report", "reporter_user_id"),
            new Reference(null, "place_information_report", "reviewed_by_admin_user_id"),
            new Reference(null, "place_information_report_dispute", "disputed_by_user_id"),
            new Reference(null, "place_information_report_dispute", "reviewed_by_admin_user_id"),
            new Reference(null, "community_post", "user_id"),
            new Reference(null, "community_post", "hidden_by_admin_user_id"),
            new Reference(null, "community_post_comment", "user_id"),
            new Reference(null, "community_post_comment", "hidden_by_admin_user_id")
    );

    private final JdbcTemplate jdbcTemplate;

    public boolean tryAcquireLock() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            String product = connection.getMetaData().getDatabaseProductName();
            if ("H2".equalsIgnoreCase(product)) {
                return true;
            }
            if (!"PostgreSQL".equalsIgnoreCase(product)) {
                throw new SQLException("지원되지 않은 DB에서는 사용자 최종 삭제를 중단합니다.");
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
                statement.setLong(1, PURGE_LOCK_KEY);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getBoolean(1);
                }
            }
        }));
    }

    public boolean hasRetainedReferences(long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            DatabaseMetaData metadata = connection.getMetaData();
            String schema = connection.getSchema();
            if (schema == null || schema.isBlank()) {
                throw new SQLException("사용자 FK 검사에 필요한 현재 스키마를 확인하지 못했습니다.");
            }
            String users = databaseName(metadata, "users");
            verifyUsersPrimaryKey(metadata, connection.getCatalog(), schema, users);
            List<Reference> references = foreignReferences(metadata, connection.getCatalog(), schema, users);
            for (Reference reference : references) {
                if (schema.equals(reference.schema()) && AUTH_REFERENCES.contains(reference.key())) {
                    continue;
                }
                // 상위 profile 자체를 보류하므로 간접 업무 FK도 연쇄 삭제되지 않는다.
                if (exists(connection, metadata, reference, userId)) {
                    return true;
                }
            }
            for (Reference reference : LOGICAL_REFERENCES) {
                Reference actual = new Reference(schema, databaseName(metadata, reference.table()),
                        databaseName(metadata, reference.column()));
                if (exists(connection, metadata, actual, userId)) {
                    return true;
                }
            }
            return false;
        }));
    }

    private List<Reference> foreignReferences(DatabaseMetaData metadata, String catalog, String schema, String users)
            throws SQLException {
        List<Reference> references = new ArrayList<>();
        Set<String> confirmedAuthReferences = new HashSet<>();
        try (ResultSet rows = metadata.getExportedKeys(catalog, schema, users)) {
            while (rows.next()) {
                if (rows.getShort("KEY_SEQ") != 1 || !"id".equalsIgnoreCase(rows.getString("PKCOLUMN_NAME"))) {
                    throw new SQLException("지원되지 않은 사용자 복합 FK에서는 최종 삭제를 중단합니다.");
                }
                Reference reference = new Reference(rows.getString("FKTABLE_SCHEM"),
                        rows.getString("FKTABLE_NAME"), rows.getString("FKCOLUMN_NAME"));
                references.add(reference);
                if (schema.equals(reference.schema())) {
                    confirmedAuthReferences.add(reference.key());
                }
            }
        }
        // 권한/스키마 오류로 빈 metadata가 반환돼도 참조 없음으로 해석하지 않는다.
        if (!confirmedAuthReferences.containsAll(Set.of("oauth_accounts.user_id", "password_reset_token.user_id"))) {
            throw new SQLException("필수 인증 FK를 확인하지 못해 사용자 최종 삭제를 중단합니다.");
        }
        return references;
    }

    private void verifyUsersPrimaryKey(DatabaseMetaData metadata, String catalog, String schema, String users)
            throws SQLException {
        try (ResultSet keys = metadata.getPrimaryKeys(catalog, schema, users)) {
            if (!keys.next() || !"id".equalsIgnoreCase(keys.getString("COLUMN_NAME")) || keys.next()) {
                throw new SQLException("사용자 PK metadata를 확인하지 못해 최종 삭제를 중단합니다.");
            }
        }
    }

    private boolean exists(Connection connection, DatabaseMetaData metadata, Reference reference, long userId)
            throws SQLException {
        String sql = "SELECT 1 FROM " + quote(metadata, reference.schema()) + "." + quote(metadata, reference.table())
                + " WHERE " + quote(metadata, reference.column()) + " = ? FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private String quote(DatabaseMetaData metadata, String identifier) throws SQLException {
        String quote = metadata.getIdentifierQuoteString();
        if (identifier == null || identifier.isBlank() || quote == null || quote.isBlank()) {
            throw new SQLException("안전한 SQL 식별자를 확인하지 못해 사용자 최종 삭제를 중단합니다.");
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    private String databaseName(DatabaseMetaData metadata, String name) throws SQLException {
        return metadata.storesUpperCaseIdentifiers() ? name.toUpperCase(Locale.ROOT) : name;
    }

    private record Reference(String schema, String table, String column) {
        String key() {
            return (table + "." + column).toLowerCase(Locale.ROOT);
        }
    }
}
