package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class RiftUnitsHelper {

    @ButtonHandler("riftUnit_")
    public static void riftUnitButton(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String ident = player.getFactionEmoji();
        String rest = buttonID.replace("riftUnit_", "").toLowerCase();
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unit = rest;
        for (int x = 0; x < amount; x++) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                ident + " " + riftUnit(unit, tile, game, event, player, null));
        }
        String message = event.getMessage().getContentRaw();
        List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
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

    public static void riftAllUnitsInASystem(String pos, ButtonInteractionEvent event, Game game, Player player, String ident, Player cabal) {
        Tile tile = game.getTileByPosition(pos);

        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
            }
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (!(unitHolder instanceof Planet)) {
                Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                        continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null)
                        continue;

                    UnitKey key = unitEntry.getKey();
                    if (key.getUnitType() == UnitType.Infantry
                        || key.getUnitType() == UnitType.Mech
                        || (!player.hasFF2Tech() && key.getUnitType() == UnitType.Fighter)
                        || (cabal != null && (key.getUnitType() == UnitType.Fighter
                            || key.getUnitType() == UnitType.Spacedock))) {
                        continue;
                    }

                    int totalUnits = unitEntry.getValue();
                    String unitAsyncID = unitModel.getAsyncId();
                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    for (int x = 1; x < damagedUnits + 1; x++) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            ident + " " + riftUnit(unitAsyncID + "damaged", tile, game, event, player, cabal));
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1; x++) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            ident + " " + riftUnit(unitAsyncID, tile, game, event, player, cabal));
                    }
                }
            }
        }
        if (cabal == null) {
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = getButtonsForRiftingUnitsInSystem(player, game, tile);
            event.getMessage().editMessage(message).setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        } else {
            List<ActionRow> actionRow2 = new ArrayList<>();
            String exhaustedMessage = event.getMessage().getContentRaw();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (!buttonRow.isEmpty()) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if ("".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Rift";
            }
            if (!actionRow2.isEmpty()) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            } else {
                ButtonHelper.deleteMessage(event);
            }
        }

    }

    public static String riftUnit(String unit, Tile tile, Game game, GenericInteractionCreateEvent event, Player player, Player cabal) {
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        Die d1 = new Die(4);
        String msg = Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " in tile " + tile.getPosition() + " rolled a " + d1.getGreenDieIfSuccessOrRedDieIfFailure();
        if (damaged) {
            msg = "A damaged " + msg;
        }
        if (d1.isSuccess()) {
            msg = msg + " and survived. May you always be so lucky.";
        } else {
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
            var parsedUnit = new ParsedUnit(key);
            RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
            msg = msg + " and failed. Condolences for your loss.";
            if (cabal != null && cabal != player
                && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(player, game, cabal)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, 1, unit, event);
            }
        }

        return msg;
    }

    public static List<Button> getButtonsForRiftingUnitsInSystem(Player player, Game game, Tile tile) {
        String finChecker = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();

        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();

            if (unitHolder instanceof Planet) {
            } else {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey key = unitEntry.getKey();
                    if (!player.unitBelongsToPlayer(key))
                        continue;

                    UnitModel unitModel = player.getUnitFromUnitKey(key);
                    if (unitModel == null)
                        continue;

                    UnitType unitType = key.getUnitType();
                    if ((!game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
                        && (unitType == UnitType.Infantry || unitType == UnitType.Mech))
                        || (!player.hasFF2Tech() && unitType == UnitType.Fighter)) {
                        continue;
                    }

                    String asyncID = key.asyncID();
                    asyncID = key.unitName();

                    int totalUnits = unitEntry.getValue();

                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
                    for (int x = 1; x < damagedUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Buttons.red(
                            finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID + "damaged",
                            "Rift " + x + " damaged " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                    totalUnits = totalUnits - damagedUnits;
                    for (int x = 1; x < totalUnits + 1 && x <= 2; x++) {
                        Button validTile2 = Buttons.red(
                            finChecker + "riftUnit_" + tile.getPosition() + "_" + x + asyncID,
                            "Rift " + x + " " + unitModel.getBaseType());
                        validTile2 = validTile2.withEmoji(emoji);
                        buttons.add(validTile2);
                    }
                }
            }
        }
        buttons.add(Buttons.gray(finChecker + "riftAllUnits_" + tile.getPosition(), "Rift all units"));
        buttons.add(Buttons.blue("getDamageButtons_" + tile.getPosition() + "_remove", "Remove excess infantry/fighters"));
        buttons.add(Buttons.red("doneRifting", "Done rifting units and removing excess capacity"));

        return buttons;
    }

    @ButtonHandler("doneRifting")
    public static void doneRifting(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        Tile tile = null;
        if (game.getActiveSystem() != null) {
            tile = game.getTileByPosition(game.getActiveSystem());
        }
        if (tile != null && tile.getTileID().equalsIgnoreCase("82b")) {
            for (Player p : game.getRealPlayers()) {
                if (FoWHelper.playerHasUnitsInSystem(p, tile)) {
                    return;
                }
            }
            String msg = player.getRepresentation() + " if mallice was improperly unlocked during this action, you can use the button below to unflip it";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("unflipMallice", "Unflip Mallice"));
            buttons.add(Buttons.red("deleteButtons", "Leave it alone"));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        }
    }

    @ButtonHandler("getRiftButtons_")
    public static void offerRiftButtons(Player player, String buttonID, Game game) {
        String ident = player.getFactionEmoji();
        String tilePosition = buttonID.replace("getRiftButtons_", "");
        Tile tile = game.getTileByPosition(tilePosition);
        MessageChannel channel = player.getCorrectChannel();
        String msg = ident + " use buttons to rift units";
        MessageHelper.sendMessageToChannel(channel, player.getFactionEmoji() + " is rifting some units");
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, RiftUnitsHelper.getButtonsForRiftingUnitsInSystem(player, game, tile));
    }
}
