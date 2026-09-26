package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class NetrunnersUnitsHandler {
    public static final String MECH_ID = "netrunners_mech";

    public static void offerTrojan(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !player.hasUnit("netrunners_flagship")
                || !ButtonHelper.doesPlayerHaveFSHere("netrunners_flagship", player, tile)) {
            return;
        }
        List<String> abilities = tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeys().stream())
                .map(key -> {
                    Player owner = game.getPlayerFromColorOrFaction(key.getColor());
                    return owner == null ? null : owner.getUnitFromUnitKey(key);
                })
                .filter(java.util.Objects::nonNull)
                .flatMap(unit -> getTrojanAbilities(unit).stream())
                .distinct()
                .limit(24)
                .toList();
        List<Button> buttons = abilities.stream()
                .map(ability -> Buttons.gray(
                        player.factionButtonChecker() + "trojanAbility_" + tile.getPosition() + "_"
                                + Integer.toHexString(ability.hashCode()),
                        ability))
                .toList();
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", please choose a unit ability that cannot be used for the rest of this action due to the Trojan (the Netrunners flagship).",
                    buttons);
        }
    }

    @ButtonHandler("trojanAbility_")
    public static void resolveTrojan(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("trojanAbility_", "").split("_", 2);
        if (parts.length != 2) {
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        if (tile == null
                || !player.hasUnit("netrunners_flagship")
                || !ButtonHelper.doesPlayerHaveFSHere("netrunners_flagship", player, tile)) {
            return;
        }
        String ability = tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeys().stream())
                .map(key -> {
                    Player owner = game.getPlayerFromColorOrFaction(key.getColor());
                    return owner == null ? null : owner.getUnitFromUnitKey(key);
                })
                .filter(java.util.Objects::nonNull)
                .flatMap(unit -> getTrojanAbilities(unit).stream())
                .distinct()
                .filter(candidate -> Integer.toHexString(candidate.hashCode()).equals(parts[1]))
                .findFirst()
                .orElse(null);
        if (ability == null) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        String affectedPlayers = tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeys().stream())
                .map(key -> game.getPlayerFromColorOrFaction(key.getColor()))
                .filter(java.util.Objects::nonNull)
                .map(Player::getRepresentation)
                .distinct()
                .collect(Collectors.joining(", "));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                affectedPlayers + ", **" + ability + "** cannot be used in "
                        + tile.getRepresentationForButtons(game, player)
                        + " for the rest of this action due to the Trojan (the Netrunners flagship)."
                        + "\n-# This effect is player-enforced.");
    }

    private static List<String> getTrojanAbilities(UnitModel unit) {
        List<String> abilities = new ArrayList<>();
        if (unit.getSpaceCannonDieCount() > 0) abilities.add("SPACE CANNON");
        if (unit.getBombardDieCount() > 0) abilities.add("BOMBARDMENT");
        if (unit.getAfbDieCount() > 0) abilities.add("ANTI-FIGHTER BARRAGE");
        if (unit.getProductionValue() > 0 || unit.getBasicProduction() != null) abilities.add("PRODUCTION");
        if (unit.getPlanetaryShield()) abilities.add("PLANETARY SHIELD");

        if (unit.getAbility().stream()
                .anyMatch(ability -> ability.toLowerCase(Locale.ROOT).contains("deploy"))) {
            abilities.add("DEPLOY");
        }
        return abilities;
    }

    public static void offerLegionDeploy(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) return;
        List<Button> buttons = player.getPlanets().stream()
                .filter(planet -> game.getTileFromPlanet(planet) != null)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersLegion_" + planet,
                        "Deploy Mech to " + Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you gained a technology. You may place 1 mech from your reinforcements on a planet you control via a Legion (Netrunners mech).",
                buttons);
    }

    @ButtonHandler("netrunnersLegion_")
    public static void resolveLegionDeploy(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("netrunnersLegion_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || !player.getPlanets().contains(planet)
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " deployed 1 mech to " + Helper.getPlanetRepresentation(planet, game)
                        + " via a Legion (Netrunners mech).");
    }
}
