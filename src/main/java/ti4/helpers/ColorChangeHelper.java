package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.model.ColorModel;
import ti4.model.PromissoryNoteModel;

@UtilityClass
public class ColorChangeHelper {

    public static boolean colorIsExclusive(String color, Player player) {
        String colorID = Mapper.getColorID(color);
        return switch (colorID) {
            // Riftset is exclusive to eronous always
            case "ero" -> !player.getUserID().equals(Constants.eronousId);
            // Lightgray is exclusive to chassit if chassit is in the game
            case "lgy" -> !player.getUserID().equals(Constants.chassitId) && player.getGame().getPlayerIDs().contains(Constants.chassitId);
            default -> false;
        };
    }

    public static void changePlayerColor(Game game, Player player, String oldColor, String newColor) {
        player.setColor(newColor);
        String oldColorID = Mapper.getColorID(oldColor);
        String newColorID = Mapper.getColorID(newColor);
        ColorModel newColorModel = Mapper.getColor(newColorID);

        Map<String, Player> players = game.getPlayers();
        for (Player playerInfo : players.values()) {

            // Promissory Notes In Hands
            Map<String, Integer> promissoryNotes = playerInfo.getPromissoryNotes();

            Map<String, Integer> promissoryNotesChanged = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn.getKey());
                if (!pnModel.isDupe()) {
                    promissoryNotesChanged.put(pn.getKey(), pn.getValue());
                    continue;
                }
                if (game.getPNOwner(pn.getKey()) != player) {
                    continue;
                }
                PromissoryNoteModel genericPNModel = pnModel.getSourcePNModel();
                String newPNModelID = genericPNModel.getID().replace("<color>", newColorModel.getName());
                promissoryNotesChanged.put(newPNModelID, pn.getValue());
            }
            playerInfo.setPromissoryNotes(promissoryNotesChanged);

            // Promissory Notes In Play Areas
            List<String> promissoryNotesInPlayArea = playerInfo.getPromissoryNotesInPlayArea();
            List<String> promissoryNotesInPlayAreaChanged = new ArrayList<>();
            for (String pn : promissoryNotesInPlayArea) {
                PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn);
                if (!pnModel.isDupe()) {
                    promissoryNotesInPlayAreaChanged.add(pn);
                    continue;
                }
                if (game.getPNOwner(pn) != player) {
                    continue;
                }
                PromissoryNoteModel genericPNModel = pnModel.getSourcePNModel();
                String newPNModelID = genericPNModel.getID().replace("<color>", newColorModel.getName());
                promissoryNotesInPlayAreaChanged.add(newPNModelID);
            }
            playerInfo.setPromissoryNotesInPlayArea(promissoryNotesInPlayAreaChanged);

            // Mahact/Edict CCs
            List<String> mahactCC = new ArrayList<>(playerInfo.getMahactCC());
            for (String cc : mahactCC) {
                if (cc.equals(oldColor)) {
                    String replacedCC = cc.replace(oldColor, newColor);
                    playerInfo.removeMahactCC(cc);
                    playerInfo.addMahactCC(replacedCC);
                }
            }

            // Debt Tokens
            Map<String, Integer> debtTokens = new LinkedHashMap<>(playerInfo.getDebtTokens());
            for (String color : debtTokens.keySet()) {
                if (color.equals(oldColor)) {
                    Integer count = debtTokens.get(color);
                    playerInfo.clearAllDebtTokens(color);
                    playerInfo.addDebtTokens(newColor, count);
                }
            }
        }

        // Player's Owned Promissory Notes
        Set<String> ownedPromissoryNotes = player.getPromissoryNotesOwned();
        Set<String> ownedPromissoryNotesChanged = new HashSet<>();
        for (String pn : ownedPromissoryNotes) {
            PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn);
            if (!pnModel.isDupe()) {
                ownedPromissoryNotesChanged.add(pn);
                continue;
            }
            PromissoryNoteModel genericPNModel = pnModel.getSourcePNModel();
            String newPNModelID = genericPNModel.getID().replace("<color>", newColorModel.getName());
            ownedPromissoryNotesChanged.add(newPNModelID);
        }
        player.setPromissoryNotesOwned(ownedPromissoryNotesChanged);

        // Convert all unitholders
        game.getTileMap().values().stream()
            .flatMap(t -> t.getUnitHolders().values().stream())
            .forEach(uh -> replaceIDsOnUnitHolder(uh, oldColorID, newColorID));
        game.getPlayers().values().stream().map(Player::getNomboxTile)
            .flatMap(t -> t.getUnitHolders().values().stream())
            .forEach(uh -> replaceIDsOnUnitHolder(uh, oldColorID, newColorID));
    }

    private static void replaceIDsOnUnitHolder(UnitHolder unitHolder, String oldColorID, String newColorID) {
        String oldColorSuffix = "_" + oldColorID + ".";
        String newColorSuffix = "_" + newColorID + ".";

        Map<Units.UnitKey, Integer> unitDamage = new HashMap<>(unitHolder.getUnitDamage());
        for (Map.Entry<Units.UnitKey, Integer> unitDmg : unitDamage.entrySet()) {
            Units.UnitKey unitKey = unitDmg.getKey();
            if (unitKey.getColorID().equals(oldColorID)) {
                Integer value = unitDmg.getValue();
                Units.UnitKey replacedKey = Mapper.getUnitKey(unitKey.asyncID(), newColorID);
                unitHolder.removeDamagedUnit(unitKey, value);
                unitHolder.addDamagedUnit(replacedKey, value);
            }
        }

        Map<Units.UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        for (Map.Entry<Units.UnitKey, Integer> unit : units.entrySet()) {
            Units.UnitKey unitKey = unit.getKey();
            if (unitKey.getColorID().equals(oldColorID)) {
                Integer value = unit.getValue();
                Units.UnitKey replacedKey = Mapper.getUnitKey(unitKey.asyncID(), newColorID);
                unitHolder.removeUnit(unitKey, value);
                unitHolder.addUnit(replacedKey, value);
            }
        }

        Set<String> controlList = new HashSet<>(unitHolder.getControlList());
        for (String control : controlList) {
            if (!control.contains(oldColorSuffix)) continue;
            unitHolder.removeControl(control);
            control = control.replace(oldColorSuffix, newColorSuffix);
            unitHolder.addControl(control);
        }

        Set<String> ccList = new HashSet<>(unitHolder.getCCList());
        for (String cc : ccList) {
            if (!cc.contains(oldColorSuffix)) continue;
            unitHolder.removeCC(cc);
            cc = cc.replace(oldColorSuffix, newColorSuffix);
            unitHolder.addCC(cc);
        }
    }
}
