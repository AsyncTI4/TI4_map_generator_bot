package ti4.listeners;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.message.MessageHelper;

public class ChannelCreationListener extends ListenerAdapter {

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        handleMakingNewGamesThreadCreation(event);
    }

    private void handleMakingNewGamesThreadCreation(ChannelCreateEvent event) {
        if (!(event.getChannel() instanceof ThreadChannel channel) || !channel.getParentChannel().getName().equalsIgnoreCase("making-new-games")) {
            return;
        }
        MessageHelper.sendMessageToChannel(channel, "To launch a new game, please run the command `/game create_game_button`, " +
            "filling in the players and fun game name. This will create a button that you may press to launch the game after confirming the members " +
            "are correct.");
    }
}
