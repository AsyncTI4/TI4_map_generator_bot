package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class GameSurveyService {

    private static List<Button> getUserSurveyButtons(String buttonID) {
        String questionNum = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();

        switch (questionNum) {
            case "1" -> {
                buttons.add(Buttons.red("answerSurvey_No Whispers_2", "No Whispers"));
                buttons.add(Buttons.blue("answerSurvey_Limited Whispers_2", "Limited Whispers"));
                buttons.add(Buttons.green("answerSurvey_Unlimited Whispers_2", "Unlimited Whispers"));
                buttons.add(Buttons.gray("answerSurvey_No Prefeference_2", "Prefer Not To Answer"));
            }
            case "2" -> {
                buttons.add(Buttons.red("answerSurvey_Purge Supports_3", "Purge Supports"));
                buttons.add(Buttons.blue("answerSurvey_Ban Support Swaps_3", "Ban Support Swaps"));
                buttons.add(Buttons.green("answerSurvey_Keep Default Rules_3", "Keep Default Rules"));
                buttons.add(Buttons.gray("answerSurvey_No Preference_3", "Prefer Not To Answer"));
            }
            case "3" -> {
                buttons.add(Buttons.red("answerSurvey_Unanimous Agreement_4", "Unanimous Agreement"));
                buttons.add(Buttons.blue("answerSurvey_Majority Agreement_4", "Majority Agreement"));
                buttons.add(Buttons.green("answerSurvey_3rd Party Arbitration_4", "3rd Party (Moderator) Arbitration"));
                buttons.add(Buttons.gray("answerSurvey_No Preference_4", "Prefer Not To Answer"));
            }
            case "4" -> {
                buttons.add(
                        Buttons.red("answerSurvey_Might Win Make In Any Position_5", "I may winmake in any position"));
                buttons.add(Buttons.blue("answerSurvey_May Winmake If Cannot Win_5", "I may winmake if I cannot win"));
                buttons.add(Buttons.green("answerSurvey_Will Not Winmake_5", "I will not winmake"));
                buttons.add(Buttons.gray("answerSurvey_No Preference_5", "Prefer Not To Answer"));
            }
            case "5" -> {
                buttons.add(Buttons.red("answerSurvey_Dislike Space Risk More_6", "Dislike Space Risk More"));
                buttons.add(Buttons.blue("answerSurvey_Dislike Boat Float More_6", "Dislike Passive Boat Float More"));
                buttons.add(Buttons.green("answerSurvey_No Strong Feelings_6", "Equal Feelings"));
                buttons.add(Buttons.gray("answerSurvey_No Preference_6", "Prefer Not To Answer"));
            }
            case "6" -> {
                return buttons;
            }
        }

        return buttons;
    }

    @ButtonHandler("purgeSupports")
    public static void purgeSupports(ButtonInteractionEvent event, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        if (buttonID.contains("confirmed")) {
            for (Player p2 : game.getRealPlayers()) {
                p2.removeOwnedPromissoryNoteByID(p2.getColor() + "_sftt");
                p2.removePromissoryNote(p2.getColor() + "_sftt");
            }
            game.setStoredValue("removeSupports", "true");

            MessageHelper.sendMessageToChannel(event.getChannel(), "Purged _Supports For The Thrones_ from the game.");
        } else {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.red("purgeSupportsconfirmed", "Purge Supports"));
            buttons.add(Buttons.gray("deleteButtons", "Oops Mistake"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(),
                    "Please confirm that you are pressing this button to purge all _Supports for the Thrones_ from the game.",
                    buttons);
        }
    }

    @ButtonHandler("purgeOverrule")
    public static void purgeOverrule(ButtonInteractionEvent event, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        if (buttonID.contains("confirmed")) {
            game.setStoredValue("removeOverrule", "true");
            boolean removed = game.getActionCards().removeIf("overrule"::equals);

            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    removed
                            ? "Purged _Overrule_ from the action card deck."
                            : "_Overrule_ will be purged from the action card deck this game ends up using.");
        } else {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.red("purgeOverruleconfirmed", "Purge Overrule"));
            buttons.add(Buttons.gray("deleteButtons", "Oops Mistake"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(),
                    "Please confirm that you are pressing this button to purge _Overrule_ from the game.",
                    buttons);
        }
    }

    @ButtonHandler("noSupportSwaps")
    public static void noSupportSwaps(ButtonInteractionEvent event, String buttonID, Game game) {
        game.setNoSwapMode(true);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Made it so you cannot swap _Supports For The Thrones_ in this game.");
    }

    @ButtonHandler("setLimitedWhispers")
    public static void setLimitedWhispers(ButtonInteractionEvent event, String buttonID, Game game) {
        game.setLimitedWhispersMode(true);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Made it so you can send hidden (redacted) deals via the transaction button.");
    }

    @ButtonHandler(value = "answerSurvey_", save = false)
    public static void answerSurvey(GenericInteractionCreateEvent event, String buttonID) {
        String questionNum = buttonID.split("_")[2];
        String answer = buttonID.split("_")[1];
        List<Button> buttons = getUserSurveyButtons(buttonID);
        User user = event.getUser();
        var userSettings = UserSettingsManager.get(user.getId());
        String msg = user.getAsMention();
        switch (questionNum) {
            case "1" -> {
                userSettings.setHasAnsweredSurvey(true);
                msg = "### Survey Question 1/5: Whispers\n" + msg;
                msg += " What are your preferences when it comes to secret communication/whispers?";
            }
            case "2" -> {
                userSettings.setWhisperPref(answer);
                msg = "### Survey Question 2/5: Support For The Throne\n" + msg;
                msg += " What are your preferences when it comes to _Support For The Throne_? ";
            }
            case "3" -> {
                userSettings.setSupportPref(answer);
                msg = "### Survey Question 3/5: How To Handle Rollback Disputes\n" + msg;
                msg += " How would you prefer arguments over the legitimacy of a rollback to be settled? ";
            }
            case "4" -> {
                userSettings.setTakebackPref(answer);
                msg = "### Survey Question 4/5: Winmaking\n" + msg;
                msg += " What is your stance on winmaking (however you define it)? ";
            }

            case "5" -> {
                userSettings.setWinmakingPref(answer);
                msg = "### Survey Question 5/5: Meta Preferences\n" + msg;
                msg +=
                        " Many players prefer not to play \"space risk\", where the game features early and ferocious attacks without an objective providing motivation for the attacks."
                                + " Many other players prefer not to play with a \"passive boat float\" where everyone sits in their slice until the end game and players promise forever wars for the slightest early game aggression."
                                + " Which describes you better?";
            }
            case "6" -> {
                userSettings.setMetaPref(answer);
                msg += " Thank you for completing the survey."
                        + " You will see anonymous results after the first strategy phase of every game if at least two people in the game have completed the survey."
                        + " You can retake this survey at any time via /user survey, but you will never be asked to complete it again. Hope you have a good rest of your day!";
            }
        }
        if (event instanceof SlashCommandInteractionEvent sevent) {
            if (buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(sevent.getChannel(), msg);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(sevent.getChannel(), msg, buttons);
            }
        }
        if (event instanceof ButtonInteractionEvent bevent) {
            ButtonHelper.deleteMessage(bevent);
            if (buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(bevent.getChannel(), msg);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(bevent.getChannel(), msg, buttons);
            }
        }
        UserSettingsManager.save(userSettings);
    }
}
