package ti4.discord.interactions.buttons.handlers.options;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
class PlayerPreferenceButtonHandler {

    @ButtonHandler("sandbagPref_")
    public static void sandbagPref(ButtonInteractionEvent event, Player player, String buttonID) {
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setSandbagPref(buttonID.split("_")[1]);
        UserSettingsManager.save(userSettings);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Decision saved.");
    }

    @ButtonHandler("setAutoPassMedian_")
    public static void setAutoPassMedian(ButtonInteractionEvent event, Player player, String buttonID) {
        String hours = buttonID.split("_")[1];
        int median = Integer.parseInt(hours);
        player.setAutoSaboPassMedian(median);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours.");
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setAutoNoSaboInterval(median);
        UserSettingsManager.save(userSettings);
        if (median > 0) {
            if (!player.hasAbility("quash")
                    && !player.ownsPromissoryNote("rider")
                    && !player.getPromissoryNotes().containsKey("riderm")
                    && !player.hasAbility("radiance")
                    && !player.hasAbility("galactic_threat")
                    && !player.hasAbility("conspirators")
                    && !player.ownsPromissoryNote("riderx")
                    && !player.ownsPromissoryNote("riderm")
                    && !player.ownsPromissoryNote("ridera")
                    && !player.hasTechReady("gr")) {
                if (!userSettings.isPrefersPassOnWhensAfters()) {
                    List<Button> buttons = new ArrayList<>();
                    String msg = player.toString()
                            + ", the bot may also auto react for you when you have no \"when\"s or \"after\"s."
                            + " Default for this is off. This will only apply to this game."
                            + " If you have any \"when\"s or \"after\"s or related \"when\"/\"after\" abilities, it will not do anything. ";
                    buttons.add(Buttons.green("playerPrefDecision_true_agenda", "Turn on"));
                    buttons.add(Buttons.green("playerPrefDecision_false_agenda", "Turn off"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
                } else {
                    player.setAutoPassOnWhensAfters(true);
                }
            }
        }

        ButtonHelper.deleteMessage(event);
    }
}
