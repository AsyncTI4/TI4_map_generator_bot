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

public class ListPlanets extends HelpSubcommandData {

    public ListPlanets() {
        super(Constants.LIST_PLANETS, "List all planets");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HashMap<String, String> planetList= AliasHandler.getPlanetAliasEntryList();
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);
        String message = "**__Planet List__**\n" + planetList.entrySet().stream().map(e -> {
            if (includeAliases) {
                return "> " + e.getKey() + "=" + e.getValue();
            } else {
                return "> " + e.getKey();
            }
        }).sorted().collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToThread(event.getChannel(), "Planet List", message);
    }
}
