package ti4.commands.explore;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.explore.ExploreService;

class ExploreUse extends GameStateSubcommand {

    public ExploreUse() {
        super(Constants.USE, "Draw and activate an explore card from the deck or discard", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Exploration card ID")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.FORCE, "True to force the draw, even if none are in the deck"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String id = event.getOption(Constants.EXPLORE_CARD_ID, "", OptionMapping::getAsString);
        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        id = StringUtils.substringBefore(id, " ");

        if (!Mapper.isValidExplore(id)) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid ID specified: " + id);
        }

        if (!force && game.pickExplore(id) == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Exploration Card ID: `" + id + "` is not in the deck or discard pile.");
            return;
        }

        OptionMapping planetOption = event.getOption(Constants.PLANET);
        String planetName = null;
        if (planetOption != null) {
            planetName = planetOption.getAsString();
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        Tile tile = null;
        if (game.getPlanets().contains(planetName)) {
            for (Tile tile_ : game.getTileMap().values()) {
                if (tile != null) {
                    break;
                }
                for (Map.Entry<String, UnitHolder> unitHolderEntry :
                        tile_.getUnitHolders().entrySet()) {
                    if (unitHolderEntry.getValue() instanceof Planet
                            && unitHolderEntry.getKey().equals(planetName)) {
                        tile = tile_;
                        break;
                    }
                }
            }
            if (tile == null) {
                MessageHelper.sendMessageToEventChannel(event, "System not found that contains planet: " + planetName);
                return;
            }
        }
        Player player = getPlayer();
        String messageText = player.getRepresentation() + " used exploration card: " + id;
        if (force)
            messageText +=
                    "\nTHIS CARD WAS DRAWN FORCEFULLY (if the card wasn't in the deck, it was created from thin air)";
        ExploreService.resolveExplore(event, id, tile, planetName, messageText, player, game);
    }
}
