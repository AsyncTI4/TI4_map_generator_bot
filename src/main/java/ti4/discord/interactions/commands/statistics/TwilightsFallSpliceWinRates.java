package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

class TwilightsFallSpliceWinRates extends Subcommand {

    private static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    private static final String HOMEBREW_FILTER = "has_homebrew";
    private static final String FOG_FILTER = "is_fog";

    TwilightsFallSpliceWinRates() {
        super(
                Constants.TWILIGHTS_FALL_SPLICE_WIN_RATES,
                "Twilight's Fall splice ability, unit upgrade, and genome win rates");
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter by player count, e.g. 3-8"));
        addOptions(new OptionData(
                OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter by victory point goal, e.g. 10-14"));
        addOptions(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter by if the game is a fog game"));
        addOptions(new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter by if the game has any homebrew"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TwilightsFallSpliceWinRateStatisticsService.queueReply(event);
    }
}
