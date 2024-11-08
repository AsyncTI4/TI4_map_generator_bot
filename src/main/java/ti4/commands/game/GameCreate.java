package ti4.commands.game;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class GameCreate extends GameSubcommandData {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    public GameCreate() {
        super(Constants.CREATE_GAME, "Create a new game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().getFirst().getAsString().toLowerCase();
        Member member = event.getMember();

        Matcher matcher = ALPHANUMERIC_PATTERN.matcher(mapName);
        if (!matcher.matches()) {
            MessageHelper.replyToMessage(event, "Game name can only contain a-z 0-9 symbols");
            return;
        }
        if (GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Game with such name exist already, choose different name");
            return;
        }

        Game game = createNewGame(event, mapName, member);
        reportNewGameCreated(game);
        MessageHelper.replyToMessage(event, "Game created with name: " + mapName);
        if (event.getMessageChannel().getName().startsWith(game.getName() + "-")) {
            ButtonHelper.offerPlayerSetupButtons(event.getMessageChannel(), game);
        }
    }

    public static Game createNewGame(GenericInteractionCreateEvent event, String gameName, Member gameOwner) {
        Game newGame = new Game();
        newGame.newGameSetup();
        String ownerID = gameOwner.getId();
        newGame.setOwnerID(ownerID);
        newGame.setOwnerName(gameOwner.getEffectiveName());
        newGame.setName(gameName);
        newGame.setAutoPing(true);
        newGame.setAutoPingSpacer(24);
        GameManager gameManager = GameManager.getInstance();
        gameManager.addGame(newGame);
        boolean setMapSuccessful = gameManager.setGameForUser(ownerID, gameName);
        newGame.addPlayer(gameOwner.getId(), gameOwner.getEffectiveName());
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active Game " + gameName);
        }
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
