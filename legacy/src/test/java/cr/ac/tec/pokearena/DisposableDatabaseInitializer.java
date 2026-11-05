package cr.ac.tec.pokearena;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class DisposableDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // Se registra antes de crear beans: schema.sql y las migraciones reciben solo conexiones verificadas.
        context.addBeanFactoryPostProcessor(factory -> factory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof DataSource source ? new GuardedDataSource(source) : bean;
            }
        }));
    }

    static Connection verify(Connection connection) throws SQLException {
        // El nombre se obtiene del servidor, nunca del texto de la URL ni de sus parámetros.
        try {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT current_database()")) {
                if (!result.next() || !"pokearena_us39_test".equals(result.getString(1))) {
                    throw new SQLException("Pruebas destructivas rechazadas: se requiere la base desechable pokearena_us39_test");
                }
            }
            return connection;
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
    }

    private static class GuardedDataSource extends DelegatingDataSource {
        GuardedDataSource(DataSource source) { super(source); }

        @Override
        public Connection getConnection() throws SQLException {
            return verify(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return verify(super.getConnection(username, password));
        }
    }
}
