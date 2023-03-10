package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class AgendaRemoveFromGame extends CustomSubcommandData {
    public AgendaRemoveFromGame() {
        super(Constants.AGENDA_REMOVE_FROM_GAME, "Agenda remove from game");
        addOptions(new OptionData(OptionType.STRING, Constants.AGENDA_ID, "Agenda ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping soOption = event.getOption(Constants.AGENDA_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify Agenda");
            return;
        }
        boolean removed = activeMap.removeAgendaFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda removed from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda not found in game deck");
        }
    }
}
