package ti4.ai.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.testUtils.BaseTi4Test;

/**
 * An AI seat clears debt only with explicit "Clear Debt" items. With the bot's default, every trade good or commodity
 * it offered a debtor would silently forgive that much debt as well.
 */
class AiSeatServiceTest extends BaseTi4Test {

    private final Map<String, UserSettings> stored = new HashMap<>();
    private MockedStatic<UserSettingsManager> settings;

    @BeforeEach
    void setUp() {
        settings = Mockito.mockStatic(UserSettingsManager.class);
        settings.when(() -> UserSettingsManager.get(Mockito.anyString()))
                .thenAnswer(call -> stored.computeIfAbsent(call.getArgument(0), AiSeatServiceTest::defaults));
    }

    @AfterEach
    void tearDown() {
        settings.close();
    }

    private static UserSettings defaults(String userId) {
        UserSettings fresh = new UserSettings();
        fresh.setUserId(userId);
        return fresh;
    }

    // A new seat's preferences (written by addSeat) turn automatic debt clearance off.
    @Test
    void aNewSeatDoesNotClearDebtAutomatically() {
        AiSeatService.writeSeatPreferences("7100000111111111");

        assertThat(stored.get("7100000111111111").isPrefersAutoDebtClearance()).isFalse();
        settings.verify(() -> UserSettingsManager.save(any()), times(1));
    }

    // The note pinned in table talk tells players how the AIs trade, play Trade and handle debt, in one message.
    @Test
    void theHelpTextExplainsTrading() {
        String help = AiSeatService.howToDealWithTheAi();

        assertThat(help).contains("**Trades.**", "**Trade card.**", "**Debt.**", "Replenish Commodities");
        assertThat(help).doesNotContain("don't trade");
        assertThat(help.length()).isLessThan(2000);
    }

    // Seats added before trading existed are fixed on their first tick, and only once per run of the bot.
    @Test
    void fixesAnOlderSeatOnce() {
        String seatId = "7100000222222222";
        assertThat(UserSettingsManager.get(seatId).isPrefersAutoDebtClearance()).isTrue();

        AiSeatService.ensureTradePreferences(seatId);
        AiSeatService.ensureTradePreferences(seatId);

        assertThat(stored.get(seatId).isPrefersAutoDebtClearance()).isFalse();
        settings.verify(() -> UserSettingsManager.save(any()), times(1));
    }
}
