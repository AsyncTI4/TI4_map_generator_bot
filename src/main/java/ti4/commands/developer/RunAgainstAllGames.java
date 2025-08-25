package ti4.commands.developer;

import java.lang.reflect.Field;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        GamesPage.consumeAllGames(game -> {
            boolean changed = setPiFactionsHomebrew(game);
            changed |= renameBlaheoUnitHolder(game);
            if (changed) {
                GameManager.save(game, "Developer ran custom command against this game, probably migration related.");
            }
        });
    }

    private static boolean setPiFactionsHomebrew(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            String faction = player.getFaction();
            if (faction != null && faction.startsWith("pi_")) {
                player.setFaction(faction.substring(3));
                changed = true;
            }
        }
        if (changed) {
            game.setHomebrew(true);
            BotLogger.info("Changed factions from 'pi_' in game: " + game.getName());
        }
        return changed;
    }

    private static boolean renameBlaheoUnitHolder(Game game) {
        boolean changed = false;
        for (Tile tile : game.getTileMap().values()) {
            if (!"d17".equalsIgnoreCase(tile.getTileID())) continue;
            Map<String, UnitHolder> holders = tile.getUnitHolders();
            if (!holders.containsKey("blaheo")) continue;
            UnitHolder holder = holders.remove("blaheo");
            try {
                Field nameField = UnitHolder.class.getDeclaredField("name");
                nameField.setAccessible(true);
                nameField.set(holder, "biaheo");
            } catch (Exception e) {
                BotLogger.error("Failed to rename blaheo unit holder", e);
            }
            holders.put("biaheo", holder);
            changed = true;
        }
        if (changed) {
            BotLogger.info("Renamed Blaheo as Biaheo in game " + game.getName());
        }
        return changed;
    }
}
