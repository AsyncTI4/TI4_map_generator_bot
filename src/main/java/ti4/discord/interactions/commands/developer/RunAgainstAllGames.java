package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import ti4.spring.service.persistence.GameEntityPersistenceService;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    RunAgainstAllGames() {
        super("run_against_all_games", "Turns Thunder's Edge off in Twilight's Fall games that still have it on.");
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
                "Looking for Twilight's Fall games that still have Thunder's Edge on"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> candidates = twilightsFallGamesWithThundersEdge(
                GameManager.getManagedGames(),
                GameEntityPersistenceService.getBean().findTwilightsFallGameNamesWithThundersEdge());
        List<String> fixedGames = new ArrayList<>();
        ConsumeGameUtility.consumeGames(
                candidates,
                game -> {
                    fixedGames.add(game.getName());
                    if (!dryRun) {
                        game.setThundersEdge(false);
                        GameManager.save(game, "Turned Thunder's Edge off in a Twilight's Fall game.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (dryRun ? "[DRY RUN] Would turn " : "Turned ") + "Thunder's Edge off in "
                        + StringHelper.pluralize(fixedGames.size(), "game") + ": "
                        + String.join(", ", fixedGames));
    }

    static List<String> twilightsFallGamesWithThundersEdge(
            List<ManagedGame> managedGames, List<String> persistedTwilightsFallGamesWithThundersEdge) {
        Set<String> persisted = Set.copyOf(persistedTwilightsFallGamesWithThundersEdge);
        return managedGames.stream()
                .filter(ManagedGame::isTwilightsFallMode)
                .map(ManagedGame::getName)
                .filter(persisted::contains)
                .toList();
    }
}
