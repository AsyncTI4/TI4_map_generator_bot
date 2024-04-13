package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.milty.MiltyDraftManager;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "jazzxhands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        if (!"228999251328368640".equals(event.getUser().getId())) {
            String jazz = AsyncTI4DiscordBot.jda.getUserById("228999251328368640").getAsMention();
            if ("150809002974904321".equals(event.getUser().getId())) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are an honorary jazz so you may proceed");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
                return;
            }
        }

        MiltyDraftManager man = getActiveGame().getMiltyDraftManager();
        String s = man.superSaveMessage();
        sendMessage(s);

        try {
            MiltyDraftManager man2 = new MiltyDraftManager();
            man2.init(game);
            man2.loadSuperSaveString(game, s);
            String s2 = man2.superSaveMessage();
            sendMessage(s2);
        } catch (Exception e) {
            sendMessage("Unable to load data");
            BotLogger.log("Unable to load data", e);
        }
    }
}
