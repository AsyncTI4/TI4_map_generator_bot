package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
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
        Map activeMap = getActiveMap();
        OptionMapping revealFromBottomOption = event.getOption(Constants.REVEAL_FROM_BOTTOM);
        boolean revealFromBottom = false;
        if (revealFromBottomOption != null) {
            revealFromBottom = revealFromBottomOption.getAsBoolean();
        }
        revealAgenda(event, revealFromBottom, activeMap, event.getChannel());
    }


    public void revealAgenda(GenericInteractionCreateEvent event, boolean revealFromBottom, Map activeMap, MessageChannel channel) {

        String id = activeMap.revealAgenda(revealFromBottom);
        LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(id);

        activeMap.setCurrentPhase("agendawaiting");
        AgendaModel agendaDetails = Mapper.getAgenda(id);
        String agendaTarget = agendaDetails.getTarget();
        String agendaType = agendaDetails.getType();
        String agendaName = agendaDetails.getName();

        if(agendaName.equalsIgnoreCase("Emergency Session"))
        {
            MessageHelper.sendMessageToChannel(channel, "# "+Helper.getGamePing(activeMap.getGuild(), activeMap)+" Emergency Session revealed. This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            revealAgenda(event, revealFromBottom, activeMap, channel);
            return;
        }
        if(agendaTarget.contains("Law") && (activeMap.getLaws().isEmpty() || activeMap.getLaws().size() == 0))
        {
            MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeMap.getGuild(), activeMap)+"An \"Elect Law\" Agenda ("+agendaName+") was revealed when no laws in play, flipping next agenda");
            revealAgenda(event, revealFromBottom, activeMap, channel);
            return;
        }
        if (agendaName!= null && !agendaName.equalsIgnoreCase("Covert Legislation")) {
            activeMap.setCurrentAgendaInfo(agendaType+"_"+agendaTarget + "_"+uniqueID);
        } else {
            boolean notEmergency = false;
            while(!notEmergency)
            {
                if(agendaName.equalsIgnoreCase("Emergency Session"))
                {
                    String id3 = activeMap.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeMap.getGuild(), activeMap)+" Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                String id2 = activeMap.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaDetails.getName();
                activeMap.setCurrentAgendaInfo(agendaType+"_"+agendaTarget+"_CL");
                if(agendaName.equalsIgnoreCase("Emergency Session"))
                {
                    notEmergency = false;
                }
                else
                {
                    notEmergency = true;
                }

            }
            
        }
        activeMap.resetCurrentAgendaVotes();
        activeMap.setHackElectionStatus(false);
        activeMap.setPlayersWhoHitPersistentNoAfter("");
        activeMap.setPlayersWhoHitPersistentNoWhen("");
        activeMap.setLatestOutcomeVotedFor("");
        activeMap.setLatestWhenMsg("");
        activeMap.setLatestAfterMsg("");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getAgendaRepresentation(id, uniqueID));
        String text = Helper.getGamePing(event, activeMap) + " Please indicate whether you abstain from playing whens/afters by pressing the buttons below. If you have an action card with those windows, you can simply play it.";

        
        Date newTime = new Date();
        activeMap.setLastActivePlayerPing(newTime);
        List<Button> whenButtons = AgendaHelper.getWhenButtons(activeMap);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(activeMap);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), text);

        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, Emojis.nowhens, activeMap, whenButtons, "when");
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, Emojis.noafters,activeMap, afterButtons,"after");

        ListVoteCount.turnOrder(event, activeMap, channel);
        Button proceed = Button.danger( "proceedToVoting", "Skip waiting and start the voting for everyone");
        List<Button> proceedButtons = new ArrayList<>(List.of(proceed));
        Button transaction = Button.primary("transaction", "Transaction");
        proceedButtons.add(transaction);
        MessageHelper.sendMessageToChannelWithButtons(channel, "Press this button if the last person forgot to react, but verbally said no whens/afters. Also a transaction button", proceedButtons);

    }
}
