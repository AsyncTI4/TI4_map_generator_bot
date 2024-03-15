package ti4.commands.explore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import software.amazon.awssdk.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
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
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;

public abstract class ExploreSubcommandData extends SubcommandData {

    private SlashCommandInteractionEvent event;
    private Game activeGame;
    private User user;
    protected final OptionData typeOption = new OptionData(OptionType.STRING, Constants.TRAIT,
            "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true);
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID,
            "Explore card id sent between (). Can include multiple comma-separated ids.");

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

    /**
     * @deprecated should use {@link ExploreModel#getRepresentationEmbed()} instead
     */
    @Deprecated
    public static String displayExplore(String cardID) {
        ExploreModel model = Mapper.getExplore(cardID);
        StringBuilder sb = new StringBuilder();
        if (model != null) {
            sb.append("(").append(cardID).append(") ").append(model.getName()).append(" - ").append(model.getText());
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

    public static void resolveExplore(GenericInteractionCreateEvent event, String cardID, Tile tile, String planetID,
            String messageText, Player player, Game activeGame) {
        if (player == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Player could not be found");
            return;
        }
        if (activeGame == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Game could not be found");
            return;
        }

        cardID = cardID.replace("extra1", "");
        cardID = cardID.replace("extra2", "");
        ExploreModel exploreModel = Mapper.getExplore(cardID);
        if (exploreModel == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    "ExploreModel could not be found: " + cardID);
            return;
        }

        MessageEmbed exploreEmbed = exploreModel.getRepresentationEmbed();
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), messageText, exploreEmbed);

        String message = null;

        if (activeGame != null && !activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
            if (planetID != null) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                        player.getFactionEmoji() + " found a " + exploreModel.getName() + " on "
                                + Helper.getPlanetRepresentation(planetID, activeGame));
            } else {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                        player.getFactionEmoji() + " found a " + exploreModel.getName());
            }
        }

        if (tile == null) {
            tile = activeGame.getTileFromPlanet(planetID);
        }

        // Generic Resolution Handling
        switch (exploreModel.getResolution().toLowerCase()) {
            case Constants.FRAGMENT -> {
                player.addFragment(cardID);
                activeGame.purgeExplore(cardID);
            }
            case Constants.ATTACH -> {
                String attachment = exploreModel.getAttachmentId().orElse("");
                String attachmentFilename = Mapper.getAttachmentImagePath(attachment);
                if (attachmentFilename == null || tile == null || planetID == null) {
                    message = "Invalid attachment, tile, or planet";
                } else {
                    PlanetModel planetInfo = Mapper.getPlanet(planetID);
                    if (Optional.ofNullable(planetInfo).isPresent()) {
                        if (Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0
                                || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planetID)) {
                            if ((attachment.equals(Constants.WARFARE) ||
                                    attachment.equals(Constants.PROPULSION) ||
                                    attachment.equals(Constants.CYBERNETIC) ||
                                    attachment.equals(Constants.BIOTIC) ||
                                    attachment.equals(Constants.WEAPON))) {
                                String attachmentID = Mapper.getAttachmentImagePath(attachment + "stat");
                                if (attachmentID != null) {
                                    attachmentFilename = attachmentID;
                                }
                            }
                        }
                    }

                    if (attachment.equals(Constants.DMZ)) {
                        String dmzLargeFilename = Mapper.getTokenID(Constants.DMZ_LARGE);
                        tile.addToken(dmzLargeFilename, planetID);
                        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
                        UnitHolder planetUnitHolder = unitHolders.get(planetID);
                        UnitHolder spaceUnitHolder = unitHolders.get(Constants.SPACE);
                        if (planetUnitHolder != null && spaceUnitHolder != null) {
                            Map<UnitKey, Integer> units = new HashMap<>(planetUnitHolder.getUnits());
                            for (Player player_ : activeGame.getPlayers().values()) {
                                String color = player_.getColor();
                                planetUnitHolder.removeAllUnits(color);
                            }
                            Map<UnitKey, Integer> spaceUnits = spaceUnitHolder.getUnits();
                            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                                UnitKey key = unitEntry.getKey();
                                if (Set.of(UnitType.Fighter, UnitType.Infantry, UnitType.Mech)
                                        .contains(key.getUnitType())) {
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
                    tile.addToken(attachmentFilename, planetID);
                    activeGame.purgeExplore(cardID);
                    message = "Attachment `" + attachment + "` added to planet";
                    if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "sol", event);
                    }
                    if (player.getLeaderIDs().contains("xxchacommander")
                            && !player.hasLeaderUnlocked("xxchacommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "xxcha", event);
                    }
                }
            }
            case Constants.TOKEN -> {
                String token = exploreModel.getAttachmentId().orElse("");
                String tokenFilename = Mapper.getTokenID(token);
                if (tokenFilename == null || tile == null) {
                    message = "Invalid token or tile";
                } else {
                    if ("ionalpha".equalsIgnoreCase(token)) {
                        message = "Use buttons to decide to place either an alpha or a beta Ion Storm";
                        List<Button> buttonIon = new ArrayList<>();
                        buttonIon.add(Button.success("addIonStorm_beta_" + tile.getPosition(), "Place a beta")
                                .withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
                        buttonIon.add(Button.secondary("addIonStorm_alpha_" + tile.getPosition(), "Place an alpha")
                                .withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttonIon);
                    } else {
                        tile.addToken(tokenFilename, Constants.SPACE);
                        message = "Token `" + token + "` added to map";
                    }

                    if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                        Helper.addMirageToTile(tile);
                        activeGame.clearPlanetsCache();
                        message = "Mirage added to map, added to your stats, readied, and explored!";
                    }
                    activeGame.purgeExplore(cardID);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        message = "Card has been discarded. Resolve effects manually.";

        // Specific Explore Handling
        switch (cardID) {
            case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getFactionEmojiOrColor() + " gained " + Emojis.CFrag);
            }
            case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getFactionEmojiOrColor() + " gained " + Emojis.HFrag);
            }
            case "irf1", "irf2", "irf3", "irf4", "irf5" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getFactionEmojiOrColor() + " gained " + Emojis.IFrag);
            }
            case "urf1", "urf2", "urf3" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getFactionEmojiOrColor() + " gained " + Emojis.UFrag);
            }
            case "ed1", "ed2" -> {
                message = "Card has been added to play area.";
                player.addRelic(Constants.ENIGMATIC_DEVICE);
                activeGame.purgeExplore(cardID);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            }
            case "lc1", "lc2" -> {
                boolean hasSchemingAbility = player.hasAbility("scheming");
                message = hasSchemingAbility
                        ? "Drew 3 action cards (Scheming) - please discard an action card from your hand"
                        : "Drew 2 action cards";
                int count = hasSchemingAbility ? 3 : 2;
                if (player.hasAbility("autonetic_memory")) {
                    ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, count);
                    message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                } else {
                    for (int i = 0; i < count; i++) {
                        activeGame.drawActionCard(player.getUserID());
                    }

                    if (activeGame.isFoWMode()) {
                        FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "Drew 2 AC");
                    }
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                }

                if (hasSchemingAbility) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentation(true, true) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                }
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
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
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            case "dw" -> {
                message = "Drew Relic";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                DrawRelic.drawRelicAndNotify(player, event, activeGame);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodities (" + player.getCommodities() + "->" + player.getCommoditiesTotal()
                        + "). Reminder that this is optional, and that you can instead convert your existing comms.";
                player.setCommodities(player.getCommoditiesTotal());
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                if (player.hasAbility("military_industrial_complex")
                        && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                            ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                }
                if (player.getLeaderIDs().contains("mykomentoricommander")
                        && !player.hasLeaderUnlocked("mykomentoricommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                }
            }
            case Constants.MIRAGE -> {
                String mirageID = Constants.MIRAGE;
                PlanetModel planetValue = Mapper.getPlanet(mirageID);
                if (Optional.ofNullable(planetValue).isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid planet: " + mirageID);
                    return;
                }
                new PlanetAdd().doAction(player, mirageID, activeGame);
                new PlanetRefresh().doAction(player, mirageID, activeGame);
                String exploreID = activeGame.drawExplore(Constants.CULTURAL);
                if (exploreID == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }
                if (((activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID())))
                        || activeGame.getCurrentPhase().contains("agenda")) && player.hasAbility("scavenge")
                        && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac + " gained 1tg from Scavenge ("
                            + player.getTg() + "->" + (player.getTg() + 1)
                            + "). Reminder you do not legally have this tg prior to exploring, and you could potentially deploy a mech before doing it to dodge pillage.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                }

                if (((activeGame.getActivePlayerID() != null && !("".equalsIgnoreCase(activeGame.getActivePlayerID())))
                        || activeGame.getCurrentPhase().contains("agenda")) && player.hasUnit("saar_mech")
                        && event != null && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4) {
                    List<Button> saarButton = new ArrayList<>();
                    saarButton.add(Button.success("saarMechRes_" + "mirage",
                            "Pay 1tg for mech on " + Helper.getPlanetRepresentation("mirage", activeGame)));
                    saarButton.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getRepresentation(true, true)
                                    + " you can pay 1tg to place a mech here. Do not do this prior to exploring. It is an after, while exploring is a when",
                            saarButton);
                }

                
                if(ButtonHelper.isPlayerElected(activeGame, player, "minister_exploration") && event != null){
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            fac + " gained one " + Emojis.tg + " from Minister of Exploration (" + player.getTg()
                                    + "->" + (player.getTg() + 1) + "). You do have this tg prior to exploring.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    
                }

                String exploredMessage = player.getRepresentation() + " explored " + Emojis.Cultural +
                        "Planet " + Helper.getPlanetRepresentationPlusEmoji(mirageID) + " *(tile " + tile.getPosition()
                        + ")*:";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, player, activeGame);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                Button getACButton = Button.success("comm_for_AC", "Spend 1 TG/Comm For An AC")
                        .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity")
                        .withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                if (player.getCommodities() > 0) {
                    message = "Resolve explore using the buttons";
                    Button convert2CommButton = Button.success("convert_2_comms", "Convert 2 Commodities Into TG")
                            .withEmoji(Emoji.fromFormatted(Emojis.Wash));
                    Button get2CommButton = Button.primary("gain_2_comms", "Gain 2 Commodities")
                            .withEmoji(Emoji.fromFormatted(Emojis.comm));
                    List<Button> buttons = List.of(convert2CommButton, get2CommButton);
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message,
                            buttons);
                } else {
                    String message2 = "Gained 2 Commodities automatically due to having no comms to convert";
                    player.setCommodities(player.getCommodities() + 2);
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getFactionEmoji() + " " + message2);
                }
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetID != null) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf " + planetID, activeGame);
                }
                message = player.getFactionEmoji() + Emojis.getColorEmojiWithName(player.getColor()) + Emojis.infantry
                        + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetID)
                        + ", however this placement *is* optional.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            case "darkvisions" -> {
                List<Button> discardButtons = new ArrayList<>();
                String type = "industrial";
                ButtonHelperFactionSpecific.resolveExpLook(player, activeGame, event, type);
                discardButtons.add(
                        Button.success("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "hazardous";
                ButtonHelperFactionSpecific.resolveExpLook(player, activeGame, event, type);
                discardButtons
                        .add(Button.danger("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "cultural";
                ButtonHelperFactionSpecific.resolveExpLook(player, activeGame, event, type);
                discardButtons.add(
                        Button.primary("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "frontier";
                ButtonHelperFactionSpecific.resolveExpLook(player, activeGame, event, type);
                discardButtons.add(
                        Button.secondary("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                discardButtons.add(Button.danger("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation()
                                + " you can use the buttons to discard the top of the explore decks if you choose",
                        discardButtons);
                List<Button> buttonsAll = new ArrayList<>();
                for (String planet : player.getPlanetsAllianceMode()) {
                    UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
                    if (unitHolder == null) {
                        continue;
                    }
                    Planet planetReal = (Planet) unitHolder;
                    List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);
                    if (buttons != null && !buttons.isEmpty()) {
                        buttonsAll.addAll(buttons);
                    }
                }
                String msg = "Click button to explore a planet after resolving any discards";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        msg, buttonsAll);

                MessageHelper.sendMessageToChannelWithButton(ButtonHelper.getCorrectChannel(player, activeGame),
                        "Use this button to shuffle explore decks once youre done with the rest",
                        Button.danger("shuffleExplores", "Shuffle Explore Decks"));

            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve using the buttons";
                Button getMechButton = Button.success("comm_for_mech", "Spend 1 TG/Comm For A Mech On " + planetID)
                        .withEmoji(Emoji.fromFormatted(Emojis.mech)); // TODO: Button resolves using planet ID at end of
                                                                      // label - add planetID to buttonId and use that
                                                                      // instead
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity")
                        .withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "kel1", "kel2", "ent", "minent", "majent" -> {
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        player.setTg(player.getTg() + 1);
                        message = "Gained 1" + Emojis.getTGorNomadCoinEmoji(activeGame) + " (" + (player.getTg() - 1)
                                + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    }
                    case "ent" -> {
                        player.setTg(player.getTg() + 2);
                        message = "Gained 2" + Emojis.getTGorNomadCoinEmoji(activeGame) + " (" + (player.getTg() - 2)
                                + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 2);
                    }
                    case "majent" -> {
                        player.setTg(player.getTg() + 3);
                        message = "Gained 3" + Emojis.getTGorNomadCoinEmoji(activeGame) + " (" + (player.getTg() - 3)
                                + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 3);
                    }
                    default -> message = "";
                }
                if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                }
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentation(true, true);
                message += "\n" + trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = "Resolve explore using the buttons.";
                Button ReadyPlanet = Button.success("planet_ready", "Remove Inf Or Have Mech To Ready " + planetID);
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ReadyPlanet, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "frln1", "frln2", "frln3" -> {
                message = "Resolve explore using the buttons.";
                Button gainTG = Button.success("freelancersBuild_" + planetID, "Build 1 Unit");
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = "Resolve explore using the buttons.";
                Button gainTG = Button.success("gain_1_tg", "Gain 1tg By Removing 1 Inf Or Having Mech On " + planetID)
                        .withEmoji(Emoji.fromFormatted(Emojis.tg));
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = "Resolve explore using the buttons.";
                Button gainCC = Button.success("gain_CC", "Gain 1CC By Removing 1 Inf Or Having Mech On " + planetID);
                Button Decline3 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainCC, Decline3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "warforgeruins" -> {
                message = "Resolve explore using the buttons.";
                Button ruinsInf = Button.success("ruins_" + planetID + "_2inf",
                        "Remove Inf Or Have Mech To Place 2 Infantry on " + Mapper.getPlanet(planetID).getName());
                Button ruinsMech = Button.success("ruins_" + planetID + "_mech",
                        "Remove Inf Or Have Mech To Place Mech on " + Mapper.getPlanet(planetID).getName());
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ruinsInf, ruinsMech, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "seedyspaceport" -> {
                List<Button> buttons = new ArrayList<>();
                message = "Resolve explore using the buttons.";
                for (Leader leader : player.getLeaders()) {
                    if (leader.isExhausted() && leader.getId().contains("agent")) {
                        buttons.add(Button.success("seedySpace_" + leader.getId() + "_" + planetID,
                                "Remove Inf Or Have Mech To Refresh " + Mapper.getLeader(leader.getId()).getName()));
                    }
                }
                buttons.add(Button.primary("seedySpace_AC_" + planetID, "Remove Inf Or Have Mech Draw AC "));
                buttons.add(Button.danger("decline_explore", "Decline Explore"));

                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "hiddenlaboratory" -> {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                        "# Exploring frontier in this system due to finding the hidden laboratory industrial explore.");
                AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
                new ExpFrontier().expFront(event, tile, activeGame, player);
            }
            case "ancientshipyard" -> {
                List<String> colors = tile.getUnitHolders().get("space").getUnitColorsOnHolder();
                if (colors.isEmpty() || colors.contains(player.getColorID())) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", activeGame);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                            "Cruiser added to the system automatically.");
                } else {
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                            "Someone else's ships were in the system, no cruiser added");
                }

            }
            case "forgottentradestation" -> {
                int tgGain = tile.getUnitHolders().size() - 1;
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        ButtonHelper.getIdentOrColor(player, activeGame) + " gained " + tgGain
                                + "tg due to the forgotten trade station (" + oldTg + "->" + player.getTg() + ")");
                ButtonHelperAbilities.pillageCheck(player, activeGame);
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, tgGain);
            }
            case "starchartcultural", "starchartindustrial", "starcharthazardous", "starchartfrontier" -> {
                activeGame.purgeExplore(cardID);
                player.addRelic(cardID);
                message = "Card has been added to play area.\nAdded as a relic (not actually a relic)";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
        }

        if (player.hasAbility("fortune_seekers")) {
            List<Button> gainComm = new ArrayList<>();
            gainComm.add(Button.success("gain_1_comms", "Gain 1 Comm").withEmoji(Emoji.fromFormatted(Emojis.comm)));
            StringBuilder sb = new StringBuilder();
            sb.append(ButtonHelper.getIdent(player)).append(" can use their **Fortune Seekers** ability\n");
            sb.append(player.getRepresentation(true, true)).append(
                    " After resolving the explore, you can use this button to get your commodity from your fortune seekers ability");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), gainComm);
        }

        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "kollecc", event);
        }
        if(player.getPlanets().contains(planetID)){
            ButtonHelperAbilities.offerOrladinPlunderButtons(player, activeGame, planetID);
        }
        if (player.getLeaderIDs().contains("bentorcommander") && !player.hasLeaderUnlocked("bentorcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "bentor", event);
        }

        if (player.hasAbility("awaken") && !activeGame.getAllPlanetsWithSleeperTokens().contains(planetID)
                && player.getPlanets().contains(planetID)) {
            Button placeSleeper = Button.success("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID)
                    .withEmoji(Emoji.fromFormatted(Emojis.Sleeper));
            Button decline = Button.danger("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(placeSleeper, decline);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
        }
    }
}
