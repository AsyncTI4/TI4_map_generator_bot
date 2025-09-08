package ti4.service.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.website.UltimateStatisticsWebsiteHelper;

public class StatisticsOptInOutService {

    public static void optOut(SlashCommandInteractionEvent event) {
        var statisticsOpIn = new StatisticOptIn();
        statisticsOpIn.setPlayerDiscordId(event.getUser().getId());
        statisticsOpIn.setExcludeFromAsyncStats(true);

        UltimateStatisticsWebsiteHelper.sendStatisticsOptIn(statisticsOpIn, event.getChannel());
    }

    public static void optIn(SlashCommandInteractionEvent event) {
        var statisticsOpIn = new StatisticOptIn();
        statisticsOpIn.setPlayerDiscordId(event.getUser().getId());
        statisticsOpIn.setShowWinRates(getOption(event, "win_rates"));
        statisticsOpIn.setShowTurnStats(getOption(event, "turns"));
        statisticsOpIn.setShowCombatStats(getOption(event, "combats"));
        statisticsOpIn.setShowVpStats(getOption(event, "victory_points"));
        statisticsOpIn.setShowFactionStats(getOption(event, "factions"));
        statisticsOpIn.setShowOpponents(getOption(event, "opponents"));
        statisticsOpIn.setShowGames(getOption(event, "games"));

        UltimateStatisticsWebsiteHelper.sendStatisticsOptIn(statisticsOpIn, event.getChannel());
    }

    private static boolean getOption(SlashCommandInteractionEvent event, String optionName) {
        return event.getOption(optionName, false, OptionMapping::getAsBoolean);
    }
}
