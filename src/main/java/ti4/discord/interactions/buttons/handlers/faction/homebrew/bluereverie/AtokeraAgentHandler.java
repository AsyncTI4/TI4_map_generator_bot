package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class AtokeraAgentHandler {
    private static final String AGENT = "atokeraagent";
    private static final String USE_AGENT = "useAtokeraAgent";
    private static final String SELECT_PLAYER = "selectAtokeraAgentPlayer_";
    private static final String SELECT_PLANET = "selectAtokeraAgentPlanet_";
    private static final String PENDING = "atokeraAgentPending_";
    private static final String VOTES = "atokeraAgentVotes_";

    @ButtonHandler(USE_AGENT)
    public static void useAtokeraAgent(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Magruda must be ready to use this ability.");
            return;
        }
        if (!game.getPhaseOfGame().startsWith("agenda")) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Magruda can only be used during the agenda phase.");
            return;
        }
        List<Player> voters = game.getRealPlayers().stream()
                .filter(voter ->
                        game.isFowMode() || !voter.getExhaustedPlanets().isEmpty())
                .toList();
        if (voters.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No player has an exhausted planet to use for voting.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, player, player.unsafeGetLeader(AGENT));
        game.setStoredValue(pendingKey(player), "yes");
        List<Button> buttons = voters.stream()
                .map(voter -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_PLAYER + voter.getFaction(),
                        "Use on " + voter.getFactionNameOrColor(),
                        FactionEmojis.atokera))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", choose the player whose exhausted planet will be used for voting.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_PLAYER)
    public static void selectAtokeraAgentPlayer(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player voter = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_PLAYER.length()));
        if (!"yes".equals(game.getStoredValue(pendingKey(agentOwner)))
                || !agentOwner.hasLeader(AGENT)
                || agentOwner.hasUnexhaustedLeader(AGENT)
                || !game.getPhaseOfGame().startsWith("agenda")
                || voter == null
                || voter.getExhaustedPlanets().isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    game.isFowMode()
                            ? "Magruda could not be resolved."
                            : "That player is no longer eligible for Magruda.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planet : voter.getExhaustedPlanets()) {
            buttons.add(Buttons.gray(
                    voter.factionButtonChecker() + SELECT_PLANET + agentOwner.getFaction() + "|" + planet,
                    "Use " + Helper.getPlanetRepresentation(planet, game),
                    FactionEmojis.atokera));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                voter.getCorrectChannel(),
                voter.getRepresentation()
                        + ", choose an exhausted planet to add its resources to your votes with Magruda.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectAtokeraAgentPlanet(
            ButtonInteractionEvent event, Game game, Player voter, String buttonID) {
        String[] payload = buttonID.substring(SELECT_PLANET.length()).split("\\|", 2);
        Player agentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String planetId = payload.length == 2 ? payload[1] : "";
        Planet planet = game.getPlanetsInfo().get(planetId);
        if (agentOwner == null
                || !"yes".equals(game.getStoredValue(pendingKey(agentOwner)))
                || !agentOwner.hasLeader(AGENT)
                || agentOwner.hasUnexhaustedLeader(AGENT)
                || !game.getPhaseOfGame().startsWith("agenda")
                || planet == null
                || !voter.getPlanets().contains(planetId)
                || !voter.getExhaustedPlanets().contains(planetId)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible for Magruda.");
            return;
        }

        game.setStoredValue(votesKey(voter), Integer.toString(planet.getResources()));
        game.removeStoredValue(pendingKey(agentOwner));
        voter.refreshPlanet(planetId);
        MessageHelper.sendMessageToChannel(
                voter.getCorrectChannel(),
                voter.getRepresentation() + " added " + planet.getResources() + " vote"
                        + (planet.getResources() == 1 ? "" : "s") + " with Magruda and readied "
                        + Helper.getPlanetRepresentation(planetId, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static int getVotes(Player player, Game game) {
        String votes = game.getStoredValue(votesKey(player));
        return votes.isEmpty() ? 0 : Integer.parseInt(votes);
    }

    public static void clearVotes(Player player, Game game) {
        game.removeStoredValue(votesKey(player));
    }

    private static String votesKey(Player player) {
        return VOTES + player.getFaction();
    }

    private static String pendingKey(Player player) {
        return PENDING + player.getFaction();
    }
}
