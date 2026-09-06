package ti4.service.statistics;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.settings.users.UserSettingsManager;
import ti4.website.UltimateStatisticsWebsiteHelper;

public final class StatisticsOptInOutService {

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
        return event.getOption(optionName, Boolean.FALSE, OptionMapping::getAsBoolean);
    }

    public static List<Button> getOptInButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String key = player.getFaction() + "optin" + "winrate";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Win Rate"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Win Rate"));
        }

        key = player.getFaction() + "optin" + "vps";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Victory Points"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Victory Points"));
        }

        key = player.getFaction() + "optin" + "turns";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Turn Time Stats"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Turn Time Stats"));
        }

        key = player.getFaction() + "optin" + "combats";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Combat Data"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Combat Data"));
        }

        key = player.getFaction() + "optin" + "opponents";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Opponents"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Opponents"));
        }

        key = player.getFaction() + "optin" + "factions";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Factions Played"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Factions Played"));
        }

        key = player.getFaction() + "optin" + "games";
        if (game.getStoredValue(key).isEmpty() || "no".equalsIgnoreCase(game.getStoredValue(key))) {
            buttons.add(Buttons.green("setOptInSinglePreference_" + key + "_yes", "Click To Display Games"));
        } else {
            buttons.add(Buttons.red("setOptInSinglePreference_" + key + "_no", "Click To Not Display Games"));
        }

        buttons.add(Buttons.blue("setOptInStats_acceptAll", "Click To Display All Stats"));
        buttons.add(Buttons.red("setOptInStats_declineAll", "Click To Display No Stats"));
        buttons.add(Buttons.gray("setOptInStats_some", "Click To Submit Decisions"));
        return buttons;
    }

    @ButtonHandler("setOptInSinglePreference_")
    public static void setOptInSinglePreference(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue(buttonID.split("_")[1], buttonID.split("_")[2]);
        String msg = "Altered a value";
        event.getMessage()
                .editMessage(msg)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(getOptInButtons(game, player)))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("setOptInStats_")
    public static void setOptInStats(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setHasIndicatedStatPreferences(true);
        UserSettingsManager.save(userSettings);
        String decision = buttonID.split("_")[1];
        var statisticsOpIn = new StatisticOptIn();
        statisticsOpIn.setPlayerDiscordId(event.getUser().getId());
        if ("acceptAll".equalsIgnoreCase(decision)) {
            statisticsOpIn.setShowWinRates(true);
            statisticsOpIn.setShowTurnStats(true);
            statisticsOpIn.setShowCombatStats(true);
            statisticsOpIn.setShowVpStats(true);
            statisticsOpIn.setShowFactionStats(true);
            statisticsOpIn.setShowOpponents(true);
            statisticsOpIn.setShowGames(true);
        }
        if ("declineAll".equalsIgnoreCase(decision)) {
            statisticsOpIn.setShowWinRates(false);
            statisticsOpIn.setShowTurnStats(false);
            statisticsOpIn.setShowCombatStats(false);
            statisticsOpIn.setShowVpStats(false);
            statisticsOpIn.setShowFactionStats(false);
            statisticsOpIn.setShowOpponents(false);
            statisticsOpIn.setShowGames(false);
        }
        if ("some".equalsIgnoreCase(decision)) {
            String key = player.getFaction() + "optin" + "winrate";
            statisticsOpIn.setShowWinRates("yes".equalsIgnoreCase(game.getStoredValue(key)));
            key = player.getFaction() + "optin" + "turns";
            statisticsOpIn.setShowTurnStats("yes".equalsIgnoreCase(game.getStoredValue(key)));
            key = player.getFaction() + "optin" + "combats";
            statisticsOpIn.setShowCombatStats("yes".equalsIgnoreCase(game.getStoredValue(key)));
            key = player.getFaction() + "optin" + "vps";
            statisticsOpIn.setShowVpStats("yes".equalsIgnoreCase(game.getStoredValue(key)));
            key = player.getFaction() + "optin" + "factions";
            statisticsOpIn.setShowFactionStats("yes".equalsIgnoreCase(game.getStoredValue(key)));
            key = player.getFaction() + "optin" + "opponents";
            statisticsOpIn.setShowOpponents("yes".equalsIgnoreCase(game.getStoredValue(key)));
            key = player.getFaction() + "optin" + "games";
            statisticsOpIn.setShowGames("yes".equalsIgnoreCase(game.getStoredValue(key)));
        }

        UltimateStatisticsWebsiteHelper.sendStatisticsOptIn(statisticsOpIn, player.getCardsInfoThread());

        ButtonHelper.deleteMessage(event);
    }
}
