package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class DreamAgent extends AgentModule<Player> {

    static final String ID = "dreamagent";
    private static final String IGNORED_ANOMALY_TILE_KEY = "dreamAgentIgnoredAnomalyTile";
    private static final String IGNORED_ANOMALY_PLAYER_KEY = "dreamAgentIgnoredAnomalyPlayer";
    private static final String PENDING_PLAYER_KEY = "dreamAgentPendingPlayer";
    private static final String PENDING_OWNER_KEY = "dreamAgentPendingOwner";
    private static final String DECLINE = "dream_agent_decline";
    private static final String LEGACY_OFFER = "dream_agent_offer_";
    private static final String SELECT_PLAYER = "dream_agent_select_player";
    private static final String CHOOSE_ANOMALY = "dream_agent_choose_";

    public DreamAgent() {
        super(ID, "Xal'thuun, the Dreaming Throne");
    }

    public static Button cardsInfoButton(Player dreamPlayer) {
        return Buttons.gray(
                dreamPlayer.factionButtonChecker() + SELECT_PLAYER,
                useLabel(dreamPlayer, ID, "Dream Agent"),
                FactionEmojis.dream);
    }

    public static void offerBeforeTacticalAction(Game game, Player activePlayer, Player dreamPlayer) {
        if (dreamPlayer == null || !dreamPlayer.hasUnexhaustedLeader(ID)) return;
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                ownedButtonId(dreamPlayer, ID, activePlayer.getFaction()),
                useLabel(dreamPlayer, ID, "Dream Agent"),
                FactionEmojis.dream));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                dreamPlayer.getRepresentation()
                        + ", you may exhaust Xal'thuun, the Dreaming Throne agent, to choose a non-home anomaly to ignore during this tactical action.",
                buttons);
    }

    @ButtonHandler(SELECT_PLAYER)
    static void offerTargets(ButtonInteractionEvent event, Game game, Player dreamPlayer) {
        if (!dreamPlayer.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Xal'thuun, the Dreaming Throne agent, is no longer available.");
            return;
        }
        if (anomalyTiles(game).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no valid non-home anomalies to choose.");
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> !target.getFaction().equalsIgnoreCase(dreamPlayer.getFaction()))
                .map(target -> Buttons.gray(
                        ownedButtonId(dreamPlayer, ID, target.getFaction()),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No eligible players were found for Xal'thuun, the Dreaming Throne agent.");
            return;
        }

        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event,
                dreamPlayer.getRepresentationUnfogged() + ", choose a player for Xal'thuun, the Dreaming Throne agent.",
                buttons);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_OFFER)
    static void legacyOffer(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String offered = buttonID.substring(LEGACY_OFFER.length());
        String ownerSuffix = "_" + player.getFaction();
        if (!offered.toLowerCase().endsWith(ownerSuffix.toLowerCase())) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Only the Dreaming Throne player may use Xal'thuun, the Dreaming Throne agent.");
            return;
        }
        String activeFaction = offered.substring(0, offered.length() - ownerSuffix.length());
        AgentLifecycle.pressLegacyButton(event, game, player, ID, activeFaction);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload).or(() -> Optional.ofNullable(game.getActivePlayer()));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player activePlayer) {
        if (anomalyTiles(game).isEmpty()) {
            return Optional.of("There are no valid non-home anomalies to choose");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player activePlayer = use.payload();
        game.setStoredValue(PENDING_PLAYER_KEY, activePlayer.getFaction());
        game.setStoredValue(PENDING_OWNER_KEY, use.user().getFaction());

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : anomalyTiles(game)) {
            buttons.add(Buttons.green(
                    CHOOSE_ANOMALY + activePlayer.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, activePlayer)));
        }
        buttons.add(Buttons.red(DECLINE, "Decline"));
        return AgentOutcome.of(Message.addressedTo(
                        activePlayer,
                        ", choose the anomaly whose movement effects you will ignore during this tactical action.",
                        buttons))
                .deletingPressedMessage();
    }

    @ButtonHandler(CHOOSE_ANOMALY)
    static void chooseAnomaly(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(CHOOSE_ANOMALY, "").split("_", 2);
        Player dreamAgentOwner = game.getPlayerFromColorOrFaction(game.getStoredValue(PENDING_OWNER_KEY));
        if (parts.length != 2
                || !player.getFaction().equalsIgnoreCase(parts[0])
                || !player.getFaction().equalsIgnoreCase(game.getStoredValue(PENDING_PLAYER_KEY))
                || dreamAgentOwner == null
                || dreamAgentOwner
                        .getLeader(ID)
                        .map(leader -> !leader.isExhausted())
                        .orElse(true)) {
            MessageHelper.sendMessageToEventChannel(event, "Only the chosen active player may choose that anomaly.");
            return;
        }
        Tile tile = game.getTileByPosition(parts[1]);
        if (tile == null || tile.isHomeSystem(game) || !tile.isAnomaly(game, player)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid non-home anomaly.");
            return;
        }
        game.setStoredValue(IGNORED_ANOMALY_TILE_KEY, tile.getPosition());
        game.setStoredValue(IGNORED_ANOMALY_PLAYER_KEY, player.getFaction());
        game.removeStoredValue(PENDING_PLAYER_KEY);
        game.removeStoredValue(PENDING_OWNER_KEY);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + ", will ignore the movement effects of "
                        + tile.getRepresentationForButtons(game, player) + " during this tactical action.");
    }

    @ButtonHandler(DECLINE)
    static void decline(ButtonInteractionEvent event, Game game) {
        clearIgnoredAnomaly(game);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean playerIgnoresAnomaly(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null) return false;
        return tile.getPosition().equalsIgnoreCase(game.getStoredValue(IGNORED_ANOMALY_TILE_KEY))
                && player.getFaction().equalsIgnoreCase(game.getStoredValue(IGNORED_ANOMALY_PLAYER_KEY));
    }

    public static void clearIgnoredAnomaly(Game game) {
        game.removeStoredValue(IGNORED_ANOMALY_TILE_KEY);
        game.removeStoredValue(IGNORED_ANOMALY_PLAYER_KEY);
        game.removeStoredValue(PENDING_PLAYER_KEY);
        game.removeStoredValue(PENDING_OWNER_KEY);
    }

    public static List<Tile> anomalyTiles(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> tile.isAnomaly(game, null))
                .toList();
    }
}
