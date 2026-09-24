package org.katacr.katpa.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcConnectionManagerTest {
    @Test
    void reconnectsBeforeUsingAStaleConnection() throws SQLException {
        List<FakeConnection> opened = new ArrayList<>();
        JdbcConnectionManager manager = manager(() -> {
            FakeConnection fake = new FakeConnection();
            opened.add(fake);
            return fake.proxy();
        }, 0);

        Connection first = manager.execute(connection -> connection);
        opened.get(0).valid.set(false);
        Connection second = manager.execute(connection -> connection);

        assertEquals(2, opened.size());
        assertNotSame(first, second);
        assertTrue(opened.get(0).closed.get());
    }

    @Test
    void invalidatesConnectionFailureWithoutReplayingTheOperation() throws SQLException {
        AtomicInteger opens = new AtomicInteger();
        JdbcConnectionManager manager = manager(() -> {
            opens.incrementAndGet();
            return new FakeConnection().proxy();
        }, TimeUnit.MINUTES.toNanos(1));

        AtomicInteger attempts = new AtomicInteger();
        SQLException error = assertThrows(SQLException.class, () -> manager.execute(connection -> {
            attempts.incrementAndGet();
            throw new SQLException("connection lost", "08006");
        }));
        assertEquals("08006", error.getSQLState());
        assertEquals(1, attempts.get());

        manager.execute(connection -> null);
        assertEquals(2, opens.get());
    }

    @Test
    void doesNotReconnectForStatementErrors() throws SQLException {
        AtomicInteger opens = new AtomicInteger();
        JdbcConnectionManager manager = manager(() -> {
            opens.incrementAndGet();
            return new FakeConnection().proxy();
        }, TimeUnit.MINUTES.toNanos(1));

        Connection first = manager.execute(connection -> connection);
        assertThrows(SQLException.class, () -> manager.execute(connection -> {
            throw new SQLException("constraint", "23000");
        }));
        Connection second = manager.execute(connection -> connection);

        assertEquals(1, opens.get());
        assertSame(first, second);
    }

    @Test
    void serializesOperationsAcrossThreads() throws Exception {
        JdbcConnectionManager manager = manager(() -> new FakeConnection().proxy(),
                TimeUnit.MINUTES.toNanos(1));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 24; i++) {
                executor.submit(() -> {
                    start.await();
                    manager.execute(connection -> {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                        active.decrementAndGet();
                        return null;
                    });
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(1, maximum.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsOperationsAfterClose() throws SQLException {
        FakeConnection fake = new FakeConnection();
        JdbcConnectionManager manager = manager(fake::proxy, TimeUnit.MINUTES.toNanos(1));
        manager.execute(connection -> null);

        manager.close();

        assertTrue(fake.closed.get());
        SQLException error = assertThrows(SQLException.class,
                () -> manager.execute(connection -> null));
        assertEquals("08003", error.getSQLState());
    }

    private JdbcConnectionManager manager(JdbcConnectionManager.ConnectionFactory factory,
                                          long validationIntervalNanos) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new JdbcConnectionManager(factory, connection -> { }, logger, true,
                validationIntervalNanos);
    }

    private static final class FakeConnection {
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean();

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isValid" -> valid.get() && !closed.get();
                        case "isClosed" -> closed.get();
                        case "close" -> {
                            closed.set(true);
                            yield null;
                        }
                        case "toString" -> "FakeConnection";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            if (type == char.class) return '\0';
            return null;
        }
    }
}
