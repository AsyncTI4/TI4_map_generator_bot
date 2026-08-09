package ti4.discord.interactions.commands.developer;

import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.overrule.OverruleStatsService;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    if (migrateActionCardTargets(game)) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Migrated action card Sabotage/Overrule targets to canceled flags.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    // Cancels used to be recorded as a "Sabotage" action card play targeting the canceled card, and
    // Overrule plays carried the strategy card that was chosen. Move both onto their new homes: the
    // canceled flag of the play itself, and the overrule_choice table.
    /**
     * @deprecated one-off. Remove this along with the migration it calls once it has run against all
     *     games.
     */
    @Deprecated
    static boolean migrateActionCardTargets(Game game) {
        GameStats.OverruleTargetMigration migration = game.getGameStats().migrateTargetsToCanceledFlags();
        if (!migration.overrulePlays().isEmpty()) {
            OverruleStatsService.get().addMigratedPlays(game.getName(), migration.overrulePlays());
        }
        return migration.changed();
    }
}
