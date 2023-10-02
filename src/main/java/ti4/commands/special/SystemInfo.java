package ti4.commands.special;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SystemInfo extends SpecialSubcommandData {
    public SystemInfo() {
        super(Constants.SYSTEM_INFO, "Info for system (all units)");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.EXTRA_RINGS, "Show additional rings around the selected system for context (Max 2)").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_2, "System/Tile name").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_3, "System/Tile name").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_4, "System/Tile name").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_5, "System/Tile name").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        int context = 0;
        OptionMapping ringsMapping = event.getOption(Constants.EXTRA_RINGS);
        if (ringsMapping != null) {
            context = ringsMapping.getAsInt();
            if (context > 2) context = 2;
            if (context < 0) context = 0;
        }

        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null || tileOption.getName().equals(Constants.EXTRA_RINGS)){
                continue;
            }
            String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
            Tile tile = AddUnits.getTile(event, tileID, activeGame);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Tile " + tileOption.getAsString() + " not found");
                continue;
            }
            String tileName = tile.getTilePath();
            tileName = tileName.substring(tileName.indexOf("_") + 1);
            tileName = tileName.substring(0, tileName.indexOf(".png"));
            tileName = " - " + tileName + "[" + tile.getTileID() + "]";
            StringBuilder sb = new StringBuilder();
            sb.append("__**Tile: ").append(tile.getPosition()).append(tileName).append("**__\n");
            Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
            Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            Map<String, String> colorToId = Mapper.getColorToId();
            Boolean privateGame = FoWHelper.isPrivateGame(activeGame, event);
            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                String name = entry.getKey();
                String representation = planetRepresentations.get(name);
                if (representation == null){
                    representation = name;
                }
                UnitHolder unitHolder = entry.getValue();
                if (unitHolder instanceof Planet planet) {
                    sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(representation, activeGame));
                    sb.append(" Resources: ").append(planet.getResources()).append("/").append(planet.getInfluence());
                } else {
                    sb.append(representation);
                }
                sb.append("\n");
                boolean hasCC = false;
                for (String cc : unitHolder.getCCList()) {
                    if (!hasCC){
                        sb.append("Command Counters: ");
                        hasCC = true;
                    }
                    addtFactionIcon(activeGame, sb, colorToId, cc, privateGame);
                }
                if (hasCC) {
                    sb.append("\n");
                }
                boolean hasControl = false;
                for (String control : unitHolder.getControlList()) {
                    if (!hasControl){
                        sb.append("Control Counters: ");
                        hasControl = true;
                    }
                    addtFactionIcon(activeGame, sb, colorToId, control, privateGame);
                }
                if (hasControl) {
                    sb.append("\n");
                }
                boolean hasToken = false;
                Map<String, String> tokensToName = Mapper.getTokensToName();
                for (String token : unitHolder.getTokenList()) {
                    if (!hasToken){
                        sb.append("Tokens: ");
                        hasToken = true;
                    }
                    for (Map.Entry<String, String> entry_ : tokensToName.entrySet()) {
                        String key = entry_.getKey();
                        String value = entry_.getValue();
                        if (token.contains(key)){
                                sb.append(value).append(" ");

                            }
                        }
                }
                if (hasToken) {
                    sb.append("\n");
                }

                HashMap<String, Integer> units = unitHolder.getUnits();
                for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                    String key = unitEntry.getKey();

                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey)) {
                            addtFactionIcon(activeGame, sb, colorToId, key, privateGame);
                            sb.append(unitRepresentation.get(unitRepresentationKey)).append(": ").append(unitEntry.getValue()).append("\n");
                        }
                    }
                }
                sb.append("----------\n");
            }
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event);
            MessageHelper.sendMessageWithFile(event.getChannel(), systemWithContext, sb.toString(), false);
            if(!activeGame.isFoWMode()){
                for(Player player : activeGame.getRealPlayers()){

                    List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeGame, tile);
                    if(players.size() > 0 && !player.getAllianceMembers().contains(players.get(0).getFaction())){
                        Player player2 = players.get(0);
                        if(player2 == player){
                            player2 = players.get(1);
                        }
                        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame,  tile.getPosition(), player, player2, "space");
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                        break;
                    }
                }
            }
                            

        }
    }

    private static void addtFactionIcon(Game activeGame, StringBuilder sb, Map<String, String> colorToId, String key, Boolean privateGame) {

        for (Map.Entry<String, String> colorEntry : colorToId.entrySet()) {
            String colorKey = colorEntry.getKey();
            String color = colorEntry.getValue();
            if (key.contains(colorKey)){
                for (Player player_ : activeGame.getPlayers().values()) {
                    if (Objects.equals(player_.getColor(), color)) {
                        if (privateGame != null && privateGame) {
                            sb.append(" (").append(color).append(") ");
                        } else {
                            sb.append(Helper.getFactionIconFromDiscord(player_.getFaction())).append(" ").append(" (").append(color).append(") ");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }
}
