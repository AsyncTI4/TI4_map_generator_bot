package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class ShuffleDiscardBackIntoDeckAgendas extends AgendaSubcommandData {
    public ShuffleDiscardBackIntoDeckAgendas() {
        super(Constants.SHUFFLE_DISCARD_BACK_INTO_DECK, "Shuffle agenda back into deck from discards");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        boolean success = activeMap.shuffleBackIntoDeck(option.getAsInt());
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda Shuffled back into deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda found");
        }
    }
}
