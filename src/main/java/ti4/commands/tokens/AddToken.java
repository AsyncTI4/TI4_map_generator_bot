package ti4.commands.tokens;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

import java.awt.*;
import java.util.*;

public class AddToken extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile) {

        OptionMapping option = event.getOption(Constants.TOKEN);
        if (option != null) {
            String tokenName = option.getAsString().toLowerCase();
            tokenName = AliasHandler.resolveAttachment(tokenName);

            String tokenFileName = Mapper.getAttachmentID(tokenName);
            String tokenPath = tile.getAttachmentPath(tokenFileName);
            if (tokenPath != null) {
                addToken(event, tile, tokenFileName, true);
            } else {
                tokenName = AliasHandler.resolveToken(tokenName);
                tokenFileName = Mapper.getTokenID(tokenName);
                tokenPath = tile.getTokenPath(tokenFileName);

                if (tokenPath == null) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Token: " + tokenName + " is not valid");
                    return;
                }
                addToken(event, tile, tokenFileName, Mapper.getSpecialCaseValues(Constants.PLANET).contains(tokenName));
            }
        }
        else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Token not specified.");
        }
    }

    private void addToken(SlashCommandInteractionEvent event, Tile tile, String tokenID, boolean needSpecifyPlanet) {
        String unitHolder = Constants.SPACE;
        if (needSpecifyPlanet) {
            OptionMapping option = event.getOption(Constants.PLANET_NAME);
            if (option != null) {
                unitHolder = option.getAsString().toLowerCase();
            } else {
                Set<String> unitHolderIDs = tile.getUnitHolders().keySet();
                if (unitHolderIDs.size() == 2) {
                    HashSet<String> unitHolders = new HashSet<>(unitHolderIDs);
                    unitHolders.remove(Constants.SPACE);
                    unitHolder = unitHolders.iterator().next();
                } else {
                    String message = "Multiple planets present in system, need to specify planet.";
                    if (unitHolderIDs.size() == 1) {
                        message = "No planets present in system.";
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), message);
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
                MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: " + planet + " is not valid and not supported.");
                continue;
            }
            tile.addToken(tokenID, planet);
            if (Mapper.getTokenID(Constants.MIRAGE).equals(tokenID)){
                HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
                if (unitHolders.get(Constants.MIRAGE) == null){
                    Point mirageCenter = new Point(Constants.MIRAGE_POSITION.x + 75, Constants.MIRAGE_POSITION.y + 65);
                    Planet planetObject = new Planet(Constants.MIRAGE, mirageCenter);
                    unitHolders.put(Constants.MIRAGE, planetObject);
                }
            }
        }
    }

    @Override
    protected String getActionDescription() {
        return "Add token to tile/planet";
    }

    @Override
    public String getActionID() {
        return Constants.ADD_TOKEN;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TOKEN, "Token name")
                                .setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.PLANET_NAME, "Planet name"))

        );
    }
}
