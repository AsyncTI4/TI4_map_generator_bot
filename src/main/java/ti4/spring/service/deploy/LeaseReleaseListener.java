package ti4.spring.service.deploy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ti4.logging.BotLogger;

@Component
public class LeaseReleaseListener {

    static final String CHANNEL = "active_lease_released";
    static final String SOURCE_CATCH_UP = "listener connect catch-up";
    static final String SOURCE_NOTIFICATION = "release notification";
    private static final int NOTIFICATION_WAIT_MILLIS = 5000;
    private static final long MIN_RECONNECT_DELAY_MILLIS = 2000;
    private static final long MAX_RECONNECT_DELAY_MILLIS = 60_000;
    private static final int WARN_EVERY_NTH_FAILURE = 30;

    private final String url;
    private final Properties connectionProperties;
    private final LeaseProperties leaseProperties;
    private volatile AtomicBoolean currentStop = new AtomicBoolean(true);

    public LeaseReleaseListener(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            LeaseProperties leaseProperties) {
        this.url = url;
        this.connectionProperties = connectionProperties(username, password);
        this.leaseProperties = leaseProperties;
    }

    private static Properties connectionProperties(String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("ApplicationName", "tibot-lease-listener");
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "30");
        return properties;
    }

    public synchronized void start(Consumer<String> onLeaseReleased) {
        if (!currentStop.get()) {
            return;
        }
        AtomicBoolean stop = new AtomicBoolean(false);
        currentStop = stop;
        Thread.ofPlatform()
                .name("lease-release-listener")
                .daemon()
                .start(() -> listenUntilStopped(onLeaseReleased, stop));
    }

    public void stop() {
        currentStop.set(true);
    }

    private void listenUntilStopped(Consumer<String> onLeaseReleased, AtomicBoolean stop) {
        AtomicInteger consecutiveFailures = new AtomicInteger();
        long reconnectDelayMillis = MIN_RECONNECT_DELAY_MILLIS;
        while (!stop.get()) {
            try {
                listenOnNewConnection(onLeaseReleased, stop, consecutiveFailures);
            } catch (SQLException | RuntimeException e) {
                warnAboutFailure(e, consecutiveFailures.incrementAndGet(), stop);
            }
            if (consecutiveFailures.get() == 0) {
                reconnectDelayMillis = MIN_RECONNECT_DELAY_MILLIS;
            }
            if (!stop.get()) {
                reconnectDelayMillis = sleepBeforeReconnect(reconnectDelayMillis, stop);
            }
        }
    }

    private void listenOnNewConnection(Consumer<String> onLeaseReleased, AtomicBoolean stop, AtomicInteger failures)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, connectionProperties)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("LISTEN " + CHANNEL);
            }
            int priorFailures = failures.getAndSet(0);
            BotLogger.info("Lease release listener is LISTENing on " + CHANNEL
                    + (priorFailures > 0 ? " after " + priorFailures + " failed attempts" : ""));
            runSafely(onLeaseReleased, SOURCE_CATCH_UP, stop);
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            while (!stop.get()) {
                PGNotification[] notifications = pgConnection.getNotifications(NOTIFICATION_WAIT_MILLIS);
                if (isLeaseReleased(notifications)) {
                    runSafely(onLeaseReleased, SOURCE_NOTIFICATION, stop);
                }
                verifyConnectionIsAlive(connection);
            }
        }
    }

    private static void verifyConnectionIsAlive(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }

    private boolean isLeaseReleased(PGNotification[] notifications) {
        if (notifications == null) {
            return false;
        }
        for (PGNotification notification : notifications) {
            if (leaseProperties.getLeaseKey().equals(notification.getParameter())) {
                return true;
            }
        }
        return false;
    }

    private void runSafely(Consumer<String> onLeaseReleased, String source, AtomicBoolean stop) {
        if (stop.get()) {
            return;
        }
        try {
            onLeaseReleased.accept(source);
        } catch (RuntimeException e) {
            BotLogger.error("Lease takeover after " + source + " failed", e);
        }
    }

    private void warnAboutFailure(Exception failure, int consecutiveFailures, AtomicBoolean stop) {
        boolean shouldWarn = consecutiveFailures == 1 || consecutiveFailures % WARN_EVERY_NTH_FAILURE == 0;
        if (shouldWarn && !stop.get()) {
            BotLogger.warning("Lease release listener is not connected (failure " + consecutiveFailures
                    + "), the polling fallback still hands the lease over: " + failure.getMessage());
        }
    }

    private long sleepBeforeReconnect(long delayMillis, AtomicBoolean stop) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop.set(true);
        }
        return Math.min(delayMillis * 2, MAX_RECONNECT_DELAY_MILLIS);
    }
}
