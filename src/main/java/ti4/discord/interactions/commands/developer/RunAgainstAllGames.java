package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
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

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = removeBlackSpectrumGenericPNs(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Removed Black Spectrum generic PN duplicates from game state.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    // Black Spectrum's colorable Political Secret/Support for the Throne replacements were dealt
    // to every player in every game, since nothing gated them behind an actual "is Black Spectrum
    // enabled" check. Strip any stray copies out of every player's hand and owned-PN pool, wherever
    // they ended up (including after being traded away from whoever originally received them).
    static boolean removeBlackSpectrumGenericPNs(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            for (String pnID : new ArrayList<>(player.getPromissoryNotes().keySet())) {
                if (pnID.endsWith("_bsp_ps") || pnID.endsWith("_bsp_sftt")) {
                    player.removePromissoryNote(pnID);
                    changed = true;
                }
            }
            for (String pnID : new ArrayList<>(player.getPromissoryNotesOwned())) {
                if (pnID.endsWith("_bsp_ps") || pnID.endsWith("_bsp_sftt")) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
