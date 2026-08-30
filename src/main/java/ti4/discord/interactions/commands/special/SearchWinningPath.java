package ti4.discord.interactions.commands.special;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.WinningPathBreakdown;
import ti4.service.statistics.game.WinningPathHelper;

class SearchWinningPath extends Subcommand {

    private record PathComponent(
            String optionName, String label, String description, ToIntFunction<WinningPathBreakdown> count) {}

    private record PathFlag(String optionName, String label, String pointSource) {}

    private record Criterion(String description, Predicate<WinningPathBreakdown> matches) {}

    private static final List<PathComponent> COMPONENTS = List.of(
            new PathComponent(
                    "stage_1s",
                    "stage 1 objectives",
                    "How many stage 1 objectives the winner scored",
                    WinningPathBreakdown::stage1s),
            new PathComponent(
                    "stage_2s",
                    "stage 2 objectives",
                    "How many stage 2 objectives the winner scored",
                    WinningPathBreakdown::stage2s),
            new PathComponent(
                    "secrets",
                    "secret objectives",
                    "How many secret objectives the winner scored",
                    WinningPathBreakdown::secrets),
            new PathComponent(
                    "supports",
                    "Support for the Throne",
                    "How many Supports for the Throne the winner held",
                    WinningPathBreakdown::supports),
            new PathComponent(
                    "custodians",
                    "custodian/imperial",
                    "How many points the winner took from the custodians token and Imperial",
                    WinningPathBreakdown::custodians),
            new PathComponent(
                    "others",
                    "other points",
                    "How many points the winner took from every other source combined",
                    WinningPathBreakdown::others));

    private static final List<PathFlag> FLAGS = List.of(
            new PathFlag("seed", "Seed of an Empire", WinningPathBreakdown.SEED),
            new PathFlag("mutiny", "Mutiny", WinningPathBreakdown.MUTINY),
            new PathFlag("shard", "Shard of the Throne", WinningPathBreakdown.SHARD),
            new PathFlag("imperial_rider", "Imperial Rider", WinningPathBreakdown.IMPERIAL_RIDER),
            new PathFlag("censure", "Political Censure", WinningPathBreakdown.CENSURE),
            new PathFlag("crown", "Crown of Emphidia", WinningPathBreakdown.CROWN),
            new PathFlag("latvinia", "Latvinia", WinningPathBreakdown.LATVINIA),
            new PathFlag("styx", "Styx", WinningPathBreakdown.STYX));

    SearchWinningPath() {
        super(Constants.SEARCH_WINNING_PATH, "List games whose winner took the provided path to victory");
        for (PathComponent component : COMPONENTS) {
            addOptions(
                    new OptionData(OptionType.INTEGER, component.optionName(), component.description()).setMinValue(0));
        }
        for (PathFlag flag : FLAGS) {
            addOptions(new OptionData(OptionType.BOOLEAN, flag.optionName(), "Did the winner score " + flag.label()));
        }
        addOptions(GameStatisticsFilterer.gameStatsFiltersExcept(
                GameStatisticsFilterer.HAS_WINNER_FILTER,
                GameStatisticsFilterer.MIN_PLAYER_COUNT_FILTER,
                GameStatisticsFilterer.FRACTURE_IN_PLAY_FILTER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Criterion> searchedPath = getSearchedPath(event);
        if (searchedPath.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Set at least one part of the winning path, e.g. `stage_2s: 3`.");
            return;
        }

        var foundGames = new HashSet<String>();
        StringBuilder sb = new StringBuilder("__**Games with Winning Path:**__ ")
                .append(describe(searchedPath))
                .append('\n');

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event).and(game -> game.getWinner()
                        .map(winner -> hasWinningPath(game, winner, searchedPath))
                        .orElse(false)),
                game -> {
                    foundGames.add(game.getName());
                    sb.append(formatGame(game)).append('\n');
                },
                ExecutionLockType.READ);

        if (foundGames.isEmpty()) {
            sb.append("No games match the selected path.");
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Winning Path Games", sb.toString());
    }

    private static List<Criterion> getSearchedPath(SlashCommandInteractionEvent event) {
        List<Criterion> searchedPath = new ArrayList<>();
        for (PathComponent component : COMPONENTS) {
            Integer count = event.getOption(component.optionName(), null, OptionMapping::getAsInt);
            if (count != null) {
                searchedPath.add(new Criterion(
                        count + " " + component.label(),
                        path -> component.count().applyAsInt(path) == count));
            }
        }
        for (PathFlag flag : FLAGS) {
            Boolean scored = event.getOption(flag.optionName(), null, OptionMapping::getAsBoolean);
            if (scored != null) {
                searchedPath.add(new Criterion(
                        (scored ? "with " : "without ") + flag.label(),
                        path -> path.scored(flag.pointSource()) == scored));
            }
        }
        return searchedPath;
    }

    private static String describe(List<Criterion> searchedPath) {
        return searchedPath.stream().map(Criterion::description).collect(Collectors.joining(", "));
    }

    private static boolean hasWinningPath(Game game, Player winner, List<Criterion> searchedPath) {
        WinningPathBreakdown path = WinningPathHelper.breakDownWinningPath(game, winner);
        return searchedPath.stream().allMatch(criterion -> criterion.matches().test(path));
    }

    private static String formatGame(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **").append(game.getName()).append("** ");
        sb.append('`').append(game.getCreationDate()).append("`-`");
        if (game.isHasEnded()) {
            sb.append(Helper.getDateRepresentation(game.getEndedDate()));
        } else {
            sb.append(Helper.getDateRepresentation(game.getLastModifiedDate()));
        }
        sb.append("`  ");
        for (Player player : game.getPlayers().values()) {
            if (!game.isFowMode() && player.getFaction() != null) {
                sb.append(player.getFactionEmoji());
            }
        }
        sb.append(" [").append(game.getGameModesText()).append("] ");
        if (game.isHasEnded()) sb.append(" ENDED");
        return sb.toString();
    }
}
