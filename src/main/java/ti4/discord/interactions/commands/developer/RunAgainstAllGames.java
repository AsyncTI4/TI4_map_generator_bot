package ti4.discord.interactions.commands.developer;

import java.util.HashSet;
import java.util.Map;
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
                    boolean changed = makeChanges(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(
                                game, "Developer ran custom command against this game, probably migration related.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    // Maps old/removed action card IDs found in game stats to their current equivalent in legacy_data.json.
    // These cards were renamed or removed from ACD2 without a corresponding stats migration.
    private static final Map<String, String> LEGACY_AC_REPLACEMENTS = Map.ofEntries(
            Map.entry("ancient_defenses", "double_agents"),
            Map.entry("contradictory_legal_text", "double_agents"),
            Map.entry("cyberwarfare", "seized_facility"),
            Map.entry("deep_cover_operatives", "espionage"),
            Map.entry("deep_space_station", "derelict_space_station"),
            Map.entry("disrupt_logistics", "disrupted_logistics"),
            Map.entry("emergency_conscription", "pivoted_plan"),
            Map.entry("fulfillment_protocols", "mercenary_contract"),
            Map.entry("graviton_shielding", "commercial_applications"),
            Map.entry("magen_engineers", "double_agents"),
            Map.entry("recurrence_protocols", "efficiency_initiative"),
            Map.entry("rehash_debates", "rehashed_debates"),
            Map.entry("rigged_explosives", "mechanized_workforce"),
            Map.entry("shock_and_awe", "classified_weapons_acd2"),
            Map.entry("space_mines", "dangerous_conditions"),
            Map.entry("transference_protocol", "masterclass_logistics"),
            Map.entry("virulent_gas_canisters", "virulent_gas"));

    private static boolean makeChanges(Game game) {
        boolean changed = false;
        for (GameStats.ActionCardPlay play : game.getGameStats().getActionCardPlays()) {
            String replacement = LEGACY_AC_REPLACEMENTS.get(play.getActionCard());
            if (replacement != null) {
                play.setActionCard(replacement);
                changed = true;
            }
        }
        return changed;
    }
}
