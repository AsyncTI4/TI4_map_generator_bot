package ti4.discord.interactions.commands.developer;

import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final Set<String> ACTION_CARDS_TO_REMOVE = Set.of(
            "deep_cover_operatives",
            "recurrence_protocols",
            "space_mines",
            "magen_engineers",
            "fulfillment_protocols",
            "rehash_debates",
            "cyberwarfare",
            "transference_protocol",
            "disrupt_logistics",
            "virulent_gas_canisters",
            "emergency_conscription",
            "rigged_explosives",
            "graviton_shielding",
            "contradictory_legal_text",
            "shock_and_awe",
            "deep_space_station",
            "ancient_defenses");

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = removeDeprecatedActionCards(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Removed deprecated action cards from game state.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean removeDeprecatedActionCards(Game game) {
        boolean changed = false;
        changed |= game.getActionCards().removeIf(ACTION_CARDS_TO_REMOVE::contains);
        changed |= game.getDiscardActionCards().keySet().removeIf(ACTION_CARDS_TO_REMOVE::contains);
        changed |= game.getDiscardACStatus().keySet().removeIf(ACTION_CARDS_TO_REMOVE::contains);
        changed |= game.getGameStats()
                .getActionCardPlays()
                .removeIf(actionCardPlay -> ACTION_CARDS_TO_REMOVE.contains(actionCardPlay.getActionCard()));
        for (Player player : game.getRealPlayers()) {
            changed |= player.getActionCards().keySet().removeIf(ACTION_CARDS_TO_REMOVE::contains);
        }
        return changed;
    }
}
