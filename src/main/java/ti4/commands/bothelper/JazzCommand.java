package ti4.commands.bothelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.json.ObjectMapperFactory;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class JazzCommand extends BothelperSubcommandData {

    ObjectMapper mapper = ObjectMapperFactory.build();

    public JazzCommand() {
        super("jazz_command", "jazzxhands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!jazzCheck(event)) return;
        //sendJazzButton(event);

        Game game = getActiveGame();
        ShowGame.simpleShowGame(game, event, DisplayType.googly);
    }

    public static void sendJazzButton(GenericInteractionCreateEvent event) {
        Emoji spinner = Emoji.fromFormatted(Emojis.scoutSpinner);
        Button jazz = Buttons.green("jazzButton", spinner.getFormatted());
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), Constants.jazzPing() + " button", jazz);
    }

    public static void handleJazzButton(ButtonInteractionEvent event, Player p, Game game) {

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
            String val = mapper.writeValueAsString(object);
            return val;
        } catch (Exception e) {
            BotLogger.log("Error mapping to json: ", e);
        }
        return null;
    }
}
