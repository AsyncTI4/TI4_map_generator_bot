package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
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

public final class YinAgent extends AgentModule<YinAgent.Replacement> {

    static final String ID = "yinagent";
    private static final String OFFER_STEP = "yinagent_";
    private static final String PLACE_PREFIX = "placeOneNDone_skipbuild";

    public YinAgent() {
        super(ID, "Brother Milor, the Yin");
    }

    public record Replacement(Tile system, Player owner) {}

    public static Button offerButton(Player yin, String position) {
        return Buttons.gray(
                yin.factionButtonChecker() + OFFER_STEP + position, useLabel(yin, ID, "Yin Agent"), FactionEmojis.Yin);
    }

    public static List<Button> fighterButtons(Player owner, Tile system) {
        return List.of(Buttons.green(
                owner.factionButtonChecker() + PLACE_PREFIX + "_2ff_" + system.getPosition(),
                "Place 2 Fighters",
                UnitEmojis.fighter));
    }

    public static List<Button> fightersOrInfantryButtons(Game game, Player owner, Tile system) {
        List<Button> buttons = new ArrayList<>(fighterButtons(owner, system));
        for (Planet planet : system.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            buttons.add(Buttons.green(
                    owner.factionButtonChecker() + PLACE_PREFIX + "_2gf_" + planetName,
                    "Place 2 Infantry on " + Helper.getPlanetRepresentation(planetName, game),
                    UnitEmojis.infantry));
        }
        return buttons;
    }

    @ButtonHandler(OFFER_STEP)
    static void offerOwners(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        offerUnitOwnerPicker(event, game, player, ID, buttonID.substring(OFFER_STEP.length()));
    }

    static List<Button> ownerButtons(Game game, Player yin, String position) {
        return unitOwnerButtons(game, yin, ID, position);
    }

    @Override
    public Optional<Replacement> decode(Game game, Player user, String payload) {
        Tile system = game.getTileByPosition(StringUtils.substringBefore(payload, "_"));
        if (system == null) {
            return Optional.empty();
        }
        return AgentTargets.player(game, StringUtils.substringAfter(payload, "_"))
                .map(owner -> new Replacement(system, owner));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Replacement> use) {
        Player owner = use.payload().owner();
        return AgentOutcome.of(Message.addressedTo(
                owner,
                ", use buttons to resolve " + use.agentNameFor(owner) + ".",
                fightersOrInfantryButtons(use.game(), owner, use.payload().system())));
    }
}
