package com.typenull.pingdom.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserPurgeReferenceRepositoryTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private Connection connection;
    @Mock private DatabaseMetaData metadata;
    @Mock private ResultSet primaryKeys;
    @Mock private ResultSet foreignKeys;
    @Mock private PreparedStatement statement;
    @Mock private ResultSet data;
    private UserPurgeReferenceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new UserPurgeReferenceRepository(jdbc);
    }

    @SuppressWarnings("unchecked")
    private void schema() throws Exception {
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getSchema()).thenReturn("public");
        when(metadata.getPrimaryKeys(null, "public", "users")).thenReturn(primaryKeys);
        when(primaryKeys.next()).thenReturn(true, false);
        when(primaryKeys.getString("COLUMN_NAME")).thenReturn("id");
        when(metadata.getExportedKeys(null, "public", "users")).thenReturn(foreignKeys);
    }

    private void authAndBusiness(String table, String column) throws SQLException {
        when(foreignKeys.next()).thenReturn(true, true, true, false);
        when(foreignKeys.getShort("KEY_SEQ")).thenReturn((short) 1);
        when(foreignKeys.getString("PKCOLUMN_NAME")).thenReturn("id");
        when(foreignKeys.getString("FKTABLE_SCHEM")).thenReturn("public");
        when(foreignKeys.getString("FKTABLE_NAME")).thenReturn("oauth_accounts", "password_reset_token", table);
        when(foreignKeys.getString("FKCOLUMN_NAME")).thenReturn("user_id", "user_id", column);
        when(metadata.getIdentifierQuoteString()).thenReturn("\"");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(data);
    }

    @Test
    void quotesMetadataIdentifiersAndBindsUserIdForUnknownBusinessForeignKey() throws Exception {
        schema();
        authAndBusiness("business\"record", "owner\"user_id");
        when(data.next()).thenReturn(true);

        assertThat(repository.hasRetainedReferences(19L)).isTrue();

        verify(connection).prepareStatement(
                "SELECT 1 FROM \"public\".\"business\"\"record\" WHERE \"owner\"\"user_id\" = ? FETCH FIRST 1 ROWS ONLY");
        verify(statement).setLong(1, 19L);
    }

    @Test
    void doesNotInterpretMissingForeignKeyMetadataAsNoReferences() throws Exception {
        schema();
        when(foreignKeys.next()).thenReturn(false);

        assertThatThrownBy(() -> repository.hasRetainedReferences(19L))
                .hasMessageContaining("필수 인증 FK");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void blocksDeletionWhenSchemaCannotBeDetermined() throws Exception {
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getSchema()).thenReturn(null);

        assertThatThrownBy(() -> repository.hasRetainedReferences(19L)).hasMessageContaining("현재 스키마");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void checksOwnerReportEvenWithoutAnActualForeignKey() throws Exception {
        schema();
        authAndBusiness("some_business", "user_id");
        when(data.next()).thenReturn(false, true);

        assertThat(repository.hasRetainedReferences(19L)).isTrue();

        verify(connection).prepareStatement(
                "SELECT 1 FROM \"public\".\"location_analysis_report\" WHERE \"owner_user_id\" = ? FETCH FIRST 1 ROWS ONLY");
    }
}
