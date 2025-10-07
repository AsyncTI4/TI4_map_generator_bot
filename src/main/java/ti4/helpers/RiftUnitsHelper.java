package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.jda.JdaComponentHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class RiftUnitsHelper {

    @ButtonHandler("riftUnit_")
    public static void riftUnitButton(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String ident = player.getFactionEmoji();
        String rest = buttonID.replace("riftUnit_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf('_'));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), ident + " " + riftUnit(unit, tile, game, event, player, null));
        }
        String message = event.getMessage().getContentRaw();
        List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
        event.getMessage()
                .editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                .queue();
    }

    @ButtonHandler("wormholeUnit_")
    public static void wormholeUnitButton(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String ident = player.getFactionEmoji();
        String rest = buttonID.replace("wormholeUnit_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf('_'));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), ident + " " + wormholeUnit(unit, tile, game, event, player));
        }
        String message = event.getMessage().getContentRaw();
        List<Button> systemButtons = getButtonsForWormholingUnitsInSystem(player, game, tile);
        event.getMessage()
                .editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                .queue();
    }

    @ButtonHandler("riftAllUnits_")
    public static void riftAllUnitsButton(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("riftAllUnits_", "").toLowerCase();
        String ident = player.getFactionEmoji();
        riftAllUnitsInASystem(pos, event, game, player, ident, null);
    }

    @ButtonHandler("wormholeAllShips_")
    public static void wormholeAllUnitsButton(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("wormholeAllShips_", "").toLowerCase();
        String ident = player.getFactionEmoji();
        wormholeAllUnitsInASystem(pos, event, game, player, ident);
    }

    public static void riftAllUnitsInASystem(
            String pos, ButtonInteractionEvent event, Game game, Player player, String ident, Player cabal) {
        Tile tile = game.getTileByPosition(pos);

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            if ((unitHolder instanceof Planet)) {
                continue;
            }
            Map<UnitKey, Integer> tileUnits = new HashMap<>(unitHolder.getUnits());
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;

                UnitKey key = unitEntry.getKey();
                if (key.getUnitType() == UnitType.Infantry
                        || key.getUnitType() == UnitType.Mech
                        || (!player.hasFF2Tech() && key.getUnitType() == UnitType.Fighter)
                        || (cabal != null
                                && (key.getUnitType() == UnitType.Fighter
                                        || key.getUnitType() == UnitType.Spacedock))) {
                    continue;
                }

                int totalUnits = unitEntry.getValue();
                String unitAsyncID = unitModel.getAsyncId();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null
                        && unitHolder.getUnitDamage().get(key) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(key);
                }
                for (int x = 0; x < damagedUnits; x++) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "A " + ident + riftUnit(unitAsyncID + "damaged", tile, game, event, player, cabal));
                }
                totalUnits -= damagedUnits;
                for (int x = 0; x < totalUnits; x++) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "A " + ident + riftUnit(unitAsyncID, tile, game, event, player, cabal));
                }
            }
        }
        if (cabal == null) {
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
            event.getMessage()
                    .editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                    .queue();
        } else {
            boolean deletedMessage = JdaComponentHelper.removeComponentFromMessageAndDeleteIfEmpty(event);
            if (!deletedMessage) {
                String exhaustedMessage = event.getMessage().getContentRaw();
                if ("".equalsIgnoreCase(exhaustedMessage)) {
                    exhaustedMessage = "Rift";
                }
                event.getMessage().editMessage(exhaustedMessage).queue();
            }
        }
    }

    public static void wormholeAllUnitsInASystem(
            String pos, ButtonInteractionEvent event, Game game, Player player, String ident) {
        Tile tile = game.getTileByPosition(pos);

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            if ((unitHolder instanceof Planet)) {
                continue;
            }
            Map<UnitKey, Integer> tileUnits = new HashMap<>(unitHolder.getUnits());
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;

                UnitKey key = unitEntry.getKey();
                if (key.getUnitType() == UnitType.Infantry
                        || key.getUnitType() == UnitType.Mech
                        || key.getUnitType() == UnitType.Spacedock
                        || (!player.hasFF2Tech() && key.getUnitType() == UnitType.Fighter)) {
                    continue;
                }

                int totalUnits = unitEntry.getValue();
                String unitAsyncID = unitModel.getAsyncId();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null
                        && unitHolder.getUnitDamage().get(key) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(key);
                }
                for (int x = 0; x < damagedUnits; x++) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "A " + ident + wormholeUnit(unitAsyncID + "damaged", tile, game, event, player));
                }
                totalUnits -= damagedUnits;
                for (int x = 0; x < totalUnits; x++) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "A " + ident + wormholeUnit(unitAsyncID, tile, game, event, player));
                }
            }
        }
        String message = event.getMessage().getContentRaw();
        List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
        event.getMessage()
                .editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                .queue();
    }

    public static String riftUnit(
            String unit, Tile tile, Game game, GenericInteractionCreateEvent event, Player player, Player cabal) {
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        Die d1 = new Die(4);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID());
        String msg = unitKey.unitEmoji() + " in tile " + tile.getPosition() + " rolled a "
                + d1.getGreenDieIfSuccessOrRedDieIfFailure();
        if (damaged) {
            msg = "damaged " + msg;
        }
        if (d1.isSuccess()) {
            msg += " and survived. May you always be so lucky.";
        } else {
            var parsedUnit = new ParsedUnit(unitKey);
            RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
            msg += " and failed. Condolences for your loss.";
            if (cabal != null
                    && cabal != player
                    && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(player, game, cabal)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, 1, unit, event);
            }
        }

        return msg;
    }

    public static String getWormholeUnit(String unit, boolean over5roll, Player player, Game game) {
        List<String> wormholeUnits = new ArrayList();

        wormholeUnits.addAll(List.of("ff", "dd", "ca", "dn", "cv", "fs", "ws"));
        if (!over5roll) {
            Collections.reverse(wormholeUnits);
        }
        boolean found = false;
        for (String u : wormholeUnits) {
            if (u.equalsIgnoreCase("ws") && !player.hasWarsunTech()) {
                continue;
            }
            if (found
                    && ButtonHelperFactionSpecific.vortexButtonAvailable(
                            game, Mapper.getUnitKey(u, player.getColorID()))) {
                return u;
            }
            if (u.equalsIgnoreCase(unit)) {
                found = true;
            }
        }
        return unit;
    }

    public static String wormholeUnit(
            String unit, Tile tile, Game game, GenericInteractionCreateEvent event, Player player) {
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        Die d1 = new Die(6);

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID());
        String unitAfterWormhole = getWormholeUnit(unitKey.asyncID(), d1.isSuccess(), player, game);
        UnitKey unitKey2 = Mapper.getUnitKey(AliasHandler.resolveUnit(unitAfterWormhole), player.getColorID());
        String msg = unitKey.unitEmoji() + " in tile " + tile.getPosition() + " rolled a "
                + d1.getGreenDieIfSuccessOrRedDieIfFailure();
        if (damaged) {
            msg = "damaged " + msg;
        }
        if (!unitKey.asyncID().equalsIgnoreCase(unitAfterWormhole)) {
            var parsedUnit = new ParsedUnit(unitKey);
            msg += " and changed into a " + unitKey2.unitEmoji();
            RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
            AddUnitService.addUnits(event, tile, game, player.getColor(), unitAfterWormhole);
        } else {
            msg += " and remained unchanged due to no valid options";
        }

        return msg;
    }

    private static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Game game, Tile tile) {
        String finChecker = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            if (!(unitHolder instanceof Planet)) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey key = unitEntry.getKey();
                    if (!player.unitBelongsToPlayer(key)) continue;

                    UnitModel unitModel = player.getUnitFromUnitKey(key);
                    if (unitModel == null) continue;

                    UnitType unitType = key.getUnitType();
                    if ((!game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
                                    && (unitType == UnitType.Infantry || unitType == UnitType.Mech))
                            || (!player.hasFF2Tech() && unitType == UnitType.Fighter)) {
                        continue;
                    }

                    String unitName = key.unitName();

                    int totalUnits = unitEntry.getValue();

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null
                            && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Buttons.red(
                                finChecker + "riftUnit_" + tile.getPosition() + "_" + x + unitName + "damaged",
                                "Rift " + x + " Damaged " + unitModel.getBaseType(),
                                unitModel.getUnitEmoji());
                        buttons.add(validTile2);
                    }
                    totalUnits -= damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Buttons.red(
                                finChecker + "riftUnit_" + tile.getPosition() + "_" + x + unitName,
                                "Rift " + x + " " + unitModel.getBaseType(),
                                unitModel.getUnitEmoji());
                        buttons.add(validTile2);
                    }
                }
            }
        }
        buttons.add(Buttons.gray(finChecker + "riftAllUnits_" + tile.getPosition(), "Rift All Units"));
        buttons.add(Buttons.blue("getDamageButtons_" + tile.getPosition() + "_remove", "Remove Transported Units"));
        buttons.add(Buttons.red("doneRifting", "Done Rifting Units and Removing Transported Units"));

        return buttons;
    }

    private static List<Button> getButtonsForWormholingUnitsInSystem(Player player, Game game, Tile tile) {
        String finChecker = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            if (!(unitHolder instanceof Planet)) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey key = unitEntry.getKey();
                    if (!player.unitBelongsToPlayer(key)) continue;

                    UnitModel unitModel = player.getUnitFromUnitKey(key);
                    if (unitModel == null) continue;

                    UnitType unitType = key.getUnitType();
                    if (((unitType == UnitType.Infantry || unitType == UnitType.Mech))
                            || (!player.hasFF2Tech() && unitType == UnitType.Fighter)) {
                        continue;
                    }

                    String unitName = key.unitName();

                    int totalUnits = unitEntry.getValue();

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null
                            && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Buttons.red(
                                finChecker + "wormholeUnit_" + tile.getPosition() + "_" + x + unitName + "damaged",
                                "Wormhole " + x + " Damaged " + unitModel.getBaseType(),
                                unitModel.getUnitEmoji());
                        buttons.add(validTile2);
                    }
                    totalUnits -= damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Buttons.red(
                                finChecker + "wormholeUnit_" + tile.getPosition() + "_" + x + unitName,
                                "Wormhole " + x + " " + unitModel.getBaseType(),
                                unitModel.getUnitEmoji());
                        buttons.add(validTile2);
                    }
                }
            }
        }
        buttons.add(Buttons.gray(finChecker + "wormholeAllShips_" + tile.getPosition(), "Wormhole All Ships"));
        buttons.add(
                Buttons.blue("getDamageButtons_" + tile.getPosition() + "_remove", "Remove Excess Transported Units"));
        buttons.add(Buttons.red("doneRifting", "Done Wormholing ships Units"));

        return buttons;
    }

    @ButtonHandler("doneRifting")
    public static void doneRifting(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        Tile tile = null;
        if (game.getActiveSystem() != null) {
            tile = game.getTileByPosition(game.getActiveSystem());
        }
        if (tile != null && "82b".equalsIgnoreCase(tile.getTileID())) {
            for (Player p : game.getRealPlayers()) {
                if (FoWHelper.playerHasUnitsInSystem(p, tile)) {
                    return;
                }
            }
            String msg = player.getRepresentation()
                    + " if the wormhole nexus was improperly unlocked during this action, you can use the button below to unflip it.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("unflipMallice", "Unflip Mallice"));
            buttons.add(Buttons.red("deleteButtons", "Leave It Alone"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        }
    }

    @ButtonHandler("getRiftButtons_")
    public static void offerRiftButtons(Player player, String buttonID, Game game) {
        String tilePosition = buttonID.replace("getRiftButtons_", "");
        Tile tile = game.getTileByPosition(tilePosition);
        MessageChannel channel = player.getCorrectChannel();
        if (player.hasAbility("celestial_guides")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " is rifting some units. However, because of their **Celestial Guides** ability, they do not roll.");
        } else if (player.hasRelic("circletofthevoid")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " is rifting some units. However, because of their _Circlet of the Void_ relic, they do not roll.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    channel,
                    player.getRepresentationNoPing()
                            + " is rifting some units. Please use the the buttons to choose the units you wish to risk in the gravity rift.",
                    getButtonsForRiftingUnitsInSystem(player, game, tile));
        }
    }

    @ButtonHandler("getWeirdWormholeButtons_")
    public static void offerWeirdWormholeButtons(Player player, String buttonID, Game game) {
        String tilePosition = buttonID.replace("getWeirdWormholeButtons_", "");
        Tile tile = game.getTileByPosition(tilePosition);
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationNoPing()
                        + " is rolling for weird wormhole units. Please use the the buttons to choose the units that went through a wormhole.",
                getButtonsForWormholingUnitsInSystem(player, game, tile));
    }
}
