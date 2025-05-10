package ti4.commands.tokens;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.PlanetService;

public class RemoveTokenCommand extends AddRemoveTokenCommand {

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.TOKEN, "Token name")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.PLANET, "Planet name")
                        .setAutoComplete(true));
    }

    @Override
    void doAction(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {
        OptionMapping option = event.getOption(Constants.TOKEN);
        if (option != null) {
            String tokenName = option.getAsString().toLowerCase();
            tokenName = StringUtils.substringBefore(tokenName, " ");
            tokenName = AliasHandler.resolveAttachment(tokenName);

            String tokenID = Mapper.getAttachmentImagePath(tokenName);
            String tokenPath = tile.getAttachmentPath(tokenID);
            if (tokenPath != null) {
                removeToken(event, tile, tokenID, true);
                game.clearPlanetsCache();
            } else {
                tokenID = Mapper.getTokenID(tokenName);
                tokenPath = tile.getTokenPath(tokenID);

                if (tokenPath == null) {
                    MessageHelper.replyToMessage(event, "Token: " + tokenName + " is not valid");
                    return;
                }
                if (tokenID.equals(Constants.CUSTODIAN_TOKEN_PNG)) {
                    removeToken(event, tile, tokenID, false);
                }
                removeToken(event, tile, tokenID, Mapper.getSpecialCaseValues(Constants.PLANET).contains(tokenName));
                game.clearPlanetsCache();
            }
        } else {
            MessageHelper.replyToMessage(event, "Token not specified.");
        }
    }

    private void removeToken(SlashCommandInteractionEvent event, Tile tile, String tokenID, boolean needSpecifyPlanet) {
        String unitHolder = Constants.SPACE;
        if (needSpecifyPlanet) {
            OptionMapping option = event.getOption(Constants.PLANET);
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
                    MessageHelper.sendMessageToChannel(event.getChannel(), message);
                }
            }
        }

        unitHolder = unitHolder.replace(" ", "");
        StringTokenizer planetTokenizer = new StringTokenizer(unitHolder, ",");
        while (planetTokenizer.hasMoreTokens()) {
            String planet = planetTokenizer.nextToken();
            planet = PlanetService.getPlanet(tile, AliasHandler.resolvePlanet(planet));
            if (!tile.isSpaceHolderValid(planet)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: " + planet + " is not valid and not supported.");
                continue;
            }
            tile.removeToken(tokenID, planet);
            if (Mapper.getTokenID(Constants.MIRAGE).equals(tokenID)) {
                Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
                if (unitHolders.get(Constants.MIRAGE) != null) {
                    unitHolders.remove(Constants.MIRAGE);
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return "Remove token from tile/planet";
    }

    @Override
    public String getName() {
        return Constants.REMOVE_TOKEN;
    }
}
