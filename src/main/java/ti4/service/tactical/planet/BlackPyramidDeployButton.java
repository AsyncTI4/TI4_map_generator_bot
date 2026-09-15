package ti4.service.tactical.planet;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.image.Mapper;
import ti4.model.UnitModel;
import ti4.service.game.MonumentsService;
import ti4.service.tactical.LandingContext;
import ti4.service.tactical.PlanetAbilityButton;

public final class BlackPyramidDeployButton implements PlanetAbilityButton {
    @Override
    public boolean enabled(LandingContext ctx) {
        UnitModel monument = Mapper.getUnit("pharadn_monument");
        List<String> planetTypes = new ArrayList<>(ctx.planet.getPlanetTypes());
        if (ctx.planet.isLegendary()) {
            planetTypes.add("LEGENDARY");
        }
        return monument != null
                && monument.canBePlacedOnPlanetTypes(planetTypes)
                && ctx.game.isMonumentsMode()
                && ctx.mainPlayer.hasUnit("pharadn_monument")
                && !MonumentsService.isMonumentOnBoard(ctx.game, ctx.mainPlayer, "pharadn_monument")
                && ctx.tile.getPosition().equals(ctx.game.getActiveSystem())
                && ctx.tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> ctx.mainPlayer.getPlanets().contains(planet.getName()));
    }

    @Override
    public List<Button> build(LandingContext ctx) {
        return List.of(MonumentsBRButtonHandler.offerBlackPyramidDeploy(ctx.game, ctx.mainPlayer, ctx.planet));
    }
}
