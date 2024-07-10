package ti4.commands.milty;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class DebugMilty extends MiltySubcommandData {

    private static final String debug = "debug";

    public DebugMilty() {
        super(debug, "Debug Milty Draft");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        MessageChannelUnion channel = event.getChannel();
        if (channel instanceof ThreadChannel thread) {
            postInfoInThread(game, thread);
        } else if (channel instanceof TextChannel textChannel) {
            textChannel.createThreadChannel(game.getName() + "-milty-debug-info").queue(t -> postInfoInThread(game, t));
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not send debug info");
        }
    }

    public static void postInfoInThread(Game game, ThreadChannel thread) {
        MiltySettings menu = game.getMiltySettingsUnsafe();
        MiltyDraftManager manager = game.getMiltyDraftManager();

        MessageHelper.sendMessageToChannel(thread, manager.superSaveMessage());
        if (menu != null) {
            MessageHelper.sendMessageToChannel(thread, menu.json());
        } else if (game.getMiltyJson() != null) {
            MessageHelper.sendMessageToChannel(thread, game.getMiltyJson());
        } else {
            MessageHelper.sendMessageToChannel(thread, "No milty setting data");
        }
    }
}
