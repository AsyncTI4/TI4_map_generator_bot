package ti4.commands.explore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public abstract class ExploreSubcommandData extends SubcommandData {

    private SlashCommandInteractionEvent event;
    private Game activeGame;
    private User user;
    protected final OptionData typeOption = new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true);
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card id sent between (). Can include multiple comma-separated ids.");

    public String getActionID() {
        return getName();
    }

    public ExploreSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return activeGame;
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
        activeGame = GameManager.getInstance().getUserActiveGame(user.getId());
    }

    public String displayExplore(String cardID) {
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

    protected Tile getTile(SlashCommandInteractionEvent event, String tileID, Game activeGame) {
        if (activeGame.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(tileID));
        if (tile == null) {
            tile = activeGame.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return null;
        }
        return tile;
    }

    public void resolveExplore(GenericInteractionCreateEvent event, String cardID, Tile tile, String planetName, String messageText, boolean enigmatic, Player player, Game activeGame_) {
        String message = "Card has been discarded. Resolve effects manually.";
        if (activeGame_ != null) {
            activeGame = activeGame_;
        }
        if (enigmatic){
            message = "Card has been added to play area.";
            activeGame.purgeExplore(cardID);
        }
        String card = Mapper.getExplore(cardID);
        String[] cardInfo = card.split(";");

        if (player == null) {
            MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), "Player could not be found");
            return;
        }

        if (activeGame != null && !activeGame.isFoWMode() &&(event.getChannel() !=  activeGame.getActionsChannel())) {
            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
            if (planetName != null) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), pF + " found a "+cardInfo[0]+ " on "+Helper.getPlanetRepresentation(planetName, activeGame));
            } else {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), pF + " found a "+cardInfo[0]);
            }
        }

        String cardType = cardInfo[3];
        if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
            message = "Gained relic fragment";
            player.addFragment(cardID);
            activeGame.purgeExplore(cardID);
        } else if (cardType.equalsIgnoreCase(Constants.ATTACH)) {
            String token = cardInfo[5];
            String tokenFilename = Mapper.getAttachmentID(token);
            if (tokenFilename != null && tile != null && planetName != null) {

                PlanetModel planetInfo = Mapper.getPlanet(planetName);
                if (Optional.ofNullable(planetInfo).isPresent()) {
                    if (Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0  || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planetName)) {
                        if ((token.equals(Constants.WARFARE) ||
                             token.equals(Constants.PROPULSION) ||
                             token.equals(Constants.CYBERNETIC) ||
                             token.equals(Constants.BIOTIC) ||
                             token.equals(Constants.WEAPON))) {
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
                    HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
                    UnitHolder planetUnitHolder = unitHolders.get(planetName);
                    UnitHolder spaceUnitHolder = unitHolders.get(Constants.SPACE);
                    if (planetUnitHolder != null && spaceUnitHolder != null){
                        Map<String, Integer> units = new HashMap<>(planetUnitHolder.getUnits());
                        for (Player player_ : activeGame.getPlayers().values()) {
                            String color = player_.getColor();
                            planetUnitHolder.removeAllUnits(color);
                        }
                        HashMap<String, Integer> spaceUnits = spaceUnitHolder.getUnits();
                        for (Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                            String key = unitEntry.getKey();
                            if (key.contains("ff") || key.contains("gf") || key.contains("mf")){
                                Integer count = spaceUnits.get(key);
                                if (count == null){
                                    count = unitEntry.getValue();
                                } else {
                                    count += unitEntry.getValue();
                                }
                                spaceUnits.put(key, count);
                            }
                        }
                    }
                }
                tile.addToken(tokenFilename, planetName);
                activeGame.purgeExplore(cardID);
                message = "Token added to planet";
                if(player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")){
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "sol", event);
                }
                if(player.getLeaderIDs().contains("xxchacommander") && !player.hasLeaderUnlocked("xxchacommander")){
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "xxcha", event);
                }
            } else {
                message = "Invalid token, tile, or planet";
            }
        } else if (cardType.equalsIgnoreCase(Constants.TOKEN)) {
            String token = cardInfo[5];
            String tokenFilename = Mapper.getTokenID(token);
            if (tokenFilename != null && tile != null) {
                if("ionalpha".equalsIgnoreCase(token)){
                    message = "Use buttons to decide to place either an alpha or a beta ionstorm";
                    List<Button> buttonIon = new ArrayList<>();
                    buttonIon.add(Button.success("addIonStorm_beta_"+tile.getPosition(), "Put down a beta"));
                     buttonIon.add(Button.secondary("addIonStorm_alpha_"+tile.getPosition(), "Put down an alpha"));
                     MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons", buttonIon);
                }else{
                    tile.addToken(tokenFilename, Constants.SPACE);
                    message = "Token added to map";
                }
                
                if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                    Helper.addMirageToTile(tile);
                    activeGame.clearPlanetsCache();
                    message = "Mirage added to map, added to your stats, readied, and explored!";
                }
                activeGame.purgeExplore(cardID);
            } else {
                message = "Invalid token or tile";
            }
        }
        cardID = cardID.replace("extra1", "");
        //cardID = cardID.replace("_", "");
        cardID = cardID.replace("extra2", "");
        switch (cardID) {
            case "lc1", "lc2" -> {
                boolean hasSchemingAbility = player.hasAbility("scheming");
                message = hasSchemingAbility ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand" : "Drew 2 Actions cards";
                int count = hasSchemingAbility ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    activeGame.drawActionCard(player.getUserID());
                }
                
                if (activeGame.isFoWMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "Drew 2 AC");
                }
                ACInfo.sendActionCardInfo(activeGame, player, event);
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + message);
                 ButtonHelper.checkACLimit(activeGame, event, player);
            }
            case "dv1", "dv2" -> {
                message = "Drew Secret Objective";
                activeGame.drawSecretObjective(player.getUserID());
                if (activeGame.isFoWMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "Drew SO");
                }
                if(player.hasAbility("plausible_deniability")){
                    activeGame.drawSecretObjective(player.getUserID());
                    message = message+". Drew a second SO due to plausible deniability";
                }
                SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + message);
            }
            case "dw" -> {
                message = "Drew Relic";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + message);
                DrawRelic.drawRelicAndNotify(player,  event, activeGame);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodities (" +player.getCommodities() +"->"+player.getCommoditiesTotal()+"). Reminder that this is optional, and that you can instead convert your existing comms.";
                player.setCommodities(player.getCommoditiesTotal());
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + "\n" + message);
                ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                ButtonHelperFactionSpecific.cabalAgentInitiation(activeGame, player);
            }
            case "mirage" -> {
                String mirageID = Constants.MIRAGE;
                PlanetModel planetValue = Mapper.getPlanet(mirageID);
                if (Optional.ofNullable(planetValue).isEmpty()) {
                    sendMessage("Invalid planet: " + mirageID);
                    return;
                }
                new PlanetAdd().doAction(player, mirageID, activeGame);
                new PlanetRefresh().doAction(player, mirageID, activeGame);
                String planetTrait = Constants.CULTURAL;
                String exploreID = activeGame.drawExplore(planetTrait);
                if (exploreID == null) {
                    sendMessage("Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }
                if(  ( (activeGame.getActivePlayer() != null && !("".equalsIgnoreCase(activeGame.getActivePlayer()))) || activeGame.getCurrentPhase().contains("agenda")) && player.hasAbility("scavenge") && event != null)
                {
                    String fac = Helper.getFactionIconFromDiscord(player.getFaction());
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac+" gained 1tg from Scavenge ("+player.getTg()+"->"+(player.getTg()+1)+"). Reminder that this is optional, but was done automatically for convenience. You do not legally have this tg prior to exploring." );
                    player.setTg(player.getTg()+1);
                    ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                }
                for(String law : activeGame.getLaws().keySet()){
                    if("minister_exploration".equalsIgnoreCase(law)){
                        if(activeGame.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction()) && event != null){
                            String fac = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac+" gained 1tg from Minister of Exploration ("+player.getTg()+"->"+(player.getTg()+1)+"). You do have this tg prior to exploring." );
                            player.setTg(player.getTg()+1);
                            ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                        }
                    }
                }

                String exploredMessage = Helper.getPlayerRepresentation(player, activeGame) + " explored " +
                    Helper.getEmojiFromDiscord(planetTrait) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(mirageID) + " *(tile " + tile.getPosition() + ")*" + ":\n" +
                    displayExplore(exploreID);
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, false, player, activeGame);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button getACButton = Button.success("comm_for_AC", "Spend 1 TG/Comm For An AC").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button convert2CommButton = Button.success("convert_2_comms", "Convert 2 Commodities Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
                Button get2CommButton = Button.primary("gain_2_comms", "Gain 2 Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(convert2CommButton, get2CommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetName != null) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf " + planetName, activeGame, planetName);
                }
                message = Helper.getColourAsMention(event.getGuild(), player.getColor()) + Emojis.infantry + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetName)+". This placement is optional though.";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + "\n" + message);
            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button getMechButton = Button.success("comm_for_mech", "Spend 1 TG/Comm For A Mech On "+planetName).withEmoji(Emoji.fromFormatted(Emojis.mech)); //TODO: Button resolves using planet ID at end of label - add planetID to buttonId and use that instead
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            case "kel1", "kel2", "ent", "minent", "majent" -> {
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        player.setTg(player.getTg()+1);
                        message = "Gained 1" + Emojis.tg + " (" +(player.getTg()-1) +" -> **"+player.getTg()+"**) ";
                        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                    }
                    case "ent" -> {
                        player.setTg(player.getTg()+2);
                        message = "Gained 2" + Emojis.tg + " (" +(player.getTg()-2) +" -> **"+player.getTg()+"**) ";
                        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                    }
                    case "majent" -> {
                        player.setTg(player.getTg()+3);
                        message = "Gained 3" + Emojis.tg + " (" +(player.getTg()-3) +" -> **"+player.getTg()+"**) ";
                        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                    }
                    default -> message = "";
                }
                if(player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")){
                            ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                        }
                Button getTactic= Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat= Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                String trueIdentity = Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true);
                String message2 = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message2, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button ReadyPlanet= Button.success("planet_ready", "Remove Inf Or Have Mech To Ready "+planetName);
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ReadyPlanet,Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            case "frln1", "frln2", "frln3" -> {
                message = "Resolve explore using the buttons.";   
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button gainTG= Button.success("freelancersBuild_"+planetName, "Build 1 Unit");
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG,Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button gainTG= Button.success("gain_1_tg", "Gain 1tg By Removing 1 Inf Or Having Mech On "+planetName).withEmoji(Emoji.fromFormatted(Emojis.tg));
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG,Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText);
                Button gainCC= Button.success("gain_CC", "Gain 1CC By Removing 1 Inf Or Having Mech On "+planetName);
                Button Decline3 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainCC, Decline3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
            default -> MessageHelper.sendMessageToChannel((MessageChannel)event.getChannel(), messageText + "\n" + message);
        }

        if(player.hasAbility("awaken") && !ButtonHelper.getAllPlanetsWithSleeperTokens(player, activeGame).contains(planetName)){
            Button gainCC= Button.success("putSleeperOnPlanet_"+planetName, "Put Sleeper on "+planetName);
            Button Decline3 = Button.danger("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(gainCC, Decline3);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
        }
    }

}
