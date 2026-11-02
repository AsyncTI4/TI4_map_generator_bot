package ti4.service.leader.agent.modules.homebrew.bluereverie;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class EdynBrAgent extends AgentModule<EdynBrAgent.Landing> {

    static final String ID = "edynagent_br";
    private static final String USE_AGENT = "useEdynAgentBr_";
    private static final String DECLINE_AGENT = "declineEdynAgentBr_";
    private static final String SEPARATOR = "|";

    public record Landing(Player landingPlayer, String planet) {}

    public EdynBrAgent() {
        super(ID, "Allant, the Edyn");
    }

    public static boolean canUseToAllowCoexistence(
            Game game, Player agentOwner, Player landingPlayer, UnitHolder unitHolder) {
        if (!agentOwner.hasUnexhaustedLeader(ID)
                || landingPlayer == null
                || unitHolder == null
                || TwilightsFallMonumentsButtonHandler.preventsCoexistence(
                        game, game.getTileFromPlanet(unitHolder.getName()))) {
            return false;
        }
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, unitHolder);
        return players.contains(landingPlayer)
                && players.stream()
                        .anyMatch(player -> player != landingPlayer && !landingPlayer.isPlayerMemberOfAlliance(player));
    }

    public static Button coexistenceButton(Player agentOwner, Player landingPlayer, String planet) {
        return Buttons.green(
                agentOwner.factionButtonChecker() + USE_AGENT + landingPlayer.getFaction() + SEPARATOR + planet,
                "Use Allant to Enter Coexistence",
                FactionEmojis.edyn);
    }

    public static void offerCoexistence(Game game, Player landingPlayer, UnitHolder unitHolder) {
        for (Player agentOwner : game.getRealPlayers()) {
            if (agentOwner == landingPlayer || !canUseToAllowCoexistence(game, agentOwner, landingPlayer, unitHolder)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentation() + ", " + landingPlayer.getRepresentationNoPing()
                            + " committed units to "
                            + Helper.getPlanetRepresentation(unitHolder.getName(), game)
                            + ". You may exhaust Allant to allow their units to enter coexistence.",
                    List.of(
                            coexistenceButton(agentOwner, landingPlayer, unitHolder.getName()),
                            Buttons.red(agentOwner.factionButtonChecker() + DECLINE_AGENT, "Decline")));
        }
    }

    @Override
    public Optional<Landing> decode(Game game, Player user, String payload) {
        String planet = StringUtils.substringAfter(payload, SEPARATOR);
        return AgentTargets.player(game, StringUtils.substringBefore(payload, SEPARATOR))
                .filter(landingPlayer -> !planet.isEmpty())
                .map(landingPlayer -> new Landing(landingPlayer, planet));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Landing landing) {
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(landing.planet());
        if (!canUseToAllowCoexistence(game, user, landing.landingPlayer(), unitHolder)) {
            return Optional.of("Allant can no longer be used for that landing");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Landing> use) {
        Player landingPlayer = use.payload().landingPlayer();
        String planet = use.payload().planet();
        if (use.event() instanceof ButtonInteractionEvent pressed) {
            ButtonHelperAbilities.resolveCoexistence(pressed, use.game(), landingPlayer, planet);
        }
        MessageHelper.sendMessageToChannel(
                landingPlayer.getCorrectChannel(),
                landingPlayer.getRepresentationNoPing() + " entered coexistence on "
                        + Helper.getPlanetRepresentation(planet, use.game()) + " with "
                        + use.agentNameFor(landingPlayer)
                        + ".");
        return AgentOutcome.none();
    }

    @ButtonHandler(USE_AGENT)
    static void use(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (AgentLifecycle.use(game, agentOwner, ID, buttonID.substring(USE_AGENT.length()), event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(DECLINE_AGENT)
    static void decline(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }
}
