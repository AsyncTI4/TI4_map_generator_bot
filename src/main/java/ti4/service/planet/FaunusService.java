package ti4.service.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.PlanetModel;
import ti4.service.regex.RegexService;

@UtilityClass
public class FaunusService {

    public PlanetModel faunus() {
        return Mapper.getPlanet("faunus");
    }

    public List<Button> getFaunusButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> tiles = FoWHelper.getTilePositionsToShow(game, player);
        for (Planet p : game.getPlanetsInfo().values()) {
            Tile t = game.getTileFromPlanet(p.getName());
            if (t == null || player.hasPlanet(p.getName())) continue;
            if (p.getUnitCount() > 0) continue;
            if (p.isLegendary()) continue;
            if (p.isHomePlanet(game)) continue;
            if (!p.getAttachments().isEmpty()) continue;

            // in fow, skip planets you can't see
            if (game.isFowMode() && !tiles.contains(t.getPosition())) continue;

            // skip space stations where someone else controls space
            if (p.isSpaceStation() && !player.is(game.getPlayerThatControlsTile(t))) continue;

            String id = player.finChecker() + "faunusTake_" + p.getName();
            String label = Helper.getPlanetRepresentation(p.getName(), game);

            Player owner = game.getPlayerThatControlsPlanet(p.getName());
            if (owner != null) {
                buttons.add(Buttons.red(id, label, owner.fogSafeEmoji()));
            } else {
                buttons.add(Buttons.green(id, label, p.getPlanetModel().getEmoji()));
            }
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No thanks"));
        return buttons;
    }

    @ButtonHandler("faunusTake_")
    private static void gainPlanetWithFaunus(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "faunusTake_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            AddPlanetService.addPlanet(player, planet, game, event, false);
            ButtonHelper.deleteMessage(event);
        });
    }
}
