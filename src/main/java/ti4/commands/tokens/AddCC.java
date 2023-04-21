package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import ti4.commands.units.MoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;

public class AddCC extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile, Map activeMap) {
        boolean usedTactics = false;
        for (String color : colors) {
            OptionMapping option = event.getOption(Constants.CC_USE);
            if (option != null && !usedTactics) {
                usedTactics = true;
                String value = option.getAsString().toLowerCase();
                switch (value) {
                    case "t/tactics", "t", "tactics", "tac", "tact" -> {
                        MoveUnits.removeTacticsCC(event, color, tile, activeMap);
                    }
                }
            }
            addCC(event, color, tile);
            Helper.isCCCountCorrect(event, activeMap, color);
        }
    }

    public static void addCC(SlashCommandInteractionEvent event, String color, Tile tile) {
        Map activeMap = MapManager.getInstance().getUserActiveMap(event.getUser().getId());
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }

        if (activeMap.isFoWMode()) {
            String colorMention = Helper.getColourAsMention(event.getGuild(), color);
            FoWHelper.pingSystem(activeMap, event, tile.getPosition(), colorMention + " has placed a token in the system");
        }

        tile.addCC(ccID);
    }

    public static boolean hasCC(@Nullable SlashCommandInteractionEvent event, String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null && event != null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        return tile.hasCC(ccID);
    }

    @Override
    protected String getActionDescription() {
        return "Add cc to tile/system";
    }

    @Override
    public String getActionID() {
        return Constants.ADD_CC;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.CC_USE, "Type tactics or t, retreat, reinforcements or r").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true))
        );
    }
}
