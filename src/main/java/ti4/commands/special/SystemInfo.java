package ti4.commands.special;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.combat.StartCombat;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

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
        Game game = getActiveGame();

        int context = 0;
        OptionMapping ringsMapping = event.getOption(Constants.EXTRA_RINGS);
        if (ringsMapping != null) {
            context = ringsMapping.getAsInt();
            int newContext = Math.min(context, 2);
            if (context < 0) newContext = 0;
            if (context == 333) newContext = 3;
            if (context == 444) newContext = 4;
            if (context == 555) newContext = 5;
            if (context == 666) newContext = 6;
            context = newContext;
        }

        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null || tileOption.getName().equals(Constants.EXTRA_RINGS)) {
                continue;
            }
            String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
            Tile tile = AddUnits.getTile(event, tileID, game);
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
            Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            Map<String, String> colorToId = Mapper.getColorToId();
            Boolean privateGame = FoWHelper.isPrivateGame(game, event);
            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                String name = entry.getKey();
                String representation = planetRepresentations.get(name);
                if (representation == null) {
                    representation = name;
                }
                UnitHolder unitHolder = entry.getValue();
                if (unitHolder instanceof Planet planet) {
                    sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(representation, game));
                    sb.append(" Resources: ").append(planet.getResources()).append("/").append(planet.getInfluence());
                } else {
                    sb.append(representation);
                }
                sb.append("\n");
                boolean hasCC = false;
                for (String cc : unitHolder.getCCList()) {
                    if (!hasCC) {
                        sb.append("Command Counters: ");
                        hasCC = true;
                    }
                    addtFactionIcon(game, sb, colorToId, cc, privateGame);
                }
                if (hasCC) {
                    sb.append("\n");
                }
                boolean hasToken = false;
                Map<String, String> tokensToName = Mapper.getTokensToName();
                for (String token : unitHolder.getTokenList()) {
                    if (!hasToken) {
                        sb.append("Tokens: ");
                        hasToken = true;
                    }
                    for (Map.Entry<String, String> entry_ : tokensToName.entrySet()) {
                        String key = entry_.getKey();
                        String value = entry_.getValue();
                        if (token.contains(key)) {
                            sb.append(value).append(" ");

                        }
                    }
                }
                if (hasToken) {
                    sb.append("\n");
                }
                boolean hasControl = false;
                for (String control : unitHolder.getControlList()) {
                    if (!hasControl) {
                        sb.append("Control Counters: ");
                        hasControl = true;
                    }
                    addtFactionIcon(game, sb, colorToId, control, privateGame);
                }
                if (hasControl) {
                    sb.append("\n");
                }

                Map<UnitKey, Integer> units = unitHolder.getUnits();
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    UnitKey unitKey = unitEntry.getKey();
                    String color = AliasHandler.resolveColor(unitKey.getColorID());
                    Player player = game.getPlayerFromColorOrFaction(color);
                    if (player == null) continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                    sb.append(player.getFactionEmojiOrColor()).append(Emojis.getColorEmojiWithName(color));
                    sb.append(" `").append(unitEntry.getValue()).append("x` ");
                    if (unitModel != null) {
                        sb.append(Emojis.getEmojiFromDiscord(unitModel.getBaseType())).append(" ").append(unitModel.getName()).append("\n");
                    } else {
                        sb.append(unitKey).append("\n");
                    }
                }
                sb.append("----------\n");
            }
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, context, tile.getPosition(), event);
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
            MessageHelper.sendMessageWithFile(event.getChannel(), systemWithContext, "System", false);
            if (!game.isFoWMode()) {
                for (Player player : game.getRealPlayers()) {

                    if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
                        continue;
                    }
                    List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile);
                    if (players.size() > 0 && !player.getAllianceMembers().contains(players.get(0).getFaction()) && FoWHelper.playerHasShipsInSystem(player, tile)) {
                        Player player2 = players.get(0);
                        if (player2 == player) {
                            player2 = players.get(1);
                        }
                        List<Button> buttons = StartCombat.getGeneralCombatButtons(game, tile.getPosition(), player, player2, "space", event);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                        return;
                    } else {
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            if (unitHolder instanceof Planet) {
                                if (ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName()).size() > 1) {
                                    List<Player> listP = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
                                    List<Button> buttons = StartCombat.getGeneralCombatButtons(game, tile.getPosition(), listP.get(0), listP.get(1), "ground", event);
                                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                                    return;
                                }
                            }
                        }
                    }

                }
            }

        }
    }

    private static void addtFactionIcon(Game game, StringBuilder sb, Map<String, String> colorToId, String key, Boolean privateGame) {

        for (Map.Entry<String, String> colorEntry : colorToId.entrySet()) {
            String colorKey = colorEntry.getKey();
            String color = colorEntry.getValue();
            if (key.contains(colorKey)) {
                for (Player player_ : game.getPlayers().values()) {
                    if (Objects.equals(player_.getColor(), color)) {
                        if (privateGame != null && privateGame) {
                            sb.append(" (").append(color).append(") ");
                        } else {
                            sb.append(player_.getFactionEmoji()).append(" ").append(" (").append(color).append(") ");
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
