package com.prog2.appointment.user;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import com.prog2.appointment.user.domain.exception.UserSecurityException;
import com.prog2.appointment.user.domain.ports.out.UserTokenIssuer;

/** Fallos controlados solo de test; conserva MySQL, transacciones y firma reales en el recorrido normal. */
@TestConfiguration(proxyBeanMethods = false)
public class UserFailureConfiguration {
    enum CommitFailure { NONE, BEFORE, AFTER }

    static final class Faults {
        final AtomicBoolean signing = new AtomicBoolean();
        final AtomicBoolean database = new AtomicBoolean();
        final AtomicReference<CommitFailure> commit = new AtomicReference<>(CommitFailure.NONE);

        void reset() {
            signing.set(false);
            database.set(false);
            commit.set(CommitFailure.NONE);
        }
    }

    @Bean
    static Faults userFaults() {
        return new Faults();
    }

    @Bean
    @Primary
    UserTokenIssuer controlledUserTokens(@Qualifier("userTokenIssuer") UserTokenIssuer actual, Faults faults) {
        return (id, roles, duration) -> {
            if (faults.signing.get()) {
                throw new UserSecurityException();
            }
            return actual.issue(id, roles, duration);
        };
    }

    @Bean
    static BeanPostProcessor commitFailureDataSource(Faults faults) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if ("dataSource".equals(name) && bean instanceof DataSource source) {
                    return new FaultingDataSource(source, faults);
                }
                return bean;
            }
        };
    }

    private static final class FaultingDataSource extends DelegatingDataSource implements AutoCloseable {
        private final Faults faults;

        private FaultingDataSource(DataSource source, Faults faults) {
            super(source);
            this.faults = faults;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (faults.database.get()) {
                throw new SQLException("Fictional database outage", "08006");
            }
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (_, method, arguments) -> {
                        var mode = "commit".equals(method.getName())
                                ? faults.commit.getAndSet(CommitFailure.NONE) : CommitFailure.NONE;
                        if (mode == CommitFailure.BEFORE) {
                            throw new SQLException("Fictional failure before commit", "08006");
                        }
                        try {
                            Object result = method.invoke(actual, arguments);
                            if (mode == CommitFailure.AFTER) {
                                // MySQL ya confirmó: otra conexión debe observar la cuenta pese al error.
                                throw new SQLException("Fictional lost commit response", "08006");
                            }
                            return result;
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }

        @Override
        public void close() throws SQLException {
            if (getTargetDataSource() instanceof AutoCloseable resource) {
                try {
                    resource.close();
                } catch (Exception _) {
                    throw new SQLException("Cannot close fictional datasource");
                }
            }
        }
    }
}
