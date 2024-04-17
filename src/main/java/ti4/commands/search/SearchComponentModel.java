package ti4.commands.search;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;

public abstract class SearchComponentModel extends SearchSubcommandData {
    public SearchComponentModel(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string.").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "Limit results to a specific source.").setAutoComplete(true));
    }
}
