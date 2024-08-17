package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;

public class FOWOptions extends FOWSubcommandData {

    public static final String HIDE_NAMES = "hide_player_names";
    public static final String HIDE_TOTAL_VOTES = "hide_total_votes";

    public FOWOptions() {
        super(Constants.FOW_OPTIONS, "Change options for fog of war game.");
        addOptions(new OptionData(OptionType.BOOLEAN, HIDE_NAMES, "Completely hide player Discord names (default: False)"));
        addOptions(new OptionData(OptionType.BOOLEAN, HIDE_TOTAL_VOTES, "Don't show total votes amount in agenda (default: False)"));
    }

     @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        Boolean hideNames = event.getOption(HIDE_NAMES, null, OptionMapping::getAsBoolean);
        if (hideNames != null) {
            game.setFowOption(HIDE_NAMES, Boolean.toString(hideNames));
        }

        Boolean hideTotalVotes = event.getOption(HIDE_TOTAL_VOTES, null, OptionMapping::getAsBoolean);
        if (hideTotalVotes != null) {
            game.setFowOption(HIDE_TOTAL_VOTES, Boolean.toString(hideTotalVotes));
        }
    }
}
