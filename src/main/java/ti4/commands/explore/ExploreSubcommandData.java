package ti4.commands.explore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

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
import ti4.buttons.Buttons;
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
import ti4.helpers.ButtonHelperStats;
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

    private Game game;
    private User user;
    protected final OptionData typeOption = new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true);
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Exploration card ID; may include multiple comma-separated IDs.");
    
    private static final String HAZ_NONE = "haz_none";
    private static final String HAZ_INF = "haz_inf";
    private static final String HAZ_MECH = "haz_mech";
    private static final String HAZ_BOTH = "haz_both";

    public String getActionID() {
        return getName();
    }

    public ExploreSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return game;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        game = GameManager.getInstance().getUserActiveGame(user.getId());
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

    protected Tile getTile(SlashCommandInteractionEvent event, String tileID, Game game) {
        if (game.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = game.getTile(AliasHandler.resolveTile(tileID));
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return null;
        }
        return tile;
    }

    public static void resolveExplore(GenericInteractionCreateEvent event, String cardID, Tile tile, String planetID,
        String messageText, Player player, Game game) {
        if (player == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Player could not be found");
            return;
        }
        if (game == null) {
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

        if (game != null && !game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            if (planetID != null) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    player.getFactionEmoji() + " found a " + exploreModel.getName() + " on "
                        + Helper.getPlanetRepresentation(planetID, game));
            } else {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    player.getFactionEmoji() + " found a " + exploreModel.getName());
            }
        }

        if (tile == null) {
            tile = game.getTileFromPlanet(planetID);
        }

        // Generic Resolution Handling
        switch (exploreModel.getResolution().toLowerCase()) {
            case Constants.FRAGMENT -> {
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            }
            case Constants.ATTACH -> {
                String attachment = exploreModel.getAttachmentId().orElse("");
                String attachmentFilename = Mapper.getAttachmentImagePath(attachment);
                if (attachmentFilename == null || tile == null || planetID == null) {
                    message = "Invalid attachment, tile, or planet.";
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
                            for (Player player_ : game.getPlayers().values()) {
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
                    game.purgeExplore(cardID);
                    message = "Attachment " + exploreModel.getName() + " (`" + attachment + "`) added to planet.";
                    if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "sol", event);
                    }
                    if (player.getLeaderIDs().contains("xxchacommander")
                        && !player.hasLeaderUnlocked("xxchacommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "xxcha", event);
                    }
                }
            }
            case Constants.TOKEN -> {
                String token = exploreModel.getAttachmentId().orElse("");
                String tokenFilename = Mapper.getTokenID(token);
                if (tokenFilename == null || tile == null) {
                    message = "Invalid token or tile.";
                } else {
                    if ("ionalpha".equalsIgnoreCase(token)) {
                        message = "Use buttons to decide to place either an alpha or a beta Ion Storm.";
                        List<Button> buttonIon = new ArrayList<>();
                        buttonIon.add(Button.success("addIonStorm_beta_" + tile.getPosition(), "Place As Beta")
                            .withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
                        buttonIon.add(Button.secondary("addIonStorm_alpha_" + tile.getPosition(), "Place As Alpha")
                            .withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttonIon);
                    } else {
                        tile.addToken(tokenFilename, Constants.SPACE);
                        message = "Token " + exploreModel.getName() + " (`" + token + "` added to tile" + tile.getPosition() + ".";
                    }

                    if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                        Helper.addMirageToTile(tile);
                        game.clearPlanetsCache();
                        message = "Mirage added to map, added to your stats, readied, and explored!";
                    }
                    game.purgeExplore(cardID);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        message = "Card has been discarded. Resolve effects manually.";
        String planetName = Mapper.getPlanet(planetID) == null ? "`error?`" : Mapper.getPlanet(planetID).getName();

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
                game.purgeExplore(cardID);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            }
            case "lc1", "lc2" -> {
                boolean hasSchemingAbility = player.hasAbility("scheming");
                message = hasSchemingAbility
                    ? "Drew 3 action cards (Scheming) - please discard 1 action card from your hand"
                    : "Drew 2 action cards";
                int count = hasSchemingAbility ? 3 : 2;
                if (player.hasAbility("autonetic_memory")) {
                    ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
                    message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                } else {
                    for (int i = 0; i < count; i++) {
                        game.drawActionCard(player.getUserID());
                    }

                    if (game.isFowMode()) {
                        FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew 2 action cards.");
                    }
                    ACInfo.sendActionCardInfo(game, player, event);
                }

                if (hasSchemingAbility) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentation(true, true) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(game, player, false));
                }
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                ButtonHelper.checkACLimit(game, event, player);
            }
            case "dv1", "dv2" -> {
                message = "Drew Secret Objective.";
                game.drawSecretObjective(player.getUserID());
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew a secret objective.");
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message = message + " Drew a second secret objective due to Plausible Deniability.";
                }
                SOInfo.sendSecretObjectiveInfo(game, player, event);
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            case "dw" -> {
                message = "Drew Relic";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                DrawRelic.drawRelicAndNotify(player, event, game);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodities (" + player.getCommodities() + "->" + player.getCommoditiesTotal()
                    + "). Reminder that this is optional, and that you may instead convert your existing commodities.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                ButtonHelperStats.replenishComms(event, game, player, true);
            }
            case Constants.MIRAGE -> {
                String mirageID = Constants.MIRAGE;
                PlanetModel planetValue = Mapper.getPlanet(mirageID);
                if (Optional.ofNullable(planetValue).isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid planet: " + mirageID);
                    return;
                }
                PlanetAdd.doAction(player, mirageID, game, null, false);
                PlanetRefresh.doAction(player, mirageID, game);
                String exploreID = game.drawExplore(Constants.CULTURAL);
                if (exploreID == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }
                if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                    || game.getPhaseOfGame().contains("agenda")) && player.hasAbility("scavenge")
                    && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac + " gained 1 trade good from Scavenge ("
                        + player.getTg() + "->" + (player.getTg() + 1)
                        + "). Reminder you do not legally have this trade good prior to exploring, and you could potentially deploy 1 mech before doing it to dodge Pillage.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                }

                if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                    || game.getPhaseOfGame().contains("agenda")) && player.hasUnit("saar_mech")
                    && event != null && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
                    List<Button> saarButton = new ArrayList<>();
                    saarButton.add(Button.success("saarMechRes_" + "mirage",
                        "Pay 1 Trade Good For Mech On " + Helper.getPlanetRepresentation("mirage", game)));
                    saarButton.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation(true, true)
                            + " you may pay 1 trade good to place 1 mech here. Do not do this prior to exploring. It is an \"after\", while exploring is a \"when\".",
                        saarButton);
                }

                if (ButtonHelper.isPlayerElected(game, player, "minister_exploration") && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        fac + " gained 1 " + Emojis.tg + " from Minister of Exploration (" + player.getTg()
                            + "->" + (player.getTg() + 1) + "). You do have this trade good prior to exploring.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);

                }

                String exploredMessage = player.getRepresentation() + " explored " + Emojis.Cultural +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(mirageID) + " *(tile " + tile.getPosition()
                    + ")*:";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, player, game);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                Button getACButton = Button.success("comm_for_AC", "Spend 1 Trade Good Or 1 Commodity For 1 Action Card")
                    .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity")
                    .withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                if (player.getCommodities() > 0) {
                    message = "Resolve exploration using the buttons";
                    Button convert = Buttons.green("convert_2_comms", "Convert 2 Commodities Into Trade Goods", Emojis.Wash);
                    Button gain = Buttons.blue("gain_2_comms", "Gain 2 Commodities", Emojis.comm);
                    List<Button> buttons = List.of(convert, gain);
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
                } else {
                    String message2 = "Gained 2 commodities automatically due to having no commodities to convert.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " " + message2);
                    ButtonHelperStats.gainComms(event, game, player, 2, true, true);
                }
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetID != null) {
                    Set<String> tokenList = ButtonHelper.getUnitHolderFromPlanetName(planetID, game).getTokenList();
                    boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
                    if (!containsDMZ) {
                        new AddUnits().unitParsing(event, player.getColor(), tile, "inf " + planetID, game);
                        message = player.getFactionEmoji() + Emojis.getColorEmojiWithName(player.getColor()) + Emojis.infantry
                            + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetID)
                            + ", however this placement *is* optional.";
                    } else {
                        message = "Planet had Demilitarized Zone attached so no infantry was placed.";
                    }
                } else {
                    message = "Tile was null, no infantry placed";
                }
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            case "darkvisions" -> {
                List<Button> discardButtons = new ArrayList<>();
                String type = "industrial";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Button.success("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "hazardous";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons
                    .add(Button.danger("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "cultural";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Button.primary("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "frontier";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Button.secondary("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                discardButtons.add(Button.danger("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " you may use the buttons to discard the top of the exploration decks if you choose.",
                    discardButtons);
                List<Button> buttonsAll = new ArrayList<>();
                for (String planet : player.getPlanetsAllianceMode()) {
                    UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder == null) {
                        continue;
                    }
                    Planet planetReal = (Planet) unitHolder;
                    List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                    if (buttons != null && !buttons.isEmpty()) {
                        buttonsAll.addAll(buttons);
                    }
                }
                String msg = "Click button to explore a planet after resolving any discards.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    msg, buttonsAll);

                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                    "Use this button to shuffle exploration decks once you're done with the rest.",
                    Button.danger("shuffleExplores", "Shuffle Exploration Decks"));

            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve using the buttons";
                Button getMechButton = Button.success("resolveLocalFab_" + planetID, "Spend 1 trade good Or 1 Commodity For 1 Mech On " + planetName).withEmoji(Emoji.fromFormatted(Emojis.mech));
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "kel1", "kel2", "ent", "minent", "majent" -> {
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        player.setTg(player.getTg() + 1);
                        message = "Gained 1" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - 1)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    }
                    case "ent" -> {
                        player.setTg(player.getTg() + 2);
                        message = "Gained 2" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - 2)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 2);
                    }
                    case "majent" -> {
                        player.setTg(player.getTg() + 3);
                        message = "Gained 3" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - 3)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 3);
                    }
                    default -> message = "";
                }
                ButtonHelper.fullCommanderUnlockCheck(player, game, "hacan", event);

                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentation(true, true);
                message += "\n" + trueIdentity + "! Your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain command tokens.";
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                Button gainWMech = Button.success("resolveExpeditionM_" + planetID, "Ready " + planetName + " Because Of Mech There");
                Button gainWInf = Button.success("resolveExpeditionI_" + planetID, "Ready " + planetName + " By Removing 1 Infantry There");
                Button Decline3 = Button.danger("decline_explore", "Decline Expedition");
                List<Button> buttons;
                switch (findHazardousUnits(planetName, tile, player))
                {
                    case HAZ_BOTH:
                        buttons = List.of(gainWMech, gainWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Expedition using the buttons.", buttons);
                        break;
                    case HAZ_MECH:
                        buttons = List.of(gainWMech, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Expedition using the buttons.", buttons);
                        break;
                    case HAZ_INF:
                        buttons = List.of(gainWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Expedition using the buttons.", buttons);
                        break;
                    case HAZ_NONE:
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "You have neither a mech nor infantry on " + planetName + ", and thus cannot resolve Expedition.");
                        break;
                }
            }
            case "frln1", "frln2", "frln3" -> {
                message = "Resolve exploration using the buttons.";
                Button gainTG = Button.success("freelancersBuild_" + planetID, "Build 1 Unit");
                Button Decline2 = Button.danger("decline_explore", "Decline Exploration");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                Button gainWMech = Button.success("resolveCoreMineM_" + planetID, "Gain 1 Trade Good Because Of Mech On " + planetName);
                Button gainWInf = Button.success("resolveCoreMineI_" + planetID, "Gain 1 Trade Good By Removing 1 Infantry From " + planetName);
                Button Decline3 = Button.danger("decline_explore", "Decline Core Mine");
                List<Button> buttons;
                switch (findHazardousUnits(planetName, tile, player))
                {
                    case HAZ_BOTH:
                        buttons = List.of(gainWMech, gainWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Core Mine using the buttons.", buttons);
                        break;
                    case HAZ_MECH:
                        buttons = List.of(gainWMech, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Core Mine using the buttons.", buttons);
                        break;
                    case HAZ_INF:
                        buttons = List.of(gainWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Core Mine using the buttons.", buttons);
                        break;
                    case HAZ_NONE:
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "You have neither a mech nor infantry on " + planetName + ", and thus cannot resolve Core Mine.");
                        break;
                }
            }
            case "vfs1", "vfs2", "vfs3" -> {
                Button gainWMech = Button.success("resolveVolatileM_" + planetID, "Gain 1 Command Token Because Of Mech On " + planetName);
                Button gainWInf = Button.success("resolveVolatileI_" + planetID, "Gain 1 Command Token By Removing 1 Infantry From " + planetName);
                Button Decline3 = Button.danger("decline_explore", "Decline Volatile Fuel Source");
                List<Button> buttons;
                switch (findHazardousUnits(planetName, tile, player))
                {
                    case HAZ_BOTH:
                        buttons = List.of(gainWMech, gainWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Volatile Fuel Source using the buttons.", buttons);
                        break;
                    case HAZ_MECH:
                        buttons = List.of(gainWMech, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Volatile Fuel Source using the buttons.", buttons);
                        break;
                    case HAZ_INF:
                        buttons = List.of(gainWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Volatile Fuel Source using the buttons.", buttons);
                        break;
                    case HAZ_NONE:
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "You have neither a mech nor infantry on " + planetName + ", and thus cannot resolve Volatile Fuel Source.");
                        break;
                }
            }
            case "warforgeruins" -> {
                Button gainMechWMech = Button.success("ruinsM_" + planetID + "_mech", "Place 1 Mech On " + planetName + " Because Of Mech There");
                Button gainMechWInf = Button.success("ruinsI_" + planetID + "_mech", "Place 1 Mech On " + planetName + " By Removing 1 Infantry There");
                Button gainInfWMech = Button.success("ruinsM_" + planetID + "_2inf", "Place 2 Infantry On " + planetName + " Because Of Mech There");
                Button gainInfWInf = Button.success("ruinsI_" + planetID + "_2inf", "Place 2 Infantry On " + planetName + " By Removing 1 Infantry There");
                Button Decline3 = Button.danger("decline_explore", "Decline War Forge Ruins");
                List<Button> buttons;
                switch (findHazardousUnits(planetName, tile, player))
                {
                    case HAZ_BOTH:
                        buttons = List.of(gainMechWMech, gainMechWInf, gainInfWMech, gainInfWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve War Forge Ruins using the buttons.", buttons);
                        break;
                    case HAZ_MECH:
                        buttons = List.of(gainMechWMech, gainInfWMech, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve War Forge Ruins using the buttons.", buttons);
                        break;
                    case HAZ_INF:
                        buttons = List.of(gainMechWInf, gainInfWInf, Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve War Forge Ruins using the buttons.", buttons);
                        break;
                    case HAZ_NONE:
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "You have neither a mech nor infantry on " + planetName + ", and thus cannot resolve War Forge Ruins.");
                        break;
                }
            }
            case "seedyspaceport" -> {
                Button gainACWMech = Button.success("seedySpaceM_" + planetID, "Draw 1 Action Card Because Of Mech On " + planetName);
                Button gainACWInf = Button.success("seedySpaceM_AC_" + planetID, "Draw 1 Action Card By Removing 1 Infantry From " + planetName);
                Button Decline3 = Button.danger("seedySpaceI_AC_", "Decline Seedy Spaceport");
                List<String> agents = new ArrayList<>();
                for (Leader leader : player.getLeaders()) {
                    if (leader.isExhausted() && leader.getId().contains("agent")) {
                        agents.add(leader.getId());
                    }
                }
                List<Button> buttons = new ArrayList<>();
                switch (findHazardousUnits(planetName, tile, player))
                {
                    case HAZ_BOTH:
                        for (String leader: agents)
                        {
                            buttons.add(Button.success("seedySpaceM_" + leader + "_" + planetID,
                                "Ready " + Mapper.getLeader(leader).getName() + " ecause Of Mech On " + planetName));
                            buttons.add(Button.success("seedySpaceI_" + leader + "_" + planetID,
                                "Ready " + Mapper.getLeader(leader).getName() + " By Removing 1 Infantry From " + planetName));
                        }
                        buttons.add(gainACWMech);
                        buttons.add(gainACWInf);
                        buttons.add(Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Seedy Spaceport using the buttons.", buttons);
                        break;
                    case HAZ_MECH:
                        for (String leader: agents)
                        {
                            buttons.add(Button.success("seedySpaceM_" + leader + "_" + planetID,
                                "Ready " + Mapper.getLeader(leader).getName() + " ecause Of Mech On " + planetName));
                        }
                        buttons.add(gainACWMech);
                        buttons.add(Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Seedy Spaceport using the buttons.", buttons);
                        break;
                    case HAZ_INF:
                        for (String leader: agents)
                        {
                            buttons.add(Button.success("seedySpaceI_" + leader + "_" + planetID,
                                "Ready " + Mapper.getLeader(leader).getName() + " By Removing 1 Infantry From " + planetName));
                        }
                        buttons.add(gainACWInf);
                        buttons.add(Decline3);
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), "Resolve Seedy Spaceport using the buttons.", buttons);
                        break;
                    case HAZ_NONE:
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "You have neither a mech nor infantry on " + planetName + ", and thus cannot resolve Seedy Spaceport.");
                        break;
                }
            }
            case "hiddenlaboratory" -> {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    "# Exploring frontier in this system due to finding the Hidden Laboratory industrial exploration.");
                AddToken.addToken(event, tile, Constants.FRONTIER, game);
                new ExpFrontier().expFront(event, tile, game, player);
            }
            case "ancientshipyard" -> {
                List<String> colors = tile.getUnitHolders().get("space").getUnitColorsOnHolder();
                if (colors.isEmpty() || colors.contains(player.getColorID())) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", game);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                        "Cruiser added to the system automatically.");
                } else {
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                        "Someone else's ships were in the system, no cruiser added.");
                }

            }
            case "forgottentradestation" -> {
                int tgGain = tile.getUnitHolders().size() - 1;
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    ButtonHelper.getIdentOrColor(player, game) + " gained " + tgGain + "trade good"
                        + (tgGain == 1 ? "" : "s") + " due to a Forgotten Trade Station (" + oldTg + "->" + player.getTg() + ").");
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, tgGain);
            }
            case "starchartcultural", "starchartindustrial", "starcharthazardous", "starchartfrontier" -> {
                game.purgeExplore(cardID);
                player.addRelic(cardID);
                message = "Card has been added to play area.\nAdded as a relic (not actually a relic).";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }

        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "hacan", event);

        if (player.hasAbility("fortune_seekers") && game.getStoredValue("fortuneSeekers").isEmpty()) {
            List<Button> gainComm = new ArrayList<>();
            gainComm.add(Button.success("gain_1_comms", "Gain 1 Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm)));
            gainComm.add(Button.danger("deleteButtons", "Decline"));
            StringBuilder sb = new StringBuilder();
            sb.append(player.getFactionEmoji()).append(" may use their **Fortune Seekers** ability.\n");
            sb.append(player.getRepresentation(true, true)).append(
                " After resolving the exploration, you may use this button to get your commodity from your Fortune Seekers ability.");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), gainComm);
            game.setStoredValue("fortuneSeekers", "Used");
        }

        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "kollecc", event);
        }
        if (player.getPlanets().contains(planetID)) {
            ButtonHelperAbilities.offerOrladinPlunderButtons(player, game, planetID);
        }
        if (player.getLeaderIDs().contains("bentorcommander") && !player.hasLeaderUnlocked("bentorcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "bentor", event);
        }

        if (player.hasAbility("awaken") && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
            && player.getPlanets().contains(planetID)) {
            Button placeSleeper = Button.success("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID)
                .withEmoji(Emoji.fromFormatted(Emojis.Sleeper));
            Button decline = Button.danger("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(placeSleeper, decline);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
        }
    }
    
    private static String findHazardousUnits(String planetName, Tile tile, Player player) {
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        String colorID = Mapper.getColorID(player.getColor());
        
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        boolean hasMech = false;
        if (unitHolder.getUnits() != null && unitHolder.getUnits().get(mechKey) != null && unitHolder.getUnits().get(mechKey) >= 1)
        {
            hasMech = true;
        }
        
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        if (unitHolder.getUnits() != null && unitHolder.getUnits().get(infKey) != null && unitHolder.getUnits().get(infKey) >= 1)
        {
            return (hasMech ? HAZ_BOTH : HAZ_INF);
        }
        
        return (hasMech ? HAZ_MECH : HAZ_NONE);
    }
}
