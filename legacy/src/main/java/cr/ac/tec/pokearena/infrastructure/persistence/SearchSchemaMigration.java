package cr.ac.tec.pokearena.infrastructure.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Aplica una versión transaccional después del esquema base y conserva su historial.
@Component
@DependsOnDatabaseInitialization
public class SearchSchemaMigration implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SearchSchemaMigration(DataSource dataSource, PlatformTransactionManager manager) {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(manager);
    }

    @Override
    public void afterPropertiesSet() {
        transaction.executeWithoutResult(status -> {
            // Coordina arranques simultáneos para no aplicar dos veces la misma versión.
            jdbc.execute("SELECT pg_advisory_xact_lock(390038)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS search_schema_versions (version INT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp())");
            Integer applied = jdbc.queryForObject("SELECT count(*) FROM search_schema_versions WHERE version = 39", Integer.class);
            if (applied == null || applied == 0) {
                jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V039__card_search.sql"));
                    return null;
                });
                jdbc.update("INSERT INTO search_schema_versions(version) VALUES (39)");
            }
        });
    }
}

