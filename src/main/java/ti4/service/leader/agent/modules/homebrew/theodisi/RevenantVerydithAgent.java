package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class RevenantVerydithAgent extends AgentModule<RevenantVerydithAgent.Choice> {

    static final String ID = "revenantverydithagent";
    private static final String LEGACY_USE = "useRevenantVerydithAgent_";
    private static final String DECLINE = "declineRevenantVerydithAgent_";
    private static final String SELECT_TARGET = "selectRevenantVerydithTarget";
    private static final String PENDING = "revenantVerydithPending_";
    private static final String OTHER = "other|";
    private static final String SEPARATOR = "|";
    private static final String PING = "|1";

    public sealed interface Choice permits OwnToken, OtherPlayer {}

    public record OwnToken(Tile system, boolean ping, String pendingPayload) implements Choice {}

    public record OtherPlayer(Player target) implements Choice {}

    public RevenantVerydithAgent() {
        super(ID, "Koral Vel, the Revenant of Verydith");
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_TARGET, "Use Revenant Verydith Agent", FactionEmojis.revenant);
    }

    public static boolean offerPrompt(
            GenericInteractionCreateEvent event, Player player, Tile tile, boolean ping, boolean useTactic) {
        if (player == null
                || tile == null
                || !useTactic
                || !player.hasUnexhaustedLeader(ID)
                || !player.getGame()
                        .getStoredValue(PENDING + player.getFaction())
                        .isEmpty()) {
            return false;
        }
        String payload = tile.getPosition() + (ping ? PING : SEPARATOR + "0");
        player.getGame().setStoredValue(PENDING + player.getFaction(), payload);
        MessageHelper.sendMessageToChannelWithButtons(
                event == null ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you are about to spend a tactical command token to place one in "
                        + tile.getRepresentationForButtons(player.getGame(), player)
                        + ". You may exhaust **Koral Vel**, the Revenant of Verydith agent, to place it from reinforcements instead.",
                promptButtons(player, payload));
        return true;
    }

    static List<Button> promptButtons(Player player, String payload) {
        return List.of(
                Buttons.green(
                        ownedButtonId(player, ID, payload), "Use Revenant Verydith Agent", FactionEmojis.revenant),
                Buttons.red(player.factionButtonChecker() + DECLINE + payload, "Decline"));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE.length()));
    }

    @ButtonHandler(DECLINE)
    static void decline(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(DECLINE.length());
        Tile tile = pendingTile(game, payload);
        if (tile == null || !payload.equals(game.getStoredValue(PENDING + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(PENDING + player.getFaction());
        CommandCounterHelper.addCC(event, player, tile, payload.endsWith(PING), true, true);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolvePending(Game game, Player player, ButtonInteractionEvent event) {
        String payload = game.getStoredValue(PENDING + player.getFaction());
        if (payload.isEmpty()) {
            return;
        }
        game.removeStoredValue(PENDING + player.getFaction());
        Tile tile = pendingTile(game, payload);
        if (tile != null) {
            CommandCounterHelper.addCC(event, player, tile, payload.endsWith(PING), true, true);
        }
    }

    public static void clearPending(Game game, Player player) {
        game.removeStoredValue(PENDING + player.getFaction());
    }

    private static Tile pendingTile(Game game, String payload) {
        return payload.contains(SEPARATOR)
                ? game.getTileByPosition(StringUtils.substringBefore(payload, SEPARATOR))
                : null;
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Koral Vel cannot be used right now.");
            return;
        }
        List<Button> buttons = targetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no other players to choose.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", choose a player to use **Koral Vel**, the Revenant of Verydith agent on.",
                buttons);
    }

    static List<Button> targetButtons(Game game, Player player) {
        return game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.green(
                        ownedButtonId(player, ID, OTHER + target.getFaction()),
                        target.getFactionNameOrColor(),
                        target.fogSafeEmoji()))
                .toList();
    }

    @Override
    public Optional<Choice> decode(Game game, Player user, String payload) {
        if (payload.startsWith(OTHER)) {
            return Optional.ofNullable(game.getPlayerFromColorOrFaction(payload.substring(OTHER.length())))
                    .map(OtherPlayer::new);
        }
        return Optional.ofNullable(pendingTile(game, payload))
                .map(tile -> new OwnToken(tile, payload.endsWith(PING), payload));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Choice choice) {
        if (choice instanceof OwnToken own
                && !own.pendingPayload().equals(game.getStoredValue(PENDING + user.getFaction()))) {
            return Optional.of("That command token placement is no longer pending");
        }
        if (choice instanceof OtherPlayer other && other.target() == user) {
            return Optional.of("Koral Vel must be used on another player");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        Game game = use.game();
        Player owner = use.user();
        if (use.payload() instanceof OwnToken own) {
            game.removeStoredValue(PENDING + owner.getFaction());
            CommandCounterHelper.addCC(use.event(), owner, own.system(), own.ping(), false, true);
            return AgentOutcome.none().deletingPressedMessage();
        }
        Player target = ((OtherPlayer) use.payload()).target();
        target.setTacticalCC(target.getTacticalCC() + 1);
        String message = owner.getRepresentationNoPing() + " exhausted " + use.agentNameFor(target) + ", allowing "
                + target.getRepresentationNoPing() + " to spend a command token from reinforcements instead. "
                + target.getRepresentationNoPing() + " gained 1 tactical command token.";
        return AgentOutcome.notifyInFog(game, target, message, owner).deletingPressedMessage();
    }
}
