package ti4.commands.agenda;

import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class PutAgendaTop extends AgendaSubcommandData {
    public PutAgendaTop() {
        super(Constants.PUT_TOP, "Put Agenda top");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }


    public void putTop(GenericInteractionCreateEvent event, int agendaID, Game activeGame) {
        boolean success = activeGame.putAgendaTop(agendaID);
        if (success && !activeGame.isFoWMode()) {

            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Agenda put on top");

            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
            if (threadChannels == null) return;
            String threadName = activeGame.getName()+"-round-"+ activeGame.getRound()+"-politics";
            // SEARCH FOR EXISTING OPEN THREAD
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    MessageHelper.sendMessageToChannel(threadChannel_, "Agenda put on top");
                }
            }


        } else {
            if (!activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "No Agenda ID found");
            }

        }
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        putTop(event, option.getAsInt(), activeGame);
    }
}
