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
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

import java.util.*;

public class RemoveToken extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, ArrayList<String> colors, Tile tile, Game activeGame) {

        OptionMapping option = event.getOption(Constants.TOKEN);
        if (option != null) {
            String tokenName = option.getAsString().toLowerCase();
            tokenName = AliasHandler.resolveAttachment(tokenName);

            String tokenID = Mapper.getAttachmentImagePath(tokenName);
            String tokenPath = tile.getAttachmentPath(tokenID);
            if (tokenPath != null) {
                removeToken(event, tile, tokenID, true);
                activeGame.clearPlanetsCache();
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
                activeGame.clearPlanetsCache();
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
            planet = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet(planet));
            if (!tile.isSpaceHolderValid(planet)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: " + planet + " is not valid and not supported.");
                continue;
            }
            tile.removeToken(tokenID, planet);
            if (Mapper.getTokenID(Constants.MIRAGE).equals(tokenID)){
                HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
                if (unitHolders.get(Constants.MIRAGE) != null){
                    unitHolders.remove(Constants.MIRAGE);
                }
            }
        }
    }

    @Override
    protected String getActionDescription() {
        return "Remove token from tile/planet";
    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_TOKEN;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TOKEN, "Token name").setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet name").setAutoComplete(true))

        );
    }
}
