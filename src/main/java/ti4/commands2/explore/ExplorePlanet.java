package ti4.commands2.explore;

import java.util.Optional;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.service.PlanetService;
import ti4.service.explore.ExploreService;

class ExplorePlanet extends GameStateSubcommand {

    public ExplorePlanet() {
        super(Constants.PLANET, "Explore a specific planet.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to explore").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TRAIT, "Planet trait to explore").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ, "Override ownership requirement. Enter YES if so"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        String planetName = AliasHandler.resolvePlanet(StringUtils.substringBefore(planetOption.getAsString(), " ("));
        Game game = getGame();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.sendMessageToEventChannel(event, "Planet not found in map");
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "System not found that contains planet");
            return;
        }
        planetName = PlanetService.getPlanet(tile, AliasHandler.resolvePlanet(planetName));
        PlanetModel planet = Mapper.getPlanet(planetName);
        if (Optional.ofNullable(planet).isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "Invalid planet");
            return;
        }
        String drawColor = planet.getPlanetType() == null ? null : planet.getPlanetType().toString();
        OptionMapping traitOption = event.getOption(Constants.TRAIT);
        if (traitOption != null) {
            drawColor = traitOption.getAsString();
        }
        if (drawColor == null) {
            MessageHelper.sendMessageToEventChannel(event, "Cannot determine trait, please specify");
            return;
        }

        boolean over = false;
        OptionMapping overRider = event.getOption(Constants.OVERRIDE_EXPLORE_OWNERSHIP_REQ);
        if (overRider != null && "YES".equalsIgnoreCase(overRider.getAsString())) {
            over = true;
        }
        ExploreService.explorePlanet(event, tile, planetName, drawColor, getPlayer(), false, game, 1, over);
    }
}
