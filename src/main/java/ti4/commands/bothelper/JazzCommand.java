package ti4.commands.bothelper;


import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.milty.MiltyDraftManager;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "jazzxhands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!jazzCheck(event)) return;
        //sendJazzButton(event);

        Game game = getActiveGame();
        MiltyDraftManager man = game.getMiltyDraftManager();
        String s = man.superSaveMessage();
        sendMessage(s);

        try {
            MiltyDraftManager man2 = new MiltyDraftManager();
            man2.init(game);
            man2.loadSuperSaveString(game, s);
            String s2 = man2.superSaveMessage();
            sendMessage(s2);
        } catch (Exception e) {
            sendMessage("Unable to load data. Check log.");
            BotLogger.log("Unable to load data", e);
        }
    }

    private static void sendJazzButton(GenericInteractionCreateEvent event) {
        Emoji spinner = Emoji.fromFormatted(Emojis.scoutSpinner);
        Button jazz = Button.success("jazzButton", spinner);
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), Constants.jazzPing() + " button", jazz);
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
}
