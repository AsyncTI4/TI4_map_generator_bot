package ti4.commands.user;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.Subcommand;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

public class SetPersonalPingInterval extends Subcommand {

    // OFFER PING INTERVAL BUTTONS
    private static final String OFFER_PING_OPTIONS = "playerPref_personalPingInterval";
    public static final Button OFFER_PING_OPTIONS_BUTTON = Buttons.gray(OFFER_PING_OPTIONS, "Change Personal Ping Interval");
    // SET PING INTERVAL BUTTONS
    private static final String SET_PING_INTERVAL = "UserSetPersonalPingIntervalTo";

    public SetPersonalPingInterval() {
        super("set_personal_ping_interval", "Set your personal ping interval");
        addOption(OptionType.INTEGER, "hours", "The number of hours between turn reminder pings. Set to 0 to disable your personal preference", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int pingInterval = event.getOption("hours", 0, OptionMapping::getAsInt);
        var userSettings = UserSettingsManager.getInstance().getUserSettings(event.getUser().getId());
        set(event, userSettings, pingInterval);
    }

    private static void set(GenericInteractionCreateEvent event, UserSettings settings, int pingInterval) {
        if (pingInterval < 0) {
            pingInterval = 0;
        }
        settings.setPersonalPingInterval(pingInterval);

        if (event == null) {
            return;
        }
        String message = "Set Personal Ping Interval to: `" + pingInterval + "`";
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    public static void set(GenericInteractionCreateEvent event, int pingInterval) {
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        set(event, userSettings, pingInterval);
    }

    @ButtonHandler(SET_PING_INTERVAL)
    public static void set(ButtonInteractionEvent event, String buttonID) {
        String pingIntervalRaw = buttonID.replace(SET_PING_INTERVAL, "");
        int pingInterval = 0;
        try {
            pingInterval = Integer.parseInt(pingIntervalRaw);
        } catch (Exception e) {
            String message = "Error - could not set ping interval to: `" + pingIntervalRaw + "`";
            MessageHelper.sendMessageToEventChannel(event, message);
            return;
        }
        event.getMessage().delete().queue();
        set(event, pingInterval);
    }

    @ButtonHandler(OFFER_PING_OPTIONS)
    public static void offerPersonalPingOptions(GenericInteractionCreateEvent event) {
        List<Button> buttons = getPersonalAutoPingButtons();
        UserSettings userSettings = UserSettingsManager.get(event.getUser().getId());
        String message = event.getUser().getAsMention() + " please select the number of hours you would like the bot to wait before it pings you that it is your turn.\n**This will apply to all your games**.\n> Your current interval is `" + userSettings.getPersonalPingInterval() + "`";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    private static List<Button> getPersonalAutoPingButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(SET_PING_INTERVAL + 0, "Turn Off"));
        for (int x = 1; x < 13; x++) {
            buttons.add(Buttons.gray(SET_PING_INTERVAL + x, "" + x));
        }
        buttons.add(Buttons.gray(SET_PING_INTERVAL + 24, "" + 24));
        buttons.add(Buttons.gray(SET_PING_INTERVAL + 48, "" + 48));
        return buttons;
    }
}
