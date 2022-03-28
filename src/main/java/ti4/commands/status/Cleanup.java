package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class Cleanup extends StatusSubcommandData {
    public Cleanup() {
        super(Constants.CLEANUP, "Status phase cleanup");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }


        Map activeMap = getActiveMap();
        HashMap<String, Tile> tileMap = activeMap.getTileMap();
        for (Tile tile : tileMap.values()) {
            tile.removeAllCC();
            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeAllCC();
                unitHolder.removeAllUnitDamage();
            }
        }
        HashMap<Integer, Boolean> scPlayed = activeMap.getScPlayed();
        for (java.util.Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            sc.setValue(false);
        }
        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        for (Player player : players.values()) {
            player.setPassed(false);
            player.setSC(0);
        }
    }
}
