package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
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
            @Nullable Player active,
            @Nullable Message message,
            List<Button> buttons,
            @Nullable Player pressAs,
            boolean combat) {

        public TurnButtons(@Nullable Player active, @Nullable Message message, List<Button> buttons) {
            this(active, message, buttons, active, false);
        }

        public List<Button> shown() {
            return buttons.subList(0, Math.min(buttons.size(), MAX_BUTTONS));
        }
    }

    public static TurnButtons find(Game game, String developerId) {
        Player active = game.getActivePlayer();
        if (active == null) return new TurnButtons(null, null, List.of());
        String prefix = active.factionButtonChecker();
        Message newest = null;
        for (MessageChannel channel : seatChannels(game, active)) {
            Message candidate = latestWithSeatButtons(channel, prefix);
            if (isNewer(candidate, newest)) newest = candidate;
        }
        Player combatSeat = combatSeat(game, developerId, active);
        ThreadChannel combatThread = TestBedCombatThreads.latest(game, combatSeat);
        Message combatMessage = combatThread == null ? null : latestWithButtons(combatThread);
        if (isNewer(combatMessage, newest)) {
            return new TurnButtons(active, combatMessage, enabledButtons(combatMessage), combatSeat, true);
        }
        if (newest == null) return new TurnButtons(active, null, List.of());
        return new TurnButtons(active, newest, enabledButtons(newest));
    }

    private static boolean isNewer(@Nullable Message candidate, @Nullable Message current) {
        return candidate != null && (current == null || candidate.getIdLong() > current.getIdLong());
    }

    private static List<Button> enabledButtons(Message message) {
        return message.getComponentTree().findAll(Button.class).stream()
                .filter(button -> button.getCustomId() != null && !button.isDisabled())
                .toList();
    }

    private static Player combatSeat(Game game, String developerId, Player active) {
        Player actingAs = TestBedService.getActingAs(game, developerId);
        if (actingAs != null) return actingAs;
        Player developerSeat = game.getPlayer(developerId);
        return developerSeat != null && developerSeat.isRealPlayer() ? developerSeat : active;
    }

    @Nullable
    private static Message latestWithButtons(MessageChannel channel) {
        for (Message message :
                channel.getHistory().retrievePast(TestBedPress.HISTORY_SIZE).complete()) {
            if (!enabledButtons(message).isEmpty()) return message;
        }
        return null;
    }

    private static List<MessageChannel> seatChannels(Game game, Player active) {
        List<MessageChannel> channels = new ArrayList<>();
        MessageChannel correct = active.getCorrectChannel();
        if (correct != null) channels.add(correct);
        MessageChannel own = TestBedPress.ownChannel(game, active);
        if (own != null && (correct == null || !own.getId().equals(correct.getId()))) channels.add(own);
        return channels;
    }

    @Nullable
    private static Message latestWithSeatButtons(MessageChannel channel, String prefix) {
        for (Message message :
                channel.getHistory().retrievePast(TestBedPress.HISTORY_SIZE).complete()) {
            boolean hasSeatButton = message.getComponentTree().findAll(Button.class).stream()
                    .anyMatch(button ->
                            button.getCustomId() != null && button.getCustomId().startsWith(prefix));
            if (hasSeatButton) return message;
        }
        return null;
    }

    public static void press(
            Game game, Member developer, int index, @Nullable String expectedLabel, Consumer<String> onDone) {
        ExecutorServiceManager.runAsync(
                "test bed turn button", () -> onDone.accept(pressNow(game, developer, index, expectedLabel)));
    }

    private static String pressNow(Game game, Member developer, int index, @Nullable String expectedLabel) {
        ManagedGame managed = GameManager.getManagedGame(game.getName());
        if (managed == null) return "The game is no longer loaded.";
        TurnButtons current = find(managed.getGame(), developer.getId());
        if (current.pressAs() == null
                || current.message() == null
                || index >= current.shown().size()) {
            return "Those buttons are gone; here are the current ones.";
        }
        Button button = current.shown().get(index);
        if (!Objects.equals(button.getLabel(), expectedLabel)) {
            return "Those buttons changed; here are the current ones.";
        }
        PressResult result = TestBedPress.press(game, developer, current.pressAs(), current.message(), button);
        waitForNewButtons(game.getName(), developer.getId(), current.message().getIdLong());
        return describe(current.pressAs(), button, result);
    }

    private static void waitForNewButtons(String gameName, String developerId, long pressedMessageId) {
        long deadline = System.currentTimeMillis() + MAX_SETTLE_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            TestBedPress.sleep(POLL_MILLIS);
            ManagedGame managed = GameManager.getManagedGame(gameName);
            if (managed == null) return;
            Message latest = find(managed.getGame(), developerId).message();
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
        result.recorder().reposts().stream()
                .findFirst()
                .ifPresent(
                        link -> status.append(" Ephemeral buttons re-posted: ").append(link));
        return status.toString();
    }
}
