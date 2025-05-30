package ti4.commands.special;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.image.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.ColorEmojis;

class SystemInfo extends GameStateSubcommand {

    public SystemInfo() {
        super(Constants.SYSTEM_INFO, "Info for system (all units)", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.EXTRA_RINGS, "Show additional rings around the selected system for context (Max 2)"));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_2, "System/Tile name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_3, "System/Tile name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_4, "System/Tile name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_5, "System/Tile name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
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

        Game game = getGame();
        for (OptionMapping tileOption : event.getOptions()) {
            if (tileOption == null || tileOption.getName().equals(Constants.EXTRA_RINGS)) {
                continue;
            }
            String tileID = tileOption.getAsString().toLowerCase();
            Tile tile = TileHelper.getTile(event, tileID, game);
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
                for (String cc : unitHolder.getCcList()) {
                    if (!hasCC) {
                        sb.append("Command Tokens: ");
                        hasCC = true;
                    }
                    appendFactionIcon(game, sb, cc, privateGame);
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
                    appendFactionIcon(game, sb, control, privateGame);
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
                    sb.append(player.getFactionEmojiOrColor()).append(ColorEmojis.getColorEmojiWithName(color));
                    sb.append(" `").append(unitEntry.getValue()).append("x` ");
                    if (unitModel != null) {
                        sb.append(unitModel.getUnitEmoji()).append(" ");
                        sb.append(privateGame ? unitModel.getBaseType() : unitModel.getName()).append("\n");
                    } else {
                        sb.append(unitKey).append("\n");
                    }
                }
                sb.append("----------\n");
            }
            FileUpload systemWithContext = new TileGenerator(game, event, null, context, tile.getPosition()).createFileUpload();
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
            MessageHelper.sendMessageWithFile(event.getChannel(), systemWithContext, "System", false);
            if (game.isFowMode()) {
                return;
            }
            for (Player player : game.getRealPlayers()) {
                if (!FoWHelper.playerHasUnitsInSystem(player, tile)) {
                    continue;
                }
                List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile);
                if (!players.isEmpty() && !player.getAllianceMembers().contains(players.get(0).getFaction()) && FoWHelper.playerHasShipsInSystem(player, tile)) {
                    Player player2 = players.get(0);
                    if (player2 == player) {
                        player2 = players.get(1);
                    }
                    List<Button> buttons = StartCombatService.getGeneralCombatButtons(game, tile.getPosition(), player, player2, "space", event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                    return;
                } else {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder instanceof Planet) {
                            if (ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName()).size() > 1) {
                                List<Player> listP = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
                                List<Button> buttons = StartCombatService.getGeneralCombatButtons(game, tile.getPosition(), listP.get(0), listP.get(1), "ground", event);
                                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), " ", buttons);
                                return;
                            }
                        }
                    }
                }

            }
        }
    }

    private static void appendFactionIcon(Game game, StringBuilder sb, String key, Boolean privateGame) {
        // parse IDs like "control_blk.png" and "command_red.png"
        String colorTokenRegex = "[a-z]+_" + RegexHelper.colorRegex(game) + "\\.png";
        Matcher tokenMatch = Pattern.compile(colorTokenRegex).matcher(key);
        if (tokenMatch.matches()) {
            String colorID = tokenMatch.group("color");
            String color = Mapper.getColorName(colorID);
            Player player = game.getPlayerFromColorOrFaction(color);
            if ((privateGame != null && privateGame) || player == null) {
                sb.append(" (").append(color).append(") ");
            } else {
                sb.append(player.getFactionEmoji()).append(" ").append(" (").append(color).append(") ");
            }
        }
    }
}
