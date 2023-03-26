package ti4.commands.explore;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public abstract class ExploreSubcommandData extends SubcommandData {

    private SlashCommandInteractionEvent event;
    private Map activeMap;
    private User user;
    protected final OptionData typeOption = new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true);
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card id sent between (). Can include multiple comma-separated ids.");

    public String getActionID() {
        return getName();
    }

    public ExploreSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Map getActiveMap() {
        return activeMap;
    }

    public User getUser() {
        return user;
    }
    
    /**
     * Send a message to the event's channel, handles large text
     * @param messageText new message
     */
    public void sendMessage(String messageText) {
        MessageHelper.replyToSlashCommand(event, messageText);
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        this.event = event;
        user = event.getUser();
        activeMap = MapManager.getInstance().getUserActiveMap(user.getId());
    }

    protected String displayExplore(String cardID) {
        String card = Mapper.getExplore(cardID);
        StringBuilder sb = new StringBuilder();
        if (card != null) {
            String[] cardInfo = card.split(";");
            String name = cardInfo[0];
            String description = cardInfo[4];
            sb.append("(").append(cardID).append(") ").append(name).append(" - ").append(description);
        } else {
            sb.append("Invalid ID ").append(cardID);
        }
        return sb.toString();
    }

    protected Tile getTile(SlashCommandInteractionEvent event, String tileID, Map activeMap) {
        if (activeMap.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(tileID));
        if (tile == null) {
            tile = activeMap.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Tile not found");
            return null;
        }
        return tile;
    }

    protected void resolveExplore(SlashCommandInteractionEvent event, String cardID, Tile tile, String planetName, String messageText, boolean enigmatic) {
        String message = "Card has been discarded. Resolve effects manually.";
        if (enigmatic){
            message = "Card has been added to play area.";
            activeMap.purgeExplore(cardID);
        }
        String card = Mapper.getExplore(cardID);
        String[] cardInfo = card.split(";");
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        String cardType = cardInfo[3];
        if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
            message = "Gained relic fragment";
            player.addFragment(cardID);
            activeMap.purgeExplore(cardID);
        } else if (cardType.equalsIgnoreCase(Constants.ATTACH)) {
            String token = cardInfo[5];
            String tokenFilename = Mapper.getAttachmentID(token);
            if (tokenFilename != null && tile != null && planetName != null) {

                String planetInfo = Mapper.getPlanet(planetName);
                if (planetInfo != null) {
                    String[] split = planetInfo.split(",");
                    if (split.length > 4) {
                        String techSpec = split[4];
                        if (!techSpec.isEmpty() &&
                                (token.equals(Constants.WARFARE) ||
                                 token.equals(Constants.PROPULSION) ||
                                 token.equals(Constants.CYBERNETIC) ||
                                 token.equals(Constants.BIOTIC))) {
                            String attachmentID = Mapper.getAttachmentID(token + "stat");
                            if (attachmentID != null){
                                tokenFilename = attachmentID;
                            }
                        }
                    }
                }

                if (token.equals(Constants.DMZ)) {
                    String dmzLargeFilename = Mapper.getTokenID(Constants.DMZ_LARGE);
                    tile.addToken(dmzLargeFilename, planetName);
                }
                tile.addToken(tokenFilename, planetName);
                activeMap.purgeExplore(cardID);
                message = "Token added to planet";
            } else {
                message = "Invalid token, tile, or planet";
            }
        } else if (cardType.equalsIgnoreCase(Constants.TOKEN)) {
            String token = cardInfo[5];
            String tokenFilename = Mapper.getTokenID(token);
            if (tokenFilename != null && tile != null) {
                tile.addToken(tokenFilename, Constants.SPACE);
                message = "Token added to map";
                if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                    Helper.addMirageToTile(tile);
                    activeMap.clearPlanetsCache();
                    message = "Mirage added to map!";
                }
                activeMap.purgeExplore(cardID);
            } else {
                message = "Invalid token or tile";
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
