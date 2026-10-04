package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentNames;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class SardakkAgent implements AgentModule<SardakkAgent.Choice> {

    public static final String ID = "sardakkagent";

    public sealed interface Choice {}

    public record OfferPlanets(Player target) implements Choice {}

    public record PlaceOnPlanet(Tile tile, String planet) implements Choice {}

    static String planetButtonId(Player owner, String position, String planet) {
        return AgentButtonIds.formatOwned(owner, ID, position, planet);
    }

    public static Button offer(Player owner, String position, String planet, String planetRepresentation) {
        return Buttons.green(
                planetButtonId(owner, position, planet),
                AgentNames.offerVerb(owner, ID) + "N'orr Agent on " + planetRepresentation,
                FactionEmojis.Sardakk);
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "T'ro An, the N'orr";
    }

    @Override
    public Optional<Choice> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(new OfferPlanets(user));
        }
        Player target = game.getPlayerFromColorOrFaction(payload);
        if (target != null) {
            return Optional.of(new OfferPlanets(target));
        }
        return placeOnPlanet(game, payload);
    }

    private static Optional<Choice> placeOnPlanet(Game game, String payload) {
        String position = StringUtils.substringBefore(payload, "_");
        String planet = StringUtils.substringAfter(payload, "_");
        Tile tile = game.getTileByPosition(position);
        if (tile == null || planet.isEmpty() || tile.getUnitHolderFromPlanet(planet) == null) {
            return AgentTargets.player(game, payload).map(OfferPlanets::new);
        }
        return Optional.of(new PlaceOnPlanet(tile, planet));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return switch (use.payload()) {
            case OfferPlanets offer -> offerPlanets(use, offer.target());
            case PlaceOnPlanet place -> placeInfantry(use, place);
        };
    }

    private static AgentOutcome offerPlanets(AgentUse<Choice> use, Player target) {
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(target, use.game(), "2gf", "placeOneNDone_skipbuild");
        return AgentOutcome.of(Message.withButtons(
                target,
                target.getRepresentationUnfogged() + ", use buttons to resolve " + use.agentName() + ".",
                buttons));
    }

    private static AgentOutcome placeInfantry(AgentUse<Choice> use, PlaceOnPlanet place) {
        Player user = use.user();
        AddUnitService.addUnits(use.event(), place.tile(), use.game(), user.getColor(), "2 gf " + place.planet());
        return AgentOutcome.of(Message.to(
                user,
                user.getFactionEmoji() + " placed " + UnitEmojis.infantry + UnitEmojis.infantry + " on "
                        + Helper.getPlanetRepresentation(place.planet(), use.game()) + "."));
    }
}
