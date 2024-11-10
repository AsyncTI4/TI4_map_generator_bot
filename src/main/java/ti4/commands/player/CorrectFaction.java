package ti4.commands.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

public class CorrectFaction extends PlayerSubcommandData {
    public CorrectFaction() {
        super(Constants.CORRECT_FACTION, "For Correcting A Players Faction");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "New faction").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        String newFaction = AliasHandler.resolveColor(event.getOption(Constants.FACTION).getAsString().toLowerCase());
        newFaction = AliasHandler.resolveFaction(newFaction);
        if (!Mapper.isValidFaction(newFaction)) {
            MessageHelper.sendMessageToEventChannel(event, "Faction not valid");
            return;
        }
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        changeFactionSheetAndComponents(event, game, player, newFaction);
    }

    public void changeFactionSheetAndComponents(GenericInteractionCreateEvent event, Game game, Player player, String newFaction) {
        Map<String, Player> players = game.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (newFaction.equals(playerInfo.getFaction())) {
                    MessageHelper.sendMessageToEventChannel(event, "Player:" + playerInfo.getUserName() + " already uses faction:" + newFaction);
                    return;
                }
            }
        }

        List<String> laws = new ArrayList<>(game.getLawsInfo().keySet());
        for (String law : laws) {
            if (game.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
                game.reviseLaw(game.getLaws().get(law), newFaction);
            }
        }

        FactionModel setupInfo = player.getFactionSetupInfo();
        player.setFaction(newFaction);
        player.getFactionTechs().clear();
        Set<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        player.setUnitsOwned(playerOwnedUnits);

        // STARTING COMMODITIES
        player.setCommoditiesTotal(setupInfo.getCommodities());
        for (String tech : setupInfo.getFactionTech()) {
            if (tech.trim().isEmpty()) {
                continue;
            }
            if (!player.getTechs().contains(tech)) {
                player.addFactionTech(tech);
            }
        }
        List<String> techs = new ArrayList<>(player.getTechs());
        for (String tech : techs) {
            player.removeTech(tech);
            player.addTech(tech);
        }
        player.setFactionEmoji(null);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        // TODO this is a bad idea overall as the ContextGame may change between when the command was run and the reply
        String userId = event.getUser().getId();
        String gameName = UserGameContextManager.getContextGame(userId);
        Game game = GameManager.getGame(gameName);
        GameSaveLoadManager.saveGame(game, event);
        ShowGame.simpleShowGame(game, event);
    }

    @SuppressWarnings("unused")
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
