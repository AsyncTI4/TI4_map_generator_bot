package ti4.discord.interactions.buttons.handlers.faction.pok.cabal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DiceHelper;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.RiftUnitsHelper;
import ti4.helpers.Units;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.RiftSetModeService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class CabalButtonHandler {

    @ButtonHandler("cabalAgentCapture_")
    public static void resolveCabalAgentCapture(
            String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Unable to resolve player, please resolve manually.");
            return;
        }
        int commodities = p2.getCommodities();
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + " a " + unit
                        + " of yours has been captured by "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "The Stillness of Stars, the Vuil'raith"
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent. "
                        + "Rejoice, for your " + commodities + " commodities been washed.");
        p2.setTg(p2.getTg() + commodities);
        p2.setCommodities(0);
        ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, player, 1, unit, event, true);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cabalHeroTile_")
    public static void executeCabalHero(String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String pos = buttonID.replace("cabalHeroTile_", "");
        Tile tile = game.getTileByPosition(pos);
        for (Player p2 : game.getRealPlayersNNeutral()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile)
                    && (!ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, game, player)
                            || game.isTwilightsFallMode())) {
                RiftUnitsHelper.riftAllUnitsInASystem(pos, event, game, p2, p2.getFactionEmoji(), player);
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile)
                    && ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, game, player)) {
                String msg = player.getRepresentationUnfogged() + " has failed to eat units owned by "
                        + p2.getRepresentation() + " because they were blockaded. Womp Womp.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        ButtonHelper.deleteTheOneButton(event);
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelper.checkFleetAndCapacity(p2, game, tile);
        }
    }

    @ButtonHandler("cabalHeroAll")
    public static void resolveCabalHero(Player player, Game game, ButtonInteractionEvent event) {
        boolean tf = game.isTwilightsFallMode();
        List<Tile> tiles = new ArrayList<>();
        Map<String, List<Object>> totalLosses = new HashMap<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 != player) {
                totalLosses.put(p2.getFactionEmoji(), new ArrayList<>());
            }
            if (tf) continue; // tf is all rifts, done below
            if (p2.hasTech("dt2")
                    || p2.getUnitsOwned().contains("cabal_spacedock")
                    || p2.getUnitsOwned().contains("cabal_spacedock2")
                    || p2.hasTech("absol_dt2")
                    || p2.getUnitsOwned().contains("absol_cabal_spacedock")
                    || p2.getUnitsOwned().contains("absol_cabal_spacedock2")) {
                tiles.addAll(
                        CheckUnitContainmentService.getTilesContainingPlayersUnits(game, p2, Units.UnitType.Spacedock));
            }
        }

        List<Tile> adjTiles = new ArrayList<>();
        if (tf || RiftSetModeService.isActive(game)) {
            tiles = RiftSetModeService.getAllTilesWithRift(game);
            adjTiles.addAll(tiles);
        }
        for (Tile tile : tiles) {
            for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
                Tile tileToAdd = game.getTileByPosition(pos);
                if (!tileToAdd.getTileModel().isHyperlane()
                        && !adjTiles.contains(tileToAdd)
                        && !tile.getPosition().equalsIgnoreCase(pos)) {
                    adjTiles.add(tileToAdd);
                }
            }
        }
        adjTiles.sort((t1, t2) -> t1.getPosition().compareToIgnoreCase(t2.getPosition()));

        Map<Tile, Player> resolveFighter2s = new HashMap<>();
        Map<Tile, Player> resolveMixedCapacity = new HashMap<>();

        StringBuilder message = new StringBuilder();
        for (Tile tile : adjTiles) {
            boolean content = false;
            message.append("### Resolving for tile ")
                    .append(tile.getRepresentationForButtons())
                    .append('\n');
            for (Player p2 : game.getRealPlayersNNeutral()) {
                if (p2 == player) {
                    continue;
                }
                if (!tf
                        && FoWHelper.playerHasShipsInSystem(p2, tile)
                        && ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, game, player)) {
                    message.append(player.getRepresentationUnfogged())
                            .append(" has failed to eat units owned by ")
                            .append(p2.getRepresentation())
                            .append(" because they were blockaded. Womp Womp.\n");
                    content = true;
                    continue;
                }
                if (!FoWHelper.playerHasShipsInSystem(p2, tile)) {
                    continue;
                }

                for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                    UnitHolder unitHolder = entry.getValue();
                    if ((unitHolder instanceof Planet)) {
                        continue;
                    }
                    Map<Units.UnitKey, Integer> tileUnits = new HashMap<>(unitHolder.getUnits());
                    for (Map.Entry<Units.UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                        if (!p2.unitBelongsToPlayer(unitEntry.getKey())) {
                            continue;
                        }
                        UnitModel unitModel = p2.getUnitFromUnitKey(unitEntry.getKey());
                        if (unitModel == null || unitModel.getUnitEmoji() == null) {
                            continue;
                        }

                        Units.UnitKey key = unitEntry.getKey();
                        if (key.getUnitType() == Units.UnitType.Infantry
                                || key.getUnitType() == Units.UnitType.Mech
                                || key.getUnitType() == Units.UnitType.Fighter
                                || key.getUnitType() == Units.UnitType.Spacedock
                                || key.getUnitType() == Units.UnitType.Pds) {
                            continue;
                        }

                        int totalUnits = unitEntry.getValue();
                        int damagedUnits = 0;
                        if (unitHolder.getUnitDamage() != null
                                && unitHolder.getUnitDamage().get(key) != null) {
                            damagedUnits = unitHolder.getUnitDamage().get(key);
                        }
                        if (damagedUnits > 0) {
                            message.append("Rolling for ")
                                    .append(p2.getRepresentationNoPing())
                                    .append(" damaged ")
                                    .append(unitModel.getBaseType())
                                    .append(damagedUnits == 1 ? "" : "s")
                                    .append(" :boom:")
                                    .append(unitModel.getUnitEmoji())
                                    .append(": ");
                            for (int i = 0; i < damagedUnits; i++) {
                                DiceHelper.Die dice = new DiceHelper.Die(tf ? 6 : 4);
                                message.append(dice.getGreenDieIfSuccessOrRedDieIfFailure());
                                if (!dice.isSuccess()) {
                                    RemoveUnitService.removeUnit(
                                            event, tile, game, p2, unitHolder, key.getUnitType(), 1, true);
                                    if (!tf) {
                                        AddUnitService.addUnits(
                                                event,
                                                player.getNomboxTile(),
                                                game,
                                                p2.getColor(),
                                                "1 " + key.asyncID());
                                    }
                                    totalLosses.get(p2.getFactionEmoji()).add(unitModel.getUnitEmoji());
                                }
                            }
                            message.append('\n');
                            content = true;
                        }
                        if (totalUnits > damagedUnits) {
                            message.append("Rolling for ")
                                    .append(p2.getRepresentationNoPing())
                                    .append(' ')
                                    .append(unitModel.getBaseType())
                                    .append(totalUnits - damagedUnits == 1 ? "" : "s")
                                    .append(' ')
                                    .append(unitModel.getUnitEmoji())
                                    .append(": ");
                            for (int i = 0; i < totalUnits - damagedUnits; i++) {
                                DiceHelper.Die dice = new DiceHelper.Die(tf ? 6 : 4);
                                message.append(dice.getGreenDieIfSuccessOrRedDieIfFailure());
                                if (!dice.isSuccess()) {
                                    RemoveUnitService.removeUnit(
                                            event, tile, game, p2, unitHolder, key.getUnitType(), 1, false);
                                    if (!tf) {
                                        AddUnitService.addUnits(
                                                event,
                                                player.getNomboxTile(),
                                                game,
                                                p2.getColor(),
                                                "1 " + key.asyncID());
                                    }
                                    totalLosses.get(p2.getFactionEmoji()).add(unitModel.getUnitEmoji());
                                }
                            }
                            message.append('\n');
                            content = true;
                        }
                    }

                    int[] capNCap = ButtonHelper.checkFleetAndCapacity(p2, game, tile, false, false);
                    int fleetUsed = capNCap[0];
                    int capacity = capNCap[2];
                    int dockedFighters = capNCap[3];
                    int fighter2s = capNCap[4];
                    int fighterCount = tileUnits.getOrDefault(Units.getUnitKey("ff", p2.getColor()), 0);
                    int mechCount = tileUnits.getOrDefault(Units.getUnitKey("mf", p2.getColor()), 0);
                    int infantryCount = tileUnits.getOrDefault(Units.getUnitKey("gf", p2.getColor()), 0);

                    if (fighter2s > 0) {
                        if (fleetUsed > p2.getFleetCC()) {
                            int overCapacity = fleetUsed - p2.getFleetCC();
                            if (fighter2s == fleetUsed) // player has only fighter 2s in the system
                            {
                                message.append(p2.getRepresentationNoPing())
                                        .append(" now has ")
                                        .append(overCapacity)
                                        .append(" fighter")
                                        .append(overCapacity == 1 ? "" : "s")
                                        .append(" in excess of their fleet pool; removing")
                                        .append(tf ? "" : " and capturing")
                                        .append(".\n");
                                RemoveUnitService.removeUnit(
                                        event, tile, game, p2, unitHolder, Units.UnitType.Fighter, overCapacity, false);
                                if (!tf) {
                                    AddUnitService.addUnits(
                                            event, player.getNomboxTile(), game, p2.getColor(), overCapacity + " ff");
                                }
                                for (int i = 0; i < overCapacity; i++) {
                                    totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.fighter);
                                }
                            } else {
                                message.append(p2.getRepresentationNoPing())
                                        .append(" is now over their fleet pool. Use buttons below to resolve this.\n");
                                resolveFighter2s.put(tile, p2);
                                for (int i = 0; i < overCapacity; i++) {
                                    totalLosses.get(p2.getFactionEmoji()).add("❔");
                                }
                            }
                        }
                    } else if (mechCount + infantryCount > capacity
                            || mechCount + infantryCount + fighterCount > capacity + dockedFighters) {
                        int overCapacity = Math.max(
                                mechCount + infantryCount - capacity,
                                mechCount + infantryCount + fighterCount - capacity - dockedFighters);
                        if (mechCount == 0 && infantryCount == 0) {
                            message.append(p2.getRepresentationNoPing())
                                    .append(" has ")
                                    .append(overCapacity)
                                    .append(" fighter")
                                    .append(overCapacity == 1 ? "" : "s")
                                    .append(" in excess of their amended capacity; removing")
                                    .append(tf ? "" : " and capturing")
                                    .append(".\n");
                            RemoveUnitService.removeUnit(
                                    event, tile, game, p2, unitHolder, Units.UnitType.Fighter, overCapacity, false);
                            if (!tf) {
                                AddUnitService.addUnits(
                                        event, player.getNomboxTile(), game, p2.getColor(), overCapacity + " ff");
                            }
                            for (int i = 0; i < overCapacity; i++) {
                                totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.fighter);
                            }
                        } else if (fighterCount == 0 && infantryCount == 0) {
                            message.append(p2.getRepresentationNoPing())
                                    .append(" has ")
                                    .append(overCapacity)
                                    .append(" mech")
                                    .append(overCapacity == 1 ? "" : "s")
                                    .append(" in excess of their amended capacity; removing")
                                    .append(tf ? "" : " and capturing")
                                    .append(".\n");
                            RemoveUnitService.removeUnit(
                                    event, tile, game, p2, unitHolder, Units.UnitType.Mech, overCapacity, false);
                            if (!tf) {
                                AddUnitService.addUnits(
                                        event, player.getNomboxTile(), game, p2.getColor(), overCapacity + " mf");
                            }
                            for (int i = 0; i < overCapacity; i++) {
                                totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.mech);
                            }
                        } else if (fighterCount == 0 && mechCount == 0) {
                            message.append(p2.getRepresentationNoPing())
                                    .append(" has ")
                                    .append(overCapacity)
                                    .append(" infantry in excess of their amended capacity; removing")
                                    .append(tf ? "" : " and capturing")
                                    .append(".\n");
                            RemoveUnitService.removeUnit(
                                    event, tile, game, p2, unitHolder, Units.UnitType.Infantry, overCapacity, false);
                            if (!tf) {
                                AddUnitService.addUnits(
                                        event, player.getNomboxTile(), game, p2.getColor(), overCapacity + " gf");
                            }
                            for (int i = 0; i < overCapacity; i++) {
                                totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.infantry);
                            }
                        } else {
                            String unitListing;
                            if (fighterCount * mechCount * infantryCount > 0) {
                                unitListing = "fighter" + (fighterCount == 1 ? "" : "s") + ", mech"
                                        + (mechCount == 1 ? "" : "s") + " and infantry";
                            } else {
                                unitListing = (fighterCount >= 1 ? "fighter" : "")
                                        + (fighterCount >= 2 ? "s" : "")
                                        + (fighterCount > 0 ? " and " : "")
                                        + (mechCount >= 1 ? "mech" : "")
                                        + (mechCount >= 2 ? "s" : "")
                                        + (mechCount * infantryCount > 0 ? " and " : "")
                                        + (infantryCount >= 1 ? "infantry" : "");
                            }
                            if (capacity == 0) {
                                message.append(p2.getRepresentationNoPing())
                                        .append(" has a mixture of ")
                                        .append(overCapacity)
                                        .append(' ')
                                        .append(unitListing)
                                        .append(" in excess of their amended (zero) capacity; removing")
                                        .append(tf ? "" : " and capturing")
                                        .append(".\n");
                                RemoveUnitService.removeUnit(
                                        event,
                                        tile,
                                        game,
                                        p2,
                                        unitHolder,
                                        Units.UnitType.Fighter,
                                        fighterCount - dockedFighters,
                                        false);
                                if (!tf) {
                                    AddUnitService.addUnits(
                                            event,
                                            player.getNomboxTile(),
                                            game,
                                            p2.getColor(),
                                            (fighterCount - dockedFighters) + " ff");
                                }
                                for (int i = dockedFighters; i < fighterCount; i++) {
                                    totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.fighter);
                                }
                                RemoveUnitService.removeUnit(
                                        event,
                                        tile,
                                        game,
                                        p2,
                                        unitHolder,
                                        Units.UnitType.Infantry,
                                        infantryCount,
                                        false);
                                if (!tf) {
                                    AddUnitService.addUnits(
                                            event, player.getNomboxTile(), game, p2.getColor(), infantryCount + " gf");
                                }
                                for (int i = 0; i < infantryCount; i++) {
                                    totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.infantry);
                                }
                                RemoveUnitService.removeUnit(
                                        event, tile, game, p2, unitHolder, Units.UnitType.Mech, mechCount, false);
                                if (!tf) {
                                    AddUnitService.addUnits(
                                            event, player.getNomboxTile(), game, p2.getColor(), mechCount + " mf");
                                }
                                for (int i = 0; i < mechCount; i++) {
                                    totalLosses.get(p2.getFactionEmoji()).add(UnitEmojis.mech);
                                }
                            } else {
                                message.append(p2.getRepresentationNoPing())
                                        .append(" has a mixture of ")
                                        .append(overCapacity)
                                        .append(' ')
                                        .append(unitListing)
                                        .append(" in excess of their amended capacity. Please remove ")
                                        .append(overCapacity == 1 ? "this" : "these")
                                        .append(" with the buttons below.\n");
                                resolveMixedCapacity.put(tile, p2);
                                for (int i = 0; i < overCapacity; i++) {
                                    totalLosses.get(p2.getFactionEmoji()).add("❓");
                                }
                            }
                        }
                    }
                }
                ButtonHelper.checkFleetAndCapacity(p2, game, tile);
            }
            if (!content) {
                message.append("No enemy units found in this system.\n");
            }
        }

        message.append("## Capture Summary");
        boolean nothing = true;
        for (Map.Entry<String, List<Object>> entry : totalLosses.entrySet()) {
            String faction = entry.getKey();
            List<Object> captured = entry.getValue();
            if (captured.isEmpty()) {
                message.append("\n> ").append(faction).append(" - nothing.");
            } else {
                message.append("\n> ").append(faction).append(" - ");
                List<TI4Emoji> orderedEmoji = Arrays.asList(
                        UnitEmojis.warsun,
                        UnitEmojis.flagship,
                        UnitEmojis.dreadnought,
                        UnitEmojis.carrier,
                        UnitEmojis.cruiser,
                        UnitEmojis.destroyer,
                        UnitEmojis.fighter,
                        UnitEmojis.mech,
                        UnitEmojis.infantry);
                for (TI4Emoji emoji : orderedEmoji) {
                    int count = Collections.frequency(captured, emoji);
                    for (int i = 0; i < count; i++) {
                        message.append(emoji);
                        captured.remove(emoji);
                    }
                    if (count > 0) {
                        message.append(Character.toString(8194));
                    }
                }
                // for any non-standard captures
                captured.sort((e1, e2) -> e1.toString().compareToIgnoreCase(e2.toString()));
                for (Object emoji : captured) {
                    message.append(emoji);
                }
                nothing = false;
            }
        }
        if (nothing) {
            DisasterWatchHelper.sendMessageInDisasterWatch(
                    game,
                    player.getRepresentationUnfogged()
                            + " purged " + (tf ? "the _Event Horizon_ paradigm" : "It Feeds on Carrion, their hero")
                            + ", and captured... nothing " + MiscEmojis.TaDont
                            + ".");
        }
        message.append("\n-# Please report any bugs to `#bot-bugs-and-feature-requests`.");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());

        for (Map.Entry<Tile, Player> location : resolveFighter2s.entrySet()) {
            Tile tile = location.getKey();
            Player p2 = location.getValue();
            List<Button> buttons = new ArrayList<>();

            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                UnitHolder unitHolder = entry.getValue();
                if ((unitHolder instanceof Planet)) {
                    continue;
                }
                Map<Units.UnitKey, Integer> tileUnits = new HashMap<>(unitHolder.getUnits());
                for (Map.Entry<Units.UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    if (!p2.unitBelongsToPlayer(unitEntry.getKey())) {
                        continue;
                    }
                    UnitModel unitModel = p2.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) {
                        continue;
                    }

                    Units.UnitKey key = unitEntry.getKey();
                    if (key.getUnitType() == Units.UnitType.Spacedock) {
                        continue;
                    }
                    if (!tf
                            && (key.getUnitType() == Units.UnitType.Infantry
                                    || key.getUnitType() == Units.UnitType.Mech
                                    || key.getUnitType() == Units.UnitType.Fighter)) {
                        buttons.add(Buttons.red(
                                "removeNCaptureThisTypeOfUnit_"
                                        + key.getUnitType().humanReadableName() + "_" + tile.getPosition() + "_"
                                        + unitHolder.getName() + "_" + player.getColor(),
                                key.getUnitType().humanReadableName() + " from " + tile.getRepresentation()
                                        + " in Space"));
                    } else {
                        buttons.add(Buttons.blue(
                                "removeThisTypeOfUnit_" + key.getUnitType().humanReadableName() + "_"
                                        + tile.getPosition() + "_" + unitHolder.getName(),
                                key.getUnitType().humanReadableName() + " from " + tile.getRepresentation()
                                        + " in Space"));
                    }
                }
            }
            buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
            MessageHelper.sendMessageToChannelWithButtons(
                    p2.getCorrectChannel(),
                    p2.getRepresentation() + ", you are exceeding your fleet pool "
                            + (resolveMixedCapacity.getOrDefault(tile, null) == p2 ? "and capacity limits" : "limit")
                            + " in tile " + tile.getRepresentationForButtons()
                            + ". Please remove some units.",
                    buttons);
        }

        for (Map.Entry<Tile, Player> location : resolveMixedCapacity.entrySet()) {
            Tile tile = location.getKey();
            Player p2 = location.getValue();
            List<Button> buttons = new ArrayList<>();
            if (resolveFighter2s.getOrDefault(tile, null) == p2) {
                continue;
            }

            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                UnitHolder unitHolder = entry.getValue();
                if ((unitHolder instanceof Planet)) {
                    continue;
                }
                Map<Units.UnitKey, Integer> tileUnits = new HashMap<>(unitHolder.getUnits());
                for (Map.Entry<Units.UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    if (!p2.unitBelongsToPlayer(unitEntry.getKey())) {
                        continue;
                    }
                    UnitModel unitModel = p2.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) {
                        continue;
                    }

                    Units.UnitKey key = unitEntry.getKey();
                    if (key.getUnitType() == Units.UnitType.Infantry
                            || key.getUnitType() == Units.UnitType.Mech
                            || key.getUnitType() == Units.UnitType.Fighter) {
                        if (tf) {
                            buttons.add(Buttons.blue(
                                    "removeThisTypeOfUnit_" + key.getUnitType().humanReadableName() + "_"
                                            + tile.getPosition() + "_" + unitHolder.getName(),
                                    key.getUnitType().humanReadableName() + " from " + tile.getRepresentation()
                                            + " in Space"));
                        } else {
                            buttons.add(Buttons.red(
                                    "removeNCaptureThisTypeOfUnit_"
                                            + key.getUnitType().humanReadableName() + "_" + tile.getPosition() + "_"
                                            + unitHolder.getName() + "_" + player.getColor(),
                                    key.getUnitType().humanReadableName() + " from " + tile.getRepresentation()
                                            + " in Space"));
                        }
                    }
                }
            }
            buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
            MessageHelper.sendMessageToChannelWithButtons(
                    p2.getCorrectChannel(),
                    p2.getRepresentation() + ", you are exceeding your capacity limit in tile "
                            + tile.getRepresentationForButtons() + ". Please remove some units.",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
    }
}
