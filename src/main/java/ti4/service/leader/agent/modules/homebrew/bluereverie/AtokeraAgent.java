package ti4.service.leader.agent.modules.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class AtokeraAgent extends AgentModule<Player> {

    static final String ID = "atokeraagent";
    private static final String SELECT_PLAYER = "selectAtokeraAgentPlayer_";
    private static final String SELECT_PLANET = "selectAtokeraAgentPlanet_";
    private static final String PENDING = "atokeraAgentPending_";
    private static final String VOTES = "atokeraAgentVotes_";

    public AtokeraAgent() {
        super(ID, "Magruda, the Atokera");
    }

    public static Button button(Player holder) {
        return useButton(holder, ID, "Magruda, the Atokera Agent", FactionEmojis.atokera);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return Optional.of(user);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player ignored) {
        if (!game.getPhaseOfGame().startsWith("agenda")) {
            return Optional.of("Magruda can only be used during the agenda phase");
        }
        if (voters(game).isEmpty()) {
            return Optional.of("No player has an exhausted planet to use for voting");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player agentOwner = use.user();
        game.setStoredValue(pendingKey(agentOwner), "yes");
        List<Button> buttons = voters(game).stream()
                .map(voter -> Buttons.gray(
                        ownedStepId(agentOwner, SELECT_PLAYER + voter.getFaction(), use.viaYssaril()),
                        "Use on " + voter.getFactionNameOrColor(),
                        FactionEmojis.atokera))
                .toList();
        return AgentOutcome.of(Message.addressedTo(
                        agentOwner, ", choose the player whose exhausted planet will be used for voting.", buttons)
                .inCardsInfoThread());
    }

    private static List<Player> voters(Game game) {
        return game.getRealPlayers().stream()
                .filter(voter ->
                        game.isFowMode() || !voter.getExhaustedPlanets().isEmpty())
                .toList();
    }

    @ButtonHandler(SELECT_PLAYER)
    static void selectPlayer(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player voter = game.getPlayerFromColorOrFaction(
                AgentStepIds.parse(buttonID).id().substring(SELECT_PLAYER.length()));
        if (!"yes".equals(game.getStoredValue(pendingKey(agentOwner)))
                || !agentOwner.hasLeader(ID)
                || agentOwner.hasUnexhaustedLeader(ID)
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
        MessageHelper.sendMessageToChannelWithButtons(
                voter.getCorrectChannel(),
                voter.getRepresentation()
                        + ", choose an exhausted planet to add its resources to your votes with Magruda.",
                planetButtons(game, agentOwner, voter));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_PLANET)
    static void selectPlanet(ButtonInteractionEvent event, Game game, Player voter, String buttonID) {
        String[] payload = buttonID.substring(SELECT_PLANET.length()).split("\\|", 2);
        Player agentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String planetId = payload.length == 2 ? payload[1] : "";
        Planet planet = game.getPlanetsInfo().get(planetId);
        if (agentOwner == null
                || !"yes".equals(game.getStoredValue(pendingKey(agentOwner)))
                || !agentOwner.hasLeader(ID)
                || agentOwner.hasUnexhaustedLeader(ID)
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

    static List<Button> planetButtons(Game game, Player agentOwner, Player voter) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : voter.getExhaustedPlanets()) {
            buttons.add(Buttons.gray(
                    voter.factionButtonChecker() + SELECT_PLANET + agentOwner.getFaction() + "|" + planet,
                    "Use " + Helper.getPlanetRepresentation(planet, game),
                    FactionEmojis.atokera));
        }
        return buttons;
    }

    private static String votesKey(Player player) {
        return VOTES + player.getFaction();
    }

    private static String pendingKey(Player player) {
        return PENDING + player.getFaction();
    }
}
