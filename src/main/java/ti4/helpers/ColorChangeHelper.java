package ti4.helpers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.helpers.Units.UnitKey;
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
            case "lgy" ->
                !player.getUserID().equals(Constants.chassitId)
                        && player.getGame().getPlayerIDs().contains(Constants.chassitId);
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
                String updatedPN = mapPromissoryNoteId(game, player, newColorModel, pn.getKey(), true);
                promissoryNotesChanged.put(updatedPN, pn.getValue());
            }
            playerInfo.setPromissoryNotes(promissoryNotesChanged);

            // Promissory Notes In Play Areas
            List<String> promissoryNotesInPlayArea = playerInfo.getPromissoryNotesInPlayArea();
            List<String> promissoryNotesInPlayAreaChanged = new ArrayList<>();
            for (String pn : promissoryNotesInPlayArea) {
                String updatedPN = mapPromissoryNoteId(game, player, newColorModel, pn, true);
                promissoryNotesInPlayAreaChanged.add(updatedPN);
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
            for (String pool : playerInfo.getAllDebtTokens().keySet()) {
                Map<String, Integer> debtTokens = new LinkedHashMap<>(playerInfo.getDebtTokens(pool));
                for (Map.Entry<String, Integer> entry : debtTokens.entrySet()) {
                    String color = entry.getKey();
                    if (color.equals(oldColor)) {
                        Integer count = entry.getValue();
                        playerInfo.clearAllDebtTokens(color, pool);
                        playerInfo.addDebtTokens(newColor, count, pool);
                    }
                }
            }
        }

        // Player's Owned Promissory Notes
        Set<String> ownedPromissoryNotes = player.getPromissoryNotesOwned();
        Set<String> ownedPromissoryNotesChanged = new HashSet<>();
        for (String pn : ownedPromissoryNotes) {
            String updatedPN = mapPromissoryNoteId(game, player, newColorModel, pn, false);
            ownedPromissoryNotesChanged.add(updatedPN);
        }
        player.setPromissoryNotesOwned(ownedPromissoryNotesChanged);

        // Convert all unitholders
        game.getTileMap().values().stream()
                .flatMap(t -> t.getUnitHolders().values().stream())
                .forEach(uh -> replaceIDsOnUnitHolder(uh, oldColorID, newColorID));
        game.getPlayers().values().stream()
                .map(Player::getNomboxTile)
                .flatMap(t -> t.getUnitHolders().values().stream())
                .forEach(uh -> replaceIDsOnUnitHolder(uh, oldColorID, newColorID));
    }

    private static String mapPromissoryNoteId(
            Game game, Player owner, ColorModel newColorModel, String promissoryNoteId, boolean requireOwnerMatch) {
        PromissoryNoteModel pnModel = Mapper.getPromissoryNote(promissoryNoteId);
        if (!pnModel.isDupe()) {
            return promissoryNoteId;
        }
        if (requireOwnerMatch && game.getPNOwner(promissoryNoteId) != owner) {
            return promissoryNoteId;
        }

        PromissoryNoteModel genericPNModel = pnModel.getSourcePNModel();
        return genericPNModel.getId().replace("<color>", newColorModel.getName());
    }

    private static void replaceIDsOnUnitHolder(UnitHolder unitHolder, String oldColorID, String newColorID) {
        String oldColorSuffix = "_" + oldColorID + ".";
        String newColorSuffix = "_" + newColorID + ".";

        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            if (unitKey.getColorID().equals(oldColorID)) {
                UnitKey replacedKey = Units.getUnitKey(unitKey.getUnitType(), newColorID);
                List<Integer> states = unitHolder.removeUnit(unitKey, unitHolder.getUnitCount(unitKey));
                unitHolder.addUnitsWithStates(replacedKey, states);
            }
        }

        Set<String> controlList = new HashSet<>(unitHolder.getControlList());
        for (String control : controlList) {
            if (!control.contains(oldColorSuffix)) continue;
            unitHolder.removeControl(control);
            control = control.replace(oldColorSuffix, newColorSuffix);
            unitHolder.addControl(control);
        }

        Set<String> ccList = new HashSet<>(unitHolder.getCcList());
        for (String cc : ccList) {
            if (!cc.contains(oldColorSuffix)) continue;
            unitHolder.removeCC(cc);
            cc = cc.replace(oldColorSuffix, newColorSuffix);
            unitHolder.addCC(cc);
        }
    }
}
