package ti4.commands.tokens;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class AddToken extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {

        String tokenName = event.getOption(Constants.TOKEN, "", OptionMapping::getAsString).toLowerCase();
        if (tokenName.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Token not specified.");
            return;
        }
        tokenName = StringUtils.substringBefore(tokenName, " ");
        tokenName = AliasHandler.resolveAttachment(tokenName);
        if (!Mapper.isValidToken(tokenName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Token not found: " + tokenName);
            return;
        }
        addToken(event, tile, tokenName, game);
        game.clearPlanetsCache();
    }

    public static void addToken(GenericInteractionCreateEvent event, Tile tile, String tokenName, Game game) {
        MessageChannel channel = event != null ? event.getMessageChannel() : game.getMainGameChannel();
        String tokenFileName = Mapper.getAttachmentImagePath(tokenName);
        String tokenPath = tile.getAttachmentPath(tokenFileName);
        if (tokenFileName != null && tokenPath != null) {
            addToken(event, tile, tokenFileName, true, game);
        } else {
            tokenName = AliasHandler.resolveToken(tokenName);
            tokenFileName = Mapper.getTokenID(tokenName);
            tokenPath = tile.getTokenPath(tokenFileName);

            if (tokenPath == null) {
                MessageHelper.sendMessageToChannel(channel, "Token: " + tokenName + " is not valid");
                return;
            }
            addToken(event, tile, tokenFileName, Mapper.getSpecialCaseValues(Constants.PLANET).contains(tokenName), game);
        }
    }

    private static void addToken(GenericInteractionCreateEvent event, Tile tile, String tokenID, boolean needSpecifyPlanet, Game game) {
        MessageChannel channel = event != null ? event.getMessageChannel() : game.getMainGameChannel();
        String unitHolder = Constants.SPACE;
        if (needSpecifyPlanet) {
            OptionMapping option = null;
            if (event instanceof SlashCommandInteractionEvent) {
                option = ((CommandInteractionPayload) event).getOption(Constants.PLANET);
            }

            if (option != null) {
                unitHolder = option.getAsString().toLowerCase();
            } else {
                Set<String> unitHolderIDs = tile.getUnitHolders().keySet();
                if (unitHolderIDs.size() == 2) {
                    Set<String> unitHolders = new HashSet<>(unitHolderIDs);
                    unitHolders.remove(Constants.SPACE);
                    unitHolder = unitHolders.iterator().next();
                } else {
                    String message = "Multiple planets present in system, need to specify planet.";
                    if (unitHolderIDs.size() == 1) {
                        message = "No planets present in system.";
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    return;
                }
            }
        }

        unitHolder = unitHolder.replace(" ", "");
        StringTokenizer planetTokenizer = new StringTokenizer(unitHolder, ",");
        while (planetTokenizer.hasMoreTokens()) {
            String planet = planetTokenizer.nextToken();
            planet = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet(planet));
            if (!tile.isSpaceHolderValid(planet)) {
                MessageHelper.sendMessageToChannel(channel, "Planet: " + planet + " is not valid and not supported.");
                continue;
            }
            if (tokenID.contains("dmz")) {
                Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
                UnitHolder planetUnitHolder = unitHolders.get(planet);
                UnitHolder spaceUnitHolder = unitHolders.get(Constants.SPACE);
                if (planetUnitHolder != null && spaceUnitHolder != null) {
                    Map<UnitKey, Integer> units = new HashMap<>(planetUnitHolder.getUnits());
                    for (Player player_ : game.getPlayers().values()) {
                        String color = player_.getColor();
                        planetUnitHolder.removeAllUnits(color);
                    }
                    Map<UnitKey, Integer> spaceUnits = spaceUnitHolder.getUnits();
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        UnitKey key = unitEntry.getKey();
                        if (Set.of(UnitType.Fighter, UnitType.Infantry, UnitType.Mech).contains(key.getUnitType())) {
                            Integer count = spaceUnits.get(key);
                            if (count == null) {
                                count = unitEntry.getValue();
                            } else {
                                count += unitEntry.getValue();
                            }
                            spaceUnits.put(key, count);
                        }
                    }

                }
            }
            tile.addToken(tokenID, planet);
            if (Mapper.getTokenID(Constants.MIRAGE).equals(tokenID)) {
                Helper.addMirageToTile(tile);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Add token to tile/planet";
    }

    @Override
    public String getName() {
        return Constants.ADD_TOKEN;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getName(), this.getDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TOKEN, "Token name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet name").setAutoComplete(true))

        );
    }
}
