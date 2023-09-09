package ti4.commands.game;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class GameCreate extends GameSubcommandData {

    public GameCreate() {
        super(Constants.CREATE_GAME, "Create a new game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        Member member = event.getMember();

        String regex = "^[a-zA-Z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(mapName);
        if (!matcher.matches()){
            MessageHelper.replyToMessage(event, "Game name can only contain a-z 0-9 symbols");
            return;
        }
        if (GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Game with such name exist already, choose different name");
            return;
        }

        createNewGame(event, mapName, member);
        MessageHelper.replyToMessage(event, "Game created with name: " + mapName);
    }

    public static Game createNewGame(SlashCommandInteractionEvent event, String mapName, Member gameOwner) {
        Game newGame = new Game();
        String ownerID = gameOwner.getId();
        newGame.setOwnerID(ownerID);
        newGame.setOwnerName(gameOwner.getEffectiveName());
        newGame.setName(mapName);
        newGame.setAutoPing(true);
        newGame.setAutoPingSpacer(36);
        GameManager gameManager = GameManager.getInstance();
        gameManager.addGame(newGame);
        boolean setMapSuccessful = gameManager.setGameForUser(ownerID, mapName);
        newGame.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active Game " + mapName);
        }
        GameSaveLoadManager.saveMap(newGame, event);
        if(MapGenerator.guildPrimary.getTextChannelsByName("bothelper-lounge", true).size() > 0){
            TextChannel bothelperLoungeChannel = MapGenerator.guildPrimary.getTextChannelsByName("bothelper-lounge", true).get(0);
            //if (bothelperLoungeChannel != null) MessageHelper.sendMessageToChannel(bothelperLoungeChannel, "Game: **" + gameName + "** on server **" + event.getGuild().getName() + "** has concluded.");
            List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
            if (threadChannels == null){
                return newGame;
            }
            String threadName = "game-starts-and-ends";
            // SEARCH FOR EXISTING OPEN THREAD
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(threadName)) {
                    MessageHelper.sendMessageToChannel(threadChannel_,
                            "Game: **" + mapName + "** on server **" + event.getGuild().getName() + "** has been created.");
                }
            }
        }
        
        return newGame;
    }
    
}
