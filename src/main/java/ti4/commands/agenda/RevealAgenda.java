package ti4.commands.agenda;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

public class RevealAgenda extends AgendaSubcommandData {
    public RevealAgenda() {
        super(Constants.REVEAL, "Reveal top Agenda from deck");
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM, "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        OptionMapping revealFromBottomOption = event.getOption(Constants.REVEAL_FROM_BOTTOM);
        boolean revealFromBottom = false;
        if (revealFromBottomOption != null) {
            revealFromBottom = revealFromBottomOption.getAsBoolean();
        }
        revealAgenda(event, revealFromBottom, activeGame, event.getChannel());
    }


    public void revealAgenda(GenericInteractionCreateEvent event, boolean revealFromBottom, Game activeGame, MessageChannel channel) {

        String id = activeGame.revealAgenda(revealFromBottom);
        LinkedHashMap<String, Integer> discardAgendas = activeGame.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(id);

        activeGame.setCurrentPhase("agendawaiting");
        AgendaModel agendaDetails = Mapper.getAgenda(id);
        String agendaTarget = agendaDetails.getTarget();
        String agendaType = agendaDetails.getType();
        String agendaName = agendaDetails.getName();
        boolean cov = false;

        if("Emergency Session".equalsIgnoreCase(agendaName))
        {
            MessageHelper.sendMessageToChannel(channel, "# "+Helper.getGamePing(activeGame.getGuild(), activeGame)+" Emergency Session revealed. This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            revealAgenda(event, revealFromBottom, activeGame, channel);
            return;
        }
        if(agendaTarget.contains("Law") && (activeGame.getLaws().isEmpty() || activeGame.getLaws().size() == 0))
        {
            MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeGame.getGuild(), activeGame)+"An \"Elect Law\" Agenda ("+agendaName+") was revealed when no laws in play, flipping next agenda");
            revealAgenda(event, revealFromBottom, activeGame, channel);
            return;
        }
        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            activeGame.setCurrentAgendaInfo(agendaType+"_"+agendaTarget + "_"+uniqueID+"_"+id);
        } else {
            boolean notEmergency = false;
            while(!notEmergency)
            {
                if("Emergency Session".equalsIgnoreCase(agendaName))
                {
                    activeGame.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeGame.getGuild(), activeGame)+" Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                if(agendaTarget.toLowerCase().contains("elect law") && activeGame.getLaws().size() < 1){
                    activeGame.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeGame.getGuild(), activeGame)+" an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                String id2 = activeGame.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaDetails.getName();
                activeGame.setCurrentAgendaInfo(agendaType+"_"+agendaTarget+"_CL_covert");
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName);
                if(agendaTarget.toLowerCase().contains("elect law") && activeGame.getLaws().size() < 1){
                    notEmergency = false;
                }
                if(notEmergency){
                    cov = true;

                     Player speaker = null;
                    if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
                        speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
                    } 
                    if(speaker != null){
                        StringBuilder sb = new StringBuilder();
                        Map.Entry<String, Integer> entry = activeGame.drawAgenda();
                        sb.append("-----------\n");
                        sb.append("Game: ").append(activeGame.getName()).append("\n");
                        sb.append(ButtonHelper.getTrueIdentity(speaker, activeGame)).append("\n");
                        sb.append("Drawn Agendas:\n");
                        sb.append(1).append(". ").append(Helper.getAgendaRepresentation(entry.getKey(), entry.getValue()));
                        sb.append("\n");
                        MessageHelper.sendMessageToChannel(speaker.getCardsInfoThread(activeGame), sb.toString());
                    }
                }

            }
            
        }
        activeGame.resetCurrentAgendaVotes();
        activeGame.setHackElectionStatus(false);
        activeGame.setPlayersWhoHitPersistentNoAfter("");
        activeGame.setPlayersWhoHitPersistentNoWhen("");
        activeGame.setLatestOutcomeVotedFor("");
        activeGame.setLatestWhenMsg("");
        activeGame.setLatestAfterMsg("");
        MessageHelper.sendMessageToChannel(channel, Helper.getAgendaRepresentation(id, uniqueID));
        String text = Helper.getGamePing(event, activeGame) + " Please indicate whether you abstain from playing whens/afters below. If you have an action card with those windows, you can simply play it.";

        
        Date newTime = new Date();
        activeGame.setLastActivePlayerPing(newTime);
        List<Button> whenButtons = AgendaHelper.getWhenButtons(activeGame);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);

        MessageHelper.sendMessageToChannel(channel, text);

        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", activeGame, whenButtons, "when");
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", activeGame, afterButtons,"after");

        ListVoteCount.turnOrder(event, activeGame, channel);
        Button proceed = Button.danger( "proceedToVoting", "Skip waiting and start the voting for everyone");
        List<Button> proceedButtons = new ArrayList<>(List.of(proceed));
        Button transaction = Button.primary("transaction", "Transaction");
        proceedButtons.add(transaction);
        proceedButtons.add(Button.danger("eraseMyVote", "Erase my vote & have me vote again"));
        MessageHelper.sendMessageToChannelWithButtons(channel, "Press this button if the last person forgot to react, but verbally said no whens/afters", proceedButtons);
        if(cov){
            MessageHelper.sendMessageToChannel(channel, "# "+Helper.getGamePing(activeGame.getGuild(), activeGame)+" the agenda target is "+agendaTarget+". Sent the agenda to the speakers cards info");
        }
    }
}
