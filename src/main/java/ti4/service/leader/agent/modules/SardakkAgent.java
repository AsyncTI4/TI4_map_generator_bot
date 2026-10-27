package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class SardakkAgent extends AgentModule<SardakkAgent.Choice> {

    static final String ID = "sardakkagent";

    public SardakkAgent() {
        super(ID, "T'ro An, the N'orr");
    }

    sealed interface Choice {}

    record OfferPlanets(Player target) implements Choice {}

    record PlaceOnPlanet(Tile tile, String planet) implements Choice {}

    public static Button button(Player sardakk, String position, String planet, String planetRepresentation) {
        return Buttons.green(
                ownedButtonId(sardakk, ID, position, planet),
                useLabel(sardakk, ID, "N'orr Agent on " + planetRepresentation),
                FactionEmojis.Sardakk);
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

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return switch (use.payload()) {
            case OfferPlanets offer -> offerPlanets(use, offer.target());
            case PlaceOnPlanet place -> placeInfantry(use, place);
        };
    }

    static List<Button> placementButtons(Game game, Player player) {
        List<Button> everyPlanet =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return everyPlanet;
        }
        List<Button> inActiveSystem = everyPlanet.stream()
                .filter(button -> activeSystem.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> button.getCustomId().endsWith("_" + planet.getName())))
                .toList();
        return inActiveSystem.isEmpty() ? everyPlanet : inActiveSystem;
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

    private static AgentOutcome offerPlanets(AgentUse<Choice> use, Player target) {
        return AgentOutcome.of(Message.addressedTo(
                target, ", use buttons to resolve " + use.agentName() + ".", placementButtons(use.game(), target)));
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
