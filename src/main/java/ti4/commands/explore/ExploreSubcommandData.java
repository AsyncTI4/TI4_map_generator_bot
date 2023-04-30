package ti4.commands.explore;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.commands.cardsso.SOInfo;

import org.jetbrains.annotations.NotNull;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.commands.player.PlanetAdd;
import ti4.commands.player.PlanetRefresh;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import java.util.List;

import javax.lang.model.util.ElementScanner14;

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
            case "lc1":
            case "lc2":
                boolean isYssaril = player.getFaction().equals("yssaril");
                message = isYssaril ? "Drew 3 Actions cards" : "Drew 2 Actions cards";
                int count = isYssaril ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    activeMap.drawActionCard(player.getUserID());
                }
                if(activeMap.isFoWMode())
                {
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, "Drew 2 AC");
                }
                ACInfo.sendActionCardInfo(activeMap, player, event);
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
                break;
            case "dv1":
            case "dv2":
                 message = "Drew Secret Objective";
                activeMap.drawSecretObjective(player.getUserID());
                if(activeMap.isFoWMode())
                {
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, "Drew SO");
                }
                SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
                break;
            case "dw":
                message = "Drew relic";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
                DrawRelic.drawRelicAndNotify(player,  event,  activeMap);
                break;
            case "ms1":
            case "ms2":
                message = "Replenished Commodifites (" +player.getCommodities() +"->"+player.getCommoditiesTotal()+")";
                player.setCommodities(player.getCommoditiesTotal());
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + "\n" + message);
                break;
            case "mirage":
                String planetName2 = AddRemoveUnits.getPlanet(event, tile, AliasHandler.resolvePlanet("mirage"));
                String planet2 = Mapper.getPlanet(planetName2);
                if (planet2 == null) {
                    sendMessage("Invalid planet");
                    return;
                }
                new PlanetAdd().doAction(player, planetName2, activeMap);
                new PlanetRefresh().doAction(player, planetName2, activeMap);
                String[] planetInfo2 = planet2.split(",");
                String drawColor2 = planetInfo2[1];
                String cardID2 = activeMap.drawExplore(drawColor2);
                if (cardID2 == null) {
                    sendMessage("Planet cannot be explored");
                    return;
                }
                StringBuilder messageText2 = new StringBuilder(Helper.getEmojiFromDiscord(drawColor2));
                messageText2.append("Planet "+ Helper.getPlanetRepresentationPlusEmoji(planetName2) +" *(tile "+ tile.getPosition() + ")* explored by " + Helper.getPlayerRepresentation(event, player)).append(":\n");
                messageText2.append(displayExplore(cardID2));
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
                resolveExplore(event, cardID2, tile, planetName2, messageText2.toString(), false);
                break;
            case "fb1":
            case "fb2":
            case "fb3":
            case "fb4":
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button getACButton = Button.success("spend_comm_for_AC", "Spend 1 TG/Comm For An AC");
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity");
                ActionRow actionRow = ActionRow.of(List.of(getACButton, getCommButton));
                MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(message);
                if (!actionRow.isEmpty()) baseMessageObject.addComponents(actionRow);
                event.getChannel().sendMessage(baseMessageObject.build()).queue(message_ -> {
                  //  Emoji reactionEmoji = Helper.getPlayerEmoji(activeMap, player, message_); 
        
                });
                break;
            case "aw1":
            case "aw2":
            case "aw3":
            case "aw4":
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button covert2CommButton = Button.success("covert_2_comms", "Covert 2 Commodities Into TG");
                Button get2CommButton = Button.primary("gain_2_comms", "Gain 2 Commodities");
                ActionRow actionRow2 = ActionRow.of(List.of(covert2CommButton, get2CommButton));
                MessageCreateBuilder baseMessageObject2 = new MessageCreateBuilder().addContent(message);
                if (!actionRow2.isEmpty()) baseMessageObject2.addComponents(actionRow2);
                event.getChannel().sendMessage(baseMessageObject2.build()).queue(message2_ -> { 
                });
                break;
            case "mo1":
            case "mo2":
            case "mo3":
                if(tile != null && planetName != null)
                {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf "+planetName, activeMap);
                }
                message = "Infantry added to the planet";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + "\n" + message);
                break;
            case "lf1":
            case "lf2":
            case "lf3":
            case "lf4":
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button getMechButton = Button.success("spend_comm_for_mech", "Spend 1 TG/Comm For A Mech On "+planetName);
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity");
                ActionRow actionRow3 = ActionRow.of(List.of(getMechButton, getCommButton3));
                MessageCreateBuilder baseMessageObject3 = new MessageCreateBuilder().addContent(message);
                if (!actionRow3.isEmpty()) baseMessageObject3.addComponents(actionRow3);
                event.getChannel().sendMessage(baseMessageObject3.build()).queue(message3_ -> {
                });
                break;
            case "kel1":
            case "kel2":
            case "ent":
            case "minent":
            case "majent":
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                if(cardID.equalsIgnoreCase("minent"))
                {
                    player.setTg(player.getTg()+1);
                    message = "Gained 1 tg (" +(player.getTg()-1) +"->"+player.getTg()+") ";
                }
                else if(cardID.equalsIgnoreCase("ent"))
                {
                    player.setTg(player.getTg()+2);
                    message = "Gained 2 tgs (" +(player.getTg()-2) +"->"+player.getTg()+") ";
                }
                else if(cardID.equalsIgnoreCase("majent"))
                {
                    player.setTg(player.getTg()+3);
                    message = "Gained 3 tgs (" +(player.getTg()-3) +"->"+player.getTg()+") ";
                }
                else 
                {
                    message = "";
                }
                message = message + "Resolve cc gain using the buttons.";   
                Button getTactic= Button.success("incease_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success("incease_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat= Button.success("incease_strategy_cc", "Gain 1 Strategy CC");
                ActionRow actionRow4 = ActionRow.of(List.of(getTactic, getFleet, getStrat));
                MessageCreateBuilder baseMessageObject4 = new MessageCreateBuilder().addContent(message);
                if (!actionRow4.isEmpty()) baseMessageObject4.addComponents(actionRow4);
                event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {
                });
                 
                break;
            case "exp2":
            case "exp1":
            case "exp3":
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button ReadyPlanet= Button.success("planet_ready", "Remove Inf Or Have Mech To Ready "+planetName);
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                ActionRow actionRow5 = ActionRow.of(List.of(ReadyPlanet,Decline));
                MessageCreateBuilder baseMessageObject5 = new MessageCreateBuilder().addContent(message);
                if (!actionRow5.isEmpty()) baseMessageObject5.addComponents(actionRow5);
                event.getChannel().sendMessage(baseMessageObject5.build()).queue(message5_ -> {
                });
                break;
            case "cm2":
            case "cm1":
            case "cm3":
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button gainTG= Button.success("gain_1_tg", "Gain 1tg By Removing 1 Inf Or Having Mech On "+planetName);
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                ActionRow actionRow6 = ActionRow.of(List.of(gainTG,Decline2));
                MessageCreateBuilder baseMessageObject6 = new MessageCreateBuilder().addContent(message);
                if (!actionRow6.isEmpty()) baseMessageObject6.addComponents(actionRow6);
                event.getChannel().sendMessage(baseMessageObject6.build()).queue(message6_ -> {
                });
                break;
            case "vfs2":
            case "vfs1":
            case "vfs3":
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText);
                Button gainCC= Button.success("gain_CC", "Gain 1CC By Removing 1 Inf Or Having Mech On "+planetName);
                Button Decline3 = Button.danger("decline_explore", "Decline Explore");
                ActionRow actionRow7 = ActionRow.of(List.of(gainCC,Decline3));
                MessageCreateBuilder baseMessageObject7 = new MessageCreateBuilder().addContent(message);
                if (!actionRow7.isEmpty()) baseMessageObject7.addComponents(actionRow7);
                event.getChannel().sendMessage(baseMessageObject7.build()).queue(message7_ -> {
                });
                break;
                
            
            default:
                MessageHelper.sendMessageToChannel(event.getChannel(), messageText + "\n" + message);
        }
        

    }
}
