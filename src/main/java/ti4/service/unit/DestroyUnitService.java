package ti4.service.unit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.function.Consumers;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

public class DestroyUnitService {

    public static void destroyAllUnitsInSystem(
        GenericInteractionCreateEvent event, Tile tile, Game game,
        boolean combat
    ) {
        List<RemovedUnit> units = new ArrayList<>();
        for (UnitHolder uh : tile.getUnitHolders().values())
            units.addAll(RemoveUnitService.removeAllUnits(event, tile, game, uh));
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllUnits(
        GenericInteractionCreateEvent event, Tile tile, Game game, UnitHolder unitHolder,
        boolean combat
    ) {
        List<RemovedUnit> units = RemoveUnitService.removeAllUnits(event, tile, game, unitHolder);
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllPlayerUnitsInSystem(
        GenericInteractionCreateEvent event, Game game, Player player,
        Tile tile, boolean combat
    ) {
        List<RemovedUnit> units = new ArrayList<>();
        for (UnitHolder uh : tile.getUnitHolders().values())
            units.addAll(RemoveUnitService.removeAllPlayerUnits(event, game, player, tile, uh));
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllPlayerUnits(
        GenericInteractionCreateEvent event, Game game, Player player, Tile tile,
        UnitHolder unitHolder, boolean combat
    ) {
        List<RemovedUnit> units = RemoveUnitService.removeAllPlayerUnits(event, game, player, tile, unitHolder);
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllPlayerNonStructureUnits(
        GenericInteractionCreateEvent event, Game game, Player player, Tile tile,
        UnitHolder unitHolder, boolean combat
    ) {
        List<RemovedUnit> units = RemoveUnitService.removeAllPlayerNonStructureUnits(event, game, player, tile, unitHolder);
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyUnits(
        GenericInteractionCreateEvent event, Tile tile, Game game, String color,
        String unitList, boolean combat
    ) {
        destroyUnits(event, tile, game, color, unitList, combat, true);
    }

    public static void destroyUnits(
        GenericInteractionCreateEvent event, Tile tile, Game game, String color,
        String unitList, boolean combat, boolean prioritizeDamagedUnits
    ) {
        List<RemovedUnit> destroyedMap = RemoveUnitService.removeUnits(event, tile, game, color, unitList,
            prioritizeDamagedUnits);
        handleDestroyedUnits(event, game, destroyedMap, combat);
    }

    public static void destroyUnit(
        GenericInteractionCreateEvent event, Tile tile, Game game, UnitKey key, int amt,
        UnitHolder unitHolder, boolean combat
    ) {
        ParsedUnit unit = new ParsedUnit(key, amt, unitHolder.getName());
        destroyUnit(event, tile, game, unit, combat);
    }

    public static void destroyUnit(
        GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit,
        boolean combat
    ) {
        destroyUnit(event, tile, game, parsedUnit, combat, true);
    }

    public static void destroyUnit(
        GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit,
        boolean combat, boolean prioritizeDamagedUnits
    ) {
        var destroyedUnit = RemoveUnitService.removeUnit(event, tile, game, parsedUnit, prioritizeDamagedUnits);
        handleDestroyedUnits(event, game, destroyedUnit, combat);
    }

    public static void destroyUnit(
        GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit,
        boolean combat, UnitState preferredState
    ) {
        var destroyedUnit = RemoveUnitService.removeUnit(event, tile, game, parsedUnit, preferredState);
        handleDestroyedUnits(event, game, destroyedUnit, combat);
    }

    private static void handleDestroyedUnits(
        GenericInteractionCreateEvent event, Game game, List<RemovedUnit> units,
        boolean combat
    ) {
        // batch up infantry for INF2-ish effects
        for (Player player : game.getRealPlayers()) {
            int numInfantry = 0;
            for (RemovedUnit u : units) {
                if (player.unitBelongsToPlayer(u.unitKey()) && u.unitKey().getUnitType() == UnitType.Infantry) {
                    numInfantry += u.getTotalRemoved();
                }
            }

            if (numInfantry > 0) {
                ButtonHelper.resolveInfantryDestroy(player, numInfantry);
            }
        }

        // Handle other destroyed units individually
        for (RemovedUnit u : units)
            handleDestroyedUnit(event, game, units, u, combat);
    }

    // TODO: Jazz add the rest of the destroy code here
    private static void handleDestroyedUnit(
        GenericInteractionCreateEvent event, Game game, List<RemovedUnit> allUnits,
        RemovedUnit unit, boolean combat
    ) {
        int totalAmount = unit.getTotalRemoved();
        Player player = game.getPlayerFromColorOrFaction(unit.unitKey().getColorID());

        List<Player> capturing = CaptureUnitService.listCapturingFlagshipPlayers(game, allUnits, unit);
        List<Player> devours = CaptureUnitService.listCapturingCombatPlayers(game, unit);
        if (combat) {
            capturing.addAll(devours);
        }

        List<Player> killers = CaptureUnitService.listProbableKiller(game, unit);

        switch (unit.unitKey().getUnitType()) {
            case Infantry -> {
                capturing.addAll(CaptureUnitService.listCapturingMechPlayers(game, allUnits, unit));
            }
            case Mech -> {
                handleSelfAssemblyRoutines(player, totalAmount, game);
                if (player != null && player.hasUnit("mykomentori_mech")) {
                    for (int x = 0; x < totalAmount; x++) {
                        ButtonHelper.rollMykoMechRevival(game, player);
                    }
                }
                if (player != null && player.hasUnit("cheiran_mech")) {
                    AddUnitService.addUnits(event, unit.tile(), game, player.getColor(),
                        totalAmount + " infantry " + unit.uh().getName());
                    String message = "> Added " + totalAmount + " infantry to the planet following " + totalAmount
                        + " Nauplius (Cheiran mech) being destroyed.\n";
                    MessageHelper.sendMessageToEventChannel(event, message);
                }
            }
            case Flagship -> {
                if (player != null && player.hasUnit("yin_flagship")) {
                    String message1 = "Moments before disaster in game " + game.getName() + ".";
                    DisasterWatchHelper.postTileInDisasterWatch(game, event, unit.tile(), 0, message1);
                    UnitHolder uh = unit.tile().getSpaceUnitHolder();
                    for (Player player_ : game.getPlayers().values()) {
                        DestroyUnitService.destroyAllPlayerNonStructureUnits(event, game, player_, unit.tile(), uh, combat);
                    }
                    DisasterWatchHelper.postTileInDisasterWatch(game, event, unit.tile(), 0, 
                        player.getRepresentation() + " has detonated the bomb.");

                }
            }
            default -> Consumers.nop();
        }

        Set<String> counted = new HashSet<>();
        for (Player cabal : capturing) {
            if (!counted.add(cabal.getColorID()))
                continue;
            CaptureUnitService.executeCapture(event, game, cabal, unit);
        }
        if (player != null && combat && player.hasAbility("heroism")
            && (unit.unitKey().getUnitType() == UnitType.Infantry
                || unit.unitKey().getUnitType() == UnitType.Fighter)) {
            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, totalAmount, unit.unitKey().unitName(),
                event);
        }
        Player mentakHero = game.getPlayerFromColorOrFaction(game.getStoredValue("mentakHero"));
        if (mentakHero != null && combat) {
            ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero, totalAmount,
                unit.unitKey().unitName(), event, unit.tile());
        }
        if (player != null && player.hasTech("nekroc4y") && !combat && unit.tile() != player.getHomeSystemTile()
            && player.getHomeSystemTile() != null) {
            UnitModel uni = player.getUnitFromUnitKey(unit.unitKey());
            if (uni != null && uni.getIsShip()) {
                if (player.hasUnit("ghoti_flagship")
                    || ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)
                        .contains(player.getHomeSystemTile())) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "useNekroNullRef",
                        "Use Null Reference (Upon Each Destroy)", FactionEmojis.Nekro));
                    buttons.add(Buttons.red("deleteButtons", "Decline", FactionEmojis.Nekro));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation()
                            + ", you may produce one of your recently destroyed ships in your home system.",
                        buttons);
                }
            }
        }
        if (game.isTotalWarMode() && player != null) {
            UnitModel uni = player.getUnitFromUnitKey(unit.unitKey());
            int cost = (int) Math.ceil(uni.getCost());
            int winnings = cost * unit.getTotalRemoved();
            if (killers.isEmpty()) {
                List<Button> buttons = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    buttons.add(Buttons.gray("totalWarCommGain_" + winnings + "_" + p2.getFaction(),
                        p2.getFactionNameOrColor()));
                }
                buttons.add(Buttons.red("deleteButtons", "No one"));
                String msg = player.getRepresentation() + ", please tell the bot who killed your " + unit.getTotalRemoved()
                    + " " + unit.unitKey().getUnitType().getUnitTypeEmoji() + ".";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            } else {
                Player killer = killers.getFirst();
                String planet = ButtonHelperActionCards.getBestResPlanetInHomeSystem(killer, game);
                int newAmount = game.changeCommsOnPlanet(winnings, planet);
                MessageHelper.sendMessageToChannel(killer.getCorrectChannel(),
                    killer.getRepresentationNoPing() + " added " + winnings +
                        " commodities to the planet of " + Helper.getPlanetRepresentation(planet, game)
                        + " (which has " + newAmount + " commodities on it now) by destroying "
                        + unit.getTotalRemoved() +
                        " of " + player.getRepresentationNoPing() + "'s "
                        + unit.unitKey().getUnitType().getUnitTypeEmoji() +
                        "\nIf this was a mistake, adjust the commodities with `/ds set_planet_comms`.");
            }
        }
    }

    public static void handleSelfAssemblyRoutines(Player player, int min, Game game) {
        if (player.hasTech("sar")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                + " you gained " + min + " trade good" + (min == 1 ? "" : "s") + " (" + player.getTg() + "->" + (player.getTg() + min)
                + ") from _Self-Assembly Routines_ because of " + min + " of your mechs dying."
                + " This is a mandatory gain" + (min > 1 ? ", and happens 1 trade good at a time" : "") + ".");
            for (int x = 0; x < min; x++) {
                player.setTg(player.getTg() + 1);
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
    }
}
