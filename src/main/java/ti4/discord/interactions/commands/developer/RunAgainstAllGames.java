package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.game.EndGameService;
import ti4.service.statistics.game.WinningPathPersistenceService;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    RunAgainstAllGames() {
        super(
                "run_against_all_games",
                "Records the most-points winner in ended games that ran out of objectives without a winner.");
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                DRY_RUN_OPTION,
                "Report what would change without saving anything (default true)."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean dryRun = event.getOption(DRY_RUN_OPTION, true, OptionMapping::getAsBoolean);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Looking for ended games that ran out of objectives without a recorded winner"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> recordedGames = new ArrayList<>();
        List<String> unbreakableTieGames = new ArrayList<>();
        ConsumeGameUtility.consumeGames(
                endedGamesWithoutWinner(GameManager.getManagedGames()),
                game -> {
                    if (!ranOutOfObjectivesWithoutWinner(game)) {
                        return;
                    }
                    Optional<Player> winner = game.getMostPointsWinner();
                    if (winner.isEmpty()) {
                        unbreakableTieGames.add(game.getName());
                        return;
                    }
                    recordedGames.add(describe(game, winner.get()));
                    if (!dryRun) {
                        game.recordWinner(winner.get());
                        GameManager.save(game, "Recorded the most-points winner.");
                        WinningPathPersistenceService.addGame(game);
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (dryRun ? "[DRY RUN] Would record " : "Recorded ") + "a most-points winner in "
                        + StringHelper.pluralize(recordedGames.size(), "game") + ":\n"
                        + String.join("\n", recordedGames));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Left without a winner because the tie for most points could not be broken by initiative ("
                        + unbreakableTieGames.size() + "): " + String.join(", ", unbreakableTieGames));
    }

    static List<String> endedGamesWithoutWinner(List<ManagedGame> managedGames) {
        return managedGames.stream()
                .filter(ManagedGame::isHasEnded)
                .filter(managedGame -> !managedGame.isHasWinner())
                .filter(managedGame -> !managedGame.isFowMode())
                .map(ManagedGame::getName)
                .toList();
    }

    static boolean ranOutOfObjectivesWithoutWinner(Game game) {
        return game.isHasEnded()
                && !game.isFowMode()
                && game.getWinner().isEmpty()
                && EndGameService.objectivesHaveRunOut(game);
    }

    private static String describe(Game game, Player winner) {
        return game.getName() + ": " + winner.getFaction() + " with " + winner.getTotalVictoryPoints() + "/"
                + game.getVp() + " VP, round " + game.getRound();
    }
}
