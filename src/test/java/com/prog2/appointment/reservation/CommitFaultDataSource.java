package com.prog2.appointment.reservation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * Falla en la frontera del commit JDBC real, conservando SQL/rollback reales y sin modificar
 * producción.
 */
final class CommitFaultDataSource extends AbstractDataSource {
    enum Mode {
        BEFORE,
        AFTER,
        BLOCK
    }

    private final DataSource delegate;
    final AtomicInteger remaining = new AtomicInteger();
    volatile String statement = "insert into reservation_process";
    volatile Mode mode = Mode.BEFORE;
    final AtomicReference<CountDownLatch> entered = new AtomicReference<>(new CountDownLatch(1));
    final AtomicReference<CountDownLatch> release = new AtomicReference<>(new CountDownLatch(1));

    CommitFaultDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    void arm(Mode value, String sql, int count) {
        mode = value;
        statement = sql;
        remaining.set(count);
        entered.set(new CountDownLatch(1));
        release.set(new CountDownLatch(1));
    }

    void clear() {
        remaining.set(0);
        release.get().countDown();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return wrap(delegate.getConnection(user, password));
    }

    private Connection wrap(Connection target) {
        var matching = new AtomicBoolean();
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            if ("prepareStatement".equals(method.getName())
                                    && args[0].toString()
                                            .toLowerCase(Locale.ROOT)
                                            .contains(statement)) {
                                matching.set(true);
                            }
                            if ("commit".equals(method.getName())
                                    && matching.get()
                                    && remaining.getAndUpdate(value -> Math.max(0, value - 1))
                                            > 0) {
                                failOrBlockCommit(target);
                            }
                            try {
                                return method.invoke(target, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }

    private void failOrBlockCommit(Connection target) throws SQLException, InterruptedException {
        Mode selected = mode;
        var gate = release.get();
        entered.get().countDown();
        if (selected == Mode.BLOCK) {
            if (!gate.await(5, TimeUnit.SECONDS)) {
                throw new SQLException("Fictional commit gate timed out", "08006");
            }
            return;
        }
        if (selected == Mode.AFTER) {
            target.commit();
        } else {
            // Una conexión perdida antes del commit descarta su transacción en MySQL.
            // La excepción simulada por sí sola dejaría bloqueos que impiden el reintento.
            target.rollback();
        }
        throw new SQLException("Fictional JDBC commit result unavailable", "08006");
    }
}
