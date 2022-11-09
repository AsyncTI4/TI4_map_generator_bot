package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.tokens.AddCC;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class AddUnits extends AddRemoveUnits {
    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        tile.addUnit(planetName, unitID, count);
    }

    @Override
    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Map map, boolean addCounter) {
        OptionMapping option = event.getOption(Constants.CC);

        if (option == null && addCounter) {
            MoveUnits.removeTacticsCC(event, color, tile, MapManager.getInstance().getUserActiveMap(event.getUser().getId()));
            AddCC.addCC(event, color, tile);
            Helper.isCCCountCorrect(event, map, color);
        } else {
            if (option == null) {
                AddCC.addCC(event, color, tile);
                Helper.isCCCountCorrect(event, map, color);
            }
            String value = option.getAsString().toLowerCase();

            switch (value) {
                case "r", "retreat", "reinforcements" -> {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, map, color);
                }
                case "n", "none", "no" -> {
                    // Do Nothing with CCs
                }
            }
        }
    }

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Map map) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        Map activeMap = mapManager.getUserActiveMap(userID);

        tile = MoveUnits.flipMallice(event, tile, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Malice");
            return;
        }
        super.unitParsingForTile(event, color, tile, map);
    }

    @Override
    public String getActionID() {
        return Constants.ADD_UNITS;
    }

    @Override
    protected String getActionDescription() {
        return "Add units to map";
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.CC, "Type no or n to not add CC, reinforcements to add a CC without taking it from tactics").setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                                .setAutoComplete(true))
        );
    }
}
