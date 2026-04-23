package ti4.discord.interactions.slashcommands.search;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.helpers.Constants;

public class FindCommand implements ParentCommand {

    private final List<OptionData> options = List.of(
            new OptionData(OptionType.STRING, Constants.SEARCH_TYPE, "What kind of component to search")
                    .setRequired(true)
                    .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.SEARCH, "Free text or exact id").setRequired(true),
            new OptionData(
                            OptionType.STRING,
                            Constants.SOURCE,
                            "Optional source filter. Defaults to base, pok, and thunders_edge")
                    .setAutoComplete(true));

    @Override
    public String getName() {
        return Constants.FIND;
    }

    @Override
    public String getDescription() {
        return "Fuzzy text search across one or many component types";
    }

    @Override
    public List<OptionData> getOptions() {
        return options;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FindService.execute(event);
    }
}
