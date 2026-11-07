package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class ChoirUnitHandler {
    private static final String LAMENT = "choir_mechlament";
    private static final String REQUIEM = "choir_mechrequiem";
    private static final String ELEGY_START = "choirElegyStart_";
    private static final String ELEGY_PLANET = "choirElegyPlanet_";
    private static final String RESOLVE = "resolveElegy_";

    public static void addElegyCombatButton(List<Button> buttons, Game game, Player player, Tile tile) {
        String side = getElegySide(player);
        if (side == null || getElegyPlanets(player, tile, side).isEmpty()) {
            return;
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + ELEGY_START + tile.getPosition() + "|" + side,
                "Use Elegy",
                FactionEmojis.choir));
    }

    @ButtonHandler(ELEGY_START)
    public static void startElegy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(ELEGY_START.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        String side = payload.length == 2 ? payload[1] : null;
        if (!isElegySide(side) || !side.equals(getElegySide(player)) || !isCurrentGroundCombat(game, player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Planet> planets = getElegyPlanets(player, tile, side);
        if (planets.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (planets.size() == 1) {
            resolveElegyPlanet(event, game, player, tile, side, planets.getFirst());
        } else {
            List<Button> buttons = new ArrayList<>();
            for (Planet planet : planets) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + ELEGY_PLANET + tile.getPosition() + "|" + side + "|"
                                + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", choose the planet containing **Elegy**.",
                    buttons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(ELEGY_PLANET)
    public static void chooseElegyPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(ELEGY_PLANET.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        String side = payload.length == 3 ? payload[1] : null;
        UnitHolder unitHolder = tile == null || payload.length != 3 ? null : tile.getUnitHolderFromPlanet(payload[2]);
        Planet planet = unitHolder instanceof Planet selectedPlanet ? selectedPlanet : null;
        if (!isElegySide(side)
                || !side.equals(getElegySide(player))
                || !isCurrentGroundCombat(game, player, tile)
                || planet == null
                || !hasElegy(player, planet, side)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        resolveElegyPlanet(event, game, player, tile, side, planet);
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveElegyPlanet(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String side, Planet planet) {
        if (REQUIEM.equals(side)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet.getName());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " placed 1 infantry on "
                            + Helper.getPlanetRepresentation(planet.getName(), game) + " with **Elegy (Requiem)**.");
            return;
        }
        sendElegyTargets(event, game, player, tile, side, planet);
    }

    private static void sendElegyTargets(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String side, Planet planet) {
        List<Button> buttons = getLamentButtons(game, player, tile, planet);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible opposing ground forces for **Elegy**.");
            return;
        }
        String action = LAMENT.equals(side) ? "destroy an opposing ground force" : "place 1 infantry";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose how to " + action + " with **Elegy** on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
    }

    public static void offerElegySustain(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder unitHolder,
            UnitModel sustainedUnit,
            int sustainCount) {
        if (!isElegySustain(game, player, tile, unitHolder, sustainedUnit) || sustainCount < 1) {
            return;
        }

        String side = sustainedUnit.getId();
        for (int occurrence = 0; occurrence < sustainCount; occurrence++) {
            List<Button> buttons = LAMENT.equals(side)
                    ? getLamentButtons(game, player, tile, unitHolder)
                    : getRequiemButtons(player, tile, unitHolder, side);
            if (buttons.isEmpty()) {
                continue;
            }
            buttons.add(Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline"));
            String effect = LAMENT.equals(side)
                    ? "destroy 1 opposing ground force on this planet"
                    : "place 1 infantry on this planet";
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", your **Elegy** used SUSTAIN DAMAGE during ground combat. You may " + effect
                            + " with its **" + (LAMENT.equals(side) ? "Lament" : "Requiem") + "** side.",
                    buttons);
        }
    }

    @ButtonHandler(RESOLVE)
    public static void resolveElegy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE.length()).split("\\|", 5);
        String side = payload.length > 0 ? payload[0] : "";
        Tile tile = payload.length >= 3 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder planet = tile == null || payload.length < 3 ? null : tile.getUnitHolderFromPlanet(payload[2]);
        if (!isElegyAvailableInCombat(game, player, tile, planet, side)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (REQUIEM.equals(side)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet.getName());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " placed 1 infantry on "
                            + Helper.getPlanetRepresentation(planet.getName(), game) + " with **Elegy (Requiem)**.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!LAMENT.equals(side)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = payload.length == 5 ? game.getPlayerFromColorOrFaction(payload[3]) : null;
        UnitKey unitKey = payload.length == 5 && target != null
                ? planet == null
                        ? null
                        : planet.getUnitKeysForPlayer(target).stream()
                                .filter(key -> key.asyncID().equals(payload[4]))
                                .findFirst()
                                .orElse(null)
                : null;
        UnitModel unit = unitKey == null || target == null ? null : target.getUnitFromUnitKey(unitKey);
        if (!isElegyAvailableInCombat(game, player, tile, planet, side)
                || target == null
                || !isOpponent(game, player, target)
                || unit == null
                || !unit.getIsGroundForce()
                || planet.getUnitCount(unitKey) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, planet, true);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed 1 " + unit.getName() + " belonging to "
                        + target.getRepresentationNoPing() + " on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with **Elegy (Lament)**.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getLamentButtons(Game game, Player player, Tile tile, UnitHolder planet) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : getOpponents(game, player)) {
            for (UnitKey unitKey : planet.getUnitKeysForPlayer(target)) {
                UnitModel unit = target.getUnitFromUnitKey(unitKey);
                if (unit == null || !unit.getIsGroundForce() || planet.getUnitCount(unitKey) < 1) {
                    continue;
                }
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + RESOLVE + LAMENT + "|" + tile.getPosition() + "|"
                                + planet.getName() + "|" + target.getFaction() + "|" + unitKey.asyncID(),
                        "Destroy 1 " + unit.getName()));
            }
        }
        return buttons;
    }

    private static List<Button> getRequiemButtons(Player player, Tile tile, UnitHolder planet, String side) {
        return List.of(Buttons.green(
                player.factionButtonChecker() + RESOLVE + side + "|" + tile.getPosition() + "|" + planet.getName(),
                "Place 1 Infantry"));
    }

    private static boolean isElegySustain(
            Game game, Player player, Tile tile, UnitHolder unitHolder, UnitModel sustainedUnit) {
        return sustainedUnit != null
                && (LAMENT.equals(sustainedUnit.getId()) || REQUIEM.equals(sustainedUnit.getId()))
                && isCurrentElegyGroundCombat(game, player, tile, unitHolder);
    }

    private static String getElegySide(Player player) {
        if (player.hasUnit(LAMENT)) {
            return LAMENT;
        }
        return player.hasUnit(REQUIEM) ? REQUIEM : null;
    }

    private static List<Planet> getElegyPlanets(Player player, Tile tile, String side) {
        if (tile == null) {
            return List.of();
        }
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> hasElegy(player, planet, side))
                .toList();
    }

    private static boolean hasElegy(Player player, UnitHolder planet, String side) {
        return planet.getUnitKeysForPlayer(player).stream().anyMatch(unitKey -> {
            UnitModel unit = player.getUnitFromUnitKey(unitKey);
            return unit != null && side.equals(unit.getId()) && planet.getUnitCount(unitKey) > 0;
        });
    }

    private static boolean isElegySide(String side) {
        return LAMENT.equals(side) || REQUIEM.equals(side);
    }

    private static boolean isElegyAvailableInCombat(
            Game game, Player player, Tile tile, UnitHolder planet, String side) {
        return isCurrentGroundCombat(game, player, tile) && planet != null && hasElegy(player, planet, side);
    }

    private static boolean isCurrentGroundCombat(Game game, Player player, Tile tile) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return combat != null
                && tile != null
                && !"space".equals(combat.unitHolderName())
                && tile.getPosition().equals(combat.tilePosition())
                && combat.factions().contains(player.getFaction());
    }

    private static boolean isCurrentElegyGroundCombat(Game game, Player player, Tile tile, UnitHolder unitHolder) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return combat != null
                && tile != null
                && unitHolder != null
                && !"space".equals(unitHolder.getName())
                && tile.getPosition().equals(combat.tilePosition())
                && unitHolder.getName().equals(combat.unitHolderName())
                && combat.factions().contains(player.getFaction());
    }

    private static boolean isOpponent(Game game, Player player, Player target) {
        return target != player && getOpponents(game, player).contains(target);
    }

    private static List<Player> getOpponents(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null) {
            return List.of();
        }
        return combat.factions().stream()
                .filter(faction -> !player.getFaction().equals(faction))
                .map(game::getPlayerFromColorOrFaction)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
