package ti4.service.testbed;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.service.testbed.TestBedPress.PressResult;

@UtilityClass
public class TestBedTurnButtons {

    public static final int MAX_BUTTONS = 20;
    private static final long MAX_SETTLE_MILLIS = 2000;
    private static final long POLL_MILLIS = 250;

    public record TurnButtons(
            @Nullable Player active, @Nullable Message message, List<Button> buttons) {

        public List<Button> shown() {
            return buttons.subList(0, Math.min(buttons.size(), MAX_BUTTONS));
        }
    }

    public static TurnButtons find(Game game) {
        Player active = game.getActivePlayer();
        if (active == null) return new TurnButtons(null, null, List.of());
        MessageChannel channel = active.getCorrectChannel();
        if (channel == null) return new TurnButtons(active, null, List.of());
        String prefix = active.factionButtonChecker();
        for (Message message :
                channel.getHistory().retrievePast(TestBedPress.HISTORY_SIZE).complete()) {
            List<Button> buttons = message.getComponentTree().findAll(Button.class).stream()
                    .filter(button -> button.getCustomId() != null)
                    .toList();
            if (buttons.stream().anyMatch(button -> button.getCustomId().startsWith(prefix))) {
                return new TurnButtons(active, message, buttons);
            }
        }
        return new TurnButtons(active, null, List.of());
    }

    public static void press(
            Game game, Member developer, int index, @Nullable String expectedLabel, Consumer<String> onDone) {
        ExecutorServiceManager.runAsync(
                "test bed turn button", () -> onDone.accept(pressNow(game, developer, index, expectedLabel)));
    }

    private static String pressNow(Game game, Member developer, int index, @Nullable String expectedLabel) {
        ManagedGame managed = GameManager.getManagedGame(game.getName());
        if (managed == null) return "The game is no longer loaded.";
        TurnButtons current = find(managed.getGame());
        if (current.active() == null
                || current.message() == null
                || index >= current.shown().size()) {
            return "Those buttons are gone; here are the current ones.";
        }
        Button button = current.shown().get(index);
        if (!Objects.equals(button.getLabel(), expectedLabel)) {
            return "Those buttons changed; here are the current ones.";
        }
        PressResult result = TestBedPress.press(game, developer, current.active(), current.message(), button);
        waitForNewButtons(game.getName(), current.message().getIdLong());
        return describe(current.active(), button, result);
    }

    private static void waitForNewButtons(String gameName, long pressedMessageId) {
        long deadline = System.currentTimeMillis() + MAX_SETTLE_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            TestBedPress.sleep(POLL_MILLIS);
            ManagedGame managed = GameManager.getManagedGame(gameName);
            if (managed == null) return;
            Message latest = find(managed.getGame()).message();
            if (latest == null || latest.getIdLong() != pressedMessageId) return;
        }
    }

    private static String describe(Player active, Button button, PressResult result) {
        StringBuilder status = new StringBuilder("Pressed `")
                .append(button.getLabel())
                .append("` as ")
                .append(active.getFaction())
                .append('.');
        if (result.recorder().modalId() != null) {
            status.append(" It opens a form the panel cannot fill in; use the button in the channel.");
        }
        result.recorder().replies().stream()
                .findFirst()
                .ifPresent(reply -> status.append(" Reply: ").append(reply));
        return status.toString();
    }
}
