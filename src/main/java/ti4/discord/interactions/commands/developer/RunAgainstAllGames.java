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
    private static final String INCLUDE_ENDED_OPTION = "include_ended";

    RunAgainstAllGames() {
        super("run_against_all_games", "Turns off rules link injection in every game that has it on.");
        addOptions(
                new OptionData(OptionType.BOOLEAN, DRY_RUN_OPTION, "Report what would change without saving anything."),
                new OptionData(OptionType.BOOLEAN, INCLUDE_ENDED_OPTION, "Also turn it off in games that have ended."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean dryRun = event.getOption(DRY_RUN_OPTION, false, OptionMapping::getAsBoolean);
        boolean includeEnded = event.getOption(INCLUDE_ENDED_OPTION, false, OptionMapping::getAsBoolean);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Turning off rules links in " + (includeEnded ? "all" : "unfinished") + " games"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> changedGames = new ArrayList<>();
        ConsumeGameUtility.consumeGames(
                gamesWithRulesLinksOn(GameManager.getManagedGames(), includeEnded),
                game -> {
                    if (!game.isInjectRulesLinks()) {
                        return;
                    }
                    changedGames.add(game.getName());
                    if (!dryRun) {
                        game.setInjectRulesLinks(false);
                        GameManager.save(game, "Turned off rules links.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (dryRun ? "[DRY RUN] Would turn off " : "Turned off ") + "rules links in "
                        + StringHelper.pluralize(changedGames.size(), "game") + " out of "
                        + GameManager.getGameCount() + ": " + String.join(", ", changedGames));
    }

    static List<String> gamesWithRulesLinksOn(List<ManagedGame> managedGames, boolean includeEnded) {
        return managedGames.stream()
                .filter(ManagedGame::isInjectRules)
                .filter(managedGame -> includeEnded || !managedGame.isHasEnded())
                .map(ManagedGame::getName)
                .toList();
    }
}
