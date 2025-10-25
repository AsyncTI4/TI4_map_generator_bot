package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem.Category;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.PartialMapService;

class BuildMap extends GameStateSubcommand {

    public BuildMap() {
        super(Constants.FRANKEN_BUILD_MAP, "Send buttons to build map from drafted tiles", false, false);
    }

    public void execute(SlashCommandInteractionEvent event) {

        Game game = getGame();

        // Set a default template if none is set
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = mapTemplateId != null ? Mapper.getMapTemplate(mapTemplateId) : null;
        if (mapTemplate == null) {
            MapTemplateModel defaultTempalte = Mapper.getDefaultMapTemplateForPlayerCount(
                    game.getRealPlayers().size());
            if (defaultTempalte == null) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "No map template is set and no default template is available for "
                                + game.getRealPlayers().size() + " players.");
                return;
            }
            game.setMapTemplateID(defaultTempalte.getID());
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "No map template was set. Set to default template: " + defaultTempalte.getAlias()
                            + ". You can use `/map set_map_template` to change it. Run this command again to proceed.");
            return;
        }

        // Put draft tiles onto the board for rendering purposes
        PartialMapService.placeFromTemplate(mapTemplate, game);

        BagDraft draft = game.getActiveBagDraft();
        if (draft == null) {
            MessageHelper.sendMessageToEventChannel(event, "There is no active draft.");
            return;
        }

        // Check that the draft is complete
        if (!draft.isDraftStageComplete()) {
            MessageHelper.sendMessageToEventChannel(event, "The draft is not complete yet.");
            return;
        }
        // Check that all players have an expected number of tiles
        // Check that all players have a speaker order
        int expectedBlueTiles = draft.getItemLimitForCategory(Category.BLUETILE);
        int expectedRedTiles = draft.getItemLimitForCategory(Category.REDTILE);
        int expectedDraftOrders = draft.getItemLimitForCategory(Category.DRAFTORDER);
        for (Player player : game.getRealPlayers()) {
            DraftBag draftHand = player.getDraftHand();
            if (draftHand == null) {
                continue;
            }
            int playerBlueTiles = draftHand.getCategoryCount(Category.BLUETILE);
            if (playerBlueTiles != expectedBlueTiles) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Player " + player.getUserName() + " has " + playerBlueTiles + " blue tiles, but "
                                + expectedBlueTiles + " are expected.");
                return;
            }
            int playerRedTiles = draftHand.getCategoryCount(Category.REDTILE);
            if (playerRedTiles != expectedRedTiles) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Player " + player.getUserName() + " has " + playerRedTiles + " red tiles, but "
                                + expectedRedTiles + " are expected.");
                return;
            }
            int playerDraftOrders = draftHand.getCategoryCount(Category.DRAFTORDER);
            if (playerDraftOrders != expectedDraftOrders) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Player " + player.getUserName() + " has " + playerDraftOrders + " draft orders, but "
                                + expectedDraftOrders + " are expected.");
                return;
            }
        }

        // Send the buttons
        MantisMapBuildContext mapBuildContext = MantisMapBuildContext.fromFranken(game);
        MantisMapBuildService.initializeMapBuilding(mapBuildContext);
    }
}
