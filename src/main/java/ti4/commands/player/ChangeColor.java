package ti4.commands.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ChangeColor extends PlayerSubcommandData {
    public ChangeColor() {
        super(Constants.CHANGE_COLOR, "Player Color Change");
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        String newColor = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isValidColor(newColor)) {
            MessageHelper.sendMessageToEventChannel(event, "Color not valid");
            return;
        }
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        Map<String, Player> players = activeGame.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (newColor.equals(playerInfo.getColor())) {
                    MessageHelper.sendMessageToEventChannel(event, "Player:" + playerInfo.getUserName() + " already uses color:" + newColor);
                    return;
                }
            }
        }

        String oldColor = player.getColor();
        String oldColorKey = oldColor + "_";
        String newColorKey = newColor + "_";
        player.changeColor(newColor);
        String oldColorID = Mapper.getColorID(oldColor);
        String newColorID = Mapper.getColorID(newColor);

        for (Player playerInfo : players.values()) {
            Map<String, Integer> promissoryNotes = playerInfo.getPromissoryNotes();

            Map<String, Integer> promissoryNotesChanged = new LinkedHashMap<>();
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
                    String replacedCC = cc.replace(oldColor, newColor);
                    playerInfo.removeMahactCC(cc);
                    playerInfo.addMahactCC(replacedCC);
                }
            }

            Map<String, Integer> debtTokens = new LinkedHashMap<>(playerInfo.getDebtTokens());
            for (String color : debtTokens.keySet()) {
                if (color.equals(oldColor)) {
                    Integer count = debtTokens.get(color);
                    playerInfo.clearAllDebtTokens(color);
                    playerInfo.addDebtTokens(newColor, count);
                }
            }
        }

        Set<String> ownedPromissoryNotes = player.getPromissoryNotesOwned();
        Set<String> ownedPromissoryNotesChanged = new HashSet<>();
        for (String pn : ownedPromissoryNotes) {
            String newKey = pn;
            if (pn.startsWith(oldColorKey)) {
                newKey = pn.replace(oldColorKey, newColorKey);
            }
            ownedPromissoryNotesChanged.add(newKey);
        }
        player.setPromissoryNotesOwned(ownedPromissoryNotesChanged);

        // Convert all unitholders
        activeGame.getTileMap().values().stream()
            .flatMap(t -> t.getUnitHolders().values().stream())
            .forEach(uh -> replaceIDsOnUnitHolder(uh, oldColorID, newColorID));
        activeGame.getPlayers().values().stream().map(Player::getNomboxTile)
            .flatMap(t -> t.getUnitHolders().values().stream())
            .forEach(uh -> replaceIDsOnUnitHolder(uh, oldColorID, newColorID));
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
        ShowGame.simpleShowGame(activeGame, event);
    }

    private void replaceIDsOnUnitHolder(UnitHolder unitHolder, String oldColorID, String newColorID) {
        String oldColorSuffix = "_" + oldColorID + ".";
        String newColorSuffix = "_" + newColorID + ".";

        Map<UnitKey, Integer> unitDamage = new HashMap<>(unitHolder.getUnitDamage());
        for (Map.Entry<UnitKey, Integer> unitDmg : unitDamage.entrySet()) {
            UnitKey unitKey = unitDmg.getKey();
            if (unitKey.getColorID().equals(oldColorID)) {
                Integer value = unitDmg.getValue();
                UnitKey replacedKey = Mapper.getUnitKey(unitKey.asyncID(), newColorID);
                unitHolder.removeUnitDamage(unitKey, value);
                unitHolder.addUnitDamage(replacedKey, value);
            }
        }

        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        for (Map.Entry<UnitKey, Integer> unit : units.entrySet()) {
            UnitKey unitKey = unit.getKey();
            if (unitKey.getColorID().equals(oldColorID)) {
                Integer value = unit.getValue();
                UnitKey replacedKey = Mapper.getUnitKey(unitKey.asyncID(), newColorID);
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
