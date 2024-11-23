package ti4.service.game;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

@UtilityClass
public class CreateGameService {

    public static Game createNewGame(GenericInteractionCreateEvent event, String gameName, Member gameOwner) {
        Game newGame = new Game();
        newGame.newGameSetup();
        String ownerID = gameOwner.getId();
        newGame.setOwnerID(ownerID);
        newGame.setOwnerName(gameOwner.getEffectiveName());
        newGame.setName(gameName);
        newGame.setAutoPing(true);
        newGame.setAutoPingSpacer(24);
        newGame.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        GameSaveLoadManager.saveGame(newGame, event);
        return newGame;
    }

    public static void reportNewGameCreated(Game game) {
        if (game == null)
            return;

        TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("staff-lounge", true)
            .stream().findFirst().orElse(null);
        if (bothelperLoungeChannel == null)
            return;
        List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
        if (threadChannels.isEmpty())
            return;
        String threadName = "game-starts-and-ends";
        // SEARCH FOR EXISTING OPEN THREAD
        for (ThreadChannel threadChannel_ : threadChannels) {
            if (threadChannel_.getName().equals(threadName)) {
                String guildName = game.getGuild() == null ? "Server Unknown" : game.getGuild().getName();
                MessageHelper.sendMessageToChannel(threadChannel_,
                    "Game: **" + game.getName() + "** on server **" + guildName + "** has been created.");
                break;
            }
        }
    }
}
