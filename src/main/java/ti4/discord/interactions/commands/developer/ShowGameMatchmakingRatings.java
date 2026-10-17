package ti4.discord.interactions.commands.developer;

import java.util.LinkedHashMap;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;

class ShowGameMatchmakingRatings extends Subcommand {

    ShowGameMatchmakingRatings() {
        super(
                Constants.GAME_MMR,
                "Show each player's matchmaking rating, the average, and the match quality of a game.");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to inspect")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can't find map for game name " + gameName);
            return;
        }
        Game game = GameManager.getManagedGame(gameName).getGame();
        Map<String, String> usernameByUserId = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) {
            usernameByUserId.put(player.getUserID(), player.getUserName());
        }
        if (usernameByUserId.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), gameName + " has no players.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                MatchmakingRatingEventService.get().describeGameRatings(game.getName(), usernameByUserId));
    }
}
