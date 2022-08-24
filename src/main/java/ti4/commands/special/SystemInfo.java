package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SystemInfo extends SpecialSubcommandData {
    public SystemInfo() {
        super(Constants.SYSTEM_INFO, "Info for system (all units)");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile not found");
            return;
        }
        StringBuilder sb = new StringBuilder();
        java.util.Map<String, String> unitRepresentation = Mapper.getUnits();
        HashMap<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        java.util.Map<String, String> colorToId = Mapper.getColorToId();
        for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null){
                representation = name;
            }
            sb.append(representation).append("\n");
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();
            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                String key = unitEntry.getKey();

                for (String unitRepresentationKey : unitRepresentation.keySet()) {
                    if (key.endsWith(unitRepresentationKey)) {
                        for (java.util.Map.Entry<String, String> colorEntry : colorToId.entrySet()) {
                            String colorKey = colorEntry.getKey();
                            String color = colorEntry.getValue();
                            if (key.contains(colorKey)){
                                for (Player player_ : activeMap.getPlayers().values()) {
                                    if (Objects.equals(player_.getColor(), color)) {
                                        sb.append(Helper.getFactionIconFromDiscord(player_.getFaction())).append(" ").append(" (").append(color).append(") ");
                                    }
                                }
                            }
                        }
                        sb.append(unitRepresentation.get(unitRepresentationKey)).append(": ").append(unitEntry.getValue()).append("\n");
                    }
                }
            }
            sb.append("----------\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }
}
