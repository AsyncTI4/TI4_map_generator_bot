package ti4.discord.interactions.buttons.handlers.actioncards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.UnusedCommanderHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.BreakthroughModel;
import ti4.model.ExploreModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.planet.FlipTileService;
import ti4.service.planet.PlanetService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class ActionCardDeck2ButtonHandler {

    private static final String ALLIANCE_RIDER_CURRENT_ALLY = "allianceRiderCurrentAlly";
    private static final String ALLIANCE_RIDER_PURGED_ALLIES = "allianceRiderPurgedAllies";
    private static final String PROJECT_RIDER_SELECTED_CARDS_PREFIX = "projectRiderSelectedCards_";
    private static final String PROJECT_RIDER_PICK_PREFIX = "projectRiderPickFromDiscard_";
    private static final int PROJECT_RIDER_MAX_SELECTIONS = 2;

    @ButtonHandler("resolveOracle")
    public static void resolveOracle(Player player, Game game, ButtonInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        game.peekAtAllUnrevealedPublicObjectives(player);

        for (String objectiveId : game.getPublicObjectives1Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String objectiveId : game.getPublicObjectives2Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String secretId : game.peekAtSecrets(5)) {
            embeds.add(Mapper.getSecretObjective(secretId).getRepresentationEmbed(true));
        }

        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                "Showing all unrevealed public objectives and the top 5 secret objectives from the deck.",
                embeds);
        Collections.shuffle(game.getSecretObjectives());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Sent _Oracle_ results to " + player.getFactionEmojiOrColor()
                        + " `#cards-info` thread and shuffled the secret objective deck.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveDataArchive")
    public static void resolveDataArchive(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game, true);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use buttons to explore planet #1.",
                buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use buttons to explore planet #2 (different planet from #1).",
                buttons);
        if (game.getPhaseOfGame().toLowerCase().contains("agenda")) {
            for (String planet : player.getPlanets()) {
                player.exhaustPlanet(planet);
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getFactionEmoji() + " exhausted all planets.");
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveDefenseInstallation")
    public static void resolveDefenseInstallation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = player.getPlanets().stream()
                .map(planet -> Buttons.green(
                        "defenseInstallationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList();
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to put 1 PDS on.",
                buttons);
    }

    @ButtonHandler("defenseInstallationStep2_")
    public static void resolveDefenseInstallationStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "pds " + planet);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " put 1 PDS on " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
    }

    @ButtonHandler("resolveDefenseRider")
    public static void resolveDefenseRider(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendDefenseRiderButtons(player, game, 2);
    }

    @ButtonHandler("resolveDefenseRiderStep2_")
    public static void resolveDefenseRiderStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveDefenseRiderStep2_", "");
        String[] parts = payload.split("_", 2);
        if (parts.length < 2) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Defense Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remaining;
        try {
            remaining = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Defense Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planet = parts[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "pds " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " put 1 PDS on " + Helper.getPlanetRepresentation(planet, game)
                        + " with _Defense Rider_.");
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) {
            sendDefenseRiderButtons(player, game, remaining - 1);
        }
    }

    @ButtonHandler("resolveBoardingParty")
    public static void resolveBoardingParty(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + ", use the buttons to place the 1 ship you killed under 5 cost. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("resolveRapidFulfillment")
    public static void resolveRapidFulfillment(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String type = "sling";
        String pos = game.getActiveSystem();
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation()
                + ", use the buttons to place up to 2 ships that have a combined cost of 3 or less.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("resolveReinforcements")
    public static void resolveReinforcements(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " could not resolve _Reinforcements_ because there is no active system.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 fighter");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed 2 fighters in " + tile.getRepresentation()
                        + " with _Reinforcements_.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveOvertime")
    public static void resolveOvertime(Player player, Game game, ButtonInteractionEvent event) {
        if (player.getTg() < 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " needs at least 3 trade goods to resolve _Overtime_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<Button> buttons = getOvertimeButtons(game, player, 2);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no exhausted components to ready with _Overtime_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        String spendMessage =
                player.getRepresentation() + " spent 3 trade goods " + player.gainTG(-3) + " to resolve _Overtime_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), spendMessage);
        sendOvertimeButtons(player, game, 2);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveOvertimeStep2_")
    public static void resolveOvertimeStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveOvertimeStep2_", "");
        String[] parts = payload.split("_", 3);
        if (parts.length < 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Overtime_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remaining;
        try {
            remaining = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Overtime_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String readyItem = readyOvertimeComponent(player, game, parts[0], parts[2]);
        if (readyItem == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " could not ready that component with _Overtime_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " readied " + readyItem + " using _Overtime_.");
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) {
            sendOvertimeButtons(player, game, remaining - 1);
        }
    }

    @ButtonHandler("resolveSisterShip")
    public static void resolveSisterShip(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);

        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                Button tileButton = Buttons.green(buttonID, tile.getRepresentationForButtons(game, player));
                buttons.add(tileButton);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose which system you wish to produce a ship in. The bot will not know that it is reduced cost and limited to a specific ship type, but you know that. ",
                buttons);
    }

    private static void sendOvertimeButtons(Player player, Game game, int remainingComponents) {
        List<Button> buttons = new ArrayList<>(getOvertimeButtons(game, player, remainingComponents));
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));

        String message = remainingComponents > 1
                ? player.getRepresentationUnfogged() + ", choose a component to ready with _Overtime_."
                : player.getRepresentationUnfogged() + ", you may ready 1 more component with _Overtime_.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static void sendDefenseRiderButtons(Player player, Game game, int remainingPds) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            buttons.add(Buttons.green(
                    "resolveDefenseRiderStep2_" + remainingPds + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no planets available for _Defense Rider_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));

        String message = remainingPds > 1
                ? player.getRepresentationUnfogged() + ", choose a planet to place a PDS on with _Defense Rider_."
                : player.getRepresentationUnfogged() + ", you may place 1 more PDS with _Defense Rider_.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static List<Button> getOvertimeButtons(Game game, Player player, int remainingComponents) {
        List<Button> buttons = new ArrayList<>();
        String prefix = "resolveOvertimeStep2_";

        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green(
                    prefix + "planet_" + remainingComponents + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }

        for (String breakthrough : player.getBreakthroughIDs()) {
            if (player.isBreakthroughExhausted(breakthrough) && player.isBreakthroughUnlocked(breakthrough)) {
                BreakthroughModel breakthroughModel = Mapper.getBreakthrough(breakthrough);
                buttons.add(Buttons.blue(
                        prefix + "breakthrough_" + remainingComponents + "_" + breakthrough,
                        "Ready " + breakthroughModel.getName() + " Breakthrough",
                        player.getFactionEmoji()));
            }
        }

        for (Leader leader : player.getLeaders()) {
            if (leader.isExhausted()) {
                String leaderName =
                        leader.getLeaderModel().map(LeaderModel::getName).orElse(leader.getId());
                buttons.add(Buttons.gray(
                        prefix + "leader_" + remainingComponents + "_" + leader.getId(),
                        "Ready " + leaderName + (Constants.AGENT.equals(leader.getType()) ? " Agent" : " Leader"),
                        LeaderEmojis.getLeaderTypeEmoji(leader.getType())));
            }
        }

        for (String relic : player.getExhaustedRelics()) {
            RelicModel relicModel = Mapper.getRelic(relic);
            buttons.add(Buttons.red(
                    prefix + "relic_" + remainingComponents + "_" + relic, "Ready " + relicModel.getName() + " Relic"));
        }

        for (String tech : player.getExhaustedTechs()) {
            TechnologyModel techModel = Mapper.getTech(tech);
            buttons.add(Buttons.green(
                    prefix + "tech_" + remainingComponents + "_" + tech,
                    "Ready " + techModel.getName() + " Technology",
                    techModel.getCondensedReqsEmojis(true)));
        }

        for (String planet : player.getExhaustedPlanetsAbilities()) {
            PlanetModel planetModel = Mapper.getPlanet(planet);
            buttons.add(Buttons.blue(
                    prefix + "legendary_" + remainingComponents + "_" + planet,
                    "Ready " + planetModel.getName() + " Ability",
                    MiscEmojis.LegendaryPlanet));
        }

        return buttons;
    }

    private static String readyOvertimeComponent(Player player, Game game, String type, String componentId) {
        return switch (type) {
            case "planet" -> {
                if (!player.getExhaustedPlanets().remove(componentId)) {
                    yield null;
                }
                yield Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(componentId, game);
            }
            case "breakthrough" -> {
                if (!player.isBreakthroughExhausted(componentId) || !player.isBreakthroughUnlocked(componentId)) {
                    yield null;
                }
                player.setBreakthroughExhausted(componentId, false);
                yield player.getBreakthroughModel(componentId).getNameRepresentation();
            }
            case "leader" -> {
                Leader leader = player.getLeaderByID(componentId).orElse(null);
                if (leader == null || !leader.isExhausted()) {
                    yield null;
                }
                RefreshLeaderService.refreshLeader(player, leader, game);
                yield leader.getLeaderModel()
                        .map(LeaderModel::getNameRepresentation)
                        .orElse(componentId);
            }
            case "relic" -> {
                if (!player.getExhaustedRelics().contains(componentId)) {
                    yield null;
                }
                player.removeExhaustedRelic(componentId);
                RelicModel relicModel = Mapper.getRelic(componentId);
                yield relicModel == null ? componentId : relicModel.getNameRepresentation();
            }
            case "tech" -> {
                if (!player.getExhaustedTechs().contains(componentId)) {
                    yield null;
                }
                player.refreshTech(componentId);
                TechnologyModel techModel = Mapper.getTech(componentId);
                yield techModel == null ? componentId : techModel.getNameRepresentation();
            }
            case "legendary" -> {
                if (!player.getExhaustedPlanetsAbilities().remove(componentId)) {
                    yield null;
                }
                PlanetModel planetModel = Mapper.getPlanet(componentId);
                yield planetModel == null ? componentId : planetModel.getLegendaryNameRepresentation();
            }
            default -> null;
        };
    }

    @ButtonHandler("resolveChainReaction")
    public static void resolveChainReaction(ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Effect changed, so old implementation was deprecated. Roll manually.");
        //        StringBuilder msg = new StringBuilder("The _Chain Reaction_ rolled: ");
        //        int currentRequirement = 7;
        //        Die die;
        //        while ((die = new Die(currentRequirement)).isSuccess()) {
        //            hits++;
        //            currentRequirement++;
        //            msg.append(die.getResult()).append(" :boom: ");
        //        }
        //        msg.append(die.getResult());
        //        List<Button> buttons = new ArrayList<>();
        //        if (game.getActiveSystem() != null && !game.getActiveSystem().isEmpty()) {
        //            buttons.add(Buttons.red("getDamageButtons_" + game.getActiveSystem() + "_" + "combat", "Assign
        // Hit" + (hits == 1 ? "" : "s")));
        //        }
    }

    @ButtonHandler("resolveHostileWorld")
    public static void resolveHostileWorld(Player player, Game game, ButtonInteractionEvent event) {
        List<DiceHelper.Die> rolls = DiceHelper.rollDice(6, 3);
        int hits = DiceHelper.countSuccesses(rolls);
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" rolled for _Hostile World_.\n")
                .append(DiceHelper.formatDiceOutput(rolls));
        String loreQuip = getHostileWorldLoreQuip(hits);
        if (loreQuip != null) {
            message.append("\n").append(loreQuip);
        }
        if (hits > 0) {
            String activeSystem = game.getActiveSystem();
            if (activeSystem == null || activeSystem.isEmpty()) {
                message.append("\nCould not find the active system, so assign the hits manually.");
            } else {
                List<Button> buttons = List.of(Buttons.red(
                        "getDamageButtons_" + activeSystem + "_groundcombat", "Assign Hit" + (hits == 1 ? "" : "s")));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
                event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static String getHostileWorldLoreQuip(int hits) {
        return switch (hits) {
            case 0 -> "\"Must've been the wind.\" - Guard, _Skyrim_.";
            case 1 -> "\"Get off my lawn.\" — Walt Kowalski, _Gran Torino_";
            case 2 -> "\"Watch out for that first step, it's a doozy!\" - Ned Ryerson, _Groundhog Day_";
            case 3 ->
                "\"One does not simply walk into Mordor.\" - Boromir, _The Lord of the Rings: The Fellowship of the Ring_";
            default -> null;
        };
    }

    @ButtonHandler("resolveFlawlessStrategy")
    public static void resolveFlawlessStrategy(Player player, ButtonInteractionEvent event) {
        List<Button> scButtons = new ArrayList<>();
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (player.getSCs().contains(2)) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (player.getSCs().contains(3)) {
            scButtons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (player.getSCs().contains(4)) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (player.getSCs().contains(5)) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (player.getSCs().contains(6)) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (player.getSCs().contains(7)) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (player.getSCs().contains(8)) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentation() + ", use the buttons to resolve.", scButtons);
    }

    @ButtonHandler("resolveIntrigue")
    public static void resolveIntrigue(Player player, Game game, ButtonInteractionEvent event) {
        AgendaHelper.drawAgenda(2, true, game, player);
        AgendaHelper.drawAgenda(2, game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveAncientTradeRoutes")
    public static void resolveAncientTradeRoutes(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        player.setCommodities(player.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, player, 2);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " gained 2 commodities.");
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("ancientTradeRoutesStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "ancientTradeRoutesStep2_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Don't Give Commodities"));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the player you wish to give 2 commodities to.",
                buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose a __different__ player you wish to give 2 commodities to.",
                buttons);
    }

    @ButtonHandler("resolveArmsDeal")
    public static void resolveArmsDeal(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers(true).contains(p2)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("armsDealStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "armsDealStep2_" + p2.getFaction(), p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which neighbor gets 1 cruiser and 1 destroyer.",
                buttons);
    }

    @ButtonHandler("resolveCache")
    public static void resolveCache(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = player.getReadiedPlanets().stream()
                .map(planet -> game.getPlanetsInfo().get(planet))
                .filter(Objects::nonNull)
                .filter(planet -> !planet.isHomePlanet(game))
                .map(planet -> Buttons.green(
                        "resolveCacheStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game) + " ("
                                + planet.getSumResourcesInfluence() + " TG)"))
                .toList();

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no ready non-home planets to exhaust for _Cache_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a non-home planet to exhaust for _Cache_.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveCacheStep2_")
    public static void resolveCacheStep2(Player player, Game game, String buttonID) {
        String planetName = buttonID.split("_")[1];
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || !player.hasPlanet(planetName) || planet.isHomePlanet(game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Cache_ for that planet.");
            return;
        }
        if (!player.hasPlanetReady(planetName)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    Helper.getPlanetRepresentation(planetName, game) + " is already exhausted.");
            return;
        }

        int tgGain = planet.getSumResourcesInfluence();
        player.exhaustPlanet(planetName);
        player.gainTG(tgGain);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, tgGain);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game)
                        + " and gained " + tgGain + " trade good" + (tgGain == 1 ? "" : "s") + " from _Cache_.");
    }

    @ButtonHandler("resolveFreedomFighters")
    public static void resolveFreedomFighters(Player player, Game game, ButtonInteractionEvent event) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || activeSystem.getPlanetUnitHolders().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", _Freedom Fighters_ requires an active system with planets.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>(activeSystem.getPlanetUnitHolders().stream()
                .map(planet -> Buttons.green(
                        "resolveFreedomFightersStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList());
        buttons.add(Buttons.red("deleteButtons", "Done placing infantry"));

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", use the buttons to place up to 1 infantry from reinforcements on each planet in the active system.",
                buttons);
    }

    @ButtonHandler("resolveFreedomFightersStep2_")
    public static void resolveFreedomFightersStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("resolveFreedomFightersStep2_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Freedom Fighters_ for that planet.");
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Freedom Fighters_.");
    }

    @ButtonHandler("resolveLiberation")
    public static void resolveLiberation(Player player, Game game, ButtonInteractionEvent event) {
        Tile activeSystem = game.getTileByPosition(game.getCurrentActiveSystem());
        Stream<String> liberationPlanets = activeSystem == null
                ? player.getPlanets().stream()
                : activeSystem.getPlanetUnitHolders().stream().map(Planet::getName);
        List<Button> buttons = liberationPlanets
                .map(planet ->
                        Buttons.green("resolveLiberationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no planets to target with _Liberation_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose the planet you just gained for _Liberation_.",
                buttons);
    }

    @ButtonHandler("resolveLiberationStep2_")
    public static void resolveLiberationStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("resolveLiberationStep2_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null || !player.hasPlanet(planet)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Liberation_ for that planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);
        PlanetService.refreshPlanet(player, planet);

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(
                game, ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        if (buttons != null && !buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + ", please press the button to explore "
                            + Helper.getPlanetRepresentation(planet, game) + ".",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed 2 infantry on and readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
                        + " via _Liberation_.");
    }

    @ButtonHandler("resolveRefugees")
    public static void resolveRefugees(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || getRefugeesPlanets(game, p2).isEmpty()) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("resolveRefugeesStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "resolveRefugeesStep2_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                buttons.add(button.withEmoji(Emoji.fromFormatted(factionEmojiString)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentation() + " has no valid _Refugees_ targets.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Done"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which player's planets to place infantry on.",
                buttons);
    }

    @ButtonHandler("resolveRefugeesStep2_")
    public static void resolveRefugeesStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("resolveRefugeesStep2_", ""));
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find the selected player for _Refugees_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        sendRefugeesPlanetButtons(player, game, target, 2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveRefugeesStep3_")
    public static void resolveRefugeesStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.replace("resolveRefugeesStep3_", "").split("_", 3);
        if (payload.length < 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Refugees_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(payload[0]);
        int infantryRemaining = Integer.parseInt(payload[1]);
        String planet = payload[2];
        Tile tile = game.getTileFromPlanet(planet);
        if (target == null || tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Refugees_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Refugees_.");

        ButtonHelper.deleteMessage(event);
        if (infantryRemaining > 1) {
            sendRefugeesPlanetButtons(player, game, target, infantryRemaining - 1);
        }
    }

    private static void sendRefugeesPlanetButtons(Player player, Game game, Player target, int infantryRemaining) {
        List<String> eligiblePlanets = getRefugeesPlanets(game, target);
        if (eligiblePlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not find any eligible planets for _Refugees_ outside " + target.getRepresentationUnfogged()
                            + "'s home system.");
            return;
        }

        List<Button> buttons = new ArrayList<>(eligiblePlanets.stream()
                .map(planet -> Buttons.green(
                        "resolveRefugeesStep3_" + target.getFaction() + "_" + infantryRemaining + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList());
        buttons.add(Buttons.red("deleteButtons", "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose where to place "
                        + (infantryRemaining == 1 ? "your last infantry" : "an infantry")
                        + " for _Refugees_ on " + target.getRepresentationUnfogged() + "'s worlds.",
                buttons);
    }

    private static List<String> getRefugeesPlanets(Game game, Player target) {
        Set<String> planets = new HashSet<>();
        Tile homeSystem = target.getHomeSystemTile();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null || tile == homeSystem) {
                continue;
            }
            for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
                if (ButtonHelper.getPlayersWithUnitsOnAPlanet(game, unitHolder).contains(target)) {
                    planets.add(unitHolder.getName());
                }
            }
        }
        List<String> eligiblePlanets = new ArrayList<>(planets);
        Collections.sort(eligiblePlanets);
        return eligiblePlanets;
    }

    @ButtonHandler("resolveSimulacrum")
    public static void resolveSimulacrum(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            for (String leaderID : p2.getLeaderIDs()) {
                var leaderModel = Mapper.getLeader(leaderID);
                if (leaderModel == null || !"agent".equals(leaderModel.getType())) {
                    continue;
                }

                Leader agent = p2.getLeader(leaderID).orElse(null);
                if (agent == null) {
                    continue;
                }

                String buttonPrefix = agent.isExhausted() ? "Ready " : "Exhaust ";
                buttons.add(Buttons.gray(
                        "simulacrumToggleAgent_" + p2.getFaction() + "_" + leaderID,
                        buttonPrefix + leaderModel.getName()));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose an agent to ready or exhaust.",
                buttons);
    }

    @ButtonHandler("simulacrumToggleAgent_")
    public static void resolveSimulacrumToggleAgent(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] buttonParts = buttonID.split("_", 3);
        if (buttonParts.length < 3) {
            return;
        }
        String faction = buttonParts[1];
        String agentID = buttonParts[2];
        Player agentOwner = game.getPlayerFromColorOrFaction(faction);
        if (agentOwner == null) {
            return;
        }

        Leader agent = agentOwner.getLeader(agentID).orElse(null);
        if (agent == null) {
            return;
        }

        String ownerName = Mapper.getLeader(agentID).getName() + " (" + agentOwner.getRepresentationNoPing() + ")";
        if (agent.isExhausted()) {
            RefreshLeaderService.refreshLeader(agentOwner, agent, game);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " readied " + ownerName + " using _Simulacrum_.");
        } else {
            ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " exhausted " + ownerName + " using _Simulacrum_.");
        }

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("armsDealStep2_")
    public static void resolveArmsDealStep2(Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(p2, game, "cruiser", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't Place"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", please choose where you wish to place the _Arms Deal_ cruiser.",
                buttons);
        buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(p2, game, "destroyer", "placeOneNDone_skipbuild"));
        buttons.add(Buttons.red("deleteButtons", "Don't Place"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", please choose where you wish to place the _Arms Deal_ destroyer.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("ancientTradeRoutesStep2_")
    public static void resolveAncientTradeRoutesStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) return;
        p2.setCommodities(p2.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, p2, 2);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getFactionEmoji() + " gained 2 commodities due to _Ancient Trade Routes_ and may transact with "
                        + player.getFactionEmojiOrColor() + " for this turn.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        player.gainCommodities(2);
        List<String> types = new ArrayList<>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged()).append(" gained 2 commodities and:");
        for (String type : types) {
            String cardId = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardId);
            String cardType = card.getResolution();
            sb.append("\nRevealed _")
                    .append(card.getName())
                    .append("_ from the top of the ")
                    .append(type)
                    .append(" deck and ");
            if (Constants.FRAGMENT.equalsIgnoreCase(cardType)) {
                sb.append("gained it.");
                player.addFragment(cardId);
                game.purgeExplore(cardId);
            } else {
                sb.append("discarded it.");
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("innovation")
    public static void resolveInnovation(Player player, Game game, ButtonInteractionEvent event) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                player.refreshPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmoji() + " readied every planet with a technology specialty.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveReconstruction")
    public static void resolveReconstruction(Player player, Game game, ButtonInteractionEvent event) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (activeSystem == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " could not resolve _Reconstruction_ because there is no active system.");
            return;
        }

        List<Button> buttons = getReconstructionPlanetButtons(game, player, activeSystem);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no planets they control in the active system for _Reconstruction_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a planet you control in "
                        + activeSystem.getRepresentationForButtons(game, player) + " for _Reconstruction_.",
                buttons);
    }

    @ButtonHandler(value = "ubiquity", save = false)
    public static void resolveUbiquity(Player player, Game game, ButtonInteractionEvent event) {
        List<TechnologyModel> techs = getUbiquityTechs(game, player);
        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible technologies to gain with _Ubiquity_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", spend 3 resources and choose a technology to gain with _Ubiquity_.",
                ListTechService.getTechButtons(techs, player, "free"));

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use these buttons to pay the 3 resources for _Ubiquity_.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("reconstructionStep2_")
    public static void resolveReconstructionStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("reconstructionStep2_", "");
        Planet planetHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (planetHolder == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " could not find that planet for _Reconstruction_.");
            return;
        }

        PlanetService.refreshPlanet(player, planet);
        List<Button> buttons = getReconstructionTraitButtons(game, planetHolder);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " readied "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
                            + " with _Reconstruction_. It has no planet trait, so the exploration deck portion cannot occur.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
                        + " with _Reconstruction_. Choose the matching exploration deck to reveal from.",
                buttons);
    }

    @ButtonHandler("reconstructionStep3_")
    public static void resolveReconstructionStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String info = buttonID.replace("reconstructionStep3_", "");
        int splitIndex = info.lastIndexOf('_');
        if (splitIndex < 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentation() + " could not resolve _Reconstruction_.");
            return;
        }
        String planet = info.substring(0, splitIndex);
        String trait = info.substring(splitIndex + 1);
        Tile tile = game.getTileFromPlanet(planet);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " could not find that planet's system for _Reconstruction_.");
            return;
        }

        StringBuilder sb = new StringBuilder(player.getRepresentation())
                .append(" revealed the top 3 cards of the ")
                .append(StringUtils.capitalize(trait))
                .append(" exploration deck for _Reconstruction_ on ")
                .append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game))
                .append(':');

        for (int x = 0; x < 3; x++) {
            String cardId = game.drawExplore(trait);
            if (cardId == null) {
                sb.append("\n> No more cards were available in that deck.");
                break;
            }

            ExploreModel explore = Mapper.getExplore(cardId);
            if (explore == null) {
                continue;
            }

            sb.append("\n> Revealed ").append(explore.getNameRepresentation());
            if (Constants.ATTACH.equalsIgnoreCase(explore.getResolution())) {
                sb.append(" and resolved the attachment.");
                String messageText = player.getRepresentation() + " resolved an attachment with _Reconstruction_ on "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game) + ":";
                ExploreService.resolveExplore(event, cardId, tile, planet, messageText, player, game);
            } else {
                sb.append(" and discarded it.");
            }
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
    }

    private static List<Button> getReconstructionPlanetButtons(Game game, Player player, Tile activeSystem) {
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            if (player.getPlanets().contains(planet.getName())) {
                buttons.add(Buttons.gray(
                        "reconstructionStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }

    private static List<Button> getReconstructionTraitButtons(Game game, Planet planet) {
        String planetId = planet.getName();
        List<Button> buttons = new ArrayList<>();
        Set<String> explorationTraits = new HashSet<>(planet.getPlanetTypes());
        for (String trait : explorationTraits) {
            if ("cultural".equals(trait)) {
                buttons.add(Buttons.blue(
                        "reconstructionStep3_" + planetId + "_" + trait, "Cultural", ExploreEmojis.Cultural));
            }
            if ("industrial".equals(trait)) {
                buttons.add(Buttons.green(
                        "reconstructionStep3_" + planetId + "_" + trait, "Industrial", ExploreEmojis.Industrial));
            }
            if ("hazardous".equals(trait)) {
                buttons.add(Buttons.red(
                        "reconstructionStep3_" + planetId + "_" + trait, "Hazardous", ExploreEmojis.Hazardous));
            }
        }
        return buttons;
    }

    @ButtonHandler("resolveOpportunists")
    public static void resolveOpportunists(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find the active system for _Opportunists_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "2 dd, cr");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " added 1 neutral cruiser and 2 neutral destroyers to "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    @ButtonHandler("resolveArbitration")
    public static void resolveArbitration(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getArbitrationOwnerButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible planets for _Arbitration_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose which player's planets to inspect for _Arbitration_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arbitrationOwner_")
    public static void resolveArbitrationOwner(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String owner = buttonID.replace("arbitrationOwner_", "");
        List<Button> buttons = getArbitrationPlanetButtons(game, player, owner);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible planets for _Arbitration_ in that category.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a non-home planet with ground forces for _Arbitration_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arbitrationPlanet_")
    public static void resolveArbitrationPlanet(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] buttonParts = buttonID.split("_", 3);
        if (buttonParts.length < 3) {
            return;
        }
        String planet = buttonParts[2];
        List<Button> buttons = getArbitrationTargetButtons(game, player, planet);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible target players for _Arbitration_ on "
                            + Helper.getPlanetRepresentation(planet, game) + ".");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose who will place 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet, game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arbitrationTarget_")
    public static void resolveArbitrationTarget(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String arbitrationTarget = buttonID.replace("arbitrationTarget_", "");
        int lastSeparator = arbitrationTarget.lastIndexOf('_');
        if (lastSeparator < 0) {
            return;
        }

        String planet = arbitrationTarget.substring(0, lastSeparator);
        String faction = arbitrationTarget.substring(lastSeparator + 1);
        Player target = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileFromPlanet(planet);
        if (target == null || tile == null || target == player || target.hasPlanet(planet)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Arbitration_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, target.getColor(), "inf " + planet);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);

        String planetRepresentation = Helper.getPlanetRepresentation(planet, game);
        String message = player.getRepresentation() + " chose " + target.getRepresentationNoPing()
                + " to place 1 infantry into coexistence on " + planetRepresentation + " using _Arbitration_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        if (!Objects.equals(player.getCorrectChannel(), target.getCorrectChannel())) {
            MessageHelper.sendMessageToChannel(target.getCorrectChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<TechnologyModel> getUbiquityTechs(Game game, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        Set<String> seenTechs = new HashSet<>();
        for (TechnologyType techType : TechnologyType.mainFive) {
            for (TechnologyModel tech : ListTechService.getAllTechOfAType(game, techType.toString(), player)) {
                if (seenTechs.add(tech.getAlias()) && isTechOwnedByEnoughPlayers(game, tech)) {
                    techs.add(tech);
                }
            }
        }
        return techs;
    }

    private static List<Button> getArbitrationOwnerButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player owner : game.getRealPlayers()) {
            if (owner == player
                    || getArbitrationPlanetButtons(game, player, owner.getFaction())
                            .isEmpty()) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "arbitrationOwner_" + owner.getFaction(), owner.getColor()));
            } else {
                Button button = Buttons.gray(
                        player.factionButtonChecker() + "arbitrationOwner_" + owner.getFaction(),
                        owner.getFactionModel().getShortName());
                buttons.add(button.withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
            }
        }

        if (!getArbitrationPlanetButtons(game, player, "neutralUnits").isEmpty()) {
            buttons.add(Buttons.gray(player.factionButtonChecker() + "arbitrationOwner_neutralUnits", "Neutral Units"));
        }
        return buttons;
    }

    private static List<Button> getArbitrationPlanetButtons(Game game, Player player, String owner) {
        List<String> planets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isHomeSystem(game)) {
                continue;
            }
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!isArbitrationPlanetInCategory(game, planet, owner)
                        || getArbitrationTargetButtons(game, player, planet.getName())
                                .isEmpty()) {
                    continue;
                }
                planets.add(planet.getName());
            }
        }

        Collections.sort(planets);
        return planets.stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "arbitrationPlanet_" + owner + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static boolean isArbitrationPlanetInCategory(Game game, Planet planet, String owner) {
        if ("neutralUnits".equals(owner)) {
            return planet.hasGroundForces(game.getNeutral());
        }
        Player planetOwner = game.getPlanetOwner(planet.getName());
        return planetOwner != null && owner.equals(planetOwner.getFaction()) && planet.hasGroundForces(game);
    }

    private static List<Button> getArbitrationTargetButtons(Game game, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player || target.hasPlanet(planet)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "arbitrationTarget_" + planet + "_" + target.getFaction(),
                        target.getColor()));
            } else {
                Button button = Buttons.gray(
                        player.factionButtonChecker() + "arbitrationTarget_" + planet + "_" + target.getFaction(),
                        target.getFactionModel().getShortName());
                String factionEmojiString = target.getFactionEmoji();
                buttons.add(button.withEmoji(Emoji.fromFormatted(factionEmojiString)));
            }
        }
        return buttons;
    }

    private static boolean isTechOwnedByEnoughPlayers(Game game, TechnologyModel tech) {
        long techOwners = game.getRealPlayers().stream()
                .filter(player -> !player.isEliminated())
                .filter(player -> player.hasTech(tech.getAlias()))
                .count();
        return tech.getRequirements().orElse("").length() < techOwners;
    }

    @ButtonHandler("resolveLostTreatise")
    public static void resolveLostTreatise(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "resolveLostTreatiseRedistribute", "Redistribute"));
        buttons.add(Buttons.green(player.factionButtonChecker() + "resolveLostTreatiseFleet", "Gain 1 Fleet Token"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Lost Treatise_.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveLostTreatiseRedistribute")
    public static void resolveLostTreatiseRedistribute(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelperStats.sendGainCCButtons(game, player, true);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveLostTreatiseFleet")
    public static void resolveLostTreatiseFleet(Player player, Game game, ButtonInteractionEvent event) {
        int oldFleetCC = player.getFleetCC();
        player.setFleetCC(oldFleetCC + 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " gained a command token in their fleet pool (" + oldFleetCC + "->"
                        + player.getFleetCC() + ") using _Lost Treatise_.");
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getEffectiveFleetCC() > 4) {
            String msg = player.getRepresentation() + ", reminder that _Fleet Regulations_ is a";
            msg += " law in play, which is limiting fleet pool to 4 tokens.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("strandedShipStep1")
    public static void resolveStrandedShipStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getStrandedShipButtons(game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the system you wish to place the _Ghost Ship_ in.",
                buttons);
    }

    @ButtonHandler("strandedShipStep2_")
    public static void resolveStrandedShipStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = FlipTileService.flipTileIfNeeded(event, tile, game);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " put 1 cruiser in " + tile.getRepresentation() + ".");

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        CommanderUnlockCheckService.checkPlayer(p2, "empyrean");
    }

    private static List<Button> getSpatialCollapseTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile.isHomeSystem(game)
                    || tile.isMecatol(game)) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        "spatialCollapseStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("spatialCollapseStep2_")
    public static void resolveSpatialCollapseStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        Tile tile1 = game.getTileByPosition(pos1);
        for (String tilePos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false, false)) {
            Tile tile = game.getTileByPosition(tilePos2);
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile == tile1
                    || tile.isHomeSystem(game)
                    || tile.isMecatol(game)) {
                continue;
            }

            buttons.add(Buttons.gray(
                    "spatialCollapseStep3_" + pos1 + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to swap places with "
                        + tile1.getRepresentationForButtons(game, player) + ".",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("spatialCollapseStep3_")
    public static void resolveSpatialCollapseStep3(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String position = buttonID.split("_")[1];
        String position2 = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(position);
        Tile tile2 = game.getTileByPosition(position2);
        tile.setPosition(position2);
        tile2.setPosition(position);
        game.setTile(tile);
        game.setTile(tile2);
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " Chose to swap "
                        + tile2.getRepresentationForButtons(game, player) + " with "
                        + tile.getRepresentationForButtons(game, player));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static List<Button> getStrandedShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getPlanetUnitHolders().isEmpty() && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                buttons.add(Buttons.green(
                        "strandedShipStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("sideProject")
    public static void resolveSideProject(Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelperFactionSpecific.offerWinnuStartingTech(player);
    }

    @ButtonHandler("brutalOccupation")
    public static void resolveBrutalOccupationStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green("brutalOccupationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the target of _Brutal Occupation_.",
                buttons);
    }

    @ButtonHandler("resolveShrapnelTurrets_")
    public static void resolveShrapnelTurrets(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("ShrapnelTurretsFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event, "combatRoll_" + buttonID.split("_")[1] + "_space_afb");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.setStoredValue("ShrapnelTurretsFaction", "");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("allianceRiderRandomAlly")
    public static void resolveAllianceRiderRandomAlly(Player player, Game game, ButtonInteractionEvent event) {
        Set<String> purgedAllies = new HashSet<>(getStoredCommanderList(game, ALLIANCE_RIDER_PURGED_ALLIES));
        String allyCommander = UnusedCommanderHelper.getUnusedCommander(game, purgedAllies);
        if (allyCommander == null || allyCommander.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " cannot reveal a new ally because none are available.");
            return;
        }

        game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, allyCommander);
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(player.factionButtonChecker() + "allianceRiderGainAlly", "Gain Ally"));
        buttons.add(Buttons.red(player.factionButtonChecker() + "allianceRiderPurgeAlly", "Purge Ally"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose whether to gain or purge this ally.",
                buttons);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " revealed this ally for _Alliance Rider_.",
                Mapper.getLeader(allyCommander).getRepresentationEmbed());
    }

    @ButtonHandler("allianceRiderGainAlly")
    public static void resolveAllianceRiderGainAlly(Player player, Game game, ButtonInteractionEvent event) {
        String allyCommander = game.getStoredValue(ALLIANCE_RIDER_CURRENT_ALLY);
        if (allyCommander.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no revealed ally to gain. Use _Random Ally_ first.");
            return;
        }

        if (player.hasLeader(allyCommander)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentation() + " already has this ally ability in play.");
            game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, "");
            return;
        }

        player.addLeader(allyCommander);
        game.addFakeCommander(allyCommander);
        player.getLeader(allyCommander).ifPresent(leader -> leader.setLocked(false));
        game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, "");
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " gained the _Alliance Rider_ ally ability: "
                        + Mapper.getLeader(allyCommander).getName() + ".");
    }

    @ButtonHandler("allianceRiderPurgeAlly")
    public static void resolveAllianceRiderPurgeAlly(Player player, Game game, ButtonInteractionEvent event) {
        String allyCommander = game.getStoredValue(ALLIANCE_RIDER_CURRENT_ALLY);
        if (allyCommander.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no revealed ally to purge. Use _Random Ally_ first.");
            return;
        }

        Set<String> purgedAllies = new HashSet<>(getStoredCommanderList(game, ALLIANCE_RIDER_PURGED_ALLIES));
        purgedAllies.add(allyCommander);
        game.setStoredValue(ALLIANCE_RIDER_PURGED_ALLIES, String.join(",", purgedAllies));
        game.setStoredValue(ALLIANCE_RIDER_CURRENT_ALLY, "");
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " purged the _Alliance Rider_ ally: "
                        + Mapper.getLeader(allyCommander).getName() + ".");
    }

    @ButtonHandler("resolveProjectRider")
    public static void resolveProjectRider(Player player, Game game, ButtonInteractionEvent event) {
        game.setStoredValue(getProjectRiderSelectionKey(player), "");
        ButtonHelper.deleteMessage(event);

        if (getProjectRiderSelectableCards(game).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no action cards in the discard pile to choose for _Project Rider_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "projectRiderCardPick_1", "Card #1"));
        buttons.add(Buttons.green(player.factionButtonChecker() + "projectRiderCardPick_2", "Card #2"));
        buttons.add(Buttons.blue(player.factionButtonChecker() + "projectRiderDone", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose up to 2 action cards in the discard pile for _Project Rider_.",
                buttons);
    }

    @ButtonHandler("projectRiderCardPick_")
    public static void resolveProjectRiderCardPick(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ActionCardHelper.pickACardFromDiscardStep1(
                game,
                player,
                PROJECT_RIDER_PICK_PREFIX,
                player.getRepresentationUnfogged()
                        + ", choose an action card from the discard pile for _Project Rider_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("projectRiderPickFromDiscard_")
    public static void resolveProjectRiderPickFromDiscard(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String acId = buttonID.replace(PROJECT_RIDER_PICK_PREFIX, "");
        List<String> selectedCards = new ArrayList<>(getProjectRiderSelections(game, player));
        if (selectedCards.contains(acId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " already selected _"
                            + Mapper.getActionCard(acId).getName() + "_ for _Project Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!isProjectRiderCardSelectable(game, acId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " cannot select that action card because it is no longer in the discard pile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (selectedCards.size() >= PROJECT_RIDER_MAX_SELECTIONS) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " already selected the maximum number of cards for _Project Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        selectedCards.add(acId);
        setProjectRiderSelections(game, player, selectedCards);
        ButtonHelper.deleteMessage(event);
        sendProjectRiderSelectionSummary(player, selectedCards);
    }

    @ButtonHandler("projectRiderDone")
    public static void resolveProjectRiderDone(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendProjectRiderSelectionSummary(player, getProjectRiderSelections(game, player));
    }

    @ButtonHandler("resolveProjectRiderReward")
    public static void resolveProjectRiderReward(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        List<String> selectedCards = new ArrayList<>(getProjectRiderSelections(game, player));
        game.setStoredValue(getProjectRiderSelectionKey(player), "");
        if (selectedCards.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " had no action cards selected for _Project Rider_.");
            return;
        }

        List<String> retrievedCards = new ArrayList<>();
        List<String> unavailableCards = new ArrayList<>();
        for (String acId : selectedCards) {
            Integer acIndex = game.getDiscardActionCards().get(acId);
            if (acIndex == null
                    || !isProjectRiderCardSelectable(game, acId)
                    || !game.pickActionCard(player.getUserID(), acIndex)) {
                unavailableCards.add(Mapper.getActionCard(acId).getName());
                continue;
            }
            retrievedCards.add(Mapper.getActionCard(acId).getName());
        }

        if (!retrievedCards.isEmpty()) {
            ActionCardHelper.sendActionCardInfo(game, player);
            ButtonHelper.checkACLimit(game, player);
        }

        StringBuilder message =
                new StringBuilder(player.getRepresentationUnfogged()).append(" resolved _Project Rider_.");
        if (retrievedCards.isEmpty()) {
            message.append(" None of the selected action cards were still available in the discard pile.");
        } else {
            message.append(" Retrieved ")
                    .append(formatProjectRiderCardNames(retrievedCards))
                    .append(" from the discard pile.");
        }
        if (!unavailableCards.isEmpty()) {
            message.append(" These selected cards were unavailable: ")
                    .append(formatProjectRiderCardNames(unavailableCards))
                    .append(".");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
    }

    private static List<String> getStoredCommanderList(Game game, String storageKey) {
        String storedValue = game.getStoredValue(storageKey);
        if (storedValue.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(storedValue.split(",")));
    }

    private static void sendProjectRiderSelectionSummary(Player player, List<String> selectedCards) {
        String summary = selectedCards.isEmpty()
                ? "selected no action cards"
                : "selected " + formatProjectRiderCardIds(selectedCards);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " " + summary + " for _Project Rider_.");
    }

    private static List<String> getProjectRiderSelections(Game game, Player player) {
        String storedValue = game.getStoredValue(getProjectRiderSelectionKey(player));
        if (storedValue.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(storedValue.split(",")));
    }

    private static void setProjectRiderSelections(Game game, Player player, List<String> selectedCards) {
        game.setStoredValue(getProjectRiderSelectionKey(player), String.join(",", selectedCards));
    }

    private static String getProjectRiderSelectionKey(Player player) {
        return PROJECT_RIDER_SELECTED_CARDS_PREFIX + player.getUserID();
    }

    private static List<String> getProjectRiderSelectableCards(Game game) {
        return game.getDiscardActionCards().keySet().stream()
                .filter(acId -> isProjectRiderCardSelectable(game, acId))
                .toList();
    }

    private static boolean isProjectRiderCardSelectable(Game game, String acId) {
        return game.getDiscardActionCards().containsKey(acId)
                && game.getDiscardACStatus().get(acId) == null;
    }

    private static String formatProjectRiderCardIds(List<String> actionCards) {
        return formatProjectRiderCardNames(actionCards.stream()
                .map(acId -> Mapper.getActionCard(acId).getName())
                .toList());
    }

    private static String formatProjectRiderCardNames(List<String> actionCards) {
        return actionCards.stream().map(name -> "_" + name + "_").collect(java.util.stream.Collectors.joining(", "));
    }

    @ButtonHandler("brutalOccupationStep2_")
    public static void resolveBrutalOccupationStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);

        player.refreshPlanet(planet);

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(
                game, ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        if (!buttons.isEmpty()) {
            String message = player.getFactionEmoji() + ", please press the button to explore "
                    + Helper.getPlanetRepresentation(planet, game) + ".";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " readied and explored "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game) + ".");
    }

    @ButtonHandler("willRevolution")
    public static void willRevolution(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("willRevolution", "active");
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Reversed strategy card picking order.");
    }

    @ButtonHandler("deflectSC_")
    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.split("_")[1];
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("deflectedSC", sc);
        // TODO: move this out of here.
        if (game.isTwilightsFallMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Put _Tartarus_ on **" + Helper.getSCName(Integer.parseInt(sc), game) + "**.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Put _Deflection_ on **" + Helper.getSCName(Integer.parseInt(sc), game) + "**.");
        }
    }

    @ButtonHandler("resolveAmendmentStep1")
    public static void resolveAmendmentStep1(Player player, Game game, ButtonInteractionEvent event) {
        int acIndex = -1;
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if ("amendment".equals(ac.getKey())) {
                acIndex = ac.getValue();
                break;
            }
        }
        if (acIndex == -1) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " does not have _Amendment_ in hand.");
            return;
        }

        game.discardActionCard(player.getUserID(), acIndex);
        ActionCardModel actionCard = Mapper.getActionCard("amendment");
        String actionCardPlayMessage = game.isFowMode()
                ? "Someone played the action card _Amendment_."
                : player.getRepresentation() + " played the action card _Amendment_.";
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getMainGameChannel(), actionCardPlayMessage, actionCard.getRepresentationEmbed(false, true, game));

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> po : game.getRevealedPublicObjectives().entrySet()) {
            String poID = po.getKey();
            List<String> scorers = game.getScoredPublicObjectives().getOrDefault(poID, List.of());
            if (scorers.contains(player.getUserID())) {
                PublicObjectiveModel poModel = Mapper.getPublicObjective(poID);
                if (poModel != null) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + "amendmentChooseObjective_" + poID,
                            "Purge: " + poModel.getName()));
                }
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " has no scored public objectives to purge for _Amendment_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a public objective you have scored to purge.",
                buttons);
    }

    @ButtonHandler("amendmentChooseObjective_")
    public static void amendmentChooseObjective(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String poID = buttonID.replace("amendmentChooseObjective_", "");
        PublicObjectiveModel poModel = Mapper.getPublicObjective(poID);
        String poName = poModel != null ? poModel.getName() : poID;
        int poPoints =
                poModel != null ? poModel.getPoints() : game.getCustomPublicVP().getOrDefault(poID, 0);

        List<String> scorers = new ArrayList<>(game.getScoredPublicObjectives().getOrDefault(poID, List.of()));
        if (!scorers.isEmpty()) {
            String purgedObjectiveName = poName + " (PURGED)";
            Integer purgedObjectiveId = game.addCustomPO(purgedObjectiveName, poPoints);
            for (String userID : scorers) {
                game.scorePublicObjective(userID, purgedObjectiveId);
            }
        }
        game.removeRevealedObjective(poID);

        String objectivePurgeMessage = game.isFowMode()
                ? "A public objective was purged using _Amendment_. Players do not lose points from this purge."
                : player.getRepresentationNoPing() + " purged the public objective _" + poName
                        + "_ using _Amendment_. Players do not lose points from this purge.";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), objectivePurgeMessage);

        List<Button> stageButtons = new ArrayList<>();
        stageButtons.add(
                Buttons.green(player.factionButtonChecker() + "amendmentRevealStage1", "Reveal Stage 1 Objective"));
        stageButtons.add(
                Buttons.blue(player.factionButtonChecker() + "amendmentRevealStage2", "Reveal Stage 2 Objective"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose which stage public objective to draw and reveal.",
                stageButtons);
    }

    @ButtonHandler("amendmentRevealStage1")
    public static void amendmentRevealStage1(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        RevealPublicObjectiveService.revealS1(game, event);
    }

    @ButtonHandler("amendmentRevealStage2")
    public static void amendmentRevealStage2(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        RevealPublicObjectiveService.revealS2(game, event);
    }
}
