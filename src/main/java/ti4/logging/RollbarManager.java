package ti4.logging;

import com.rollbar.api.payload.data.Level;
import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.Config;
import com.rollbar.notifier.config.ConfigBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.AsyncTI4DiscordBot;

/**
 * Rollbar bootstrap, reporting facade, and thread-local context store.
 *
 * <p>Configuration is environment-variable driven:
 *
 * <p>- {@code ROLLBAR_ACCESS_TOKEN}: required to enable reporting
 *
 * <p>- {@code ROLLBAR_ENVIRONMENT}: optional, defaults to {@code SPRING_PROFILES_ACTIVE} or {@code production}
 *
 * <p>- {@code ROLLBAR_CODE_VERSION}: optional, defaults to the packaged implementation version or {@code dev}
 *
 * <p>Reports automatically include the current thread-local context snapshot and any {@link LogOrigin} details that
 * were available when the error was logged.
 */
@UtilityClass
public class RollbarManager {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final ThreadLocal<Map<String, Object>> CUSTOM_DATA = ThreadLocal.withInitial(LinkedHashMap::new);
    private static volatile Rollbar rollbar;

    /**
     * Initializes the global Rollbar client once per process.
     *
     * <p>If no access token is present, Rollbar remains disabled and calls to {@link #report(Level, Throwable, String,
     * LogOrigin)} become no-ops.
     */
    public static void init() {
        if (INITIALIZED.getAndSet(true)) {
            return;
        }

        String accessToken = getSetting("ROLLBAR_ACCESS_TOKEN");
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        Config config = ConfigBuilder.withAccessToken(accessToken)
                .environment(getSetting("ROLLBAR_ENVIRONMENT", getDefaultEnvironment()))
                .codeVersion(getSetting("ROLLBAR_CODE_VERSION", getDefaultCodeVersion()))
                .framework("spring-boot")
                .appPackages(List.of("ti4"))
                .handleUncaughtErrors(true)
                .build();

        rollbar = Rollbar.init(config);
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            try {
                                if (rollbar != null) {
                                    rollbar.close(true);
                                }
                            } catch (Exception ignored) {
                            }
                        },
                        "rollbar-shutdown"));
    }

    public static boolean isEnabled() {
        if (!INITIALIZED.get()) {
            init();
        }
        return rollbar != null;
    }

    /**
     * Clears all thread-local Rollbar metadata for the current request/task.
     */
    public static void clear() {
        CUSTOM_DATA.remove();
    }

    /**
     * Adds a single custom field to the active Rollbar scope.
     *
     * <p>Null keys, null values, and blank strings are ignored so callers can populate optional metadata without
     * repetitive guards.
     */
    public static void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        CUSTOM_DATA.get().put(key, value);
    }

    /**
     * Adds standard Discord interaction metadata shared by button, slash, modal, menu, and context-menu handlers.
     */
    public static void putInteractionMetadata(String interactionType, GenericInteractionCreateEvent event) {
        put("interaction_type", interactionType);
        if (event == null) {
            return;
        }
        put("discord_user_id", event.getUser().getId());
        put("channel_id", event.getChannel().getId());
        if (event.getGuild() != null) {
            put("guild_id", event.getGuild().getId());
        }
    }

    /**
     * Sends a report to Rollbar using the current thread-local context.
     *
     * <p>Most callers should not build custom metadata maps directly. Instead they should populate this manager's
     * thread-local context near the request/task boundary and then log through {@code BotLogger}, which forwards
     * throwable-backed error logs here.
     */
    public static void report(Level level, Throwable throwable, String message, LogOrigin origin) {
        if (!isEnabled()) {
            return;
        }

        Map<String, Object> custom = new LinkedHashMap<>(snapshot());
        if (message != null && !message.isBlank()) {
            custom.put("log_message", message);
        }
        if (origin != null) {
            if (origin.getEventString() != null) {
                custom.put("origin_event", origin.getEventString());
            }
            if (origin.getGameInfo() != null) {
                custom.put("origin_game", origin.getGameInfo());
            }
        }

        if (throwable != null) {
            rollbar.log(throwable, custom, message, level);
        } else if (message != null && !message.isBlank()) {
            rollbar.log(message, custom, level);
        }
    }

    private static Map<String, Object> snapshot() {
        return new LinkedHashMap<>(CUSTOM_DATA.get());
    }

    private static String getDefaultEnvironment() {
        return getSetting("SPRING_PROFILES_ACTIVE", "production");
    }

    private static String getDefaultCodeVersion() {
        String implementationVersion = AsyncTI4DiscordBot.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank() ? "dev" : implementationVersion;
    }

    private static String getSetting(String key) {
        return System.getenv(key);
    }

    private static String getSetting(String key, String defaultValue) {
        String value = getSetting(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
