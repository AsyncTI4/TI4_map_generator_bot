package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class KaltrimAgentHandler {
    private static final String AGENT = "kaltrimagent";
    private static final String USE_AGENT = "useKaltrimAgent_";
    private static final String DISCARD_AGENDA = "discardKaltrimAgentAgenda_";
    private static final String DECLINE = "declineKaltrimAgent";

    public static void offerAfterPoliticsSecondary(Game game, Player target) {
        for (Player agentOwner : game.getRealPlayers()) {
            if (!agentOwner.hasUnexhaustedLeader(AGENT)) continue;
            MessageHelper.sendMessageToChannelWithButtons(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentation() + ", " + target.getRepresentationNoPing()
                            + " resolved the secondary ability of **Politics**. You may use Keda Demas, the Kaltrim agent, on them.",
                    List.of(
                            Buttons.gray(
                                    agentOwner.factionButtonChecker() + USE_AGENT + target.getFaction(),
                                    "Use Keda Demas",
                                    FactionEmojis.kaltrim),
                            Buttons.red(DECLINE, "Decline")));
        }
    }

    @ButtonHandler(USE_AGENT)
    public static void useKaltrimAgent(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_AGENT.length()));
        if (!agentOwner.hasUnexhaustedLeader(AGENT) || target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Keda Demas can no longer be used on that player.");
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, agentOwner, agentOwner.unsafeGetLeader(AGENT));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", gain 1 command token due to Keda Demas, the Kaltrim agent.",
                ButtonHelper.getGainCCButtons(target));
        if (agentOwner != target && !game.getAgendas().isEmpty()) {
            String agendaId = game.getAgendas().getFirst();
            AgendaModel agenda = Mapper.getAgenda(agendaId);
            if (agenda != null) {
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                        agentOwner.getCardsInfoThread(),
                        agentOwner.getRepresentationNoPing() + ", you may discard the top agenda card.",
                        List.of(agenda.getRepresentationEmbed()),
                        List.of(
                                Buttons.red(DISCARD_AGENDA + agendaId, "Discard " + agenda.getName()),
                                Buttons.gray(DECLINE, "Keep It")));
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DISCARD_AGENDA)
    public static void discardTopAgenda(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String agendaId = buttonID.substring(DISCARD_AGENDA.length());
        if (!game.getAgendas().isEmpty() && agendaId.equals(game.getAgendas().getFirst())) {
            game.discardSpecificAgenda(agendaId);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " discarded the top agenda with Keda Demas.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE)
    public static void declineKaltrimAgent(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }
}
