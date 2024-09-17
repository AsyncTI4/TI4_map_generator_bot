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
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.relic.DrawRelic;
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
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card id sent between (). May include multiple comma-separated ids.");

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

    public static void resolveExplore(GenericInteractionCreateEvent event, String cardID, Tile tile, String planetID, String messageText, Player player, Game game) {
        if (player == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Player could not be found");
            return;
        }
        if (game == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Game could not be found");
            return;
        }
        String ogID = cardID;

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
                game.purgeExplore(ogID);
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
                    game.purgeExplore(ogID);
                    message = "Attachment `" + attachment + "` added to planet";
                    if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
                        CommanderUnlockCheck.commanderUnlockCheck(player, game, "sol", event);
                    }
                    if (player.getLeaderIDs().contains("xxchacommander")
                        && !player.hasLeaderUnlocked("xxchacommander")) {
                        CommanderUnlockCheck.commanderUnlockCheck(player, game, "xxcha", event);
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
                        buttonIon.add(Buttons.green("addIonStorm_beta_" + tile.getPosition(), "Place a beta")
                            .withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
                        buttonIon.add(Buttons.gray("addIonStorm_alpha_" + tile.getPosition(), "Place an alpha")
                            .withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttonIon);
                    } else {
                        tile.addToken(tokenFilename, Constants.SPACE);
                        message = "Token `" + token + "` added to map";
                    }

                    if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                        Helper.addMirageToTile(tile);
                        game.clearPlanetsCache();
                        message = "Mirage added to map, added to your stats, readied, and explored!";
                    }
                    game.purgeExplore(ogID);
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
                game.purgeExplore(ogID);
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
                        FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew 2 ACs");
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
                CommanderUnlockCheck.fullCommanderUnlockCheck(player, game, "yssaril", event);
            }
            case "dv1", "dv2" -> {
                message = "Drew A Secret Objective.";
                game.drawSecretObjective(player.getUserID());
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew An SO");
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message = message + " Drew a second SO due to Plausible Deniability.";
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
                    || game.getPhaseOfGame().contains("agenda")) && player.hasUnit("saar_mech")
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
                    List<Button> saarButton = new ArrayList<>();
                    saarButton.add(Buttons.green("saarMechRes_" + "mirage",
                        "Pay 1TG for mech on " + Helper.getPlanetRepresentation("mirage", game)));
                    saarButton.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation(true, true)
                            + " you may pay 1TG to place 1 mech here. Do not do this prior to exploring. It is an after, while exploring is a when.",
                        saarButton);
                }

                if (ButtonHelper.isPlayerElected(game, player, "minister_exploration")) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        fac + " gained one " + Emojis.tg + " from Minister of Exploration (" + player.getTg()
                            + "->" + (player.getTg() + 1) + "). You do have this TG prior to exploring.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                }

                String exploredMessage = player.getRepresentation() + " explored " + Emojis.Cultural +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(mirageID) +
                    (tile == null ? "" : " *(tile " + tile.getPosition() + ")*:");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, player, game);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                Button getACButton = Buttons.green("comm_for_AC", "Spend 1TG or 1 Commodity For 1 AC")
                    .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity")
                    .withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                if (player.getCommodities() > 0) {
                    message = "Resolve explore using the buttons";
                    Button convert = Buttons.green("convert_2_comms", "Convert 2 Commodities Into TG", Emojis.Wash);
                    Button gain = Buttons.blue("gain_2_comms", "Gain 2 Commodities", Emojis.comm);
                    List<Button> buttons = List.of(convert, gain);
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
                } else {
                    String message2 = "Gained 2 Commodities automatically due to having no comms to convert";
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
                        message = "Planet had DMZ so no infantry was placed";
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
                    Buttons.green("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "hazardous";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons
                    .add(Buttons.red("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "cultural";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Buttons.blue("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "frontier";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Buttons.gray("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                discardButtons.add(Buttons.red("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " you may use the buttons to discard the top of the explore decks if you choose.",
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
                String msg = "Click button to explore a planet after resolving any discards";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    msg, buttonsAll);

                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                    "Use this button to shuffle explore decks once you're done with the rest",
                    Buttons.red("shuffleExplores", "Shuffle Explore Decks"));

            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve Local Fabricators:\n-# You currently have " + player.getTg() + Emojis.tg + " and " + player.getCommodities() + "/" + player.getCommoditiesTotal() + Emojis.comm;
                Button getMechButton = Buttons.green("resolveLocalFab_" + planetID, "Spend 1 Commodity or TG for a Mech on " + planetName, Emojis.mech);
                Button getCommButton3 = Buttons.blue("gain_1_comms", "Gain 1 Commodity", Emojis.comm);
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
                CommanderUnlockCheck.fullCommanderUnlockCheck(player, game, "hacan", event);

                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentation(true, true);
                message += "\n" + trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = "Resolve explore using the buttons.";
                Button ReadyPlanet = Buttons.green("resolveExpedition_" + planetID, "Ready " + planetName + " by removing 1 infantry from or having mech on planet.");
                Button Decline = Buttons.red("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ReadyPlanet, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "frln1", "frln2", "frln3" -> {
                message = "Resolve explore using the buttons.";
                Button gainTG = Buttons.green("freelancersBuild_" + planetID, "Build 1 Unit");
                Button Decline2 = Buttons.red("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = "Resolve explore using the buttons.";
                Button gainTG = Buttons.green("resolveCoreMine_" + planetID, "Gain 1TG by removing infantry or having mech on " + planetName)
                    .withEmoji(Emoji.fromFormatted(Emojis.tg));
                Button Decline2 = Buttons.red("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = player.getRepresentation() + " please resolve Volatile Fuel Source:\n-# Your current CCs are " + player.getCCRepresentation();
                Planet planet = game.getUnitHolderFromPlanet(planetID);
                if (planet != null) {
                    String unitList = planet.getPlayersUnitListEmojisOnHolder(player);
                    if (unitList.isEmpty()) {
                        message += " and you have no units on " + planetName;
                    } else {
                        message += " and you have " + unitList + " on " + planetName;
                    }
                }
                Button gainCC = Buttons.green("resolveVolatile_" + planetID, "Gain a CC by removing 1 Infantry or by having a Mech on " + planetName);
                Button Decline3 = Buttons.red("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainCC, Decline3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "warforgeruins" -> {
                message = "Resolve explore using the buttons.";
                Button ruinsInf = Buttons.green("ruins_" + planetID + "_2inf",
                    "Remove 1 infantry or have mech on planet to place 2 infantry on " + planetName);
                Button ruinsMech = Buttons.green("ruins_" + planetID + "_mech",
                    "Remove 1 infantry or have mech on planet to place mech on " + planetName);
                Button Decline = Buttons.red("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ruinsInf, ruinsMech, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "seedyspaceport" -> {
                List<Button> buttons = new ArrayList<>();
                message = "Resolve explore using the buttons.";
                for (Leader leader : player.getLeaders()) {
                    if (leader.isExhausted() && leader.getId().contains("agent")) {
                        buttons.add(Buttons.green("seedySpace_" + leader.getId() + "_" + planetID,
                            "Remove 1 infantry or have mech on planet to refresh " + Mapper.getLeader(leader.getId()).getName()));
                    }
                }
                buttons.add(Buttons.blue("seedySpace_AC_" + planetID, "Draw AC by removing 1 infantry or have mech on" + planetName));
                buttons.add(Buttons.red("decline_explore", "Decline Explore"));

                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "hiddenlaboratory" -> {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    "# Exploring frontier in this system due to finding the hidden laboratory industrial explore.");
                AddToken.addToken(event, tile, Constants.FRONTIER, game);
                new ExpFrontier().expFront(event, tile, game, player);
            }
            case "ancientshipyard" -> {
                List<String> colors = tile == null ? List.of() : tile.getUnitHolders().get("space").getUnitColorsOnHolder();
                if (colors.isEmpty() || colors.contains(player.getColorID())) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", game);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Cruiser added to the system automatically.");
                } else {
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Someone else's ships were in the system, no cruiser added");
                }
            }
            case "forgottentradestation" -> {
                int tgGain = tile == null ? 0 : tile.getUnitHolders().size() - 1;
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ButtonHelper.getIdentOrColor(player, game) + " gained " + tgGain + "TG"
                    + (tgGain == 1 ? "" : "s") + " due to the forgotten trade station (" + oldTg + "->" + player.getTg() + ")");
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, tgGain);
            }
            case "starchartcultural", "starchartindustrial", "starcharthazardous", "starchartfrontier" -> {
                game.purgeExplore(ogID);
                player.addRelic(cardID);
                message = "Card has been added to play area.\nAdded as a relic (not actually a relic)";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }

        }
        CommanderUnlockCheck.fullCommanderUnlockCheck(player, game, "hacan", event);

        if (player.hasAbility("fortune_seekers") && game.getStoredValue("fortuneSeekers").isEmpty()) {
            List<Button> gainComm = new ArrayList<>();
            gainComm.add(Buttons.green("gain_1_comms", "Gain 1 Comm").withEmoji(Emoji.fromFormatted(Emojis.comm)));
            gainComm.add(Buttons.red("deleteButtons", "Decline"));
            StringBuilder sb = new StringBuilder();
            sb.append(player.getFactionEmoji()).append(" may use their **Fortune Seekers** ability\n");
            sb.append(player.getRepresentation(true, true)).append(
                " After resolving the explore, you may use this button to get your commodity from your fortune seekers ability.");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), gainComm);
            game.setStoredValue("fortuneSeekers", "Used");
        }

        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            CommanderUnlockCheck.commanderUnlockCheck(player, game, "kollecc", event);
        }
        if (player.getPlanets().contains(planetID)) {
            ButtonHelperAbilities.offerOrladinPlunderButtons(player, game, planetID);
        }
        if (player.getLeaderIDs().contains("bentorcommander") && !player.hasLeaderUnlocked("bentorcommander")) {
            CommanderUnlockCheck.commanderUnlockCheck(player, game, "bentor", event);
        }

        if (player.hasAbility("awaken") && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
            && player.getPlanetsAllianceMode().contains(planetID)) {
            Button placeSleeper = Buttons.green("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID)
                .withEmoji(Emoji.fromFormatted(Emojis.Sleeper));
            Button decline = Buttons.red("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(placeSleeper, decline);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
        }
    }
}
