package org.katacr.katpa.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/** 串行化共享 JDBC 操作，并在连接失效后为后续操作重建连接。 */
public final class JdbcConnectionManager {
    private static final long DEFAULT_VALIDATION_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final ConnectionFactory connectionFactory;
    private final ConnectionInitializer connectionInitializer;
    private final Logger logger;
    private final boolean validateConnection;
    private final long validationIntervalNanos;
    private final ReentrantLock lock = new ReentrantLock(true);
    private Connection connection;
    private boolean connectedOnce;
    private boolean closed;
    private long lastValidationNanos;

    JdbcConnectionManager(ConnectionFactory connectionFactory, ConnectionInitializer connectionInitializer,
                          Logger logger, boolean validateConnection) {
        this(connectionFactory, connectionInitializer, logger, validateConnection,
                DEFAULT_VALIDATION_INTERVAL_NANOS);
    }

    JdbcConnectionManager(ConnectionFactory connectionFactory, ConnectionInitializer connectionInitializer,
                          Logger logger, boolean validateConnection, long validationIntervalNanos) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.connectionInitializer = Objects.requireNonNull(connectionInitializer, "connectionInitializer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.validateConnection = validateConnection;
        this.validationIntervalNanos = Math.max(0, validationIntervalNanos);
    }

    /** 在连接锁内执行完整 JDBC 操作，避免多个 Store 并发污染连接与事务状态。 */
    public <T> T execute(SqlFunction<T> operation) throws SQLException {
        Objects.requireNonNull(operation, "operation");
        lock.lock();
        try {
            Connection current = ensureConnection();
            try {
                return operation.apply(current);
            } catch (SQLException exception) {
                if (isConnectionFailure(exception)) {
                    invalidateConnection();
                }
                throw exception;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 在连接锁内执行无返回值 JDBC 操作。 */
    public void executeVoid(SqlConsumer operation) throws SQLException {
        execute(connection -> {
            operation.accept(connection);
            return null;
        });
    }

    /** 关闭物理连接；关闭后拒绝新操作。 */
    public void close() throws SQLException {
        lock.lock();
        try {
            closed = true;
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private Connection ensureConnection() throws SQLException {
        if (closed) {
            throw new SQLException("数据库连接管理器已关闭", "08003");
        }

        long now = System.nanoTime();
        boolean invalid = connection == null;
        if (!invalid) {
            try {
                invalid = connection.isClosed();
                if (!invalid && validateConnection
                        && now - lastValidationNanos >= validationIntervalNanos) {
                    invalid = !connection.isValid(2);
                }
            } catch (SQLException exception) {
                invalid = true;
            }
        }

        if (invalid) {
            invalidateConnection();
            if (connectedOnce) {
                logger.warning("数据库连接已失效，正在重新连接。");
            }
            Connection replacement = connectionFactory.open();
            try {
                connectionInitializer.initialize(replacement);
            } catch (SQLException exception) {
                try {
                    replacement.close();
                } catch (SQLException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
            connection = replacement;
            connectedOnce = true;
        }
        lastValidationNanos = now;
        return connection;
    }

    private void invalidateConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 原连接已经不可用，关闭失败不应阻止后续重连。
        }
        connection = null;
        lastValidationNanos = 0;
    }

    static boolean isConnectionFailure(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            String state = current.getSQLState();
            if (state != null && state.startsWith("08")) {
                return true;
            }
            if (current instanceof java.sql.SQLNonTransientConnectionException
                    || current instanceof java.sql.SQLRecoverableException
                    || current instanceof java.sql.SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    interface ConnectionInitializer {
        void initialize(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
