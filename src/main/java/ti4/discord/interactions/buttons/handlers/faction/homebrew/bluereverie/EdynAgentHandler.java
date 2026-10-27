package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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

@UtilityClass
public class EdynAgentHandler {
    private static final String AGENT = "edynagent_br";
    private static final String USE_AGENT = "useEdynAgentBr_";
    private static final String DECLINE_AGENT = "declineEdynAgentBr_";

    public static boolean canUseToAllowCoexistence(
            Game game, Player agentOwner, Player landingPlayer, UnitHolder unitHolder) {
        if (!agentOwner.hasUnexhaustedLeader(AGENT)
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

    public static Button getCoexistenceButton(Player agentOwner, Player landingPlayer, String planet) {
        return Buttons.green(
                agentOwner.factionButtonChecker() + USE_AGENT + landingPlayer.getFaction() + "|" + planet,
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
                            getCoexistenceButton(agentOwner, landingPlayer, unitHolder.getName()),
                            Buttons.red(
                                    agentOwner.factionButtonChecker() + DECLINE_AGENT + landingPlayer.getFaction() + "|"
                                            + unitHolder.getName(),
                                    "Decline")));
        }
    }

    @ButtonHandler(USE_AGENT)
    public static void useEdynAgent(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] payload = buttonID.substring(USE_AGENT.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player landingPlayer = game.getPlayerFromColorOrFaction(payload[0]);
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(payload[1]);
        if (landingPlayer == null || !canUseToAllowCoexistence(game, agentOwner, landingPlayer, unitHolder)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Allant can no longer be used for that landing.");
            return;
        }
        AgentLifecycle.spend(game, agentOwner, AGENT);
        ButtonHelperAbilities.resolveCoexistence(event, game, landingPlayer, payload[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                landingPlayer.getCorrectChannel(),
                landingPlayer.getRepresentationNoPing() + " entered coexistence on "
                        + Helper.getPlanetRepresentation(payload[1], game) + " with Allant, the Edyn agent.");
    }

    @ButtonHandler(DECLINE_AGENT)
    public static void declineEdynAgent(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        ButtonHelper.deleteMessage(event);
    }
}
