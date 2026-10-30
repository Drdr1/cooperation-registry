package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The database itself refuses to rewrite or delete audit history. */
class AuditLogImmutabilityTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void auditRowsCannotBeUpdated() throws Exception {
        bearer("admin"); // writes a LOGIN_SUCCESS row
        assertThatThrownBy(() -> jdbc.update("update audit_log set username = 'someone-else'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void auditRowsCannotBeDeleted() throws Exception {
        bearer("admin");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
