package ti4.commands.tigl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;

class Games extends Subcommand {

    Games() {
        super(Constants.GAMES, "Show ongoing TIGL games grouped by rank");
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.SHOW_GAME_IDS,
                "True to also show the game IDs for each rank (default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean showGameIds = event.getOption(Constants.SHOW_GAME_IDS, false, OptionMapping::getAsBoolean);

        List<Game> ongoingTiglGames = GameManager.getManagedGames().stream()
                .map(ManagedGame::getGame)
                .filter(Game::isCompetitiveTIGLGame)
                .filter(game -> !game.isHasEnded())
                .toList();

        Map<TIGLRank, List<String>> rankToGames = ongoingTiglGames.stream()
                .collect(Collectors.groupingBy(
                        game -> game.getMinimumTIGLRankAtGameStart() == null
                                ? TIGLRank.UNRANKED
                                : game.getMinimumTIGLRankAtGameStart(),
                        Collectors.mapping(Game::getName, Collectors.toList())));

        StringBuilder sb = new StringBuilder("## Ongoing TIGL games by rank\n");
        sb.append("Total ongoing TIGL games: `").append(ongoingTiglGames.size()).append("`\n");

        List<TIGLRank> rankedTiglRanks = TIGLRank.getSortedRanks();

        for (TIGLRank rank : rankedTiglRanks) {
            List<String> gamesForRank = rankToGames.getOrDefault(rank, List.of());
            sb.append("- **")
                    .append(rank.getShortName())
                    .append("**: `")
                    .append(gamesForRank.size())
                    .append("` game");
            if (gamesForRank.size() != 1) {
                sb.append("s");
            }
            if (showGameIds && !gamesForRank.isEmpty()) {
                sb.append(" → ").append(String.join(", ", gamesForRank));
            }
            sb.append("\n");
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
