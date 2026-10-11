package ti4.service.explore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.explore.theodisi.LostLegciesExploreHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kairn.KairnTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant.RevenantLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi.ArvaxiAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.lunarium.LunariumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.lunarium.LunariumBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.relics.theodisi.LostLegaciesRelicHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsDSButtonHandler;
import ti4.discord.interactions.commands.tokens.AddTokenCommand;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.helper.CardIdHelper;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.RiftSetModeService;
import ti4.service.game.MonumentsService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.map.FractureService;
import ti4.service.planet.AddPlanetService;
import ti4.service.planet.PlanetService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ExploreService {

    public void explorePlanet(
            GenericInteractionCreateEvent event,
            Tile tile,
            String planetName,
            String drawColor,
            Player player,
            boolean nraCheck,
            Game game,
            int numExplores,
            boolean ownerShipOverride) {
        if (!player.getPlanetsAllianceMode().contains(planetName) && !ownerShipOverride) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You do not control this planet, thus cannot explore it.");
            return;
        }
        game.removeStoredValue("revArcanumAgentExploreOptions_" + player.getFaction());
        boolean kolleccbt = drawColor.endsWith("kolleccbt");
        if (kolleccbt) {
            drawColor = drawColor.replace("kolleccbt", "");
        }
        game.setStoredValue(
                player.getFaction() + "planetsExplored",
                game.getStoredValue(player.getFaction() + "planetsExplored") + planetName + "*");

        if (RiftSetModeService.willPlanetGetStellarConverted(planetName, player, game, event)) {
            return;
        }
        if ("bozgarbia".equalsIgnoreCase(planetName)) {
            if (player.hasAbility("distant_suns")) {
                String reportMessage = """
                    Bozgarbia exploration with **Distant Suns** is not implemented.\

                    Please use `/explore draw_and_discard trait` then `/explore use explore_card_id` to manually resolve this exploration.\

                    -# (NB: Player chooses a trait, reveals two of that trait and one of each other; reveal four cards total.)""";
                MessageHelper.sendMessageToChannel(FoWHelper.actionsChannelOrLocal(game, event), reportMessage);
                return;
            }
            String cardIDC = game.drawExplore("CULTURAL");
            String cardIDH = game.drawExplore("INDUSTRIAL");
            String cardIDI = game.drawExplore("HAZARDOUS");
            String reportMessage = player.getFactionEmoji() + " explored " + MiscEmojis.LegendaryPlanet
                    + "Bozgarbia ability and found " + articledExploreName(cardIDC) + ", "
                    + articledExploreName(cardIDH) + " and " + articledExploreName(cardIDI) + ".";
            MessageHelper.sendMessageToChannel(FoWHelper.actionsChannelOrLocal(game, event), reportMessage);
            offerExploreChoice(event, player, planetName, List.of(cardIDC, cardIDH, cardIDI));
            return;
        }

        if (player.hasAbility("distant_suns")) {
            if (Helper.mechCheck(planetName, game, player)) {
                if (!nraCheck) {
                    readyWithPreFabArcologies(event, player, planetName);
                    String message = "Please decide whether or not to use your " + FactionEmojis.Naaz
                            + "**Distant Suns** ability.";
                    Button resolveExplore1 = Buttons.green(
                            "distant_suns_accept_" + planetName + "_" + drawColor, "Choose from 2 Exploration Cards");
                    Button resolveExplore2 = Buttons.red(
                            "distant_suns_decline_" + planetName + "_" + drawColor,
                            "Resolve First Exploration Card Only");
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
                    if (cardID2.equalsIgnoreCase(cardID1)) {
                        cardID2 = game.drawExplore(drawColor);
                    }
                    if (!addLanefirCommanderInfantry(event, game, player, planetName)) {
                        return;
                    }
                    boolean sameExplore = Mapper.getExplore(cardID1)
                            .getName()
                            .equals(Mapper.getExplore(cardID2).getName());
                    String reportMessage = player.getFactionEmoji() + " used their " + FactionEmojis.Naaz
                            + "**Distant Suns** ability and found " + articledExploreName(cardID1) + " and "
                            + articledExploreName(cardID2) + " on "
                            + Helper.getPlanetRepresentationPlusEmoji(planetName) + "."
                            + (sameExplore ? " Auto-resolving." : "");
                    MessageHelper.sendMessageToChannel(FoWHelper.actionsChannelOrLocal(game, event), reportMessage);

                    if (sameExplore) {
                        resolveExploreAuto(event, player, cardID1, planetName, game);
                        return;
                    }
                    offerExploreChoice(event, player, planetName, List.of(cardID1, cardID2));
                    return;
                }
            }
        }
        if ((player.hasAbility("deep_mining") || player.hasTech("tf-deepinstallations")) && tile != null) {
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            if (unitHolder.getUnitCount(Units.UnitType.Mech, player.getColor()) > 0
                    || unitHolder.getUnitCount(Units.UnitType.Spacedock, player.getColor()) > 0
                    || unitHolder.getUnitCount(Units.UnitType.Pds, player.getColor()) > 0) {
                if (!nraCheck) {
                    String message = "Please decide whether or not to use your " + FactionEmojis.gledge
                            + "**Deep Mining** (gain 1 trade good instead of exploring) ability.";
                    Button resolveExplore1 = Buttons.green("deep_mining_accept", "Gain 1 Trade Good");
                    Button resolveExplore2 =
                            Buttons.red("deep_mining_decline_" + planetName + "_" + drawColor, "Explore");
                    List<Button> buttons = List.of(resolveExplore1, resolveExplore2);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    return;
                }
            }
        }

        if (kolleccbt) {
            player.setBreakthroughExhausted("kolleccbt", true);
            MessageHelper.sendMessageToChannel(
                    (MessageChannel) event.getChannel(),
                    player.getRepresentation()
                            + " has exhausted _The Collector's Museum_ to explore from the discard pile of "
                            + drawColor + " on " + planetName
                            + ". Not yet implemented fully. Please use `/explore shuffle_back_into_deck` and then `/explore use` to resolve (and then maybe `/explore shuffle_back_into_deck` back into deck again).");
            return;
        }
        String cardID = game.drawExplore(drawColor);
        if (cardID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Planet cannot be explored");
            return;
        }
        String position_ = tile == null ? "none" : tile.getPosition();
        String messageText =
                player.getRepresentation() + " explored " + ExploreEmojis.getTraitEmoji(drawColor) + "Planet "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " in tile "
                        + position_ + ":";
        if (player.hasUnexhaustedLeader("lanefiragent")) {
            String yssaril = player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "";
            offerExploreDecline(
                    event,
                    game,
                    player,
                    cardID,
                    planetName,
                    "lanefirAgentRes_Decline_" + drawColor,
                    "Choose ",
                    Buttons.green("lanefirAgentRes_Accept_" + drawColor + "_" + planetName, "Use Lanefir Agent"),
                    " You have " + (yssaril.isEmpty() ? "" : "Clever Clever ") + "Vassa Hagi, the Lanefir" + yssaril
                            + " agent, and thus may decline this exploration to draw another one instead.",
                    "");
            return;
        }
        if (player.hasUnlockedBreakthrough("augersbt")) {
            offerExploreDecline(
                    event,
                    game,
                    player,
                    cardID,
                    planetName,
                    "absolsdn_Decline_" + drawColor,
                    "Resolve ",
                    Buttons.green(
                            "draw_1_ACDelete", "Draw 1 Action Card Instead With Breakthrough", FactionEmojis.augers),
                    " You have Augers Breakthrough, and thus may decline this exploration to draw 1 action card instead.",
                    ".");
            return;
        }
        if (player.hasTech("absol_sdn")) {
            offerExploreDecline(
                    event,
                    game,
                    player,
                    cardID,
                    planetName,
                    "absolsdn_Decline_" + drawColor,
                    "Resolve ",
                    Buttons.green("absolsdn_Accept" + drawColor + "_" + planetName, "Gain 1 Trade Good"),
                    " You have _Scanlink Drone Network_, and thus may decline this exploration to gain 1 trade good.",
                    ".");
            return;
        }
        if (player.hasAbility("doctrine_discovery")) {
            offerExploreDecline(
                    event,
                    game,
                    player,
                    cardID,
                    planetName,
                    "natauDiscovery_Decline_" + drawColor,
                    "Resolve ",
                    Buttons.green("natauDiscovery_Accept" + drawColor + "_" + planetName, "Gain 1 Trade Good"),
                    " You have _Discovery_, and thus may decline this exploration to gain 1 trade good.",
                    ".");
            return;
        }
        Planet exploredPlanet = planetName == null ? null : game.getUnitHolderFromPlanet(planetName);
        boolean isPlanetExplore = !Constants.FRONTIER.equals(drawColor) && exploredPlanet != null;
        if (isPlanetExplore
                && TaAbilityHandler.offerPerfectArchitecture(
                        event, player, game, tile, planetName, cardID, drawColor)) {
            return;
        }
        if (isPlanetExplore) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    "resolve_explore_" + cardID + "_" + planetName,
                    "Resolve " + Mapper.getExplore(cardID).getName()));

            RevenantLeadersHandler.addArlirMirroredButton(buttons, game, player, planetName, cardID, drawColor);

            if (buttons.size() > 1) {
                List<MessageEmbed> embeds = List.of(Mapper.getExplore(cardID).getRepresentationEmbed());
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", choose how to resolve this exploration.",
                        embeds,
                        buttons);
                return;
            }
        }
        resolveExplore(event, cardID, tile, planetName, messageText, player, game);
        readyWithPreFabArcologies(event, player, planetName);
        if (player.hasAbility("ultimate_authority")) {
            ArvaxiAbilityHandler.onExplorePlanet(player, game, planetName);
        }
        if (ButtonHelper.doesPlayerHaveFSHere("ghemina_flagship_lord", player, tile)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planetName);
            MessageHelper.sendMessageToChannel(
                    (MessageChannel) event.getChannel(),
                    "Infantry added due to presence of The Lord (a Ghemina flagship) . Technically happens after exploring.");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && game.getPhaseOfGame().contains("agenda")) {
            PlanetService.refreshPlanet(player, planetName);
            MessageHelper.sendMessageToChannel(
                    (MessageChannel) event.getChannel(),
                    planetName + " has been readied because of Quaxdol Junitas, the Florzen Commander.");
            AgendaHelper.listVoteCountIfUnfogged(game);
        }
        if (!addLanefirCommanderInfantry(event, game, player, planetName)) {
            return;
        }
        addAtsArmamentsCommodity(player, numExplores, event.getMessageChannel(), player.getRepresentation());
        if (game.playerHasLeaderUnlockedOrAlliance(player, "tacommander")) {
            TaLeadersHandler.resolveTaCommander(player, tile, game, planetName);
        }
        if (ButtonHelper.isPlanetLegendaryOrTechSkip(planetName, game)
                && Helper.getPlayerFromUnlockedLeader(game, "augersagent") != null) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2.hasUnexhaustedLeader("augersagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            "exhaustAgent_augersagent_" + player.getFaction(),
                            "Use Ilyxum Agent on " + player.getColor(),
                            FactionEmojis.augers));
                    buttons.add(Buttons.DECLINE);
                    String msg2 = p2.getRepresentationUnfogged() + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Clodho, the Ilyxum" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, on " + player.getFactionEmojiOrColor() + " to give them 2 trade goods.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg2, buttons);
                }
            }
        }
        if (game.isMonumentsMode()) {
            Player monumentPlayer = MonumentsService.getMonumentOwner(game, "bentor_monument");
            if (monumentPlayer != null
                    && !MonumentsService.isMonumentOnBoard(game, monumentPlayer, "bentor_monument")
                    && player != monumentPlayer
                    && MonumentsDSButtonHandler.isPlanetNotAdjacentToHomeSystem(game, exploredPlanet, player)
                    && Mapper.getUnit("bentor_monument").canBePlacedOnPlanetTypes(exploredPlanet.getPlanetTypes())
                    && !exploredPlanet.getUnitKeys().isEmpty()) {
                MessageHelper.sendMessageToChannelWithButton(
                        monumentPlayer.getCardsInfoThread(),
                        monumentPlayer.getRepresentation()
                                + ", " + player.getRepresentationNoPing() + " has explored "
                                + exploredPlanet.getRepresentation(game)
                                + " and as such, you may choose to deploy _Tucc Academy_ to that planet and explore it.",
                        MonumentsDSButtonHandler.getTuccAcademyButton(monumentPlayer, player, exploredPlanet));
            }
        }
    }

    private static String indefiniteArticle(String cardID, String exploreName) {
        if ("Freelancers".equalsIgnoreCase(exploreName)
                || "Local Fabricators".equalsIgnoreCase(exploreName)
                || "mirage".equalsIgnoreCase(cardID)) {
            return "";
        }
        if ("dmz".equalsIgnoreCase(cardID) || "toe".equalsIgnoreCase(cardID)) {
            return "the ";
        }
        if ("ls".equalsIgnoreCase(cardID)) {
            return "some ";
        }
        return "aeiou".indexOf(exploreName.toLowerCase().charAt(0)) >= 0 ? "an " : "a ";
    }

    private static String articledExploreName(String cardID) {
        String exploreName = Mapper.getExplore(cardID).getName();
        return indefiniteArticle(cardID, exploreName) + "_" + exploreName + "_";
    }

    private static void offerExploreChoice(
            GenericInteractionCreateEvent event, Player player, String planetName, List<String> cardIDs) {
        List<ExploreModel> models = cardIDs.stream().map(Mapper::getExplore).toList();
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < cardIDs.size(); i++) {
            buttons.add(Buttons.green(
                    "resolve_explore_" + cardIDs.get(i) + "_" + planetName + "_distantSuns",
                    models.get(i).getName()));
        }
        List<MessageEmbed> embeds =
                models.stream().map(ExploreModel::getRepresentationEmbed).toList();
        String message = player.getRepresentation() + " please choose 1 exploration card to resolve.";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getMessageChannel(), message, embeds, buttons);
    }

    private static void offerExploreDecline(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String cardID,
            String planetName,
            String declineButtonPrefix,
            String resolveLabelVerb,
            Button alternative,
            String reason,
            String announcementEnd) {
        ExploreModel exploreModel = Mapper.getExplore(cardID);
        String name = exploreModel.getName();
        Button resolve = Buttons.green(declineButtonPrefix + "_" + cardID + "_" + planetName, resolveLabelVerb + name);
        FoWHelper.announcePublicOrLocal(
                game,
                event,
                player.getFactionEmoji() + " found a " + name + " on " + planetName + announcementEnd,
                "Found a " + name + " on " + planetName + announcementEnd);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + reason,
                List.of(exploreModel.getRepresentationEmbed()),
                List.of(resolve, alternative));
    }

    private static void readyWithPreFabArcologies(
            GenericInteractionCreateEvent event, Player player, String planetName) {
        if (player.hasTech("pfa")) {
            PlanetService.refreshPlanet(player, planetName);
            MessageHelper.sendMessageToChannel(
                    (MessageChannel) event.getChannel(),
                    Mapper.getPlanet(planetName).getName()
                            + " has been automatically readied because you have _Pre-Fab Arcologies_.");
        }
    }

    private static boolean addLanefirCommanderInfantry(
            GenericInteractionCreateEvent event, Game game, Player player, String planetName) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            return true;
        }
        Tile tileWithPlanet = game.getTileFromPlanet(planetName);
        if (tileWithPlanet == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "An error occurred while placing 1 infantry. Resolve manually.");
            return false;
        }
        tileWithPlanet.getUnitHolders().get(planetName).addUnit(Mapper.getUnitKey("gf", player.getColor()), 1);
        MessageHelper.sendMessageToChannel(
                (MessageChannel) event.getChannel(),
                "Added infantry to " + planetName + " because of Master Halbert, the Lanefir Commander.");
        return true;
    }

    private static void addAtsArmamentsCommodity(
            Player player, int count, MessageChannel channel, String playerRepresentation) {
        if (player.hasTech("dslaner")) {
            player.setAtsCount(player.getAtsCount() + count);
            MessageHelper.sendMessageToChannel(channel, playerRepresentation + " put 1 commodity on _ATS Armaments_.");
        }
    }

    public static void postPlanetExploreChecks(
            GenericInteractionCreateEvent event, Game game, Player player, String planetName, MessageChannel channel) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && game.getPhaseOfGame().contains("agenda")) {
            PlanetService.refreshPlanet(player, planetName);
            MessageHelper.sendMessageToChannel(
                    channel, "Planet has been readied because of Quaxdol Junitas, the Florzen Commander.");
            AgendaHelper.listVoteCountIfUnfogged(game);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            game.getTileFromPlanet(planetName)
                    .getUnitHolders()
                    .get(planetName)
                    .addUnit(Mapper.getUnitKey("gf", player.getColor()), 1);
            MessageHelper.sendMessageToChannel(
                    channel, "Added 1 infantry to planet because of Master Halbert, the Lanefir Commander.");
        }
        addAtsArmamentsCommodity(player, 1, event.getMessageChannel(), player.getRepresentation());
    }

    public static void resolveExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String bID = buttonID.replace("resolve_explore_", "");
        String[] info = bID.split("_");
        String cardID = info[0];
        String planetName = info[1];
        Tile tile = game.getTileFromPlanet(planetName);
        String tileName = tile == null ? "no tile" : tile.getPosition();
        String messageText = player.getRepresentationNoPing() + " explored the planet "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " in tile "
                + tileName + ":";
        if (buttonID.contains("_distantSuns")) {
            messageText = player.getRepresentationNoPing() + " chose to resolve _"
                    + Mapper.getExplore(cardID).getName() + "_.";
        }
        resolveExplore(event, cardID, tile, planetName, messageText, player, game);
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveExploreAuto(
            GenericInteractionCreateEvent event, Player player, String cardID, String planetName, Game game) {
        Tile tile = game.getTileFromPlanet(planetName);
        String messageText = player.getRepresentationNoPing() + " \"chose\" to resolve _"
                + Mapper.getExplore(cardID).getName() + "_.";
        resolveExplore(event, cardID, tile, planetName, messageText, player, game);
    }

    public static void resolveExplore(
            GenericInteractionCreateEvent event,
            String cardID,
            Tile tile,
            String planetID,
            String messageText,
            Player player,
            Game game) {
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        if (game == null) {
            MessageHelper.sendMessageToEventChannel(event, "Game could not be found");
            return;
        }
        String ogID = cardID;

        cardID = CardIdHelper.stripExtra(cardID);
        ExploreModel exploreModel = Mapper.getExplore(cardID);
        if (exploreModel == null) {
            MessageHelper.sendMessageToEventChannel(event, "ExploreModel could not be found: " + cardID);
            return;
        }

        MessageEmbed exploreEmbed = exploreModel.getRepresentationEmbed(false, true);
        MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), messageText, exploreEmbed);

        StringBuilder message = new StringBuilder(player.getFactionEmoji() + " found " + articledExploreName(cardID));
        if (planetID != null) {
            message.append(" on ").append(Helper.getPlanetRepresentation(planetID, game));
        }
        message.append(".");
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message.toString());
        }
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " "
                        + StringUtils.capitalize(message.toString()));

        if (tile == null) {
            tile = game.getTileFromPlanet(planetID);
        }

        // Generic Resolution Handling
        switch (exploreModel.getResolution().toLowerCase()) {
            case Constants.FRAGMENT -> {
                player.addFragment(cardID);
                game.purgeExplore(ogID);
                KairnTechHandler.offerSurveyorsLensReady(event, game, player, tile, planetID, cardID);
            }
            case "leader" -> {
                String leader = cardID.replace("gain", "");
                player.addLeader(leader);
                MessageHelper.sendMessageToEventChannel(event, "Leader has been added to your party");
                game.purgeExplore(ogID);
            }
            case Constants.ATTACH -> {
                String attachment = exploreModel.getAttachmentId().orElse("");
                String attachmentFilename = Mapper.getAttachmentImagePath(attachment);
                if (attachmentFilename == null || tile == null || planetID == null) {
                    message = new StringBuilder("Invalid attachment, tile, or planet");
                } else {
                    PlanetModel planetInfo = Mapper.getPlanet(planetID);
                    if (planetInfo != null) {
                        if ((planetInfo.getTechSpecialties() != null
                                        && !planetInfo.getTechSpecialties().isEmpty())
                                || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planetID)) {
                            if ("diversifiedresearchfacility".equals(attachment)) {
                                attachment = "diversifiedresearchfacilitystat";
                                attachmentFilename = Mapper.getAttachmentImagePath(attachment);
                            } else if ((Constants.WARFARE.equals(attachment)
                                    || Constants.PROPULSION.equals(attachment)
                                    || Constants.CYBERNETIC.equals(attachment)
                                    || Constants.BIOTIC.equals(attachment)
                                    || Constants.WEAPON.equals(attachment))) {
                                attachment += "stat";
                                String attachmentID = Mapper.getAttachmentImagePath(attachment);
                                if (attachmentID != null) {
                                    attachmentFilename = attachmentID;
                                }
                            }
                        }
                    }

                    AttachmentModel aModel = Mapper.getAttachmentInfo(attachment);
                    tile.addToken(attachmentFilename, planetID);
                    TaUnitHandler.offerTaMechDeploy(event, player, game, tile, planetID);
                    message = new StringBuilder("Attachment _" + aModel.getName() + "_ added to "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game) + ".");
                    if (Constants.DMZ.equals(attachment)) {
                        String dmzLargeFilename = Mapper.getTokenID(Constants.DMZ_LARGE);
                        tile.addToken(dmzLargeFilename, planetID);
                        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
                        UnitHolder planetUnitHolder = unitHolders.get(planetID);
                        UnitHolder spaceUnitHolder = unitHolders.get(Constants.SPACE);
                        for (Player p2 : game.getPlayers().values()) {
                            List<String> groundForces = new ArrayList<>();
                            List<String> structures = new ArrayList<>();
                            for (UnitKey key : planetUnitHolder.getUnitKeys()) {
                                if (!p2.getColor().equals(key.getColor())) continue;
                                int amt = planetUnitHolder.getUnitCount(key);
                                var removed = planetUnitHolder.removeUnit(key, amt);
                                if (Set.of(UnitType.Fighter, UnitType.Infantry, UnitType.Mech)
                                        .contains(key.unitType())) {
                                    spaceUnitHolder.addUnitsWithStates(key, removed);
                                    groundForces.addAll(Collections.nCopies(
                                            amt, key.unitEmoji().emojiString()));
                                } else {
                                    structures.addAll(Collections.nCopies(
                                            amt, key.unitEmoji().emojiString()));
                                }
                            }
                            if (!groundForces.isEmpty()) {
                                message.append('\n')
                                        .append(p2.getRepresentationUnfogged())
                                        .append(", your ")
                                        .append(String.join("", groundForces))
                                        .append(' ')
                                        .append(groundForces.size() == 1 ? "has" : "have")
                                        .append(" been yote into space")
                                        .append(structures.isEmpty() ? "." : "");
                            }
                            if (!structures.isEmpty()) {
                                message.append(
                                                groundForces.isEmpty()
                                                        ? "\n" + p2.getRepresentationUnfogged() + ", "
                                                        : ", and ")
                                        .append("your ")
                                        .append(String.join("", structures))
                                        .append(' ')
                                        .append(structures.size() == 1 ? "has" : "have")
                                        .append(" been yote into the shadow realm.");
                                if (!game.isFowMode()) {
                                    DisasterWatchHelper.sendMessageInDisasterWatch(
                                            game,
                                            "\\> \"" + String.join("", structures) + "⁉️" + UnitEmojis.Blank
                                                    + "🇾​🇪​🇪​🇹‼️\"\n\\- _Demilitarized Zone_, to "
                                                    + p2.getRepresentation() + ", in " + game.getName() + ".");
                                }
                            }
                        }
                    }

                    game.purgeExplore(ogID);
                    CommanderUnlockCheckService.checkPlayer(player, "sol", "xxcha");
                    ButtonHelper.checkFleetAndCapacity(player, game, tile);
                }
            }
            case Constants.TOKEN -> {
                String token = exploreModel.getAttachmentId().orElse("");
                String tokenFilename = Mapper.getTokenID(token);
                if (tokenFilename == null || tile == null) {
                    message = new StringBuilder("Invalid token or tile");
                } else {
                    if (tile.isScar(game) && ("ionalpha".equalsIgnoreCase(token) || "gamma".equalsIgnoreCase(token))) {
                        message = new StringBuilder(
                                "Token `" + token + "` was not added to tile " + tile.getAutoCompleteName()
                                        + " because it is an entropic scar and cannot have wormholes.");
                    } else {
                        if ("ionalpha".equalsIgnoreCase(token)) {
                            message = new StringBuilder(player.getRepresentation()
                                    + ", please choose if the _Ion Storm_ is placed on its alpha or beta side.");
                            List<Button> buttonIon = new ArrayList<>();
                            buttonIon.add(Buttons.gray(
                                    "addIonStorm_alpha_" + tile.getPosition(),
                                    "Place an Alpha",
                                    MiscEmojis.CreussAlpha));
                            buttonIon.add(Buttons.green(
                                    "addIonStorm_beta_" + tile.getPosition(), "Place a Beta", MiscEmojis.CreussBeta));
                            MessageHelper.sendMessageToChannelWithButtons(
                                    player.getCorrectChannel(), message.toString(), buttonIon);
                        } else {
                            tile.addToken(tokenFilename, Constants.SPACE);
                            message = new StringBuilder(
                                    "Token `" + token + "` added to tile " + tile.getAutoCompleteName() + ".");
                            if ("gamma".equalsIgnoreCase(token) && "mallice".equals(planetID) && !game.isFowMode()) {
                                DisasterWatchHelper.sendMessageInDisasterWatch(
                                        game,
                                        player.getRepresentation() + " has explored Mallice in " + game.getName()
                                                + ", and discovered the _Gamma Wormhole_.");
                            }
                            if ("loststation".equalsIgnoreCase(token)) {
                                Helper.addTokenPlanetToTile(game, tile, "loststation");
                                game.clearPlanetsCache();
                                player.addPlanet("loststation");
                                player.refreshPlanet("loststation");
                                MessageHelper.sendMessageToChannel(
                                        event.getMessageChannel(),
                                        "Lost Station added to map, play area, and readied.");
                            }
                            DSHelperBreakthroughs.doLanefirBtCheck(game, player);
                            OblivionUnitHandler.doOblivionMechCheck(game, player);
                        }
                    }

                    if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                        Helper.addTokenPlanetToTile(game, tile, Constants.MIRAGE);
                        game.clearPlanetsCache();
                        message = new StringBuilder(
                                "Mirage added to map, added to your play area, readied, and explored!");
                        if (!game.isFowMode()
                                && FoWHelper.playerHasShipsInSystem(player, tile)
                                && tile.getPlanetUnitHolders().size() == 1) {
                            if (game.getRevealedPublicObjectives().containsKey("deep_space")
                                    && !game.didPlayerScoreThisAlready(player.getUserID(), "deep_space")) {
                                DisasterWatchHelper.sendMessageInDisasterWatch(
                                        game,
                                        player.getRepresentation() + " is attempting to _Explore Deep Space_ in "
                                                + game.getName() + ". Alas, alack, they have discovered Mirage!");
                            } else if (game.getRevealedPublicObjectives().containsKey("vast_territories")
                                    && !game.didPlayerScoreThisAlready(player.getUserID(), "vast_territories")) {
                                DisasterWatchHelper.sendMessageInDisasterWatch(
                                        game,
                                        player.getRepresentation() + " is attempting to _Patrol Vast Territories_ in "
                                                + game.getName() + ". Alas, alack, they have discovered Mirage!");
                            }
                        }
                        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
                        OblivionUnitHandler.doOblivionMechCheck(game, player);
                    }
                    game.purgeExplore(ogID);
                }
            }
        }
        MessageHelper.sendMessageToEventChannel(event, message.toString());
        message = new StringBuilder("Card has been discarded. Resolve effects manually.");
        String planetName = Mapper.getPlanet(planetID) == null
                ? "`error?`"
                : Mapper.getPlanet(planetID).getName();
        Button decline = Buttons.red("decline_explore", "Decline Exploration");
        String onPlanet = "On " + planetName;
        String fragMessage = player.getFactionEmojiOrColor() + " gained "
                + (exploreModel.getAlias().contains("supermassive") ? "a supermassive " : "a ") + "%s fragment.";
        if (RandomHelper.isOneInX(100)) {
            fragMessage = "%s\n" + ExploreEmojis.LinkGet;
        }

        // Specific Explore Handling
        switch (cardID) {
            case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9", "supermassivecultural" ->
                MessageHelper.sendMessageToEventChannel(event, String.format(fragMessage, ExploreEmojis.CFrag));
            case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7", "supermassivehazardous" ->
                MessageHelper.sendMessageToEventChannel(event, String.format(fragMessage, ExploreEmojis.HFrag));
            case "irf1", "irf2", "irf3", "irf4", "irf5", "supermassiveindustrial" ->
                MessageHelper.sendMessageToEventChannel(event, String.format(fragMessage, ExploreEmojis.IFrag));
            case "urf1", "urf2", "urf3", "supermassiveunknown" ->
                MessageHelper.sendMessageToEventChannel(event, String.format(fragMessage, ExploreEmojis.UFrag));
            case "ed1", "ed2" -> {
                message = new StringBuilder("_Enigmatic Device_ has been placed in play area.");
                player.addRelic(Constants.ENIGMATIC_DEVICE);
                game.purgeExplore(ogID);
                MessageHelper.sendMessageToEventChannel(event, message.toString());
            }
            case "lc1", "lc2" -> {
                ActionCardHelper.drawActionCards(player, 2);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew 2 action cards.");
                }
            }
            case "fiveac1", "fiveac2", "fiveac3" -> {
                ActionCardHelper.drawActionCards(player, 5);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew 5 action cards.");
                }
            }
            case "dv1", "dv2" -> {
                message = new StringBuilder("Drew a secret objective.");
                game.drawSecretObjective(player.getUserID());
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, message.toString());
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message.append(" Drew a second secret objective due to **Plausible Deniability**.");
                }
                if (player.hasAbility("multitasking")) {
                    LunariumAbilityHandler.offerFactionSheetCCButtons(game, player);
                }
                if (player.hasUnlockedBreakthrough("lunariumbt")) {
                    LunariumBreakthroughHandler.offerDarkSideExploitationButtons(game, player);
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
                MessageHelper.sendMessageToEventChannel(event, message.toString());
            }
            case "dw" -> {
                message = new StringBuilder("Drew a relic.");
                MessageHelper.sendMessageToEventChannel(event, message.toString());
                RelicHelper.drawRelicAndNotify(player, event, game);
            }
            case "ms1", "ms2" -> {
                if (player.getCommodities() == 0) {
                    int oldComm = player.getCommodities();
                    ButtonHelperStats.replenishComms(event, game, player, true);
                    message = new StringBuilder(
                            "Replenished Commodities (" + oldComm + "->" + player.getCommodities()
                                    + "). Reminder that this is optional, and that you may instead convert your existing commodities.");
                    MessageHelper.sendMessageToEventChannel(event, message.toString());
                } else {
                    message = new StringBuilder(
                            "You have " + player.getCommodities()
                                    + " commodities. You may convert them into trade goods, or replenish your commodities. Reminder that you can transact before resolving this if you have willing neighbors.");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            player.getFactionCheckerPrefix() + "mallice_convert_comm",
                            "Convert Commodities Into Trade Goods",
                            MiscEmojis.Wash));
                    buttons.add(Buttons.blue(
                            player.getFactionCheckerPrefix() + "resolveHarness",
                            "Replenish Commodities",
                            MiscEmojis.comm));
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(), message.toString(), buttons);
                }
            }
            case Constants.MIRAGE -> {
                String mirageID = Constants.MIRAGE;
                PlanetModel planetValue = Mapper.getPlanet(mirageID);
                if (planetValue == null) {
                    MessageHelper.sendMessageToEventChannel(event, "Invalid planet: " + mirageID);
                    return;
                }
                AddPlanetService.addPlanet(player, mirageID, game, null, false);
                PlanetService.refreshPlanet(player, mirageID);
                String exploreID = game.drawExplore(Constants.CULTURAL);
                if (exploreID == null) {
                    MessageHelper.sendMessageToEventChannel(
                            event, "Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }

                if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                                || game.getPhaseOfGame().contains("agenda"))
                        && player.hasUnit("saar_mech")
                        && !ButtonHelper.isLawInPlay(game, "articles_war")
                        && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
                    List<Button> saarButton = new ArrayList<>();
                    saarButton.add(Buttons.green(
                            "saarMechRes_" + "mirage",
                            "Pay 1 Trade Good for Mech on " + Helper.getPlanetRepresentation("mirage", game),
                            MiscEmojis.tg));
                    saarButton.add(Buttons.DECLINE);
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + " you may pay 1 trade good to place 1 "
                                    + " mech here.\n-# Do not do this prior to exploring; it is an \"after\", while exploring is a \"when\".",
                            saarButton);
                }

                String exploredMessage = player.getRepresentation() + " explored the planet " + ExploreEmojis.Cultural
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(mirageID, game)
                        + (tile == null ? "" : " in tile " + tile.getPosition() + ":");
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, player, game);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = new StringBuilder("Resolve _Functioning Base_:\n-# You currently have "
                        + StringHelper.pluralize(player.getTg(), "trade good") + ", "
                        + player.getCommoditiesRepresentation() + " " + commodityWord(player.getCommodities())
                        + ", and "
                        + StringHelper.pluralize(player.getActionCards().size(), "action card") + ".");
                Button getACButton = Buttons.green(
                        "comm_for_AC",
                        "Spend 1 Trade Good or 1 Commodity For 1 Action Card",
                        CardEmojis.getACEmoji(game));
                Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
            }
            case "freetech1", "freetech2", "freetech3" -> {
                game.setComponentAction(true);
                MessageHelper.sendMessageToChannelWithButton(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", please use the button to research a technology.",
                        Buttons.GET_A_FREE_TECH);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                int commod = Math.min(player.getCommodities(), 2);
                if (commod > 0) {
                    message = new StringBuilder("Resolve _Abandoned Warehouses_:\n-# You currently have "
                            + player.getCommoditiesRepresentation() + " " + commodityWord(commod) + ".");
                    Button convert = Buttons.green(
                            "convert_2_comms",
                            commod == 1
                                    ? "Convert 1 Commodity Into a Trade Good"
                                    : "Convert 2 Commodities Into Trade Goods",
                            MiscEmojis.Wash);
                    Button gain = Buttons.blue("gain_2_comms", "Gain 2 Commodities", MiscEmojis.comm);
                    List<Button> buttons = List.of(convert, gain);
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(), message.toString(), buttons);
                } else {
                    message = new StringBuilder(player.getFactionEmoji()
                            + " gained 2 commodities automatically due to having no commodities to convert.");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
                    ButtonHelperStats.gainComms(event, game, player, 2, true, true);
                }
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetID != null) {
                    Set<String> tokenList = ButtonHelper.getUnitHolderFromPlanetName(planetID, game)
                            .getTokenList();
                    boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
                    if (!containsDMZ) {
                        AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + planetID);
                        message = new StringBuilder(
                                player.getFactionEmoji() + ColorEmojis.getColorEmojiWithName(player.getColor())
                                        + UnitEmojis.infantry
                                        + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetID)
                                        + ", however this placement is __optional__.");
                        if (tile.isFracture()) {
                            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
                        }
                    } else {
                        message = new StringBuilder(
                                "Planet has the _Demilitarized Zone_ attached, so no infantry could be placed.");
                    }
                } else {
                    message = new StringBuilder("Tile was null, no infantry placed.");
                }
                MessageHelper.sendMessageToEventChannel(event, message.toString());
            }
            case "darkvisions" -> {
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "industrial");
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "hazardous");
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "cultural");
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, "frontier");

                List<Button> discardButtons = new ArrayList<>();
                discardButtons.add(Buttons.green(
                        "discardExploreTop_industrial", "Discard Top Industrial", ExploreEmojis.Industrial));
                discardButtons.add(
                        Buttons.red("discardExploreTop_hazardous", "Discard Top Hazardous", ExploreEmojis.Hazardous));
                discardButtons.add(
                        Buttons.blue("discardExploreTop_cultural", "Discard Top Cultural", ExploreEmojis.Cultural));
                discardButtons.add(
                        Buttons.gray("discardExploreTop_frontier", "Discard Top Frontier", ExploreEmojis.Frontier));
                discardButtons.add(Buttons.DONE_RESOLVING);
                message = new StringBuilder(player.getRepresentation()
                        + " you may use the buttons to discard the top of the exploration decks if you choose.");
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), message.toString(), discardButtons);
                List<Button> explorePlanets = new ArrayList<>();
                for (String planet : player.getPlanetsAllianceMode()) {
                    Planet unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder == null) {
                        continue;
                    }
                    Planet planetReal = unitHolder;
                    List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                    if (buttons != null && !buttons.isEmpty()) {
                        explorePlanets.addAll(buttons);
                    }
                }
                message = new StringBuilder("Click button to explore a planet after resolving any discards");
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), message.toString(), explorePlanets);
                message = new StringBuilder(
                        "Use this button to shuffle exploration decks once you're done with the rest");
                MessageHelper.sendMessageToChannelWithButton(
                        player.getCorrectChannel(),
                        message.toString(),
                        Buttons.red("shuffleExplores", "Shuffle Exploration Decks"));
            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = new StringBuilder(
                        player.getRepresentation() + " please resolve _Local Fabricators_:\n-# You currently have "
                                + StringHelper.pluralize(player.getTg(), "trade good") + " and "
                                + player.getCommoditiesRepresentation() + " "
                                + commodityWord(player.getCommodities()) + ".");
                Button getMechButton = Buttons.green(
                        "resolveLocalFab_" + planetID,
                        "Spend 1 Commodity or Trade Good for a Mech on " + planetName,
                        UnitEmojis.mech);
                Button getCommButton3 = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
            }
            case "fivetg1", "fivetg2", "fivetg3" -> {
                message = gainExploreTradeGoods(player, game, 5);
                CommanderUnlockCheckService.checkPlayer(player, "hacan");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
            }
            case "kel1", "kel2", "kel3", "ent", "minent", "majent" -> {
                int ccsToGain = 1;
                switch (cardID.toLowerCase()) {
                    case "minent" -> message = gainExploreTradeGoods(player, game, 1);
                    case "ent" -> message = gainExploreTradeGoods(player, game, 2);
                    case "majent" -> message = gainExploreTradeGoods(player, game, 3);
                    case "kel1", "kel2", "kel3" -> {
                        ccsToGain = 2;
                        message = new StringBuilder();
                    }
                    default -> message = new StringBuilder();
                }
                CommanderUnlockCheckService.checkPlayer(player, "hacan");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                if (!message.isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
                }
                message = new StringBuilder(player.getRepresentationUnfogged() + ", your current command tokens are "
                        + player.getCCRepresentation()
                        + ". Use buttons to gain " + StringHelper.pluralize(ccsToGain, "command token") + ".");
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = new StringBuilder(hazardIntro(player, game, planetID, "Expedition"));
                String planetRep = Helper.getPlanetRepresentation(planetID, game);
                List<Button> buttons = new ArrayList<>();
                for (boolean mech : payableHazardUnits(planetID, game, player)) {
                    buttons.add(
                            hazardUnitButton(mech, true, "resolveExpedition", planetID, "Ready " + planetRep, "There"));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, true);
            }
            case "folderspace", "folderspace2" -> {
                player.setFleetCC(player.getFleetCC() + 1);
                message = new StringBuilder(player.getRepresentation()
                        + ", please resolve _Folded Space_. You have been automatically granted 1 fleet command token. Your current command tokens are now "
                        + player.getCCRepresentation() + ".");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
                List<Button> buttons = new ArrayList<>();
                for (Tile tile2 : game.getTileMap().values()) {
                    if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, game) || tile == tile2) {
                        continue;
                    }
                    buttons.add(Buttons.green(
                            "argentHeroStep3_" + tile2.getPosition() + "_" + tile.getPosition(),
                            tile2.getRepresentationForButtons(game, player)));
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(), "Select a tile to move to with folded space.", buttons);
            }
            case "scorcheddepot" -> {
                message = new StringBuilder(hazardIntro(player, game, planetID, "Scorched Depot"));
                List<Button> buttons = new ArrayList<>();
                for (boolean mech : payableHazardUnits(planetID, game, player)) {
                    buttons.add(hazardUnitButton(mech, true, "resolveScorchedDepot_", planetID, "Resolve", "There"));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, false);
            }
            case "frln1", "frln2", "frln3" -> {
                message = new StringBuilder(player.getRepresentation() + ", please resolve _Freelancers_:\n-# "
                        + ButtonHelper.getListOfStuffAvailableToSpend(player, game, false));
                Button gainTG = Buttons.green("freelancersBuild_" + planetID, "Produce 1 Unit");
                List<Button> buttons = List.of(gainTG, decline);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = new StringBuilder(hazardIntro(player, game, planetID, "Core Mine"));
                List<Button> buttons = new ArrayList<>();
                for (boolean mech : payableHazardUnits(planetID, game, player)) {
                    buttons.add(
                            hazardUnitButton(mech, true, "resolveCoreMine", planetID, "Gain a Trade Good", onPlanet));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, true);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = new StringBuilder(player.getRepresentation()
                        + ", please resolve _Volatile Fuel Source_:\n-# Your current command tokens are "
                        + player.getCCRepresentation() + ". and you have "
                        + ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID));
                List<Button> buttons = new ArrayList<>();
                for (boolean mech : payableHazardUnits(planetID, game, player)) {
                    buttons.add(hazardUnitButton(
                            mech, true, "resolveVolatile", planetID, "Gain a Command Token", onPlanet));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, true);
            }
            case "warforgeruins" -> {
                message = new StringBuilder(hazardIntro(player, game, planetID, "War Forge Ruins"));
                List<Button> buttons = new ArrayList<>();
                for (boolean mech : payableHazardUnits(planetID, game, player)) {
                    buttons.add(
                            hazardUnitButton(mech, true, "resolveRuins", planetID + "_mech", "Place A Mech", onPlanet));
                    buttons.add(hazardUnitButton(
                            mech, false, "resolveRuins", planetID + "_2inf", "Place 2 Infantry", onPlanet));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, true);
            }
            case "seedyspaceport" -> {
                message = new StringBuilder(hazardIntro(player, game, planetID, "Seedy Spaceport"));
                List<Boolean> payableUnits = payableHazardUnits(planetID, game, player);
                List<Button> buttons = new ArrayList<>();
                for (Leader leader : player.getLeaders()) {
                    if (leader.getId().contains("agent")) {
                        String readyLeader =
                                "Ready " + Mapper.getLeader(leader.getId()).getName();
                        for (boolean mech : payableUnits) {
                            buttons.add(hazardUnitButton(
                                    mech,
                                    true,
                                    "resolveSeedySpace",
                                    leader.getId() + "_" + planetID,
                                    readyLeader,
                                    onPlanet));
                        }
                    }
                }
                for (boolean mech : payableUnits) {
                    buttons.add(hazardUnitButton(
                            mech, false, "resolveSeedySpace", "AC_" + planetID, "Draw 1 Action Card", onPlanet));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, true);
            }
            case "hiddenlaboratory" -> {
                MessageHelper.sendMessageToEventChannel(
                        event, "# Exploring frontier in this system due to finding the _Hidden Laboratory_.");
                AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
                ButtonHelper.resolveFullFrontierExplore(game, player, tile, event);
            }
            case "ancientshipyard" -> {
                List<String> colors = tile == null
                        ? Collections.emptyList()
                        : tile.getSpaceUnitHolder().getUnitColorsOnHolder();
                if (colors.isEmpty() || colors.contains(player.getColorID())) {
                    AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
                    MessageHelper.sendMessageToEventChannel(event, "Cruiser added to the system automatically.");
                } else {
                    MessageHelper.sendMessageToEventChannel(
                            event, "Someone else's ships were in the system, no cruiser added.");
                }
            }
            case "forgottentradestation" -> {
                int tgGain = tile == null ? 0 : tile.getPlanetUnitHolders().size();
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmojiOrColor() + " gained " + StringHelper.pluralize(tgGain, "trade good")
                                + " due to the _Forgotten Trade Station_ (" + oldTg + "->" + player.getTg() + ").");
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, tgGain);
            }
            case "starchartcultural",
                    "starchartindustrial",
                    "starcharthazardous",
                    "starchartfrontier",
                    "culturalartifact",
                    "hazardousartifact",
                    "industrialartifact",
                    "unknownartifact",
                    "economicboon",
                    "naturesboon",
                    "diplomaticboon",
                    "cosmicboon",
                    "mutagenindustrial",
                    "mutagenhazardous",
                    "mutagencultural",
                    "mutagenfrontier" -> {
                game.purgeExplore(ogID);
                player.addRelic(cardID);
                if (List.of("economicboon", "naturesboon", "diplomaticboon", "cosmicboon")
                        .contains(cardID)) {
                    LostLegaciesRelicHandler.initializeBoon(game, player, cardID, tile, planetID);
                }
                message =
                        new StringBuilder("Card has been added to play area.\nAdded as a relic (not actually a relic)");
                MessageHelper.sendMessageToEventChannel(event, message.toString());
            }
            case "suspiciouswreckage" -> {
                Button button = Buttons.green(
                        player.factionButtonChecker() + "resolveDiplomaticPressureStep1",
                        "Resolve Suspicious Wreckage");
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", button);
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", button);
            }
            case "freedomfighters" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "2 dd, 2 inf " + planetName);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Automatically placed 2 destroyers in the space area, and 2 infantry on the explored planet.");
            }
            case "distinguishedadmiral" -> {
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "You can use these buttons to redistribute or gain a command token, along with spending 2 influence potentially..",
                        buttons);
                Button deleteButton =
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Delete These Buttons");
                String message2 = player.getRepresentation(false, true) + " cc buttons";
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(), message2, List.of(Buttons.REDISTRIBUTE_CCs, deleteButton));
            }
            case "spatialdisplacement" ->
                LostLegciesExploreHandler.resolveSpatialDisplacement(event, game, player, tile);
            case "immediateassembly" -> {
                message = new StringBuilder(hazardIntro(player, game, planetID, "Immediate Assembly"));
                String prefix = player.factionButtonChecker() + "resolveImmediateAssembly";
                List<Button> buttons = new ArrayList<>();
                for (boolean mech : payableHazardUnits(planetID, game, player)) {
                    buttons.add(hazardUnitButton(
                            mech, true, prefix, planetID + "_production", "Gain PRODUCTION 3", onPlanet));
                    buttons.add(hazardUnitButton(mech, false, prefix, planetID + "_mech", "Place A Mech", onPlanet));
                }
                sendHazardExploreButtons(event, player, game, message.toString(), buttons, decline, false);
            }
            case "objectivedeliberations" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation()
                                + ", please resolve _Objective Deliberations_.\n-# You have " + player.getTg() + " "
                                + (player.getTg() > 1 ? "trade goods." : "trade good."),
                        LostLegciesExploreHandler.offerObjectiveDeliberationButtons(event, game, player));
            case "explorationenclave" ->
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation()
                                + ", please resolve _Exploration Enclave_.\n-# You have " + player.getTg() + " "
                                + (player.getTg() > 1 ? "trade goods." : "trade good."),
                        LostLegciesExploreHandler.getExplorationEnclaveButtons(event, game, player));
            case "synergisticresearch" -> {
                var breakthrough = player.getBreakthroughModel();
                if (!player.hasUnlockedBreakthrough(player.getBreakthroughID())) {
                    BreakthroughCommandHelper.unlockAllBreakthroughs(game, player);
                } else if (breakthrough != null && breakthrough.hasSynergy()) {
                    Set<String> seenTechs = new HashSet<>();

                    List<TechnologyModel> techs = new ArrayList<>(breakthrough.getSynergy().stream()
                            .filter(TechnologyType.mainFour::contains)
                            .flatMap(type ->
                                    ListTechService.getAllTechOfAType(game, type.toString(), player, false, false)
                                            .stream())
                            .filter(tech -> tech.getRequirements().orElse("").isEmpty())
                            .filter(tech -> seenTechs.add(tech.getAlias()))
                            .toList());

                    List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");

                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(),
                            player.getRepresentation() + ", please choose a technology.",
                            buttons);
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(),
                            player.getRepresentation()
                                    + ", you may gain 2 command tokens, since your breakthrough has no tech synergies.",
                            ButtonHelper.getGainCCButtons(player));
                }
            }
            case "disruptiveforces" -> {
                if (!FractureService.isFractureInPlay(game)) {
                    FractureService.spawnFracture(event, game);
                    FractureService.spawnIngressTokens(event, game, player, null);
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation() + ", please resolve _Disruptive Forces_.",
                        LostLegciesExploreHandler.getDisruptiveForcesButtons(event, game, player, tile));
            }
            case "celestialpath" -> {
                if (tile.hasPlayerCC(player)) {
                    String ccID = Mapper.getCCID(player.getColor());
                    tile.removeCC(ccID);
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getMessageChannel(),
                            player.getRepresentation()
                                    + " had their command token removed from the system due to _Celestial Path_. You main now gain a command token.",
                            ButtonHelper.getGainCCButtons(player));
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No token to remove in system.");
                }
                Tile homeSystem = player.getHomeSystemTile();
                homeSystem.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getRepresentation()
                                + " placed a frontier token in their home system due to _Celestial Path_.");
            }
        }
        RiftSetModeService.resolveExplore(ogID, player, game);
        FOWPlusService.resolveExplore(event, ogID, tile, planetID, player, game);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");

        if (player.hasAbility("fortune_seekers")
                && game.getStoredValue("fortuneSeekers").isEmpty()) {
            List<Button> gainComm = new ArrayList<>();
            gainComm.add(Buttons.green("gain_1_comms", "Gain 1 Comm", MiscEmojis.comm));
            gainComm.add(Buttons.DECLINE);
            String sb = player.getFactionEmoji() + " may use their **Fortune Seekers** ability\n"
                    + player.getRepresentationUnfogged()
                    + ", after resolving the exploration, you may use this button to get your commodity from your **Fortune Seekers** ability.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb, gainComm);
            game.setStoredValue("fortuneSeekers", "Used");
        }

        CommanderUnlockCheckService.checkPlayer(player, "kollecc", "bentor", "ghost", "kairn");
        if (player.getPlanets().contains(planetID)) {
            ButtonHelperAbilities.offerOrladinPlunderButtons(player, game, planetID);
        }

        if (player.getPlanets().contains(planetID) && player.hasAbility("planetary_reconfiguration")) {
            TaAbilityHandler.offerPlanetaryReconfigurationButtons(player, game, tile, planetID);
        }
        if (player.hasAbility("awaken")
                && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
                && player.getPlanetsAllianceMode().contains(planetID)
                && !game.isTwilightsFallMode()) {
            Button placeSleeper = Buttons.green(
                    "putSleeperOnPlanet_" + planetID,
                    "Put Sleeper on " + Helper.getPlanetRepresentation(planetID, game),
                    MiscEmojis.Sleeper);
            Button declineSleeper = Buttons.red("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(placeSleeper, declineSleeper);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
        }
        if (planetID != null
                && player.hasUnlockedBreakthrough("pharadnbt")
                && player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) > 0) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "hiddenVaultsProduce_" + planetID,
                    "Produce 1 Infantry on " + Helper.getPlanetRepresentation(planetID, game),
                    UnitEmojis.infantry));
            buttons.add(Buttons.DECLINE);

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may produce 1 infantry on "
                            + Helper.getPlanetRepresentation(planetID, game) + " with _Hidden Vaults_.",
                    buttons);
        }
    }

    public static String spendCommodityOrTg(Player player) {
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
            if (player.getPromissoryNotesInPlayArea().contains("dark_pact")) {
                return "commodity (though you may wish to manually spend a trade good instead because of _Dark Pact_)";
            }
            return "commodity";
        }
        if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            return "trade good";
        }
        return null;
    }

    private static StringBuilder gainExploreTradeGoods(Player player, Game game, int count) {
        StringBuilder message = new StringBuilder(
                "Gained " + count + MiscEmojis.getTGorNomadCoinEmoji(game) + " " + player.gainTG(count) + " ");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, count);
        return message;
    }

    private static String commodityWord(int count) {
        return count == 1 ? "commodity" : "commodities";
    }

    private static String hazardIntro(Player player, Game game, String planetID, String cardName) {
        return player.getRepresentation() + ", please resolve _" + cardName + "_:\n-# You have "
                + ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(game, player, planetID);
    }

    private static List<Boolean> payableHazardUnits(String planetID, Game game, Player player) {
        List<Boolean> units = new ArrayList<>();
        if (ExploreHelper.checkForMech(planetID, game, player)) {
            units.add(true);
        }
        if (ExploreHelper.checkForInf(planetID, game, player)) {
            units.add(false);
        }
        return units;
    }

    private static Button hazardUnitButton(
            boolean mech, boolean green, String idPrefix, String idSuffix, String action, String location) {
        String id = idPrefix + (mech ? "Mech_" : "Inf_") + idSuffix;
        String label = action + (mech ? " With A Mech " : " By Removing 1 Infantry ") + location;
        Button button = green ? Buttons.green(id, label) : Buttons.blue(id, label);
        return button.withEmoji((mech ? UnitEmojis.mech : UnitEmojis.infantry).asEmoji());
    }

    private static void sendHazardExploreButtons(
            GenericInteractionCreateEvent event,
            Player player,
            Game game,
            String message,
            List<Button> buttons,
            Button decline,
            boolean ironBreakthrough) {
        buttons.add(decline);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        if (ironBreakthrough && player.hasUnlockedBreakthrough("ironbt")) {
            IronBreakthroughHandler.sendIronBtMessage(player, game);
        }
    }

    public static void expFront(GenericInteractionCreateEvent event, Tile tile, Game game, Player player) {
        expFront(event, tile, game, player, false);
    }

    public static void expFront(
            GenericInteractionCreateEvent event, Tile tile, Game game, Player player, boolean force) {
        expFront(event, tile, game, player, force, null);
    }

    public static void expFront(
            GenericInteractionCreateEvent event, Tile tile, Game game, Player player, boolean force, String cardID) {
        expFront(event, tile, game, player, force, cardID, player.getCorrectChannel());
    }

    public static void expFrontAlreadyDone(
            GenericInteractionCreateEvent event, Tile tile, Game game, Player player, String cardID) {
        expFront(event, tile, game, player, false, cardID, event.getMessageChannel());
    }

    private static void expFront(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            Player player,
            boolean force,
            String cardID,
            MessageChannel replyChannel) {
        UnitHolder space = tile.getSpaceUnitHolder();
        String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
        boolean hasFrontierToken = space.getTokenList().contains(frontierFilename);
        boolean hasGhotiAnchorpointFrontier =
                MonumentsService.treatsSystemAsGhotiAnchorpointFrontier(game, player, tile);
        if (!hasFrontierToken && !hasGhotiAnchorpointFrontier && !force) {
            MessageHelper.sendMessageToChannel(replyChannel, "No frontier token in given system.");
            return;
        }
        if (hasFrontierToken && !force) {
            space.removeToken(frontierFilename);
        }
        cardID = cardID == null ? game.drawExplore(Constants.FRONTIER) : cardID;
        boolean isSlashForce = force && event instanceof SlashCommandInteractionEvent;
        String messageText = player.getRepresentationNoPing() + (isSlashForce ? " force" : "") + " explored the "
                + ExploreEmojis.Frontier + "frontier token in tile " + tile.getPosition() + ":";
        resolveExplore(event, cardID, tile, null, messageText, player, game);
        addAtsArmamentsCommodity(player, 1, replyChannel, player.getRepresentationNoPing());
    }

    public static void secondHalfOfExpInfo(
            List<String> types, GenericInteractionCreateEvent event, Player player, Game game, boolean overRide) {
        secondHalfOfExpInfo(types, event.getMessageChannel(), player, game, overRide, false);
    }

    public static void secondHalfOfExpInfo(
            List<String> types, MessageChannel channel, Player player, Game game, boolean overRide, boolean fullText) {
        if (!FOWPlusService.deckInfoAvailable(player, game) || !RiftSetModeService.deckInfoAvailable(player, game)) {
            return;
        }
        boolean isGM = player != null && game.getPlayersWithGMRole().contains(player);
        for (String currentType : types) {
            StringBuilder info = new StringBuilder();
            List<String> deck = game.getExploreDeck(currentType);
            Collections.sort(deck);
            int deckCount = deck.size();
            List<String> discard = game.getExploreDiscard(currentType);
            Collections.sort(discard);
            int discardCount = discard.size();

            info.append("__").append(currentType.substring(0, 1).toUpperCase()).append(currentType.substring(1));
            info.append(" exploration deck__ (")
                    .append(deckCount)
                    .append(" - ")
                    .append(drawChance(deckCount, 1))
                    .append(")\n");
            info.append(listNames(
                            deck,
                            true,
                            fullText,
                            ExploreEmojis.getTraitEmoji(currentType).toString()))
                    .append('\n');

            info.append("__").append(currentType.substring(0, 1).toUpperCase()).append(currentType.substring(1));
            info.append(" exploration discards__ (").append(discardCount).append(")\n");
            info.append(listNames(
                    discard,
                    false,
                    fullText,
                    ExploreEmojis.getTraitEmoji(currentType).toString()));

            if (types.indexOf(currentType) != types.size() - 1) {
                info.append("​"); // add a zero width space at the end to cement newlines between sets of explores
            }

            if (player == null || player.getSCs().isEmpty() || overRide || !game.isFowMode() || isGM) {
                MessageHelper.sendMessageToChannel(channel, info.toString());
            }
        }
        if (player != null
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && game.isFowMode()
                && !overRide
                && !isGM) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        }
    }

    private static String listNames(List<String> deck, boolean showPercents, boolean showFullText, String emoji) {
        if (deck.isEmpty()) {
            return "> There are no cards here.\n";
        }
        StringBuilder sb = new StringBuilder();

        Map<String, List<ExploreModel>> explores = deck.stream()
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(ExploreModel::getName));
        List<Map.Entry<String, List<ExploreModel>>> orderedExplores = explores.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> 15 - e.getValue().size()))
                .toList();
        int index = 1;
        for (Map.Entry<String, List<ExploreModel>> entry : orderedExplores) {
            String exploreName = entry.getKey();
            List<String> ids =
                    entry.getValue().stream().map(ExploreModel::getId).toList();

            if (showFullText) {
                sb.append(index)
                        .append("\\. ")
                        .append(emoji)
                        .append(" _")
                        .append(exploreName)
                        .append("_ (`")
                        .append(String.join("`, `", ids))
                        .append("`)");
                index++;
                if (showPercents && ids.size() > 1) {
                    sb.append(" - ").append(drawChance(deck.size(), ids.size()));
                }
                sb.append("\n> ").append(entry.getValue().getFirst().getText().replace("\n", "\n> "));
            } else {
                sb.append("1. ")
                        .append(emoji)
                        .append(" _")
                        .append(exploreName)
                        .append("_ (`")
                        .append(String.join("`, `", ids))
                        .append("`)");
                if (!"nothin"
                        .equalsIgnoreCase(
                                entry.getValue().getFirst().getAttachmentId().orElse("nothin"))) {
                    if (!"gw".equalsIgnoreCase(entry.getValue().getFirst().getAlias())
                            && !"frontier"
                                    .equalsIgnoreCase(
                                            entry.getValue().getFirst().getType())) {
                        sb.append(' ').append(ExploreEmojis.Chevrons);
                    }
                }
                if (showPercents && ids.size() > 1) {
                    sb.append(" - ").append(drawChance(deck.size(), ids.size()));
                }
            }
            sb.append('\n');
        }

        deck.stream()
                .filter(e -> Mapper.getExplore(e) == null)
                .forEach(cardID -> sb.append("> (").append(cardID).append(") null\n"));
        return sb.toString();
    }

    private static String drawChance(int deckCount, int copies) {
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);
        return formatPercent.format(deckCount == 0 ? 0.0 : (1.0 / deckCount) * copies);
    }
}
