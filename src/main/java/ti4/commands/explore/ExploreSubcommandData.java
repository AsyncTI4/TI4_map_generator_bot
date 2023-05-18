package ti4.commands.explore;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsso.SOInfo;

import org.jetbrains.annotations.NotNull;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.commands.player.PlanetAdd;
import ti4.commands.player.PlanetRefresh;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;

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
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), messageText);
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
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
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
                    message = "Mirage added to map, added to your stats, readied, and explored!";
                }
                activeMap.purgeExplore(cardID);
            } else {
                message = "Invalid token or tile";
            }
        }
        
        switch (cardID) {
            case "lc1", "lc2" -> {
                boolean hasSchemingAbility = player.getFactionAbilities().contains("scheming");
                message = hasSchemingAbility ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand" : "Drew 2 Actions cards";
                int count = hasSchemingAbility ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    activeMap.drawActionCard(player.getUserID());
                }
                if(activeMap.isFoWMode())
                {
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, "Drew 2 AC");
                }
                ACInfo.sendActionCardInfo(activeMap, player, event);
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
            }
            case "dv1", "dv2" -> {
                message = "Drew Secret Objective";
                activeMap.drawSecretObjective(player.getUserID());
                if(activeMap.isFoWMode())
                {
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, "Drew SO");
                }
                SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
            }
            case "dw" -> {
                message = "Drew Relic";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
                DrawRelic.drawRelicAndNotify(player,  event,  activeMap);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodifites (" +player.getCommodities() +"->"+player.getCommoditiesTotal()+")";
                player.setCommodities(player.getCommoditiesTotal());
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + "\n" + message);
            }
            case "mirage" -> {
                String mirageID = Constants.MIRAGE;
                String planetValue = Mapper.getPlanet(mirageID);
                if (planetValue == null) {
                    sendMessage("Invalid planet: " + mirageID);
                    return;
                }
                new PlanetAdd().doAction(player, mirageID, activeMap);
                new PlanetRefresh().doAction(player, mirageID, activeMap);
                String planetTrait = Constants.CULTURAL;
                String exploreID = activeMap.drawExplore(planetTrait);
                if (exploreID == null) {
                    sendMessage("Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }
                StringBuilder exploredMessage = new StringBuilder(Helper.getPlayerRepresentation(event, player)).append(" explored ");
                exploredMessage.append(Helper.getEmojiFromDiscord(planetTrait));
                exploredMessage.append("Planet "+ Helper.getPlanetRepresentationPlusEmoji(mirageID) +" *(tile "+ tile.getPosition() + ")*").append(":\n");
                exploredMessage.append(displayExplore(exploreID));
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage.toString(), false);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button getACButton = Button.success("comm_for_AC", "Spend 1 TG/Comm For An AC").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button covert2CommButton = Button.success("covert_2_comms", "Covert 2 Commodities Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
                Button get2CommButton = Button.primary("gain_2_comms", "Gain 2 Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(covert2CommButton, get2CommButton);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
            case "mo1", "mo2", "mo3" -> {
                if(tile != null && planetName != null)
                {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf " + planetName, activeMap);
                }
                message = Helper.getColourAsMention(event.getGuild(), player.getColor()) + Emojis.infantry + " added to " + Helper.getPlanetRepresentationPlusEmoji(planetName);
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + "\n" + message);
                break;
            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button getMechButton = Button.success("comm_for_mech", "Spend 1 TG/Comm For A Mech On "+planetName).withEmoji(Emoji.fromFormatted(Emojis.mech)); //TODO: Button resolves using planet ID at end of label - add planetID to buttonId and use that instead
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
            case "kel1", "kel2", "ent", "minent", "majent" -> {
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        player.setTg(player.getTg()+1);
                        message = "Gained 1" + Emojis.tg + " (" +(player.getTg()-1) +" -> **"+player.getTg()+"**) ";
                    }
                    case "ent" -> {
                        player.setTg(player.getTg()+2);
                        message = "Gained 2" + Emojis.tg + " (" +(player.getTg()-2) +" -> **"+player.getTg()+"**) ";
                    }
                    case "majent" -> {
                        player.setTg(player.getTg()+3);
                        message = "Gained 3" + Emojis.tg + " (" +(player.getTg()-3) +" -> **"+player.getTg()+"**) ";
                    }
                    default -> message = "";
                }
                Button getTactic= Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat= Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                String trueIdentity = Helper.getPlayerRepresentation(event, player, true);
                String message2 = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button ReadyPlanet= Button.success("planet_ready", "Remove Inf Or Have Mech To Ready "+planetName);
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ReadyPlanet,Decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button gainTG= Button.success("gain_1_tg", "Gain 1tg By Removing 1 Inf Or Having Mech On "+planetName).withEmoji(Emoji.fromFormatted(Emojis.tg));
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG,Decline2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button gainCC= Button.success("gain_CC", "Gain 1CC By Removing 1 Inf Or Having Mech On "+planetName);
                Button Decline3 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainCC, Decline3);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
        }
    }
}
