package ti4.service.leader.agent.modules.homebrew.bluereverie;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class KaltrimAgent extends TargetedAgent {

    static final String ID = "kaltrimagent";
    private static final String DISCARD_AGENDA = "discardKaltrimAgentAgenda_";
    private static final String DECLINE = "declineKaltrimAgent";

    public KaltrimAgent() {
        super(ID, "Keda Demas, the Kaltrim");
    }

    public static void offerAfterPoliticsSecondary(Game game, Player target) {
        for (Player agentOwner : game.getRealPlayers()) {
            if (!agentOwner.hasUnexhaustedLeader(ID)) continue;
            MessageHelper.sendMessageToChannelWithButtons(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentation() + ", " + target.getRepresentationNoPing()
                            + " resolved the secondary ability of **Politics**. You may use Keda Demas, the Kaltrim agent, on them.",
                    List.of(
                            Buttons.gray(
                                    ownedButtonId(agentOwner, ID, target.getFaction()),
                                    useLabel(agentOwner, ID, "Keda Demas"),
                                    FactionEmojis.kaltrim),
                            Buttons.red(DECLINE, "Decline")));
        }
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player agentOwner = use.user();
        Player target = use.payload();
        if (agentOwner != target && !game.getAgendas().isEmpty()) {
            offerToDiscardTopAgenda(game, agentOwner);
        }
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", gain 1 command token due to " + use.agentNameFor(target) + ".",
                ButtonHelper.getGainCCButtons(target)));
    }

    private static void offerToDiscardTopAgenda(Game game, Player agentOwner) {
        String agendaId = game.getAgendas().getFirst();
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        if (agenda == null) {
            return;
        }
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                agentOwner.getCardsInfoThread(),
                agentOwner.getRepresentationNoPing() + ", you may discard the top agenda card.",
                List.of(agenda.getRepresentationEmbed()),
                List.of(
                        Buttons.red(DISCARD_AGENDA + agendaId, "Discard " + agenda.getName()),
                        Buttons.gray(DECLINE, "Keep It")));
    }

    @ButtonHandler(DISCARD_AGENDA)
    static void discardTopAgenda(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
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
    static void decline(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }
}
