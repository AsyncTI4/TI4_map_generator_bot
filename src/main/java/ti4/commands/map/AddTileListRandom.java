package ti4.commands.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.service.map.AddTileListService;
import ti4.service.map.AddTileService;
import ti4.service.map.AddTileService.RandomOption;

public class AddTileListRandom extends GameStateSubcommand {

    public AddTileListRandom() {
        super(
                Constants.ADD_TILE_LIST_RANDOM,
                "Show dialog for tile list to generate map (supports random options from /map add_tile_random)",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Modal modal = AddTileListService.buildMapStringModal(game, "addMapStringRandom");

        // Inject a new action row to the modal
        Modal.Builder newModalBuilder = Modal.create(modal.getId(), modal.getTitle());
        modal.getComponents().forEach(component -> newModalBuilder.addComponents(component));
        boolean hasExistingErTiles = game.getTileMap().values().stream()
                .anyMatch(t -> t.getTileID().toLowerCase().startsWith("er"));
        TextInput sourcesInput = TextInput.create(
                        Constants.INCLUDE_ERONOUS_TILES, "Include Eronous tiles", TextInputStyle.SHORT)
                .setPlaceholder("(Y)es / (N)o")
                .setValue(hasExistingErTiles ? "Yes" : "No")
                .setRequired(false)
                .build();

        newModalBuilder.addActionRow(sourcesInput).build();
        modal = newModalBuilder.build();

        event.replyModal(modal).queue();
    }

    @ModalHandler("addMapStringRandom")
    public static void addMapStringFromModal(ModalInteractionEvent event, Game game) {
        String mapStringRaw = event.getValue("mapString").getAsString().replace(",", " ");
        String eronousTiles = event.getValue(Constants.INCLUDE_ERONOUS_TILES).getAsString();
        eronousTiles = eronousTiles != null ? eronousTiles.toLowerCase().trim() : "";

        Set<ComponentSource> sources =
                AddTileService.getSources(game, ("y".equals(eronousTiles) || "yes".equals(eronousTiles)));

        StringTokenizer tileListTokenizer = new StringTokenizer(mapStringRaw, " ");
        List<String> tilesToAdd = new ArrayList<>();

        // Replace each instance of RandomOption with a randomized tile
        while (tileListTokenizer.hasMoreTokens()) {
            String tileToken = tileListTokenizer.nextToken().trim();
            boolean isCenter = false;
            if (tileToken.contains("{")) {
                isCenter = true;
                tileToken = tileToken.replace("{", "").replace("}", "").trim();
            }

            if (RandomOption.isValid(tileToken)) {
                // Ignoring existing tiles from the map as those will be cleared by addTileListToMap
                List<TileModel> availableTiles = AddTileService.availableTiles(
                        sources, RandomOption.valueOf(tileToken), new HashSet<>(), tilesToAdd);
                if (availableTiles.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(), "Not enough " + tileToken + " tiles to draw from.");
                    return;
                }

                Collections.shuffle(availableTiles);
                tileToken = availableTiles.getFirst().getId();
            }
            tilesToAdd.add((isCenter ? "{" : "") + tileToken + (isCenter ? "}" : ""));
        }

        AddTileListService.addTileListToMap(game, String.join(", ", tilesToAdd), event);
    }
}
