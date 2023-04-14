package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

import java.util.*;

public class ChangeColor extends PlayerSubcommandData {
    public ChangeColor() {
        super(Constants.CHANGE_COLOR, "Player Color Change");
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isMapOpen()) {
            sendMessage("Can do faction setup only when map is open and not locked");
            return;
        }

        @SuppressWarnings("ConstantConditions")
        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isColorValid(color)) {
            sendMessage("Color not valid");
            return;
        }
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (color.equals(playerInfo.getColor())) {
                    sendMessage("Player:" + playerInfo.getUserName() + " already uses color:" + color);
                    return;
                }
            }
        }

        String oldColor = player.getColor();
        player.changeColor(color);

        for (Player playerInfo : players.values()) {
            LinkedHashMap<String, Integer> promissoryNotes = playerInfo.getPromissoryNotes();

            LinkedHashMap<String, Integer> promissoryNotesChanged = new LinkedHashMap<>();
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                String key = pn.getKey();
                Integer value = pn.getValue();
                String replacedKey = key.replace(oldColor, color);
                promissoryNotesChanged.put(replacedKey, value);
            }
            playerInfo.setPromissoryNotes(promissoryNotesChanged);

            List<String> promissoryNotesInPlayArea = playerInfo.getPromissoryNotesInPlayArea();
            List<String> promissoryNotesInPlayAreaChanged = new ArrayList<>();
            for (String pn : promissoryNotesInPlayArea) {
                String replacedPN = pn.replace(oldColor, color);
                promissoryNotesInPlayAreaChanged.add(replacedPN);
            }
            playerInfo.setPromissoryNotesInPlayArea(promissoryNotesInPlayAreaChanged);
        }

        String oldColorID = Mapper.getColorID(oldColor);
        String colorID = Mapper.getColorID(color);

        for (Tile tile : activeMap.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {

                HashMap<String, Integer> unitDamage = unitHolder.getUnitDamage();
                for (java.util.Map.Entry<String, Integer> unitDmg : unitDamage.entrySet()) {
                    String key = unitDmg.getKey();
                    Integer value = unitDmg.getValue();
                    String replacedKey = key.replace(oldColorID, colorID);
                    unitHolder.removeUnit(key, value);
                    unitHolder.addUnit(replacedKey, value);
                }

                HashMap<String, Integer> units = new HashMap<>(unitHolder.getUnits());
                for (java.util.Map.Entry<String, Integer> unit : units.entrySet()) {
                    String key = unit.getKey();
                    Integer value = unit.getValue();
                    String replacedKey = key.replace(oldColorID, colorID);
                    unitHolder.removeUnit(key, value);
                    unitHolder.addUnit(replacedKey, value);
                }

                HashSet<String> controlList = new HashSet<>(unitHolder.getControlList());
                for (String control : controlList) {
                    unitHolder.removeControl(control);
                    control = control.replace(oldColorID, colorID);
                    unitHolder.addControl(control);
                }

                HashSet<String> ccList = new HashSet<>(unitHolder.getCCList());
                for (String cc : ccList) {
                    unitHolder.removeCC(cc);
                    cc = cc.replace(oldColorID, colorID);
                    unitHolder.addCC(cc);
                }
            }
        }
    }
}
