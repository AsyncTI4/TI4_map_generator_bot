package ti4.commands.help;

import java.util.HashMap;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListTiles extends HelpSubcommandData {

    public ListTiles() {
        super(Constants.LIST_TILES, "List all tiles");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HashMap<String, String> tilesList= AliasHandler.getTileAliasEntryList();
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);
        String message = "**__Tile List__**\n" + tilesList.entrySet().stream().map(e -> {
            if (includeAliases) {
                return "> " + e.getKey() + "=" + e.getValue();
            } else {
                return "> " + e.getKey();
            }
        }).sorted().collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Tile List", message);
    }
}
