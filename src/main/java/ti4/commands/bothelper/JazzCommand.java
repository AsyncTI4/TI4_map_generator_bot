package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

class JazzCommand extends Subcommand {

    public JazzCommand() {
        super("jazz_command", "jazzxhands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!jazzCheck(event)) return;
        sendJazzButton(event);
    }

    public static void sendJazzButton(GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("jazzButton", "Jazz button", MiscEmojis.ScoutSpinner));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Constants.jazzPing() + " button", buttons);
    }

    public static boolean jazzCheck(GenericInteractionCreateEvent event) {
        if (Constants.jazzId.equals(event.getUser().getId())) return true;
        if (Constants.honoraryJazz.contains(event.getUser().getId())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are an honorary jazz so you may proceed");
            return true;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + Constants.jazzPing());
        return false;
    }

    public String json(MiltySettings object) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            BotLogger.error("Error mapping to json: ", e);
        }
        return null;
    }

    @ButtonHandler("jazzButton")
    private static void jazzButton() {
        AsyncTI4DiscordBot.jda.getGuildById("847560709730730064")
            .getTextChannelById("1352824638354231439")
            .sendMessage("```fix\nBorgJedi used /search my_titles player: @Mentak\n```").queue();

        AsyncTI4DiscordBot.jda.getGuildById("847560709730730064")
            .getTextChannelById("1352824638354231439")
            .sendMessage("**__Mentak's Titles__**\n` 1.`**You Made Me Mad** x5 (g15, g15, g15, g15, g15)").queue();
    }
}
