package ti4.service.explore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.tokens.AddTokenCommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.ExploreModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.service.PlanetService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ExploreService {

    public void explorePlanet(GenericInteractionCreateEvent event, Tile tile, String planetName, String drawColor, Player player, boolean NRACheck, Game game, int numExplores,
        boolean ownerShipOverride) {
        if (!player.getPlanetsAllianceMode().contains(planetName) && !ownerShipOverride) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You do not own this planet, thus cannot explore it.");
            return;
        }
        game.setStoredValue(player.getFaction() + "planetsExplored", game.getStoredValue(player.getFaction() + "planetsExplored") + planetName + "*");

        if (planetName.equalsIgnoreCase("garbozia")) {
            if (player.hasAbility("distant_suns")) {
                String reportMessage = "Garbozia exploration with Distant Suns is not implemented.\nPlease use `/explore draw_and_discard trait` then `/explore use explore_card_id` to manually resolve this exploration.\n(NB: Player chooses a trait, reveals two of that trait and one of each other; reveal four cards total.)";
                if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                    MessageHelper.sendMessageToChannel(game.getActionsChannel(), reportMessage);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
                }
                return;
            }
            String cardIDC = game.drawExplore("CULTURAL");
            String cardIDH = game.drawExplore("INDUSTRIAL");
            String cardIDI = game.drawExplore("HAZARDOUS");

            ExploreModel exploreModelC = Mapper.getExplore(cardIDC);
            ExploreModel exploreModelH = Mapper.getExplore(cardIDH);
            ExploreModel exploreModelI = Mapper.getExplore(cardIDI);

            String reportMessage = player.getFactionEmoji() + " explored " + MiscEmojis.LegendaryPlanet + "**Garbozia** ability and found a **" + exploreModelC.getName() + "**, **" + exploreModelH.getName() + "** and a **" + exploreModelI.getName() + "**";
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), reportMessage);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
            }

            Button resolveExploreC = Buttons.green("resolve_explore_" + cardIDC + "_" + planetName + "_distantSuns", exploreModelC.getName());
            Button resolveExploreH = Buttons.green("resolve_explore_" + cardIDH + "_" + planetName + "_distantSuns", exploreModelH.getName());
            Button resolveExploreI = Buttons.green("resolve_explore_" + cardIDI + "_" + planetName + "_distantSuns", exploreModelI.getName());
            List<Button> buttons = List.of(resolveExploreC, resolveExploreH, resolveExploreI);
            List<MessageEmbed> embeds = List.of(exploreModelC.getRepresentationEmbed(), exploreModelH.getRepresentationEmbed(), exploreModelI.getRepresentationEmbed());
            String message = player.getRepresentation() + " please choose 1 Explore card to resolve.";
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;

        }

        if (player.hasAbility("distant_suns")) {
            if (Helper.mechCheck(planetName, game, player)) {
                if (!NRACheck) {
                    if (player.hasTech("pfa")) { //Pre-Fab Arcologies
                        PlanetService.refreshPlanet(player, planetName);
                        MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                            planetName + " has been automatically readied because you have _Pre-Fab Arcologies_.");
                    }
                    String message = "Please decide whether or not to use your " + FactionEmojis.Naaz + "**Distant Suns** (explore twice) ability.";
                    Button resolveExplore1 = Buttons.green("distant_suns_accept_" + planetName + "_" + drawColor, "Choose to Explore Twice");
                    Button resolveExplore2 = Buttons.red("distant_suns_decline_" + planetName + "_" + drawColor, "Decline Distant Suns");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                } else if (numExplores == 2) {
                    String cardID1 = game.drawExplore(drawColor);
                    String cardID2 = game.drawExplore(drawColor);
                    if (cardID1 == null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored.");
                        return;
                    }
                    ExploreModel exploreModel1 = Mapper.getExplore(cardID1);
                    ExploreModel exploreModel2 = Mapper.getExplore(cardID2);

                    // Report to common channel
                    String reportMessage = player.getFactionEmoji() + " used their " + FactionEmojis.Naaz + "**Distant Suns** ability and found a **"
                        + exploreModel1.getName() + "** and a **" + exploreModel2.getName() + "** on " + Helper.getPlanetRepresentationPlusEmoji(planetName);
                    if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                        MessageHelper.sendMessageToChannel(game.getActionsChannel(), reportMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), reportMessage);
                    }

                    Button resolveExplore1 = Buttons.green("resolve_explore_" + cardID1 + "_" + planetName + "_distantSuns", exploreModel1.getName());
                    Button resolveExplore2 = Buttons.green("resolve_explore_" + cardID2 + "_" + planetName + "_distantSuns", exploreModel2.getName());
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed(), exploreModel2.getRepresentationEmbed());
                    String message = player.getRepresentation() + " please choose 1 exploration card to resolve.";
                    MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
                    return;
                }
            }
        }
        if (player.hasAbility("deep_mining") && tile != null) {
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            if (unitHolder.getUnitCount(Units.UnitType.Mech, player.getColor()) > 0 || unitHolder.getUnitCount(Units.UnitType.Spacedock, player.getColor()) > 0 || unitHolder.getUnitCount(Units.UnitType.Pds, player.getColor()) > 0) {
                if (!NRACheck) {
                    String message = "Please decide whether or not to use your " + FactionEmojis.gledge + "**Deep Mining** (gain 1 trade good instead of explore) ability.";
                    Button resolveExplore1 = Buttons.green("deep_mining_accept", "Gain 1 Trade Good");
                    Button resolveExplore2 = Buttons.red("deep_mining_decline_" + planetName + "_" + drawColor, "Explore");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                }
            }
        }

        String cardID = game.drawExplore(drawColor);
        if (cardID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored");
            return;
        }
        String position_ = tile == null ? "none" : tile.getPosition();
        String messageText = player.getRepresentation() + " explored " + ExploreEmojis.getTraitEmoji(drawColor) +
            "Planet " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile " + position_ + ")*:";
        if (player.hasUnexhaustedLeader("lanefiragent")) {
            ExploreModel exploreModel = Mapper.getExplore(cardID);
            String name1 = exploreModel.getName();
            Button resolveExplore1 = Buttons.green("lanefirAgentRes_Decline_" + drawColor + "_" + cardID + "_" + planetName, "Choose " + name1);
            Button resolveExplore2 = Buttons.green("lanefirAgentRes_Accept_" + drawColor + "_" + planetName, "Use Lanefir Agent");
            List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
            String message = player.getRepresentationUnfogged() + " You have " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Vassa Hagi, the Lanefir" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, and thus may decline this explore to draw another one instead.";
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), pF + " found a " + name1 + " on " + planetName);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " on " + planetName);
            }
            ExploreModel exploreModel1 = Mapper.getExplore(cardID);
            List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;
        }
        if (player.hasTech("absol_sdn")) {
            ExploreModel exploreModel = Mapper.getExplore(cardID);
            String name1 = exploreModel.getName();
            Button resolveExplore1 = Buttons.green("absolsdn_Decline_" + drawColor + "_" + cardID + "_" + planetName, "Resolve " + name1);
            Button resolveExplore2 = Buttons.green("absolsdn_Accept" + drawColor + "_" + planetName, "Gain 1 Trade Good");
            List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
            String message = player.getRepresentationUnfogged() + " You have _Scanlink Drone Network_, and thus may decline this explore to gain 1 trade good.";
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), pF + " found a " + name1 + " on " + planetName + ".");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " on " + planetName + ".");
            }
            ExploreModel exploreModel1 = Mapper.getExplore(cardID);
            List<MessageEmbed> embeds = List.of(exploreModel1.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
            return;
        }
        ExploreService.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
        if (player.hasTech("pfa")) { //Pre-Fab Arcologies
            PlanetService.refreshPlanet(player, planetName);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), planetName + " has been automatically readied because you have _Pre-Fab Arcologies_.");
        }
        if (ButtonHelper.doesPlayerHaveFSHere("ghemina_flagship_lord", player, tile)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planetName);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Infantry added due to presence of The Lord (a Ghemina flagship) . Technically happens after exploring.");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && game.getPhaseOfGame().contains("agenda")) {
            PlanetService.refreshPlanet(player, planetName);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), planetName + " has been readied because of Quaxdol Junitas, the Florzen Commander.");
            AgendaHelper.listVoteCount(game, game.getMainGameChannel());
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            Units.UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
            Tile tileWithPlanet = game.getTileFromPlanet(planetName);
            if (tileWithPlanet == null) {
                MessageHelper.sendMessageToEventChannel(event, "An error occurred while placing 1 infantry. Resolve manually.");
                return;
            }
            tileWithPlanet.getUnitHolders().get(planetName).addUnit(infKey, 1);
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Added infantry to " + planetName + " because of Master Halbert, the Lanefir Commander.");
        }
        if (player.hasTech("dslaner")) {
            player.setAtsCount(player.getAtsCount() + numExplores);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
        }
        if (ButtonHelper.isPlanetLegendaryOrTechSkip(planetName, game) && Helper.getPlayerFromUnlockedLeader(game, "augersagent") != null) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasUnexhaustedLeader("augersagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("exhaustAgent_augersagent_" + player.getFaction(), "Use Augers Agent on " + player.getColor(), FactionEmojis.augers));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    String msg2 = p2.getRepresentationUnfogged() + " you may use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Clodho, the Augers" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                        + " agent, on " + player.getFactionEmojiOrColor() + " to give them 2 trade goods.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg2, buttons);
                }
            }
        }
    }

    public static void resolveExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String bID = buttonID.replace("resolve_explore_", "");
        String[] info = bID.split("_");
        String cardID = info[0];
        String planetName = info[1];
        Tile tile = game.getTileFromPlanet(planetName);
        String tileName = tile == null ? "no tile" : tile.getPosition();
        String messageText = player.getRepresentation() + " explored " + "Planet "
            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile " + tileName + ")*:";
        if (buttonID.contains("_distantSuns")) {
            messageText = player.getFactionEmoji() + " chose to resolve: ";
        }
        resolveExplore(event, cardID, tile, planetName, messageText, player, game);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveExplore(GenericInteractionCreateEvent event, String cardID, Tile tile, String planetID, String messageText,
        Player player, Game game) {
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        if (game == null) {
            MessageHelper.sendMessageToEventChannel(event, "Game could not be found");
            return;
        }
        String ogID = cardID;

        cardID = cardID.replace("extra1", "");
        cardID = cardID.replace("extra2", "");
        ExploreModel exploreModel = Mapper.getExplore(cardID);
        if (exploreModel == null) {
            MessageHelper.sendMessageToEventChannel(event, "ExploreModel could not be found: " + cardID);
            return;
        }

        MessageEmbed exploreEmbed = exploreModel.getRepresentationEmbed();
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), messageText, exploreEmbed);

        String message = null;

        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            message = player.getFactionEmoji() + " found a " + exploreModel.getName();
            if (planetID != null) {
                message += " on " + Helper.getPlanetRepresentation(planetID, game);
            }
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
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
            case "Leader" -> {
                String leader = cardID.replace("gain", "");
                player.addLeader(leader);
                MessageHelper.sendMessageToEventChannel(event, "Leader has been added to your party");
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
                        if (!Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).isEmpty()
                            || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planetID)) {
                            if ((attachment.equals(Constants.WARFARE) ||
                                attachment.equals(Constants.PROPULSION) ||
                                attachment.equals(Constants.CYBERNETIC) ||
                                attachment.equals(Constants.BIOTIC) ||
                                attachment.equals(Constants.WEAPON))) {
                                attachment += "stat";
                                String attachmentID = Mapper.getAttachmentImagePath(attachment);
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
                            Map<Units.UnitKey, Integer> units = new HashMap<>(planetUnitHolder.getUnits());
                            for (Player player_ : game.getPlayers().values()) {
                                String color = player_.getColor();
                                planetUnitHolder.removeAllUnits(color);
                            }
                            Map<Units.UnitKey, Integer> spaceUnits = spaceUnitHolder.getUnits();
                            for (Map.Entry<Units.UnitKey, Integer> unitEntry : units.entrySet()) {
                                Units.UnitKey key = unitEntry.getKey();
                                if (Set.of(Units.UnitType.Fighter, Units.UnitType.Infantry, Units.UnitType.Mech)
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
                    AttachmentModel aModel = Mapper.getAttachmentInfo(attachment);
                    message = "Attachment " + aModel.getName() + " added to " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game);
                    CommanderUnlockCheckService.checkPlayer(player, "sol", "xxcha");
                }
            }
            case Constants.TOKEN -> {
                String token = exploreModel.getAttachmentId().orElse("");
                String tokenFilename = Mapper.getTokenID(token);
                if (tokenFilename == null || tile == null) {
                    message = "Invalid token or tile";
                } else {
                    if ("ionalpha".equalsIgnoreCase(token)) {
                        message = player.getRepresentation() + " please decide to place either an Alpha or a Beta Ion Storm";
                        List<Button> buttonIon = new ArrayList<>();
                        buttonIon.add(Buttons.green("addIonStorm_beta_" + tile.getPosition(), "Place a Beta", MiscEmojis.CreussBeta));
                        buttonIon.add(Buttons.gray("addIonStorm_alpha_" + tile.getPosition(), "Place an Alpha", MiscEmojis.CreussAlpha));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttonIon);
                    } else {
                        tile.addToken(tokenFilename, Constants.SPACE);
                        message = "Token `" + token + "` added to tile " + tile.getAutoCompleteName();
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
        MessageHelper.sendMessageToEventChannel(event, message);
        message = "Card has been discarded. Resolve effects manually.";
        String planetName = Mapper.getPlanet(planetID) == null ? "`error?`" : Mapper.getPlanet(planetID).getName();
        Button decline = Buttons.red("decline_explore", "Decline Explore");

        // Specific Explore Handling
        switch (cardID) {
            case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" -> MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " gained " + ExploreEmojis.CFrag);
            case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" -> MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " gained " + ExploreEmojis.HFrag);
            case "irf1", "irf2", "irf3", "irf4", "irf5" -> MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " gained " + ExploreEmojis.IFrag);
            case "urf1", "urf2", "urf3" -> MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " gained " + ExploreEmojis.UFrag);
            case "ed1", "ed2" -> {
                message = "Card has been added to play area.";
                player.addRelic(Constants.ENIGMATIC_DEVICE);
                game.purgeExplore(ogID);
                MessageHelper.sendMessageToEventChannel(event, message);
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
                    ActionCardHelper.sendActionCardInfo(game, player, event);
                }

                if (hasSchemingAbility) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard",
                        ActionCardHelper.getDiscardActionCardButtons(player, false));
                }
                MessageHelper.sendMessageToEventChannel(event, message);
                ButtonHelper.checkACLimit(game, player);
                CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            }
            case "fiveac1", "fiveac2", "fiveac3" -> {
                boolean hasSchemingAbility = player.hasAbility("scheming");
                message = hasSchemingAbility
                    ? "Drew 6 action cards (Scheming) - please discard 1 action card from your hand"
                    : "Drew 5 action cards";
                int count = hasSchemingAbility ? 6 : 5;
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
                    ActionCardHelper.sendActionCardInfo(game, player, event);
                }

                if (hasSchemingAbility) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard",
                        ActionCardHelper.getDiscardActionCardButtons(player, false));
                }
                MessageHelper.sendMessageToEventChannel(event, message);
                ButtonHelper.checkACLimit(game, player);
                CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            }
            case "dv1", "dv2" -> {
                message = "Drew a " + CardEmojis.SecretObjective + "Secret Objective";
                game.drawSecretObjective(player.getUserID());
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message = message + " Drew a second " + CardEmojis.SecretObjective + "Secret Objective due to Plausible Deniability.";
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
                MessageHelper.sendMessageToEventChannel(event, message);
            }
            case "dw" -> {
                message = "Drew a " + ExploreEmojis.Relic + "Relic";
                MessageHelper.sendMessageToEventChannel(event, message);
                RelicHelper.drawRelicAndNotify(player, event, game);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodities (" + player.getCommodities() + "->" + player.getCommoditiesTotal()
                    + "). Reminder that this is optional, and that you may instead convert your existing commodities.";
                MessageHelper.sendMessageToEventChannel(event, message);
                ButtonHelperStats.replenishComms(event, game, player, true);
            }
            case Constants.MIRAGE -> {
                String mirageID = Constants.MIRAGE;
                PlanetModel planetValue = Mapper.getPlanet(mirageID);
                if (Optional.ofNullable(planetValue).isEmpty()) {
                    MessageHelper.sendMessageToEventChannel(event, "Invalid planet: " + mirageID);
                    return;
                }
                AddPlanetService.addPlanet(player, mirageID, game, null, false);
                PlanetService.refreshPlanet(player, mirageID);
                String exploreID = game.drawExplore(Constants.CULTURAL);
                if (exploreID == null) {
                    MessageHelper.sendMessageToEventChannel(event, "Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }

                if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                    || game.getPhaseOfGame().contains("agenda")) && player.hasUnit("saar_mech")
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
                    List<Button> saarButton = new ArrayList<>();
                    saarButton.add(Buttons.green("saarMechRes_" + "mirage", "Pay 1 Trade Good for Mech on " + Helper.getPlanetRepresentation("mirage", game), MiscEmojis.tg));
                    saarButton.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + " you may pay 1 trade good to place one "
                        + UnitEmojis.mech + " mech here.\n-# Do not do this prior to exploring; it is an \"after\", while exploring is a \"when\".",
                        saarButton);
                }

                if (ButtonHelper.isPlayerElected(game, player, "minister_exploration")) {
                    String fac = player.getFactionEmoji();
                    message = fac + " gained 1 trade good from __Minister of Exploration__ " + player.gainTG(1) + ". You do have this trade good prior to exploring.";
                    MessageHelper.sendMessageToEventChannel(event,
                        message);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, 1);
                }

                String exploredMessage = player.getRepresentation() + " explored " + ExploreEmojis.Cultural +
                    "Planet " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(mirageID, game) +
                    (tile == null ? "" : " *(tile " + tile.getPosition() + ")*:");
                MessageHelper.sendMessageToEventChannel(event, message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, player, game);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve _Functioning Base_:\n-# You currently have " + player.getTg() + " trade good" + (player.getTg() == 1 ? "" : "s") + ", "
                    + player.getCommoditiesRepresentation() + " commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ", and "
                    + player.getActionCards().size() + " action card" + (player.getActionCards().size() == 1 ? "" : "s") + ".";
                Button getACButton = Buttons.green("comm_for_AC", "Spend 1 Trade Good or 1 Commodity For 1 Action Card", CardEmojis.ActionCard);
                Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "freetech1", "freetech2", "freetech3" -> {
                game.setComponentAction(true);
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), player.getRepresentation() + ", please use the button to research a technology.", Buttons.GET_A_FREE_TECH);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                int commod = player.getCommodities();
                if (commod > 0) {
                    message = "Resolve _Abandoned Warehouses_:\n-# You currently have " + player.getCommoditiesRepresentation()
                        + " commodit" + (commod == 1 ? "y" : "ies") + ".";
                    commod = commod > 2 ? 2 : commod;
                    Button convert = Buttons.green("convert_2_comms", "Convert " + commod + " Commodit" + (commod == 1 ? "y" : "ies")
                        + " Into " + (commod == 1 ? "a " : "") + "Trade Good" + (commod == 1 ? "" : "s"), MiscEmojis.Wash);
                    Button gain = Buttons.blue("gain_2_comms", "Gain 2 Commodities", MiscEmojis.comm);
                    List<Button> buttons = List.of(convert, gain);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                } else {
                    message = player.getFactionEmoji() + " gained 2 commodities automatically due to having no commodities to convert.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    ButtonHelperStats.gainComms(event, game, player, 2, true, true);
                }
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetID != null) {
                    Set<String> tokenList = ButtonHelper.getUnitHolderFromPlanetName(planetID, game).getTokenList();
                    boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
                    if (!containsDMZ) {
                        AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + planetID);
                        message = player.getFactionEmoji() + ColorEmojis.getColorEmojiWithName(player.getColor()) + UnitEmojis.infantry
                            + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetID)
                            + ", however this placement is __optional__.";
                    } else {
                        message = "Planet had DMZ so no infantry was placed";
                    }
                } else {
                    message = "Tile was null, no infantry placed";
                }
                MessageHelper.sendMessageToEventChannel(event, message);
            }
            case "darkvisions" -> {
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "industrial");
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "hazardous");
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "cultural");
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "frontier");

                List<Button> discardButtons = new ArrayList<>();
                discardButtons.add(Buttons.green("discardExploreTop_industrial", "Discard Top Industrial", ExploreEmojis.Industrial));
                discardButtons.add(Buttons.red("discardExploreTop_hazardous", "Discard Top Hazardous", ExploreEmojis.Hazardous));
                discardButtons.add(Buttons.blue("discardExploreTop_cultural", "Discard Top Cultural", ExploreEmojis.Cultural));
                discardButtons.add(Buttons.gray("discardExploreTop_frontier", "Discard Top Frontier", ExploreEmojis.Frontier));
                discardButtons.add(Buttons.red("deleteButtons", "Done Resolving"));
                message = player.getRepresentation() + " you may use the buttons to discard the top of the explore decks if you choose.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, discardButtons);
                List<Button> explorePlanets = new ArrayList<>();
                for (String planet : player.getPlanetsAllianceMode()) {
                    UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder == null) {
                        continue;
                    }
                    Planet planetReal = (Planet) unitHolder;
                    List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                    if (buttons != null && !buttons.isEmpty()) {
                        explorePlanets.addAll(buttons);
                    }
                }
                message = "Click button to explore a planet after resolving any discards";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, explorePlanets);
                message = "Use this button to shuffle explore decks once you're done with the rest";
                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), message, Buttons.red("shuffleExplores", "Shuffle Explore Decks"));
            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = player.getRepresentation() + " please resolve _Local Fabricators_:\n-# You currently have " + player.getTg()
                    + " trade good" + (player.getTg() == 1 ? "" : "s") + " and " + player.getCommoditiesRepresentation() +
                    " commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ".";
                Button getMechButton = Buttons.green("resolveLocalFab_" + planetID, "Spend 1 Commodity or Trade Good for a Mech on " + planetName, UnitEmojis.mech);
                Button getCommButton3 = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "fivetg1", "fivetg2", "fivetg3" -> {
                message = "Gained 5" + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(5) + " ";
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, 5);
                CommanderUnlockCheckService.checkPlayer(player, "hacan");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            }
            case "kel1", "kel2", "kel3", "ent", "minent", "majent" -> {
                int ccsToGain = 1;
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        message = "Gained 1" + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(1) + " ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, 1);
                    }
                    case "ent" -> {
                        message = "Gained 2" + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(2) + " ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, 2);
                    }
                    case "majent" -> {
                        message = "Gained 3" + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(3) + " ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, 3);
                    }
                    case "kel1", "kel2", "kel3" -> {
                        ccsToGain = 2;
                        message = "";
                    }
                    default -> message = "";
                }
                CommanderUnlockCheckService.checkPlayer(player, "hacan");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                message += "\n" + player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain " + ccsToGain + " command token" + (ccsToGain > 1 ? "s" : "") + ".";
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = player.getRepresentation() + " please resolve Expedition:\n-# You have ";
                message += ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID);
                Button readyPlanet = Buttons.green("resolveExpedition_" + planetID, "Ready " + Helper.getPlanetRepresentation(planetID, game) + " by removing 1 infantry from or having mech on planet.");
                List<Button> buttons = List.of(readyPlanet, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "frln1", "frln2", "frln3" -> {
                message = player.getRepresentation() + " please resolve Freelancers:\n-# " + ButtonHelper.getListOfStuffAvailableToSpend(player, game, false);
                Button gainTG = Buttons.green("freelancersBuild_" + planetID, "Produce 1 Unit");
                List<Button> buttons = List.of(gainTG, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = player.getRepresentation() + " please resolve Core Mine:\n-# You have ";
                message += ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID);
                Button gainTG = Buttons.green("resolveCoreMine_" + planetID, "Gain 1 Trade Good by Removing Infantry or Having Mech on " + planetName, MiscEmojis.tg);
                List<Button> buttons = List.of(gainTG, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = player.getRepresentation() + " please resolve Volatile Fuel Source:\n-# Your current command tokens are " + player.getCCRepresentation() + ".";
                message += " and you have " + ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID);
                Button gainCC = Buttons.green("resolveVolatile_" + planetID, "Remove 1 Infantry or Have a mech on " + planetName + " to Gain a Command Token");
                List<Button> buttons = List.of(gainCC, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "warforgeruins" -> {
                message = player.getRepresentation() + " please resolve War Forge Ruins:\n-# You have ";
                message += ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID);
                Button ruinsInf = Buttons.green("ruins_" + planetID + "_2inf", "Remove 1 Infantry or Have a Mech on " + planetName + " to Place 2 Infantry Here");
                Button ruinsMech = Buttons.green("ruins_" + planetID + "_mech", "Remove 1 Infantry or Have a Mech on " + planetName + " to Place Mech Here");
                List<Button> buttons = List.of(ruinsInf, ruinsMech, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "seedyspaceport" -> {
                List<Button> buttons = new ArrayList<>();
                message = player.getRepresentation() + " please resolve Seedy Spaceport:\n-# You have " + ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID);
                for (Leader leader : player.getLeaders()) {
                    if (leader.isExhausted() && leader.getId().contains("agent")) {
                        LeaderModel leaderM = Mapper.getLeader(leader.getId());
                        buttons.add(Buttons.green(
                            "seedySpace_" + leader.getId() + "_" + planetID,
                            "Remove 1 Infantry or Have a Mech on " + planetName + " to Ready " + leaderM.getName(),
                            leaderM.getLeaderEmoji()));
                    }
                }
                buttons.add(Buttons.blue("seedySpace_AC_" + planetID, "Remove 1 Infantry or Have a Mech on " + planetName + " to Draw 1 Action Card"));
                buttons.add(decline);

                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            }
            case "hiddenlaboratory" -> {
                MessageHelper.sendMessageToEventChannel(event, "# Exploring frontier in this system due to finding the hidden laboratory industrial explore.");
                AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
                expFront(event, tile, game, player);
            }
            case "ancientshipyard" -> {
                List<String> colors = tile == null ? List.of() : tile.getUnitHolders().get("space").getUnitColorsOnHolder();
                if (colors.isEmpty() || colors.contains(player.getColorID())) {
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
                    MessageHelper.sendMessageToEventChannel(event, "Cruiser added to the system automatically.");
                } else {
                    MessageHelper.sendMessageToEventChannel(event, "Someone else's ships were in the system, no cruiser added");
                }
            }
            case "forgottentradestation" -> {
                int tgGain = tile == null ? 0 : tile.getUnitHolders().size() - 1;
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " gained " + tgGain + " trade good"
                    + (tgGain == 1 ? "" : "s") + " due to the _Forgotten Trade Station_ (" + oldTg + "->" + player.getTg() + ").");
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, tgGain);
            }
            case "starchartcultural", "starchartindustrial", "starcharthazardous", "starchartfrontier" -> {
                game.purgeExplore(ogID);
                player.addRelic(cardID);
                message = "Card has been added to play area.\nAdded as a relic (not actually a relic)";
                MessageHelper.sendMessageToEventChannel(event, message);
            }

        }
        CommanderUnlockCheckService.checkPlayer(player, "hacan");

        if (player.hasAbility("fortune_seekers") && game.getStoredValue("fortuneSeekers").isEmpty()) {
            List<Button> gainComm = new ArrayList<>();
            gainComm.add(Buttons.green("gain_1_comms", "Gain 1 Comm", MiscEmojis.comm));
            gainComm.add(Buttons.red("deleteButtons", "Decline"));
            String sb = player.getFactionEmoji() + " may use their **Fortune Seekers** ability\n" +
                player.getRepresentationUnfogged() +
                " After resolving the explore, you may use this button to get your commodity from your fortune seekers ability.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb, gainComm);
            game.setStoredValue("fortuneSeekers", "Used");
        }

        CommanderUnlockCheckService.checkPlayer(player, "kollecc", "bentor", "ghost");
        if (player.getPlanets().contains(planetID)) {
            ButtonHelperAbilities.offerOrladinPlunderButtons(player, game, planetID);
        }

        if (player.hasAbility("awaken") && !game.getAllPlanetsWithSleeperTokens().contains(planetID) && player.getPlanetsAllianceMode().contains(planetID)) {
            Button placeSleeper = Buttons.green("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID, MiscEmojis.Sleeper);
            Button declineSleeper = Buttons.red("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(placeSleeper, declineSleeper);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    public static void expFront(GenericInteractionCreateEvent event, Tile tile, Game game, Player player) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String cardID = game.drawExplore(Constants.FRONTIER);
            String messageText = ExploreEmojis.Frontier + "Frontier *(tile " + tile.getPosition() + ")* explored by " + player.getRepresentation() + ":";
            ExploreService.resolveExplore(event, cardID, tile, null, messageText, player, game);

            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No frontier token in given system.");
        }
    }

    public static void expFrontAlreadyDone(GenericInteractionCreateEvent event, Tile tile, Game game, Player player, String cardID) {
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        if (space.getTokenList().contains(frontierFilename)) {
            space.removeToken(frontierFilename);
            String messageText = ExploreEmojis.Frontier + "Frontier *(tile " + tile.getPosition() + ")* explored by " + player.getRepresentation() + ":";
            resolveExplore(event, cardID, tile, null, messageText, player, game);

            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No frontier token in given system.");
        }
    }

    public static void secondHalfOfExpInfo(List<String> types, GenericInteractionCreateEvent event, Player player, Game game, boolean overRide) {
        secondHalfOfExpInfo(types, event, player, game, overRide, false);
    }

    public static void secondHalfOfExpInfo(List<String> types, GenericInteractionCreateEvent event, Player player, Game game, boolean overRide, boolean fullText) {
        for (String currentType : types) {
            StringBuilder info = new StringBuilder();
            List<String> deck = game.getExploreDeck(currentType);
            Collections.sort(deck);
            Integer deckCount = deck.size();
            Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
            NumberFormat formatPercent = NumberFormat.getPercentInstance();
            formatPercent.setMaximumFractionDigits(1);
            List<String> discard = game.getExploreDiscard(currentType);
            Collections.sort(discard);
            Integer discardCount = discard.size();

            info.append(ExploreEmojis.getTraitEmoji(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK** (")
                .append(deckCount).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            info.append(listNames(deck, true, fullText)).append("\n");

            info.append(ExploreEmojis.getTraitEmoji(currentType)).append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD** (")
                .append(discardCount).append(")\n");
            info.append(listNames(discard, false, fullText));

            if (types.indexOf(currentType) != types.size() - 1) {
                info.append("​"); // add a zero width space at the end to cement newlines between sets of explores
            }

            if (player == null || player.getSCs().isEmpty() || overRide || !game.isFowMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), info.toString());
            }
        }
        if (player != null && "action".equalsIgnoreCase(game.getPhaseOfGame()) && game.isFowMode() && !overRide) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        }
    }

    private static String listNames(List<String> deck, boolean showPercents, boolean showFullText) {
        int deckCount = deck.size();
        double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);

        StringBuilder sb = new StringBuilder();
        if (deck.isEmpty()) {
            sb.append("> there is nothing here\n");
        }

        Map<String, List<ExploreModel>> explores = deck.stream().map(Mapper::getExplore).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(ExploreModel::getName));
        List<Map.Entry<String, List<ExploreModel>>> orderedExplores = explores.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> 15 - e.getValue().size())).toList();
        for (Map.Entry<String, List<ExploreModel>> entry : orderedExplores) {
            String exploreName = entry.getKey();
            List<String> ids = entry.getValue().stream().map(ExploreModel::getId).toList();

            if (showFullText) {
                sb.append("> ").append(exploreName).append("\n").append(entry.getValue().getFirst().getText()).append(" [").append(String.join(", ", ids)).append("]");
            } else {
                sb.append("> ").append(exploreName).append(" [").append(String.join(", ", ids)).append("]");
            }

            if (showPercents && ids.size() > 1) {
                sb.append(" _").append(formatPercent.format(deckDrawChance * ids.size())).append("_");
            }
            sb.append("\n");
        }

        List<String> unmapped = deck.stream().filter(e -> Mapper.getExplore(e) == null).toList();
        for (String cardID : unmapped) {
            ExploreModel card = Mapper.getExplore(cardID);
            String name = card != null ? card.getName() : null;
            sb.append("> (").append(cardID).append(") ").append(name).append("\n");
        }
        return sb.toString();
    }
}
