package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public final class L1z1xAgent extends AgentModule<L1z1xAgent.Choice> {

    static final String ID = "l1z1xagent";

    public L1z1xAgent() {
        super(ID, "I48S, the L1Z1X");
    }

    sealed interface Choice {}

    record OfferPlanets(Player target) implements Choice {}

    record ReplaceInfantry(Tile tile, String planet) implements Choice {}

    public static List<Button> buttons(Game game, Tile activeSystem, Player l1z1x) {
        if (activeSystem == null) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            if (l1z1x.getPlanetsAllianceMode().contains(planetName)
                    && FoWHelper.playerHasInfantryOnPlanet(l1z1x, activeSystem, planetName)) {
                buttons.add(Buttons.green(
                        ownedButtonId(l1z1x, ID, activeSystem.getPosition(), planetName),
                        useLabel(
                                l1z1x,
                                ID,
                                cardLabel(l1z1x, ID, "L1Z1X Agent") + " on "
                                        + Helper.getPlanetRepresentation(planetName, game)),
                        FactionEmojis.L1Z1X));
            }
        }
        return buttons;
    }

    @Override
    public Optional<Choice> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrPlanetInSystem(
                game,
                user,
                payload,
                OfferPlanets::new,
                planet -> new ReplaceInfantry(planet.system(), planet.planet()));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Choice choice) {
        return switch (choice) {
            case ReplaceInfantry replace ->
                FoWHelper.playerHasInfantryOnPlanet(user, replace.tile(), replace.planet())
                        ? Optional.empty()
                        : Optional.of(
                                "You have no infantry on " + Helper.getPlanetRepresentation(replace.planet(), game));
            case OfferPlanets offer ->
                mechSwapButtons(game, offer.target()).isEmpty()
                        ? Optional.of(offer.target().getFactionEmojiOrColor() + " has no infantry in the active system")
                        : Optional.empty();
        };
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return switch (use.payload()) {
            case OfferPlanets offer -> offerPlanets(use, offer.target());
            case ReplaceInfantry replace -> replaceInfantry(use, replace);
        };
    }

    private static List<Button> mechSwapButtons(Game game, Player player) {
        return ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(
                player, game, "l1z1x", systemsWithInfantryToReplace(game));
    }

    private static Collection<Tile> systemsWithInfantryToReplace(Game game) {
        if (StringUtils.isBlank(game.getActiveSystem())) {
            return game.getTileMap().values();
        }
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        return activeSystem == null ? List.of() : List.of(activeSystem);
    }

    private static AgentOutcome offerPlanets(AgentUse<Choice> use, Player target) {
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", use buttons to resolve " + use.agentNameFor(target) + ".",
                mechSwapButtons(use.game(), target)));
    }

    private static AgentOutcome replaceInfantry(AgentUse<Choice> use, ReplaceInfantry replace) {
        Player user = use.user();
        Game game = use.game();
        RemoveUnitService.removeUnits(
                use.event(), replace.tile(), game, user.getColor(), "1 infantry " + replace.planet());
        AddUnitService.addUnits(use.event(), replace.tile(), game, user.getColor(), "1 mech " + replace.planet());
        return AgentOutcome.of(Message.plain(
                user,
                user.getFactionEmojiOrColor() + " replaced 1 " + UnitEmojis.infantry + " on "
                        + Helper.getPlanetRepresentation(replace.planet(), game) + " with 1 " + UnitEmojis.mech
                        + "."));
    }
}
