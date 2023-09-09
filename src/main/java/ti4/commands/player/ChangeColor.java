package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.*;

public class ChangeColor extends PlayerSubcommandData {
    public ChangeColor() {
        super(Constants.CHANGE_COLOR, "Player Color Change");
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        String newColour = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isColorValid(newColour)) {
            sendMessage("Color not valid");
            return;
        }
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        LinkedHashMap<String, Player> players = activeGame.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (newColour.equals(playerInfo.getColor())) {
                    sendMessage("Player:" + playerInfo.getUserName() + " already uses color:" + newColour);
                    return;
                }
            }
        }

        String oldColor = player.getColor();
        String oldColorKey = oldColor + "_";
        String newColorKey = newColour + "_";
        player.changeColor(newColour);
        String oldColorID = Mapper.getColorID(oldColor);
        String colorID = Mapper.getColorID(newColour);
        
        String oldColorSuffix = "_" + oldColorID + ".";
        String newColorSuffix = "_" + colorID + ".";

        for (Player playerInfo : players.values()) {
            LinkedHashMap<String, Integer> promissoryNotes = playerInfo.getPromissoryNotes();
            
            LinkedHashMap<String, Integer> promissoryNotesChanged = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                String key = pn.getKey();
                Integer value = pn.getValue();
                String newKey = key;
                if (key.startsWith(oldColorKey)) {
                    newKey = key.replace(oldColorKey, newColorKey);
                }
                promissoryNotesChanged.put(newKey, value);
            }
            playerInfo.setPromissoryNotes(promissoryNotesChanged);

            List<String> promissoryNotesInPlayArea = playerInfo.getPromissoryNotesInPlayArea();
            List<String> promissoryNotesInPlayAreaChanged = new ArrayList<>();
            for (String pn : promissoryNotesInPlayArea) {
                String newKey = pn;
                if (pn.startsWith(oldColorKey)) {
                    newKey = pn.replace(oldColorKey, newColorKey);
                }
                promissoryNotesInPlayAreaChanged.add(newKey);
            }
            playerInfo.setPromissoryNotesInPlayArea(promissoryNotesInPlayAreaChanged);
            
            List<String> mahactCC = new ArrayList<>(playerInfo.getMahactCC());
            for (String cc : mahactCC) {
                if (cc.equals(oldColor)) {
                    String replacedCC = cc.replace(oldColor, newColour);
                    playerInfo.removeMahactCC(cc);
                    playerInfo.addMahactCC(replacedCC);
                }
            }

            Map<String, Integer> debtTokens = new LinkedHashMap<>(playerInfo.getDebtTokens());
            for (String colour : debtTokens.keySet()) {
                if (colour.equals(oldColor)) {
                    Integer count = debtTokens.get(colour);
                    playerInfo.clearAllDebtTokens(colour);
                    playerInfo.addDebtTokens(newColour, count);
                }
            }
        }

        Set<String> ownedPromissoryNotes = player.getPromissoryNotesOwned();
        HashSet<String> ownedPromissoryNotesChanged = new HashSet<>();
        for (String pn : ownedPromissoryNotes) {
            String newKey = pn;
            if (pn.startsWith(oldColorKey)) {
                newKey = pn.replace(oldColorKey, newColorKey);
            }
            ownedPromissoryNotesChanged.add(newKey);
        }
        player.setPromissoryNotesOwned(ownedPromissoryNotesChanged);


        for (Tile tile : activeGame.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {

                Map<String, Integer> unitDamage = new HashMap<>(unitHolder.getUnitDamage());
                for (Map.Entry<String, Integer> unitDmg : unitDamage.entrySet()) {
                    String key = unitDmg.getKey();
                    if (!key.startsWith(oldColorID)) continue;
                    Integer value = unitDmg.getValue();
                    String replacedKey = key.replace(oldColorID, colorID);
                    unitHolder.removeUnitDamage(key, value);
                    unitHolder.addUnitDamage(replacedKey, value);
                }

                Map<String, Integer> units = new HashMap<>(unitHolder.getUnits());
                for (Map.Entry<String, Integer> unit : units.entrySet()) {
                    String key = unit.getKey();
                    if (!key.startsWith(oldColorID)) continue;
                    Integer value = unit.getValue();
                    String replacedKey = key.replace(oldColorID, colorID);
                    unitHolder.removeUnit(key, value);
                    unitHolder.addUnit(replacedKey, value);
                }

                Set<String> controlList = new HashSet<>(unitHolder.getControlList());
                for (String control : controlList) {
                    if (!control.contains(oldColorID)) continue;
                    unitHolder.removeControl(control);
                    control = control.replace(oldColorID, colorID);
                    unitHolder.addControl(control);
                }

                
                Set<String> ccList = new HashSet<>(unitHolder.getCCList());
                for (String cc : ccList) {
                    unitHolder.removeCC(cc);
                    cc = cc.replace(oldColorSuffix, newColorSuffix);
                    unitHolder.addCC(cc);
                }
            }
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
        GameSaveLoadManager.saveMap(activeGame, event);

        File file = GenerateMap.getInstance().saveImage(activeGame, event);
        MessageHelper.replyToMessage(event, file);
    }
}
