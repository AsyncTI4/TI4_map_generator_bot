package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    private static final Map<String, String> REMOVED_TECHS = Map.of(
            "baldrick_ac2", "ac2",
            "baldrick_cv2", "cv2",
            "baldrick_dn2", "dn2",
            "baldrick_exo2", "exo2",
            "baldrick_ff2", "ff2",
            "baldrick_hcf2", "hcf2",
            "baldrick_ht2", "ht2",
            "baldrick_pds2", "pds2",
            "baldrick_sdn2", "sdn2");

    private static final Map<String, String> REMOVED_UNITS = Map.of(
            "baldrick_carrier2", "carrier2",
            "baldrick_sol_carrier2", "sol_carrier2",
            "baldrick_dreadnought2", "dreadnought2",
            "baldrick_sardakk_dreadnought2", "sardakk_dreadnought2",
            "baldrick_l1z1x_dreadnought2", "l1z1x_dreadnought2",
            "baldrick_fighter2", "fighter2",
            "baldrick_naalu_fighter2", "naalu_fighter2",
            "baldrick_pds2", "pds2",
            "baldrick_titans_pds2", "titans_pds2");

    RunAgainstAllGames() {
        super(
                "run_against_all_games",
                "Replaces the removed Ignis Aurora unit upgrades with the base ones in every game.");
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
                "Looking for games that still hold the removed Ignis Aurora unit upgrades"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> changedGames = new ArrayList<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    if (!replaceRemovedUnitUpgrades(game)) {
                        return;
                    }
                    changedGames.add(game.getName());
                    if (!dryRun) {
                        GameManager.save(game, "Replaced the removed Ignis Aurora unit upgrades.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (dryRun ? "[DRY RUN] Would replace " : "Replaced ") + "the removed unit upgrades in "
                        + StringHelper.pluralize(changedGames.size(), "game") + ": "
                        + String.join(", ", changedGames));
    }

    static boolean replaceRemovedUnitUpgrades(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            for (Map.Entry<String, String> removed : REMOVED_TECHS.entrySet()) {
                changed |= replaceWithoutDuplicating(player.getTechs(), removed.getKey(), removed.getValue());
                changed |= replaceWithoutDuplicating(player.getExhaustedTechs(), removed.getKey(), removed.getValue());
                changed |= replaceWithoutDuplicating(player.getPurgedTechs(), removed.getKey(), removed.getValue());
                changed |= replaceWithoutDuplicating(player.getFactionTechs(), removed.getKey(), removed.getValue());
            }
            for (Map.Entry<String, String> removed : REMOVED_UNITS.entrySet()) {
                if (player.getUnitsOwned().remove(removed.getKey())) {
                    player.getUnitsOwned().add(removed.getValue());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean replaceWithoutDuplicating(List<String> list, String removed, String replacement) {
        int index = list.indexOf(removed);
        if (index < 0) {
            return false;
        }
        list.removeIf(removed::equals);
        if (!list.contains(replacement)) {
            list.add(Math.min(index, list.size()), replacement);
        }
        return true;
    }
}
