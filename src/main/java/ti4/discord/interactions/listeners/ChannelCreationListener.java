package ti4.discord.interactions.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.service.game.CreateGameLaunchPostService;
import ti4.spring.service.deploy.ActiveLeaseService;

class ChannelCreationListener extends ListenerAdapter {

    private static final String FOW_MAKING_GAMES_CHANNEL = "making-fow-games";

    private static final String FOW_REPLACEMENT_TAG = "1336539499668443229";

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) {
            return;
        }
        if (!JdaService.isReadyToReceiveCommands()) {
            return;
        }
        ExecutorServiceManager.runAsync(
                "ChannelCreationListener task", () -> handleMakingNewGamesThreadCreation(event));
    }

    private void handleMakingNewGamesThreadCreation(ChannelCreateEvent event) {
        if (!JdaService.isValidGuild(event.getGuild().getId())
                || !(event.getChannel() instanceof ThreadChannel channel)) {
            return;
        }

        String parentName = channel.getParentChannel().getName();
        if (CreateGameLaunchPostService.isCreateGameLaunchParentName(parentName)) {
            Member owner = channel.getOwner();
            if (owner == null || owner.getUser().isBot()) {
                return;
            }
            List<Member> membersOG = new ArrayList<>(List.of(owner));
            CreateGameLaunchPostService.postLaunchButtons(channel, membersOG, "");
        } else if (FOW_MAKING_GAMES_CHANNEL.equalsIgnoreCase(parentName) && !hasTag(channel, FOW_REPLACEMENT_TAG)) {
            String message = """
                To launch a new Fog of War game, please run the command `/fow create_fow_game_button`, \
                filling in the players, GM and fun game name. This will create a button that you may press to launch the game after confirming the members \
                are correct.

                If you need a GM and don't seem to find one, give `@Game Supervisor` a ping.
                """;
            channel.sendMessage(message).queueAfter(5, TimeUnit.SECONDS);
        }
    }

    private boolean hasTag(ThreadChannel channel, String tagId) {
        return channel.getAppliedTags().stream().anyMatch(tag -> tag.getId().equals(tagId));
    }
}
