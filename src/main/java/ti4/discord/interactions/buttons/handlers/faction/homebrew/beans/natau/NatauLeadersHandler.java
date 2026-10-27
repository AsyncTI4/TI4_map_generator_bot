package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;

@UtilityClass
public class NatauLeadersHandler {
    private static final String AGENT = "natauagent";
    private static final String AGENT_OFFER = "natauAgentOffer_";
    private static final String AGENT_CHOOSE = "natauAgentChoose_";
    private static final String AGENT_ANOMALY = "natauAgentAnomaly_";

    public static Button getNatauAgentCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + AGENT_OFFER + "active", "Use Natau Agent", FactionEmojis.natau);
    }

    public static void offerNatauAgentButtons(Game game, Player activePlayer) {
        if (getNatauAgentAnomalies(game).isEmpty()) {
            return;
        }
        for (Player agentOwner : game.getRealPlayers()) {
            if (!agentOwner.hasUnexhaustedLeader(AGENT)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentationNoPing() + ", you may exhaust Serael, the Natau agent, to let "
                            + activePlayer.getRepresentationNoPing()
                            + " treat a non-home anomaly as adjacent to the active system this tactical action.",
                    List.of(
                            Buttons.gray(
                                    agentOwner.factionButtonChecker() + AGENT_OFFER + activePlayer.getFaction(),
                                    "Use Natau Agent",
                                    FactionEmojis.natau),
                            Buttons.red("deleteButtons", "Decline")));
        }
    }

    @ButtonHandler(AGENT_OFFER)
    public static void offerNatauAgentAnomalies(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (!agentOwner.hasUnexhaustedLeader(AGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Serael, the Natau agent, is no longer available.");
            return;
        }
        Player activePlayer = game.getActivePlayer();
        String targetFaction = buttonID.substring(AGENT_OFFER.length());
        if (activePlayer == null
                || (!"active".equals(targetFaction)
                        && !activePlayer.getFaction().equals(targetFaction))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "This agent may only be used after the current active player activates a system.");
            return;
        }

        List<Tile> anomalies = getNatauAgentAnomalies(game);
        if (anomalies.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no valid non-home anomalies to choose.");
            return;
        }

        AgentLifecycle.spend(game, agentOwner, AGENT);
        List<Button> buttons = getNatauAgentAnomalyButtons(game, activePlayer);
        String message = activePlayer.getRepresentation()
                + ", choose the non-home anomaly to treat as adjacent to the active system this tactical action.";
        NewStuffHelper.sendPaginated(
                activePlayer.getCorrectChannel(),
                message,
                buttons,
                List.of(),
                activePlayer.factionButtonChecker() + AGENT_CHOOSE);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(AGENT_CHOOSE)
    public static void chooseNatauAgentAnomaly(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getNatauAgentAnomalyButtons(game, player);
        String message = player.getRepresentation()
                + ", choose the non-home anomaly to treat as adjacent to the active system this tactical action.";
        String prefix = player.factionButtonChecker() + AGENT_CHOOSE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }
        if (player != game.getActivePlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only the active player may choose the anomaly.");
            return;
        }
        Tile anomaly = game.getTileByPosition(buttonID.substring(AGENT_CHOOSE.length()));
        if (!isNatauAgentAnomaly(game, anomaly)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That is not a valid non-home anomaly.");
            return;
        }

        game.setStoredValue(agentAnomalyKey(player), anomaly.getPosition());
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
        String anomalyPosition = game.getStoredValue(agentAnomalyKey(player));
        return (position.equals(game.getActiveSystem()) && otherPosition.equals(anomalyPosition))
                || (position.equals(anomalyPosition) && otherPosition.equals(game.getActiveSystem()));
    }

    public static void clearAgentAdjacency(Game game, Player player) {
        game.removeStoredValue(agentAnomalyKey(player));
    }

    public static boolean canMoveOutOfLockedAnomaly(Game game, Player player, Tile tile) {
        return tile != null
                && player.getLeader("natauhero").map(Leader::isActive).orElse(false)
                && tile.getTileModel().isAnomaly()
                && CommandCounterHelper.hasCC(player, tile);
    }

    private static List<Tile> getNatauAgentAnomalies(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> isNatauAgentAnomaly(game, tile))
                .toList();
    }

    private static List<Button> getNatauAgentAnomalyButtons(Game game, Player player) {
        return getNatauAgentAnomalies(game).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + AGENT_CHOOSE + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static boolean isNatauAgentAnomaly(Game game, Tile tile) {
        return tile != null && !tile.isHomeSystem(game) && tile.isAnomaly(game, null);
    }

    private static String agentAnomalyKey(Player player) {
        return AGENT_ANOMALY + player.getFaction();
    }
}
