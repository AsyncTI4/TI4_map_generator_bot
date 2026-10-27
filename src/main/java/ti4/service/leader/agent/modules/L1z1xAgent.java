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

    public static Button button(Player l1z1x, String position, String planet, String planetRepresentation) {
        return Buttons.green(
                ownedButtonId(l1z1x, ID, position, planet),
                useLabel(l1z1x, ID, "L1Z1X Agent on " + planetRepresentation),
                FactionEmojis.L1Z1X);
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
        Tile tile = game.getTileByPosition(StringUtils.substringBefore(payload, "_"));
        String planet = StringUtils.substringAfter(payload, "_");
        if (tile == null || planet.isEmpty() || tile.getUnitHolderFromPlanet(planet) == null) {
            return AgentTargets.player(game, payload).map(OfferPlanets::new);
        }
        return Optional.of(new ReplaceInfantry(tile, planet));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Choice choice) {
        if (choice instanceof ReplaceInfantry replace
                && !FoWHelper.playerHasInfantryOnPlanet(user, replace.tile(), replace.planet())) {
            return Optional.of("You have no infantry on " + Helper.getPlanetRepresentation(replace.planet(), game));
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return switch (use.payload()) {
            case OfferPlanets offer -> offerPlanets(use, offer.target());
            case ReplaceInfantry replace -> replaceInfantry(use, replace);
        };
    }

    static List<Button> mechSwapButtons(Game game, Player player) {
        List<Button> everywhere =
                new ArrayList<>(ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, "l1z1x"));
        String activeSystemStep = "mitoMechPlacement_" + game.getActiveSystem() + "_";
        List<Button> inActiveSystem = everywhere.stream()
                .filter(button -> button.getCustomId().contains(activeSystemStep))
                .toList();
        return inActiveSystem.isEmpty() ? everywhere : inActiveSystem;
    }

    private static AgentOutcome offerPlanets(AgentUse<Choice> use, Player target) {
        return AgentOutcome.offer(
                Message.addressedTo(
                        target,
                        ", use buttons to resolve " + use.agentNameFor(target) + ".",
                        mechSwapButtons(use.game(), target)),
                use.noteToUser(target, " has no infantry to replace with a mech."));
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
