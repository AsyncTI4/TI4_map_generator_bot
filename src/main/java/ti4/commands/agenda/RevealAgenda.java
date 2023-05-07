package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.util.ArrayList;
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
        String[] agendaDetails = Mapper.getAgenda(id).split(";");
        String agendaTarget = agendaDetails[2];
        String agendaType = agendaDetails[1];
        String agendaName = agendaDetails[0];
        if(agendaName!= null && !agendaName.equalsIgnoreCase("Covert Legislation"))
        {
            activeMap.setCurrentAgendaInfo(agendaType+"_"+agendaTarget + "_"+uniqueID);
        }
        else
        {
            String id2 = activeMap.getNextAgenda(revealFromBottom);
            String[] agendaDetails2 = Mapper.getAgenda(id2).split(";");
            agendaTarget = agendaDetails2[2];
            agendaType = agendaDetails2[1];
            agendaName = agendaDetails[0];
            activeMap.setCurrentAgendaInfo(agendaType+"_"+agendaTarget);
        }
        activeMap.resetCurrentAgendaVotes();
        activeMap.setHackElectionStatus(false);
        activeMap.setPlayersWhoHitPersistentNoAfter("");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getAgendaRepresentation(id, uniqueID));
        String text = Helper.getGamePing(event, activeMap) + " Please indicate whether you will **Play a When** or **Play an After** or not by pressing the buttons below:";

        Button playWhen = Button.danger("play_when", "Play When");
        Button noWhen = Button.primary("no_when", "No Whens").withEmoji(Emoji.fromFormatted(Emojis.nowhens));
        Button noWhenPersistent = Button.primary("no_when_persistent", "No Whens No Matter What (for this agenda)").withEmoji(Emoji.fromFormatted(Emojis.nowhens));

        List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
        
        Button playAfter = Button.danger("play_after", "Play A Non-AC Rider");
        Button noAfter = Button.primary("no_after", "No Afters").withEmoji(Emoji.fromFormatted(Emojis.noafters));
        Button noAfterPersistent = Button.primary("no_after_persistent", "No Afters No Matter What (for this agenda)").withEmoji(Emoji.fromFormatted(Emojis.noafters));
        List<Button> afterButtons = new ArrayList<>(List.of(playAfter, noAfter, noAfterPersistent));

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), text);
        
        MessageHelper.sendMessageToChannelWithButtons(channel, Emojis.nowhens, whenButtons);
        MessageHelper.sendMessageToChannelWithButtons(channel, Emojis.noafters, afterButtons);
        ListVoteCount.turnOrder(event, activeMap, channel);
    }
}
