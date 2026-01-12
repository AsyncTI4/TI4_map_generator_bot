package ti4.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.game.CreateGameButtonHandler;
import ti4.helpers.ButtonHelper;
import ti4.spring.jda.JdaService;

public class ChannelCreationListener extends ListenerAdapter {

    private static final String PBD_MAKING_GAMES_CHANNEL = "making-new-games";
    private static final String FOW_MAKING_GAMES_CHANNEL = "making-fow-games";

    private static final String FOW_REPLACEMENT_TAG = "1336539499668443229";

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) {
            return;
        }
        handleMakingNewGamesThreadCreation(event);
    }

    private void handleMakingNewGamesThreadCreation(ChannelCreateEvent event) {
        if (!JdaService.isValidGuild(event.getGuild().getId())
                || !(event.getChannel() instanceof ThreadChannel channel)) {
            return;
        }

        String parentName = channel.getParentChannel().getName();
        if (parentName.equalsIgnoreCase(PBD_MAKING_GAMES_CHANNEL)
                || "making-private-games".equalsIgnoreCase(parentName)
                || "making-superfast-games".equalsIgnoreCase(parentName)) {
            String message = """
                To launch a new game, please use the buttons. Players can add themselves or you can add them manually. Once all players are added, press the launch button.
                """;
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("joinGameList", "Join Game"));
            buttons.add(Buttons.red("leaveGameList", "Leave Game"));
            buttons.add(Buttons.gray("editPlayers~MDL", "Add Players"));
            buttons.add(Buttons.gray("removePlayers~MDL", "Remove Players"));
            buttons.add(Buttons.gray("addSillyName~MDL", "Add Fun Game Name"));
            buttons.add(Buttons.blue("launchGame", "Launch Game"));
            channel.getOwnerId();
            List<Member> membersOG = new ArrayList<>(List.of(channel.getOwner()));

            channel.sendMessage(message + CreateGameButtonHandler.generateMemberListMessage(membersOG, ""))
                    .addComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                    .queueAfter(
                            2, TimeUnit.SECONDS); // We were having issues where we'd get errors related to the channel
            // having no messages.

        } else if (parentName.equalsIgnoreCase(FOW_MAKING_GAMES_CHANNEL) && !hasTag(channel, FOW_REPLACEMENT_TAG)) {
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
