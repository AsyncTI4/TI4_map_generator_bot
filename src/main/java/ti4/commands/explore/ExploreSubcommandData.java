package ti4.commands.explore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import ti4.commands.tokens.AddToken;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Leader;
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
     * 
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
        String card = Mapper.getExploreRepresentation(cardID);
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
        if (enigmatic || cardID.contains("starchart")) {
            message = "Card has been added to play area.";
            activeGame.purgeExplore(cardID);
        }
        String card = Mapper.getExploreRepresentation(cardID);
        String[] cardInfo = card.split(";");
        if (tile == null){
            tile = activeGame.getTileFromPlanet(planetName);
            // if (tile == null) {
            //     MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Tile could not be found");
            //     return;
            // }
        }
        if (player == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Player could not be found");
            return;
        }

        if (activeGame != null && !activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
            String factionIcon = player.getFactionEmoji();
            if (planetName != null) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), factionIcon + " found a " + cardInfo[0] + " on " + Helper.getPlanetRepresentation(planetName, activeGame));
            } else {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), factionIcon + " found a " + cardInfo[0]);
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
                    if (Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0 || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planetName)) {
                        if ((token.equals(Constants.WARFARE) ||
                            token.equals(Constants.PROPULSION) ||
                            token.equals(Constants.CYBERNETIC) ||
                            token.equals(Constants.BIOTIC) ||
                            token.equals(Constants.WEAPON))) {
                            String attachmentID = Mapper.getAttachmentID(token + "stat");
                            if (attachmentID != null) {
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
                    if (planetUnitHolder != null && spaceUnitHolder != null) {
                        Map<UnitKey, Integer> units = new HashMap<>(planetUnitHolder.getUnits());
                        for (Player player_ : activeGame.getPlayers().values()) {
                            String color = player_.getColor();
                            planetUnitHolder.removeAllUnits(color);
                        }
                        HashMap<UnitKey, Integer> spaceUnits = spaceUnitHolder.getUnits();
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
                tile.addToken(tokenFilename, planetName);
                activeGame.purgeExplore(cardID);
                message = "Token added to planet";
                if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "sol", event);
                }
                if (player.getLeaderIDs().contains("xxchacommander") && !player.hasLeaderUnlocked("xxchacommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "xxcha", event);
                }
            } else {
                message = "Invalid token, tile, or planet";
            }
        } else if (cardType.equalsIgnoreCase(Constants.TOKEN)) {
            String token = cardInfo[5];
            String tokenFilename = Mapper.getTokenID(token);
            if (tokenFilename != null && tile != null) {
                if ("ionalpha".equalsIgnoreCase(token)) {
                    message = "Use buttons to decide to place either an alpha or a beta ionstorm";
                    List<Button> buttonIon = new ArrayList<>();
                    buttonIon.add(Button.success("addIonStorm_beta_" + tile.getPosition(), "Put down a beta"));
                    buttonIon.add(Button.secondary("addIonStorm_alpha_" + tile.getPosition(), "Put down an alpha"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons", buttonIon);
                } else {
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
        // cardID = cardID.replace("_", "");
        cardID = cardID.replace("extra2", "");

        switch (cardID) {
            case "lc1", "lc2" -> {
                boolean hasSchemingAbility = player.hasAbility("scheming");
                message = hasSchemingAbility
                    ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand"
                    : "Drew 2 Actions cards";
                int count = hasSchemingAbility ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    activeGame.drawActionCard(player.getUserID());
                }

                if (activeGame.isFoWMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "Drew 2 AC");
                }
                ACInfo.sendActionCardInfo(activeGame, player, event);
                if (hasSchemingAbility) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                }
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + message);
                ButtonHelper.checkACLimit(activeGame, event, player);
            }
            case "dv1", "dv2" -> {
                message = "Drew Secret Objective";
                activeGame.drawSecretObjective(player.getUserID());
                if (activeGame.isFoWMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "Drew SO");
                }
                if (player.hasAbility("plausible_deniability")) {
                    activeGame.drawSecretObjective(player.getUserID());
                    message = message + ". Drew a second SO due to plausible deniability";
                }
                SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + message);
            }
            case "dw" -> {
                message = "Drew Relic";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + message);
                DrawRelic.drawRelicAndNotify(player, event, activeGame);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodities (" + player.getCommodities() + "->" + player.getCommoditiesTotal()
                    + "). Reminder that this is optional, and that you can instead convert your existing comms.";
                player.setCommodities(player.getCommoditiesTotal());
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + "\n" + message);
                ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                }
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
                if (((activeGame.getActivePlayer() != null && !("".equalsIgnoreCase(activeGame.getActivePlayer()))) || activeGame.getCurrentPhase().contains("agenda")) && player.hasAbility("scavenge")
                    && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac + " gained 1tg from Scavenge (" + player.getTg() + "->" + (player.getTg() + 1)
                        + "). Reminder that this is optional, but was done automatically for convenience. You do not legally have this tg prior to exploring.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                }

                final String ministerOfExploration = "minister_exploration";
                if (activeGame.getLaws().keySet().contains(ministerOfExploration)) {
                    if (activeGame.getLawsInfo().get(ministerOfExploration).equalsIgnoreCase(player.getFaction()) && event != null) {
                        String fac = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            fac + " gained one " + Emojis.tg + " from Minister of Exploration (" + player.getTg() + "->" + (player.getTg() + 1) + "). You do have this tg prior to exploring.");
                        player.setTg(player.getTg() + 1);
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    }
                }

                String exploredMessage = player.getRepresentation() + " explored " +
                    Emojis.getEmojiFromDiscord(planetTrait) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(mirageID) + " *(tile " + tile.getPosition() + ")*" + ":\n" +
                    displayExplore(exploreID);
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, false, player, activeGame);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button getACButton = Button.success("comm_for_AC", "Spend 1 TG/Comm For An AC").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button convert2CommButton = Button.success("convert_2_comms", "Convert 2 Commodities Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
                Button get2CommButton = Button.primary("gain_2_comms", "Gain 2 Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(convert2CommButton, get2CommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetName != null) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf " + planetName, activeGame, planetName);
                }
                message = Emojis.getColourEmojis(player.getColor()) + Emojis.infantry + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetName)
                    + ". This placement is optional though.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + "\n" + message);
            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve using the buttons";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button getMechButton = Button.success("comm_for_mech", "Spend 1 TG/Comm For A Mech On " + planetName).withEmoji(Emoji.fromFormatted(Emojis.mech)); //TODO: Button resolves using planet ID at end of label - add planetID to buttonId and use that instead
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "kel1", "kel2", "ent", "minent", "majent" -> {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        player.setTg(player.getTg() + 1);
                        message = "Gained 1" + Emojis.tg + " (" + (player.getTg() - 1) + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    }
                    case "ent" -> {
                        player.setTg(player.getTg() + 2);
                        message = "Gained 2" + Emojis.tg + " (" + (player.getTg() - 2) + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 2);
                    }
                    case "majent" -> {
                        player.setTg(player.getTg() + 3);
                        message = "Gained 3" + Emojis.tg + " (" + (player.getTg() - 3) + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 3);
                    }
                    default -> message = "";
                }
                if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                }
                Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                String trueIdentity = Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true);
                String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
                
                if(activeGame.getNomadCoin()){
                    message = message.replace(Emojis.tg, Emojis.nomadcoin);
                }
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button ReadyPlanet = Button.success("planet_ready", "Remove Inf Or Have Mech To Ready " + planetName);
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ReadyPlanet, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "frln1", "frln2", "frln3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button gainTG = Button.success("freelancersBuild_" + planetName, "Build 1 Unit");
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button gainTG = Button.success("gain_1_tg", "Gain 1tg By Removing 1 Inf Or Having Mech On " + planetName).withEmoji(Emoji.fromFormatted(Emojis.tg));
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button gainCC = Button.success("gain_CC", "Gain 1CC By Removing 1 Inf Or Having Mech On " + planetName);
                Button Decline3 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainCC, Decline3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "warforgeruins"->{
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                Button ruinsInf = Button.success("ruins_"+planetName+"_2inf", "Remove Inf Or Have Mech To Place 2 Infantry on " + Mapper.getPlanet(planetName).getName());
                Button ruinsMech = Button.success("ruins_"+planetName+"_mech", "Remove Inf Or Have Mech To Place Mech on " + Mapper.getPlanet(planetName).getName());
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ruinsInf, ruinsMech, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "seedyspaceport"->{
                List<Button> buttons = new ArrayList<Button>();
                message = "Resolve explore using the buttons.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText);
                for(Leader leader : player.getLeaders()){
                    if(leader.isExhausted()&& leader.getId().contains("agent")){
                        buttons.add(Button.success("seedySpace_"+leader.getId()+"_"+planetName, "Remove Inf Or Have Mech To Refresh " + Mapper.getLeader(leader.getId()).getName()));
                    }
                }
                buttons.add(Button.primary("seedySpace_AC_"+planetName, "Remove Inf Or Have Mech Draw AC "));
                buttons.add(Button.danger("decline_explore", "Decline Explore"));
                
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "hiddenlaboratory"->{
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "# Exploring frontier in this system due to finding the hidden laboratory industrial explore.");
                AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
                new ExpFrontier().expFront(event, tile, activeGame, player);
            }
            case "ancientshipyard"->{
                List<String> colors = tile.getUnitHolders().get("space").getUnitColorsOnHolder();
                if(colors.size() == 0 || colors.contains(player.getColorID())){
                    new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", activeGame);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Cruiser added to the system automatically.");
                }else{
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Someone else's ships were in the system, no cruiser added");
                }
                
            }
            case "forgottentradestation"->{
                int tgGain = tile.getUnitHolders().size()-1;
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    ButtonHelper.getIdentOrColor(player, activeGame) + " gained "+tgGain+"tg due to the forgotten trade station (" + oldTg + "->" + player.getTg() + ")");
                ButtonHelperAbilities.pillageCheck(player, activeGame);
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, tgGain);
            }
            case "starchartcultural", "starchartindustrial", "starcharthazardous", "starchartfrontier" -> {
                player.addRelic(cardID);
                message = "Added as a relic (not actually a relic) - use /explore relic_purge to use it";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            default -> MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), messageText + "\n" + message);
        }

        if (player.hasAbility("awaken") && !activeGame.getAllPlanetsWithSleeperTokens().contains(planetName)) {
            Button gainCC = Button.success("putSleeperOnPlanet_" + planetName, "Put Sleeper on " + planetName);
            Button Decline3 = Button.danger("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(gainCC, Decline3);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
        }
    }

}
