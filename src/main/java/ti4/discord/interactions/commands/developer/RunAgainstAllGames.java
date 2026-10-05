package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    RunAgainstAllGames() {
        super("run_against_all_games", "Clears the end date of every game that isn't flagged as ended.");
        addOptions(new OptionData(
                OptionType.BOOLEAN, DRY_RUN_OPTION, "Report what would change without saving anything."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean dryRun = event.getOption(DRY_RUN_OPTION, false, OptionMapping::getAsBoolean);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Clearing end dates on games that aren't flagged as ended"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> changedGames = new ArrayList<>();
        ConsumeGameUtility.consumeGames(
                unendedGamesWithAnEndDate(GameManager.getManagedGames()),
                game -> {
                    if (game.isHasEnded() || game.getEndedDate() == 0) {
                        return;
                    }
                    changedGames.add(game.getName());
                    if (!dryRun) {
                        game.setEndedDate(0);
                        GameManager.save(game, "Cleared the end date of a game that isn't ended.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (dryRun ? "[DRY RUN] Would clear " : "Cleared ") + "the end date of "
                        + StringHelper.pluralize(changedGames.size(), "game") + " out of "
                        + GameManager.getGameCount() + ": " + String.join(", ", changedGames));
    }

    static List<String> unendedGamesWithAnEndDate(List<ManagedGame> managedGames) {
        return managedGames.stream()
                .filter(managedGame -> !managedGame.isHasEnded() && managedGame.getEndedDate() != 0)
                .map(ManagedGame::getName)
                .toList();
    }
}
