package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AddLaw extends AgendaSubcommandData {
    public AddLaw() {
        super(Constants.ADD_LAW, "Add Agenda as Law");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
        // addOptions(new OptionData(OptionType.STRING, Constants.ELECTED, "Elected PO or anything").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Elected faction").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }

        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);

        OptionMapping optionElected = event.getOption(Constants.ELECTED);
        String optionText = null;
        if (optionElected != null) {
           optionText = optionElected.getAsString();
        }

        boolean success = activeMap.addLaw(option.getAsInt(), player != null ? player.getFaction() : optionText);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law added");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
        }
    }
}
