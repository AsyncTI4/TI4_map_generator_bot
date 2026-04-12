package ti4.buttons;

import static org.apache.commons.lang3.StringUtils.capitalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StatusHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.StatusCleanupService;
import ti4.service.button.ReactionService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.game.StartPhaseService;
import ti4.service.objectives.ScorePublicObjectiveService;
import ti4.service.strategycard.PlayStrategyCardService;

@UtilityClass
public class UnfiledButtonHandlers {

    private static String getBestBombardablePlanet(Player player, Game game, Tile tile) {
        String best = "";
        for (String planet : getBombardablePlanets(player, game, tile)) {
            best = planet;
            for (Player p2 : game.getRealPlayers()) {
                if (ButtonHelper.getNumberOfGroundForces(p2, game.getUnitHolderFromPlanet(planet)) > 0) {
                    return best;
                }
            }
        }
        return best;
    }

    public static void autoAssignAllBombardmentToAPlanet(Player player, Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        game.removeStoredValue("assignedBombardment" + player.getFaction());
        if (tile == null) {
            return;
        }
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(tile, player, null);
        String planet = getBestBombardablePlanet(player, game, tile);
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            for (int x = 0; x < entry.getValue(); x++) {
                String name = entry.getKey().getAsyncId() + "_" + x;

                String assignedUnit = name + "_" + planet;
                game.setStoredValue(
                        "assignedBombardment" + player.getFaction(),
                        game.getStoredValue("assignedBombardment" + player.getFaction()) + assignedUnit + ";");
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    game.getStoredValue("assignedBombardment" + player.getFaction()) + "plasma_99_" + planet + ";");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    game.getStoredValue("assignedBombardment" + player.getFaction()) + "argentcommander_99_" + planet
                            + ";");
        }
    }

    private static List<Button> getBombardmentAssignmentButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return buttons;
        }
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(tile, player, null);
        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        List<String> usedLabels = new ArrayList<>();
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            UnitModel mod = entry.getKey();
            for (int x = 0; x < entry.getValue(); x++) {
                String name = mod.getAsyncId() + "_" + x;
                if (assignedUnits.contains(name)) {
                    for (String assignedUnit : assignedUnits.split(";")) {

                        if (assignedUnit.contains(name)) {
                            String planet = assignedUnit.split("_")[2];
                            String label = "Unassign " + capitalize(mod.getBaseType()) + " From "
                                    + Helper.getPlanetRepresentationNoResInf(planet, game);
                            if (!usedLabels.contains(label)) {
                                buttons.add(
                                        Buttons.red("unassignBombardUnit_" + assignedUnit, label, mod.getUnitEmoji()));
                                usedLabels.add(label);
                            }
                        }
                    }
                } else {
                    for (String planet : getBombardablePlanets(player, game, tile)) {
                        String label = "Assign " + capitalize(mod.getBaseType()) + " To "
                                + Helper.getPlanetRepresentationNoResInf(planet, game);
                        if (!usedLabels.contains(label)) {
                            buttons.add(Buttons.green(
                                    "assignBombardUnit_" + name + "_" + planet, label, mod.getUnitEmoji()));
                            usedLabels.add(label);
                        }
                    }
                }
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            if (assignedUnits.contains("plasma")) {
                for (String assignedUnit : assignedUnits.split(";")) {
                    if (assignedUnit.contains("plasma")) {
                        String planet = assignedUnit.split("_")[2];
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit,
                                "Unassign Plasma Scoring Die From "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                TechEmojis.WarfareTech));
                    }
                }
            } else {
                for (String planet : getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_plasma_99_" + planet,
                            "Assign Plasma Scoring Die To " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            TechEmojis.WarfareTech));
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            if (assignedUnits.contains("argent")) {
                for (String assignedUnit : assignedUnits.split(";")) {
                    if (assignedUnit.contains("argent")) {
                        String planet = assignedUnit.split("_")[2];
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit,
                                "Unassign Argent Commander Die from "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                FactionEmojis.Argent));
                    }
                }
            } else {
                for (String planet : getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_argentcommander_99_" + planet,
                            "Assign Argent Commander Die to " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            FactionEmojis.Argent));
                }
            }
        }
        buttons.add(Buttons.blue(
                "combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment + "_deleteTheseButtons",
                "Done Assigning"));
        // buttons.add(Buttons.blue("doneAssigningBombard", "Done Assigning"));
        return buttons;
    }

    public static List<String> getBombardablePlanets(Player player, Game game, Tile tile) {
        List<String> planets = new ArrayList<>();
        for (Planet planetUH : tile.getPlanetUnitHolders()) {
            if (!player.getPlanetsAllianceMode().contains(planetUH.getName())
                    || FoWHelper.otherPlayersHaveUnitsOnPlanet(player, planetUH)) {
                if (!planetUH.getPlanetTypes().contains("cultural") || !ButtonHelper.isLawInPlay(game, "conventions")) {
                    planets.add(planetUH.getName());
                }
            }
        }

        return planets;
    }

    private static String getBombardmentSummary(Player player, Game game) {
        StringBuilder summary = new StringBuilder();
        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return summary.toString();
        }
        for (String planet : getBombardablePlanets(player, game, tile)) {
            summary.append("### ")
                    .append(player.fogSafeEmoji())
                    .append(" BOMBARDMENT of ")
                    .append(Helper.getPlanetRepresentationNoResInf(planet, game))
                    .append(":\n");
            for (Player p2 : game.getRealAndEliminatedPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(planet))) {
                    summary.append("-# ")
                            .append(p2.fogSafeEmoji())
                            .append(" currently has ")
                            .append(ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(
                                    game, p2, planet))
                            .append('\n');
                    break;
                }
            }

            for (String assignedUnit : assignedUnits.split(";")) {
                if (assignedUnit.endsWith(planet)) {
                    if (assignedUnit.contains("99")) {
                        if (assignedUnit.contains("argent")) {
                            summary.append("- Trrakan Aun Zulok die\n");
                        } else {
                            summary.append("- _Plasma Scoring_ die\n");
                        }
                    } else {
                        String asyncID = assignedUnit.split("_")[0];
                        UnitModel mod = player.getUnitFromAsyncID(asyncID);
                        summary.append("- ").append(mod.getUnitEmoji()).append('\n');
                    }
                }
            }
        }
        return summary.toString();
    }

    // @ButtonHandler("strategicAction_")
    public static void strategicAction(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTyrannusAssignTyrantButtons(Game game, Player politicsHolder) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if ((!player.isSpeaker() || !politicsHolder.getSCs().contains(3)) && !player.isTyrant()) {
                String faction = player.getFaction();
                if (Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!game.isFowMode()) {
                        button = Buttons.gray(
                                politicsHolder.getFinsFactionCheckerPrefix() + "assignTyrant_" + faction,
                                " ",
                                player.getFactionEmoji());
                    } else {
                        button = Buttons.gray(
                                politicsHolder.getFinsFactionCheckerPrefix() + "assignTyrant_" + faction,
                                player.getColor(),
                                ColorEmojis.getColorEmoji(player.getColor()));
                    }
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }

    public static void poScoring(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel privateChannel) {
        if (!"true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (!game.getStoredValue("newStatusScoringMode").isEmpty() && event != null) {
                    String msg = "Please score objectives.";
                    msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
                    event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
                }
            } catch (Exception e) {
                if (event != null) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel()
                            .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                } else {
                    BotLogger.error("Hm", e);
                }
            }
            return;
        }
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key3b = "potentialScoreSOBlockers";
        String message;
        for (Player player2 : StatusHelper.getPlayersInScoringOrder(game)) {
            if (player2 == player) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }

                String poID = buttonID.replace(Constants.PO_SCORING, "");
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                    if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                        Helper.resolvePOScoringQueue(game, event);
                    }
                }
                break;
            }
            if (game.getStoredValue(key3).contains(player2.getFaction() + "*")
                    || game.getStoredValue(key3b).contains(player2.getFaction() + "*")) {
                message = " has been queued to score a public objective. ";
                if (!game.isFowMode()) {
                    message += player2.getRepresentationUnfogged() + " is the one the game is currently waiting on.";
                }
                String poID = buttonID.replace(Constants.PO_SCORING, "");
                try {
                    int poIndex = Integer.parseInt(poID);
                    if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "Queued");
                    }
                    game.setStoredValue(player.getFaction() + "queuedPOScore", "" + poIndex);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel()
                            .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                }
                game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                ReactionService.addReaction(event, game, player, message);
                break;
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()
                && !"action".equalsIgnoreCase(game.getPhaseOfGame())
                && event != null
                && !game.isFowMode()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        if ("action".equalsIgnoreCase(game.getPhaseOfGame()) && event != null) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void soScoreFromHand(
            ButtonInteractionEvent event,
            String buttonID,
            Game game,
            Player player,
            MessageChannel privateChannel,
            MessageChannel mainGameChannel,
            MessageChannel actionsChannel) {
        String soID = buttonID.replace(Constants.SO_SCORE_FROM_HAND, "");
        MessageChannel channel;
        if (game.isFowMode()) {
            channel = privateChannel;
        } else if (game.isCommunityMode() && game.getMainGameChannel() != null) {
            channel = mainGameChannel;
        } else {
            channel = actionsChannel;
        }
        if (channel != null) {
            int soIndex2 = Integer.parseInt(soID);
            // String phase = "action";
            if (player.getSecret(soIndex2) != null
                    && "status".equalsIgnoreCase(player.getSecret(soIndex2).getPhase())
                    && "true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScoreSOs";
                String key3 = "potentialScoreSOBlockers";
                String key3b = "potentialScorePOBlockers";
                String message;
                for (Player player2 : StatusHelper.getPlayersInScoringOrder(game)) {
                    if (player2 == player) {
                        int soIndex = Integer.parseInt(soID);
                        SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                        }
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                            }
                        }

                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")
                            || game.getStoredValue(key3b).contains(player2.getFaction() + "*")) {
                        message = player.getRepresentation() + " has been queued to score a secret objective. ";
                        if (!game.isFowMode()) {
                            message += player2.getRepresentationUnfogged()
                                    + " is the one the game is currently waiting on.";
                        }
                        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                            game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "Queued");
                        }
                        MessageHelper.sendMessageToChannel(channel, message);
                        int soIndex = Integer.parseInt(soID);
                        game.setStoredValue(player.getFaction() + "queuedSOScore", "" + soIndex);
                        game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                        break;
                    }
                }
            } else {
                try {
                    int soIndex = Integer.parseInt(soID);
                    SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse SO ID: " + soID, e);
                    event.getChannel()
                            .sendMessage("Could not parse secret objective ID: " + soID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                    return;
                }
            }
        } else {
            if (event != null) {
                event.getChannel()
                        .sendMessage("Could not find channel to play card. Please ping Bothelper.")
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
        ButtonHelper.deleteMessage(event);
        checkForAllReactions(event, game);
    }

    public static void clearAllReactions(@NotNull ButtonInteractionEvent event) {
        Message mainMessage = event.getInteraction().getMessage();
        mainMessage.clearReactions().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game game) {
        if (event == null) {
            return;
        }
        String buttonID = event.getButton().getCustomId();

        String messageId = event.getInteraction().getMessage().getId();
        var gameMessage = GameMessageManager.getOne(game.getName(), messageId);
        int matchingFactionReactions = 0;
        if (buttonID != null
                && (buttonID.contains("po_scoring")
                        || buttonID.contains("po_no_scoring")
                        || buttonID.contains("so_no_scoring")
                        || buttonID.contains("so_score_hand"))) {
            boolean allReacted = true;
            for (Player player : game.getRealPlayers()) {
                String po = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
                String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
                if (po.isEmpty()
                        || so.isEmpty()
                        || game.getPhaseOfGame().contains("action")
                        || game.getPhaseOfGame().contains("agenda")) {
                    allReacted = false;
                }
            }
            if (allReacted) {
                respondAllPlayersReacted(event, game);
                GameMessageManager.remove(game.getName(), messageId);
            }
        } else {

            for (Player player : game.getRealPlayers()) {
                boolean factionReacted = false;
                String faction = player.getFaction();
                if (gameMessage.isPresent()
                        && gameMessage.get().factionsThatReacted().contains(faction)) {
                    factionReacted = true;
                } else if (buttonID.contains("no_after")) {
                    if (game.getPlayersWhoHitPersistentNoAfter().contains(faction)) {
                        factionReacted = true;
                    } else {
                        Message mainMessage = event.getMessage();
                        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, event.getMessageId());
                        MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                        if (reaction != null) {
                            factionReacted = true;
                        }
                    }
                } else if (buttonID.contains("no_when")) {
                    if (game.getPlayersWhoHitPersistentNoWhen().contains(faction)) {
                        factionReacted = true;
                    } else {
                        Message mainMessage = event.getMessage();
                        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, event.getMessageId());
                        MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                        if (reaction != null) {
                            factionReacted = true;
                        }
                    }
                }
                if (factionReacted) {
                    matchingFactionReactions++;
                }
            }
            int numberOfPlayers = game.getRealPlayers().size();
            if (matchingFactionReactions >= numberOfPlayers) {
                respondAllPlayersReacted(event, game);
                GameMessageManager.remove(game.getName(), messageId);
            }
        }
    }

    public static void respondAllHaveScored(Game game) {
        String message2 =
                "All players have indicated scoring. Flip the relevant public objective using the buttons. This will automatically run status clean-up if it has not been run already.";
        Button draw2Stage2 = Buttons.green("reveal_stage_2x2", "Reveal 2 Stage 2");
        Button drawStage2 = Buttons.green("reveal_stage_2", "Reveal Stage 2");
        Button drawStage1 = Buttons.green("reveal_stage_1", "Reveal Stage 1");
        List<Button> buttons = new ArrayList<>();
        if (game.isRedTapeMode() || game.isCivilizedSocietyMode()) {
            message2 = "All players have indicated scoring. In this game mode, no objective is revealed at this stage."
                    + " Please press one of the buttons below anyways though - don't worry, it won't reveal anything, it will just run cleanup.";
        }
        if (game.getRound() < 4 || !game.getPublicObjectives1Peekable().isEmpty()) {
            buttons.add(drawStage1);
        }
        if ((game.getRound() > 3 || game.getPublicObjectives1Peekable().isEmpty()) && !game.isOmegaPhaseMode()) {
            if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                buttons.add(draw2Stage2);
            } else {
                buttons.add(drawStage2);
            }
        }
        var endGameDeck =
                game.isOmegaPhaseMode() ? game.getPublicObjectives1Peekable() : game.getPublicObjectives2Peekable();
        var endGameRound = game.isOmegaPhaseMode() ? 9 : 7;
        if ((game.getRound() > endGameRound || endGameDeck.isEmpty())
                && !game.isRedTapeMode()
                && !game.isCivilizedSocietyMode()) {
            if (game.isFowMode()) {
                message2 += "\n> - If there are no more objectives to reveal, use the button to continue as is.";
                message2 += " Or end the game manually.";
                buttons.add(Buttons.green("reveal_stage_none", "Continue without revealing"));
            } else {
                message2 += "\n> - If there are no more objectives to reveal, use the button to end the game.";
                message2 +=
                        " Whoever has the most points is crowned the winner, or whoever has the earliest initiative in the case of ties.";

                buttons.add(Buttons.red("gameEnd", "End Game"));
                buttons.add(Buttons.blue("rematch", "Rematch (make new game with same players/channels)"));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message2, buttons);
    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getCustomId();
        if (game == null || buttonID == null) {
            return;
        }
        if (buttonID.startsWith(Constants.PO_SCORING)
                || buttonID.contains("po_no_scoring")
                || buttonID.contains("so_no_scoring")
                || buttonID.contains("so_score_hand")) {
            buttonID = Constants.PO_SCORING;
        } else if ((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction()
                    .getMessage()
                    .reply("All players have reacted to \"" + buttonText + "\".")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW,
                    "sc_refresh",
                    "sc_refresh_and_wash",
                    "trade_primary",
                    "sc_ac_draw",
                    "sc_draw_so",
                    "sc_trade_follow" -> {
                String message = "All players have reacted to this strategy card.";
                if (game.isFowMode()) {
                    event.getInteraction().getMessage().reply(message).queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage(message).queueAfter(10, TimeUnit.SECONDS);
                }
            }
            case "no_when", "no_when_persistent" ->
                ReactionService.handleAllPlayersReactingNoWhens(
                        event.getInteraction().getMessage(), game);
            case "no_after", "no_after_persistent" ->
                ReactionService.handleAllPlayersReactingNoAfters(
                        event.getInteraction().getMessage(), game);
            case "no_sabotage" ->
                ReactionService.handleAllPlayersReactingNoSabotage(
                        event.getInteraction().getMessage(), game);
            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> respondAllHaveScored(game);
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    if (game.isTwilightsFallMode()) {
                        Button flipAgenda = Buttons.blue("edictPhase", "Do Edict Phase");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(), "Please proceed to the Edict Phase now.", buttons);
                    } else {
                        Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(), "Please flip agenda now.", buttons);
                    }
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            game.getPing()
                                    + ", all players have indicated completion of Status Phase. Proceeding to Strategy Phase.");
                    StartPhaseService.startPhase(event, game, "strategy");
                }
                if (game.isFowMode()) {
                    game.setStoredValue("fowStatusDone", "");
                    StatusCleanupService.returnEndStatusPNs(game);
                }
            }
            case "redistributeCCButtons" -> {
                StatusCleanupService.returnEndStatusPNs(
                        game); // return any PNs with "end of status phase" return timing
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    if (game.isTwilightsFallMode()) {
                        Button flipAgenda = Buttons.blue("edictPhase", "Do Edict Phase");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(),
                                "Please proceed to Edict Phase after the last person finishing doing gaining and redistributing command tokens.",
                                buttons);
                    } else {
                        Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(),
                                "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                        + " Please press the \"Flip Agenda\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                                buttons);
                    }

                } else {
                    Button flipAgenda = Buttons.blue("startStrategyPhase", "Start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    String condition;
                    if (game.isOrdinianC1Mode()) {
                        condition = "the _Coatl_ is still damaged";
                    } else if (game.isTwilightsFallMode()) {
                        condition = "there is no Tyrant";
                    } else {
                        condition = "the Custodians token is still on Mecatol Rex";
                    }
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(),
                            "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                    + " As " + condition + ", there will be no Agenda Phase this round."
                                    + " Please press the \"Start Strategy Phase\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                            buttons);
                }
            }
        }
    }

    @Deprecated
    public static void gain1tgFromCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message =
                player.getRepresentation() + " gained 1 trade good " + player.gainTG(1) + " from their commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromMuaatCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromLetnevCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1TG(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = "";
        String labelP = event.getButton().getLabel();
        boolean failed = false;
        if (labelP.contains("inf") && labelP.contains("mech")) {
            message += "Please resolve removing infantry manually, if applicable.";
            failed = message.contains("Please try again.");
        }
        if (!failed) {
            message += "Gained 1 trade good " + player.gainTG(1, true) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ReactionService.addReaction(event, game, player, message);
        if (!failed) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(mainGameChannel, pF + " " + message);
            }
        }
    }
}
