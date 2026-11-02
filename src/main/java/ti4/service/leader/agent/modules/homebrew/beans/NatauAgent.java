package ti4.service.leader.agent.modules.homebrew.beans;

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
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class NatauAgent extends AgentModule<Player> {

    static final String ID = "natauagent";
    private static final String LEGACY_OFFER = "natauAgentOffer_";
    private static final String ACTIVE_PLAYER = "active";
    private static final String AGENT_CHOOSE = "natauAgentChoose_";
    private static final String AGENT_ANOMALY = "natauAgentAnomaly_";

    public NatauAgent() {
        super(ID, "Serael, the Natau");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                ownedButtonId(holder, ID, ACTIVE_PLAYER), useLabel(holder, ID, "Natau Agent"), FactionEmojis.natau);
    }

    public static void offerAfterActivation(Game game, Player activePlayer) {
        if (anomalies(game).isEmpty()) {
            return;
        }
        for (Player agentOwner : game.getRealPlayers()) {
            if (!agentOwner.hasUnexhaustedLeader(ID)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentationNoPing() + ", you may exhaust Serael, the Natau agent, to let "
                            + activePlayer.getRepresentationNoPing()
                            + " treat a non-home anomaly as adjacent to the active system this tactical action.",
                    List.of(
                            Buttons.gray(
                                    ownedButtonId(agentOwner, ID, activePlayer.getFaction()),
                                    useLabel(agentOwner, ID, "Natau Agent"),
                                    FactionEmojis.natau),
                            Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_OFFER)
    static void legacyOffer(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_OFFER.length()));
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        Player activePlayer = game.getActivePlayer();
        if (ACTIVE_PLAYER.equals(payload) || payload.isEmpty()) {
            return Optional.ofNullable(activePlayer);
        }
        return AgentTargets.player(game, payload).filter(target -> target == activePlayer);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player activePlayer) {
        if (anomalies(game).isEmpty()) {
            return Optional.of("There are no valid non-home anomalies to choose");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player activePlayer = use.payload();
        NewStuffHelper.sendPaginated(
                activePlayer.getCorrectChannel(),
                chooseMessage(activePlayer),
                anomalyButtons(game, activePlayer),
                List.of(),
                activePlayer.factionButtonChecker() + AGENT_CHOOSE);
        return AgentOutcome.none();
    }

    @ButtonHandler(AGENT_CHOOSE)
    static void chooseAnomaly(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = anomalyButtons(game, player);
        String prefix = player.factionButtonChecker() + AGENT_CHOOSE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, chooseMessage(player), prefix, buttonID)) {
            return;
        }
        if (player != game.getActivePlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only the active player may choose the anomaly.");
            return;
        }
        Tile anomaly = game.getTileByPosition(buttonID.substring(AGENT_CHOOSE.length()));
        if (!isAgentAnomaly(game, anomaly)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That is not a valid non-home anomaly.");
            return;
        }

        game.setStoredValue(anomalyKey(player), anomaly.getPosition());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " treats " + anomaly.getRepresentationForButtons(game, player)
                        + " as adjacent to the active system this tactical action with Serael, the Natau agent.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean treatsAsAdjacent(Game game, Player player, String position, String otherPosition) {
        if (player == null
                || player != game.getActivePlayer()
                || game.getActiveSystem().isEmpty()) {
            return false;
        }
        String anomalyPosition = game.getStoredValue(anomalyKey(player));
        return (position.equals(game.getActiveSystem()) && otherPosition.equals(anomalyPosition))
                || (position.equals(anomalyPosition) && otherPosition.equals(game.getActiveSystem()));
    }

    public static void clearAgentAdjacency(Game game, Player player) {
        game.removeStoredValue(anomalyKey(player));
    }

    private static String chooseMessage(Player activePlayer) {
        return activePlayer.getRepresentation()
                + ", choose the non-home anomaly to treat as adjacent to the active system this tactical action.";
    }

    private static List<Tile> anomalies(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> isAgentAnomaly(game, tile))
                .toList();
    }

    private static List<Button> anomalyButtons(Game game, Player player) {
        return anomalies(game).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + AGENT_CHOOSE + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static boolean isAgentAnomaly(Game game, Tile tile) {
        return tile != null && !tile.isHomeSystem(game) && tile.isAnomaly(game, null);
    }

    private static String anomalyKey(Player player) {
        return AGENT_ANOMALY + player.getFaction();
    }
}
