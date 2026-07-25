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
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.GenericCardModel;
import ti4.model.Source.ComponentSource;

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
                    boolean changed = revertBlackSpectrumPlots(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Removed stray Black Spectrum plot card duplicates.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    // Player.setupFactionSpecificOptions() hands every Firmament/Obsidian player every plot card
    // ever loaded (Mapper.getPlots()), with no source filtering. Black Spectrum added 5 plot cards
    // reusing the same names as the base 5 (Enervate/Siphon/Seethe/Assail/Extract), so every such
    // player ended up with 10 cards: a normal one and a buffed Black Spectrum one for each name.
    // Strip the stray Black Spectrum copies from every player's plot card pool.
    static boolean revertBlackSpectrumPlots(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            for (String plotId : new ArrayList<>(player.getPlotCardsRaw().keySet())) {
                GenericCardModel model = Mapper.getPlot(plotId);
                if (model != null
                        && model.getSource() == ComponentSource.black_spectrum
                        && model.getHomebrewReplacesID().isPresent()) {
                    player.getPlotCardsRaw().remove(plotId);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
