package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.acd2.PublicOutrageAcd2ButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.acd2.SettlementsAcd2ButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.DreamButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor.VeylorAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor.VeylorLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.xan.XanAbilityHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaust;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.PlanetModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.abilities.MahactTokenService;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.fow.GMService;
import ti4.service.fow.RiftSetModeService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;
import tools.jackson.core.type.TypeReference;

@UtilityClass
public final class AgendaHelper {
    public static final String AGENDA_START_VOTE_COUNTS = "agendaStartVoteCounts";

    private static void pingAboutDebt(Game game) {
        if (game.isHiddenAgendaMode() || !game.getStoredValue("executiveOrder").isEmpty()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player
                        || (player.getTg() + player.getCommodities()) < 0
                        || p2.hasAbility("data_recovery")
                        || p2.getDebtTokenCount(player.getColor()) < 1) {
                    continue;
                }
                String msg = player.getRepresentation() + ", a reminder that you owe debt to "
                        + p2.getRepresentationNoPing()
                        + " and now could be a good time to pay it (or get it cleared if it was paid already).";
                List<Button> buttons = new ArrayList<>();
                if (player.getCommodities() >= 1) {
                    buttons.add(Buttons.green("sendTGTo_" + p2.getFaction() + "_comm", "Send 1 Commodity"));
                }
                if (p2.getDebtTokenCount(player.getColor()) >= 3 && player.getCommodities() >= 3) {
                    buttons.add(Buttons.green("sendTGTo_" + p2.getFaction() + "_comm3", "Send 3 Commodities"));
                }
                if (player.getTg() >= 1) {
                    buttons.add(Buttons.green("sendTGTo_" + p2.getFaction() + "_tg", "Send 1 Trade Good"));
                }
                if (p2.getDebtTokenCount(player.getColor()) >= 3 && player.getTg() >= 3) {
                    buttons.add(Buttons.green("sendTGTo_" + p2.getFaction() + "_tg3", "Send 3 Trade Goods"));
                }
                buttons.add(Buttons.blue("sendTGTo_" + p2.getFaction() + "_debt", "Erase 1 Debt"));
                if (p2.getDebtTokenCount(player.getColor()) >= 3) {
                    buttons.add(Buttons.blue("sendTGTo_" + p2.getFaction() + "_debt3", "Erase 3 Debt"));
                }
                buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
    }

    @ButtonHandler("sendTGTo_")
    public static void erase1DebtTo(Game game, String buttonID, ButtonInteractionEvent event, Player player) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String tgOrDebt = buttonID.split("_")[2];
        int amount = tgOrDebt.endsWith("3") ? 3 : 1;
        tgOrDebt = tgOrDebt.replace("3", "");
        p2.clearDebt(player, amount);
        String msg = amount + " debt owed by " + player.getRepresentation() + " to " + p2.getRepresentation()
                + " was cleared. " + p2.getDebtTokenCount(player.getColor()) + " debt remains.";
        if ("tg".equalsIgnoreCase(tgOrDebt)) {
            player.setTg(player.getTg() - amount);
            p2.setTg(p2.getTg() + amount);
            msg = player.getRepresentation(false, false) + " sent " + StringHelper.pluralize(amount, "trade good")
                    + " to " + p2.getRepresentation(false, false) + ".\n" + msg;
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAbilities.pillageCheck(player, game);
        } else if ("comm".equalsIgnoreCase(tgOrDebt)) {
            player.setCommodities(player.getCommodities() - amount);
            p2.setTg(p2.getTg() + amount);
            msg = player.getRepresentation(false, false) + " sent " + amount + " commodit" + (amount == 1 ? "y" : "ies")
                    + " to " + p2.getRepresentation(false, false) + ".\n" + msg;
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (p2.getDebtTokenCount(player.getColor()) < 1) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static void exhaustPlanetsForVotingVersion2(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String outcome = buttonID.substring(buttonID.indexOf('_') + 1);
        String formattedOutcome = getAgendaOutcomeName(game, outcome, true);
        String voteMessage = "Chose to vote for " + formattedOutcome
                + ". Click buttons to exhaust planets and use abilities for votes.";
        if (game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
            voteMessage = "Chose to vote for **" + formattedOutcome
                    + "**. Click buttons to exhaust planets and use abilities for votes.";
        }
        game.setLatestOutcomeVotedFor(outcome);
        game.setStoredValue("latestOutcomeVotedFor" + player.getFaction(), outcome);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n\n" + voteMessage,
                getPlanetButtonsVersion2(event, player, game));
        ButtonHelper.deleteMessage(event);
    }

    private static void checkForAssigningGeneticRecombination(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.setStoredValue("Genetic Recombination " + player.getFaction(), "");
            if (player.hasTechReady("gr")) {
                String msg = player.getRepresentation()
                        + " you have the option to pre-assign the declaration of using _Genetic Recombination_ on someone."
                        + " When they are up to vote, it will ping them saying that you wish to use _Genetic Recombination_, and then it will be your job to clarify."
                        + " Feel free to not pre-assign if you don't wish to use it on this agenda.";
                List<Button> buttons2 = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (!game.isFowMode()) {
                        buttons2.add(Buttons.gray(
                                "resolvePreassignment_Genetic Recombination " + player.getFaction() + "_"
                                        + p2.getFaction(),
                                p2.getFaction()));
                    } else {
                        buttons2.add(Buttons.gray(
                                "resolvePreassignment_Genetic Recombination " + player.getFaction() + "_"
                                        + p2.getFaction(),
                                p2.getColor()));
                    }
                }
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
            }
        }
    }

    @ButtonHandler("exhaust_")
    public static void exhaustStuffForVoting(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        String planetName = StringUtils.substringAfter(buttonID, "_");
        String votes = StringUtils.substringBetween(buttonLabel, "(", ")");
        if (!buttonID.contains("argent")
                && !buttonID.contains("blood")
                && !buttonID.contains("predictive")
                && !buttonID.contains("everything")) {
            PlanetExhaust.doAction(player, planetName, game, false);
            TaLeadersHandler.clearLenPredeclareForPlanet(game, player, planetName);
        }
        if (buttonID.contains("everything")) {
            for (String planet : player.getPlanets()) {
                player.exhaustPlanet(planet);
            }
            TaLeadersHandler.clearAllLenPredeclaresForPlayer(game, player);
        }
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (!buttonID.contains("argent")
                && !buttonID.contains("blood")
                && !buttonID.contains("predictive")
                && !buttonID.contains("everything")) {

            if ("Exhaust stuff".equalsIgnoreCase(totalVotesSoFar)) {
                totalVotesSoFar = "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            } else {
                int totalVotes = Integer.parseInt(totalVotesSoFar.substring(
                                totalVotesSoFar.indexOf(':') + 2, totalVotesSoFar.indexOf('\n')))
                        + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(':') + 2)
                        + totalVotes
                        + totalVotesSoFar.substring(totalVotesSoFar.indexOf('\n'))
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            }
            event.getMessage().editMessage(totalVotesSoFar).queue(Consumers.nop(), BotLogger::catchRestError);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        } else {
            if ("Exhaust stuff".equalsIgnoreCase(totalVotesSoFar)) {
                totalVotesSoFar =
                        "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: all planets";
            } else {
                int totalVotes = Integer.parseInt(totalVotesSoFar.substring(
                                totalVotesSoFar.indexOf(':') + 2, totalVotesSoFar.indexOf('\n')))
                        + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(':') + 2)
                        + totalVotes
                        + totalVotesSoFar.substring(totalVotesSoFar.indexOf('\n'));
            }
            event.getMessage().editMessage(totalVotesSoFar).queue(Consumers.nop(), BotLogger::catchRestError);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            String message;
            if (buttonID.contains("everything")) {
                message = "Exhausted all planets for " + votes + " vote" + ("1".equals(votes) ? "" : "s");
            } else {
                message = "Used ability for " + votes + " vote" + ("1".equals(votes) ? "" : "s");
            }
            ReactionService.addReaction(event, game, player, true, false, message);
        }
    }

    public static List<Player> getPlayersWhoNeedToPreVoted(Game game) {
        List<Player> players = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            int[] voteInfo = getVoteTotal(player, game);
            if (voteInfo[0] < 1) {
                continue;
            }
            if (!player.hasAbility("zeal") && player.isSpeaker() && game.isOmegaPhaseMode()) {
                continue;
            }
            if (game.isHiddenAgendaMode()
                    && game.getStoredValue("Abstain On Agenda").contains(player.getFaction())) {
                continue;
            }
            if (game.getStoredValue("preVoting" + player.getFaction()).isEmpty()
                    || "0".equalsIgnoreCase(game.getStoredValue("preVoting" + player.getFaction()))) {
                players.add(player);
            }
        }
        return players;
    }

    @ButtonHandler("resolveAgendaVote_")
    private static void resolvingAnAgendaVote(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        boolean resolveTime = false;
        String winner = "";
        String votes = buttonID.substring(buttonID.lastIndexOf('_') + 1);
        MessageChannel channel;

        boolean playerPrevotesIsEmpty =
                game.getStoredValue("preVoting" + player.getFaction()).isEmpty();
        boolean playerIsNotActivePlayer = "agendaWaiting".equalsIgnoreCase(game.getPhaseOfGame());
        boolean playerIsPrevoting =
                !playerPrevotesIsEmpty && (playerIsNotActivePlayer || game.getActivePlayer() != player);
        if (playerIsPrevoting) {
            if ("0".equalsIgnoreCase(votes)) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "You cannot pre-vote 0 votes. Pre-abstain if you wish to not vote.");
                return;
            }
            ButtonHelper.deleteMessage(event);
            game.setStoredValue("preVoting" + player.getFaction(), votes);
            List<Button> buttonsPV = new ArrayList<>();
            buttonsPV.add(Buttons.red("erasePreVote", "Erase Pre-Vote"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    "Successfully stored a pre-vote. You can erase it with this button. It will be automatically erased if someone plays an \"after\".",
                    buttonsPV);
            if (game.isOmegaPhaseMode() || game.isHiddenAgendaMode()) {
                if (player.hasAbility("zeal")) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "## The player with the **Zeal** ability votes the following:\n"
                                    + Helper.buildSpentThingsMessageForVoting(player, game, false));
                }
                if ("Yes".equalsIgnoreCase(game.getStoredValue("aftersResolved"))) {
                    if (getPlayersWhoNeedToPreVoted(game).isEmpty()) {
                        startTheVoting(game);
                    } else {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                "Game needs "
                                        + AgendaWhensAftersHelper.pluralPerson(getPlayersWhoNeedToPreVoted(game)
                                                .size())
                                        + " to pre-vote before voting will start.");
                    }
                }
            }
            return;
        } else {
            game.setStoredValue("preVoting" + player.getFaction(), "");
        }
        if (!buttonID.contains("outcomeTie*")) {
            if ("0".equalsIgnoreCase(votes)) {
                String pfaction2 = player.getFaction();
                if (pfaction2 != null) {
                    player.resetSpentThings();
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(), player.getRepresentation() + " abstained.");
                    ButtonHelper.deleteMessage(event);
                }
            } else {
                String identifier;
                winner = game.getStoredValue("latestOutcomeVotedFor" + player.getFaction());
                if (game.isFowMode()) {
                    identifier = player.getColor();
                } else {
                    identifier = player.getFaction();
                }
                Map<String, String> outcomes = game.getCurrentAgendaVotes();
                String existingData = outcomes.getOrDefault(winner, "empty");
                int numV = Integer.parseInt(votes);
                int numVOrig = Integer.parseInt(Helper.buildSpentThingsMessageForVoting(player, game, true));
                if (numV > numVOrig) {
                    player.addSpentThing("specialVotes_" + (numV - numVOrig));
                }
                if ((game.getLaws() == null
                                || (!game.getLaws().containsKey("rep_govt")
                                                && !game.getLaws().containsKey("absol_government")
                                        || !game.getStoredValue("executiveOrder")
                                                .isEmpty()))
                        && (player.ownsPromissoryNote("blood_pact")
                                || player.getPromissoryNotesInPlayArea().contains("blood_pact"))) {
                    for (Player p2 : getWinningVoters(winner, game)) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.ownsPromissoryNote("blood_pact")
                                || p2.getPromissoryNotesInPlayArea().contains("blood_pact")) {
                            player.addSpentThing("bloodPact_" + 4);
                            votes = (Integer.parseInt(votes) + 4) + "";
                            break;
                        }
                    }
                }
                if ((game.getLaws() == null
                                || (!game.getLaws().containsKey("rep_govt")
                                        && !game.getLaws().containsKey("absol_government")))
                        && (player.ownsPromissoryNote("sigma_blood_pact")
                                || player.getPromissoryNotesInPlayArea().contains("sigma_blood_pact"))) {
                    List<Player> winnners = new ArrayList<>();
                    for (Entry<String, String> entry : outcomes.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(winner)) {
                            StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");

                            while (vote_info.hasMoreTokens()) {
                                String specificVote = vote_info.nextToken();
                                String faction = specificVote.substring(0, specificVote.indexOf('_'));
                                Player p = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                                if (p != null && !winnners.contains(p)) {
                                    winnners.add(p);
                                }
                            }
                        }
                    }
                    for (Player p2 : winnners) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.ownsPromissoryNote("sigma_blood_pact")
                                || p2.getPromissoryNotesInPlayArea().contains("sigma_blood_pact")) {
                            player.addSpentThing("bloodPact_" + 6);
                            votes = (Integer.parseInt(votes) + 6) + "";
                            break;
                        }
                    }
                }
                if ("empty".equalsIgnoreCase(existingData)) {
                    existingData = identifier + "_" + votes;
                } else {
                    existingData += ";" + identifier + "_" + votes;
                }
                game.setCurrentAgendaVote(winner, existingData);
                if (!game.isHiddenAgendaMode()) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(), Helper.buildSpentThingsMessageForVoting(player, game, false));
                }
            }

            if (!game.isFowMode()) {
                Button eraseAndReVote = Buttons.red("eraseMyVote", "Erase my vote & have me vote again");
                String revoteMsg = "You may press this button to revote if you made a mistake, ignore it otherwise.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), revoteMsg, eraseAndReVote);
            }
            String message = " up to vote! Please use the buttons to choose the outcome you wish to vote for.";
            Player nextInLine = getNextInLine(player, getVotingOrder(game), game);
            String realIdentity2 = nextInLine.getRepresentationUnfogged();

            int[] voteInfo = getVoteTotal(nextInLine, game);
            boolean willPrevote =
                    !game.getStoredValue("preVoting" + nextInLine.getFaction()).isEmpty()
                            && !"0".equalsIgnoreCase(game.getStoredValue("preVoting" + nextInLine.getFaction()))
                            && voteInfo[0] > 0;
            while ((voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor()))
                    || game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())
                    || willPrevote) {
                String skippedMessage =
                        nextInLine.getRepresentation(true, false) + ", you are being skipped because you cannot vote.";
                if (game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                    CryypterHelper.checkForMentakEnvoy(nextInLine, game);
                    skippedMessage = realIdentity2
                            + ", you are being skipped because you told the bot you wanted to preset an abstention.";
                    game.setStoredValue(
                            "Abstain On Agenda",
                            game.getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (willPrevote) {
                    skippedMessage = realIdentity2 + " had logged a pre-vote.";
                    votes = game.getStoredValue("preVoting" + nextInLine.getFaction());
                    game.setStoredValue("preVoting" + nextInLine.getFaction(), "");
                    for (String thing : nextInLine.getSpentThingsThisWindow()) {
                        if (thing.contains("tg_") || thing.contains("infantry_")) {
                            continue;
                        }
                        exhaustForVotes(event, nextInLine, game, "exhaustForVotes_" + thing, true);
                    }
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(
                                nextInLine.getPrivateChannel(),
                                AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n ");
                    }
                    ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                    CryypterHelper.checkForMentakEnvoy(nextInLine, game);
                    if (!game.isHiddenAgendaMode()) {
                        MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                    }
                    resolvingAnAgendaVote("resolveAgendaVote_" + votes, event, game, nextInLine);
                    return;
                }
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(
                            nextInLine.getPrivateChannel(), AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n ");
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, game, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                }
                player = nextInLine;
                nextInLine = getNextInLine(nextInLine, getVotingOrder(game), game);
                realIdentity2 = nextInLine.getRepresentationUnfogged();
                voteInfo = getVoteTotal(nextInLine, game);
                willPrevote = !game.getStoredValue("preVoting" + nextInLine.getFaction())
                                .isEmpty()
                        && !"0".equalsIgnoreCase(game.getStoredValue("preVoting" + nextInLine.getFaction()))
                        && voteInfo[0] > 0;
            }

            if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                String realIdentity;
                realIdentity = nextInLine.getRepresentationUnfogged();
                String pFaction = nextInLine.getFlexibleDisplayName();
                String factionChecker = "FFCC_" + nextInLine.getFaction() + "_";
                Button Vote = Buttons.blue(factionChecker + "vote", pFaction + " Choose To Vote");
                Button Abstain;
                if (nextInLine.hasAbility("future_sight")
                        && game.getStoredValue("executiveOrder").isEmpty()) {
                    Abstain = Buttons.red(
                            factionChecker + "resolveAgendaVote_0",
                            pFaction + " Choose To Abstain (You have Future Sight)");
                } else {
                    Abstain = Buttons.red(factionChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
                }
                Button forcedAbstain = Buttons.gray(
                        "forceAbstainForPlayer_" + nextInLine.getFaction(), "(For Others) Abstain for this player");
                game.updateActivePlayer(nextInLine);
                game.setStoredValue("preVoting" + nextInLine.getFaction(), "");
                List<Button> buttons = List.of(Vote, Abstain, forcedAbstain);
                if (game.isFowMode()) {
                    if (nextInLine.getPrivateChannel() != null) {
                        MessageHelper.sendMessageToChannel(
                                nextInLine.getPrivateChannel(),
                                AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n ");
                        MessageHelper.sendMessageToChannelWithButtons(
                                nextInLine.getPrivateChannel(), "\n " + realIdentity + message, buttons);
                        player.getCorrectChannel()
                                .sendMessage("Notified next in line")
                                .queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                } else {
                    message = AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n \n " + realIdentity + message;
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getCorrectChannel(), message, buttons);
                }
                ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                CryypterHelper.checkForMentakEnvoy(nextInLine, game);
            } else {
                winner = getWinner(game);
                if (!"".equalsIgnoreCase(winner) && !winner.contains("*")) {
                    resolveTime = true;
                } else {
                    Player speaker;
                    if (game.getPlayer(game.getSpeakerUserID()) != null) {
                        speaker = game.getPlayers().get(game.getSpeakerUserID());
                    } else {
                        speaker = game.getRealPlayers().getFirst();
                    }
                    List<Button> tiedWinners = new ArrayList<>();
                    if (!"".equalsIgnoreCase(winner)) {
                        StringTokenizer winnerInfo = new StringTokenizer(winner, "*");
                        while (winnerInfo.hasMoreTokens()) {
                            String tiedWinner = winnerInfo.nextToken();
                            Button button = Buttons.blue(
                                    speaker.factionButtonChecker() + "resolveAgendaVote_outcomeTie* " + tiedWinner,
                                    tiedWinner);
                            tiedWinners.add(button);
                        }
                    } else {
                        tiedWinners = AgendaRiderHelper.getAgendaButtons(null, game, "resolveAgendaVote_outcomeTie*");
                    }
                    if (!tiedWinners.isEmpty()) {
                        channel = speaker.getCorrectChannel();
                        MessageHelper.sendMessageToChannelWithButtons(
                                channel,
                                speaker.getRepresentationUnfogged()
                                        + ", there are tied outcomes. As Speaker, please decide a winner.",
                                tiedWinners);
                        game.updateActivePlayer(speaker);
                    }
                }
            }
        } else {
            resolveTime = true;
            winner = buttonID.substring(buttonID.lastIndexOf('*') + 2);
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(), "## The speaker has broken the tie for \"" + winner + "\".");
        }
        if (resolveTime) {
            resolveTime(game, winner);
        }
        if (!"0".equalsIgnoreCase(votes) && event != null) {
            ButtonHelper.deleteMessage(event);
        }
    }

    public static void resolveTime(Game game, String winner) {
        if (winner == null) {
            winner = getWinner(game);
        }
        if (!game.isHiddenAgendaMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n \n");
        } else {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    AgendaSummaryHelper.getSummaryOfVotes(game, true, false, true) + "\n \n");
            MessageHelper.sendMessageToChannel(
                    game.getSpeaker().getCardsInfoThread(),
                    AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n \n");
        }
        GMService.logActivity(game, AgendaSummaryHelper.getSummaryOfVotes(game, true, true, false), false);
        game.setPhaseOfGame("agendaEnd");
        game.removeStoredValue(AGENDA_START_VOTE_COUNTS);
        game.setActivePlayerID(null);
        StringBuilder message = new StringBuilder();
        String formattedWinner = getAgendaOutcomeName(game, winner, true);
        if (game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
            formattedWinner = "**" + formattedWinner + "**";
        }
        message.append(game.getPing())
                .append(", the current winner is \"")
                .append(formattedWinner)
                .append("\".\n");
        if (!game.isAcd2()) {
            handleShenanigans(game, winner);
            message.append(
                    "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.");
        }
        Button autoResolve = Buttons.blue("agendaResolution_" + winner, "Resolve with Current Winner");
        Button manualResolve = Buttons.red("autoresolve_manual", "Resolve it Manually");
        List<Button> resolutions = List.of(autoResolve, manualResolve);
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message.toString(), resolutions);
    }

    private static void handleShenanigans(Game game, String winner) {
        List<Player> losers = getLosers(winner, game);
        boolean shenanigans = false;
        if (game.islandMode()) return;

        if ((!game.isACInDiscard("Bribery") || !game.isACInDiscard("Deadly Plot"))
                && (!losers.isEmpty() || game.isAbsolMode())) {
            StringBuilder message = new StringBuilder(
                    "You may hold while people resolve shenanigans. If it is not an important agenda, you are encouraged to move on and float the shenanigans.\n");
            Button noDeadly = Buttons.blue("generic_button_id_1", "No Deadly Plot");
            Button noBribery = Buttons.blue("generic_button_id_2", "No Bribery");
            List<Button> deadlyActionRow = List.of(noBribery, noDeadly);
            if (!game.isFowMode()) {
                if (!game.isACInDiscard("Deadly Plot")) {
                    message.append("The following players (")
                            .append(losers.size())
                            .append(") have the opportunity to play ")
                            .append(CardEmojis.ActionCard)
                            .append("_Deadly Plot_:\n");
                }
                for (Player loser : losers) {
                    message.append("> ")
                            .append(loser.getRepresentationUnfogged())
                            .append('\n');
                }
                message.append("Please play or confirm that you will not be playing _Bribery_ or _Deadly Plot_.");
            } else {
                message.append(losers.size())
                        .append(" player")
                        .append(losers.size() == 1 ? "" : "s")
                        .append(" have the opportunity to play _Deadly Plot_.\n");
                MessageHelper.privatelyPingPlayerList(
                        losers, game, "Please play or confirm that you will not be playing _Deadly Plot_.");
            }
            MessageHelper.sendMessageToChannelWithPersistentReacts(
                    game.getMainGameChannel(),
                    message.toString(),
                    game,
                    deadlyActionRow,
                    GameMessageType.AGENDA_DEADLY_PLOT);
            shenanigans = true;
        } else {
            String message = (game.isACInDiscard("Bribery") && game.isACInDiscard("Deadly Plot"))
                    ? "Both _Bribery_ and _Deadly Plot_ are in the discard pile."
                    : "No player can legally play _Bribery_ or _Deadly Plot_.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }

        // Confounding & Confusing Legal Text
        if (game.getCurrentAgendaInfo().contains("Elect Player")) {
            if (!game.isACInDiscard("Confounding") || !game.isACInDiscard("Confusing")) {
                String message = "Please confirm no _Confusing/Confounding Legal Texts_.";
                Button noConfounding = Buttons.blue("generic_button_id_3", "Refuse Confounding Legal Text");
                Button noConfusing = Buttons.blue("genericReact4", "Refuse Confusing Legal Text");
                List<Button> buttons = List.of(noConfounding, noConfusing);
                MessageHelper.sendMessageToChannelWithPersistentReacts(
                        game.getMainGameChannel(),
                        message,
                        game,
                        buttons,
                        GameMessageType.AGENDA_CONFOUNDING_CONFUSING_LEGAL_TEXT);
                shenanigans = true;
            } else {
                String message =
                        "Both _Confounding Legal Text_ and _Confusing Legal Text_ are in the discard pile.\nThere are no shenanigans possible. Please resolve the agenda.";
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
            }
        }

        if (!shenanigans) {
            String message = "There are no shenanigans possible. Please resolve the agenda.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
    }

    private static List<Button> getVoteButtonsVersion2(int minVote, int voteTotal) {
        List<Button> voteButtons = new ArrayList<>();
        if (minVote < 0) {
            minVote = 0;
        }
        for (int x = minVote; x < voteTotal + 1; x++) {
            Button button = Buttons.gray("resolveAgendaVote_" + x, "" + x);
            voteButtons.add(button);
        }
        Button button = Buttons.red("distinguished_" + voteTotal, "Increase Votes");
        voteButtons.add(button);
        return voteButtons;
    }

    public static void startTheVoting(Game game) {
        game.setPhaseOfGame("agendaVoting");
        if (!game.getStoredValue("CommFormPreset").isEmpty()) {
            autoResolve(
                    null,
                    game.getPlayerFromColorOrFaction(game.getStoredValue("CommFormPreset")),
                    "autoresolve_manualcommittee",
                    game);
            game.removeStoredValue("CommFormPreset");
            return;
        }
        if (game.getCurrentAgendaInfo() != null) {
            Player nextInLine = null;
            try {
                nextInLine = getNextInLine(null, getVotingOrder(game), game);
            } catch (Exception e) {
                BotLogger.error(new LogOrigin(game), "Could not find next in line", e);
            }
            if (nextInLine == null) {
                BotLogger.warning(new LogOrigin(game), "`startTheVoting` is **null**");
                return;
            }
            String realIdentity = nextInLine.getRepresentationUnfogged();
            int[] voteInfo = getVoteTotal(nextInLine, game);
            int counter = 0;
            boolean willPrevote =
                    !game.getStoredValue("preVoting" + nextInLine.getFaction()).isEmpty()
                            && !"0".equalsIgnoreCase(game.getStoredValue("preVoting" + nextInLine.getFaction()));
            while ((voteInfo[0] < 1
                            || game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())
                            || willPrevote)
                    && counter < game.getRealPlayers().size()) {
                String skippedMessage = nextInLine.getRepresentation(true, false)
                        + ", you are being skipped because the bot thinks you can't vote.";
                if (game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                    skippedMessage = realIdentity
                            + ", you are being skipped because you told the bot you wanted to preset an abstention.";
                    game.setStoredValue(
                            "Abstain On Agenda",
                            game.getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (willPrevote) {
                    ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                    skippedMessage = realIdentity + " had logged a pre-vote.";
                    String votes = game.getStoredValue("preVoting" + nextInLine.getFaction());
                    game.setStoredValue("preVoting" + nextInLine.getFaction(), "");
                    for (String thing : nextInLine.getSpentThingsThisWindow()) {
                        if (thing.contains("tg_") || thing.contains("infantry_")) {
                            continue;
                        }
                        exhaustForVotes(null, nextInLine, game, "exhaustForVotes_" + thing, true);
                    }
                    MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                    resolvingAnAgendaVote("resolveAgendaVote_" + votes, null, game, nextInLine);
                    return;
                }
                if (game.isFowMode()) {
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, game, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                }
                nextInLine = getNextInLine(nextInLine, getVotingOrder(game), game);
                realIdentity = nextInLine.getRepresentationUnfogged();
                voteInfo = getVoteTotal(nextInLine, game);
                willPrevote = !game.getStoredValue("preVoting" + nextInLine.getFaction())
                                .isEmpty()
                        && !"0".equalsIgnoreCase(game.getStoredValue("preVoting" + nextInLine.getFaction()));
                counter += 1;
            }

            String message = AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n" + realIdentity
                    + " up to vote! Please use the buttons to choose the outcome you wish to vote for.";
            String pFaction = StringUtils.capitalize(nextInLine.getFaction());
            String factionChecker = "FFCC_" + nextInLine.getFaction() + "_";
            Button vote = Buttons.blue(factionChecker + "vote", pFaction + " Choose To Vote");
            Button abstain;
            if (nextInLine.hasAbility("future_sight")
                    && game.getStoredValue("executiveOrder").isEmpty()) {
                abstain = Buttons.red(
                        factionChecker + "resolveAgendaVote_0",
                        pFaction + " Choose To Abstain (You Have Future Sight)");
            } else {
                abstain = Buttons.red(factionChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
            }
            Button forcedAbstain = Buttons.gray(
                    "forceAbstainForPlayer_" + nextInLine.getFaction(), "(For Others) Abstain For This Player");
            try {
                game.updateActivePlayer(nextInLine);
                game.setStoredValue("preVoting" + nextInLine.getFaction(), "");
            } catch (Exception e) {
                BotLogger.error(new LogOrigin(game), "Could not update active player", e);
            }
            List<Button> buttons = List.of(vote, abstain, forcedAbstain);
            if (game.isFowMode()) {
                if (nextInLine.getPrivateChannel() != null) {
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), message, buttons);
                    game.getMainGameChannel()
                            .sendMessage("Voting started. Notified first in line")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                }
            } else {
                MessageHelper.sendMessageToChannelWithButtons(nextInLine.getCorrectChannel(), message, buttons);
            }
            ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
            CryypterHelper.checkForMentakEnvoy(nextInLine, game);
        } else {
            game.getMainGameChannel()
                    .sendMessage("Cannot find voting info, sorry. Please resolve automatically.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static List<Player> getWinningRiders(String winner, Game game, GenericInteractionCreateEvent event) {
        List<Player> winningRs = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (Entry<String, String> entry : outcomes.entrySet()) {
            String outcome = entry.getKey();
            StringTokenizer vote_info2 = new StringTokenizer(entry.getValue(), ";");
            while (vote_info2.hasMoreTokens()) {
                String specificVote = vote_info2.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf('_'));
                Player keleres = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                if (keleres != null && specificVote.contains("Keleres Xxcha Hero")) {
                    int size = getLosingVoters(outcome, game).size()
                            + getAbstainingVoters(winner, game).size();
                    String message = keleres.getRepresentation()
                            + " You have Odlynn Myrr, the Keleres (Xxcha) Hero, to resolve. There were " + size
                            + " players who abstained or voted for a different outcome, so you get that many trade goods and command tokens. ";
                    MessageHelper.sendMessageToChannel(keleres.getCorrectChannel(), message);
                    if (size > 0) {
                        keleres.setTg(keleres.getTg() + size);
                        String msg2 = "Gained " + StringHelper.pluralize(size, "trade good") + " ("
                                + (keleres.getTg() - size) + " -> **" + keleres.getTg() + "**).";
                        ButtonHelperAgents.resolveArtunoCheck(keleres, size);
                        MessageHelper.sendMessageToChannel(keleres.getCorrectChannel(), msg2);
                        List<Button> buttons = ButtonHelper.getGainCCButtons(keleres);
                        String trueIdentity = keleres.getRepresentationUnfogged();
                        String msg3 = trueIdentity + "! Your current command tokens are "
                                + keleres.getCCRepresentation()
                                + ". Use buttons to gain command tokens.";
                        game.setStoredValue("originalCCsFor" + keleres.getFaction(), keleres.getCCRepresentation());
                        MessageHelper.sendMessageToChannelWithButtons(keleres.getCorrectChannel(), msg3, buttons);
                        for (int x = 0; x < size; x++) {
                            ButtonHelperAbilities.pillageCheck(keleres, game);
                        }
                    }
                }
            }
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf('_'));
                    Player winningR = game.getPlayerFromColorOrFaction(faction.toLowerCase());

                    if (winningR != null && specificVote.contains("Sanction")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, game);
                        for (Player p2 : loseFleetPlayers) {
                            MahactTokenService.removeFleetCC(game, p2, "due to voting the same way as a _Sanction_");
                        }
                    }

                    if (winningR != null && specificVote.contains("Corporate Lobbying")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, game);
                        for (Player p2 : loseFleetPlayers) {
                            p2.setTg(p2.getTg() + 2);
                            ButtonHelperAgents.resolveArtunoCheck(p2, 2);
                            ButtonHelperAbilities.pillageCheck(p2, game);
                            MessageHelper.sendMessageToChannel(
                                    p2.getCorrectChannel(),
                                    p2.getRepresentation()
                                            + " you gained trade goods due to voting the same way as _Corporate Lobbying_.");
                            ButtonHelper.checkFleetInEveryTile(p2, game);
                        }
                    }

                    if (winningR != null && specificVote.contains("Public Outrage")) {
                        PublicOutrageAcd2ButtonHandler.resolveWinningPublicOutrage(game, winningR, winner);
                    }

                    if (winningR != null && specificVote.contains("Settlements")) {
                        SettlementsAcd2ButtonHandler.resolveWinningSettlements(game, winningR, winner);
                    }

                    if (winningR != null
                            && (specificVote.contains("Rider")
                                    || (winningR.hasAbility("future_sight")
                                            && game.getStoredValue("executiveOrder")
                                                    .isEmpty())
                                    || winningR.hasTech("dsatokcr")
                                    || winningR.hasUnit("kaltrim_mech")
                                    || specificVote.contains("Radiance")
                                    || specificVote.contains("Tarrock Ability"))) {

                        MessageChannel channel = winningR.getCorrectChannel();
                        String identity = winningR.getRepresentationUnfogged();
                        if (specificVote.contains("Galactic Threat Rider")) {
                            List<Player> voters = getWinningVoters(winner, game);
                            List<String> potentialTech = new ArrayList<>();
                            for (Player techGiver : voters) {
                                potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(
                                        winningR, techGiver, potentialTech, game);
                            }
                            List<Button> nekroBs = ButtonHelperAbilities.getButtonsForPossibleTechForNekro(
                                    winningR, potentialTech, game);
                            for (Player techGiver : voters) {
                                if (winningR.hasUnlockedBreakthrough("nekrobt")
                                        && !game.playerHasLeaderUnlockedOrAlliance(techGiver, "bastioncommander")) {
                                    if (!game.getStoredValue("valefarZ").contains(techGiver.getFaction())) {
                                        String vfzID =
                                                winningR.factionButtonChecker() + "valefarZ_" + techGiver.getFaction();
                                        nekroBs.add(Buttons.blue(vfzID, "Copy Flagship", techGiver.getFactionEmoji()));
                                    }
                                }
                            }
                            MessageHelper.sendMessageToChannelWithButtons(
                                    channel,
                                    identity + ", please resolve **Galactic Threat** ability using the buttons.",
                                    nekroBs);
                        }

                        if (specificVote.contains("Technology Rider") && !winningR.hasAbility("propagation")) {

                            MessageHelper.sendMessageToChannelWithButtons(
                                    channel,
                                    identity
                                            + ", please resolve _Technology Rider_ by using the button to research 1 technology.",
                                    List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Schematics Rider")) {

                            MessageHelper.sendMessageToChannelWithButtons(
                                    channel,
                                    identity
                                            + ", please resolve _Schematics Rider_ by using the button to get the pre-selected technology.",
                                    List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Leadership Rider")) {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                            String message = identity + ", your current command tokens are "
                                    + winningR.getCCRepresentation()
                                    + ". Use buttons to gain command tokens.";
                            game.setStoredValue(
                                    "originalCCsFor" + winningR.getFaction(), winningR.getCCRepresentation());
                            MessageHelper.sendMessageToChannel(
                                    channel,
                                    identity
                                            + " resolve _Leadership Rider_ by using the button to gain 3 command tokens.");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Technology Rider") && winningR.hasAbility("propagation")) {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                            String message = winningR.getRepresentation()
                                    + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                                    + " Your current command tokens are " + winningR.getCCRepresentation()
                                    + ". Use buttons to gain command tokens.";
                            game.setStoredValue(
                                    "originalCCsFor" + winningR.getFaction(), winningR.getCCRepresentation());
                            MessageHelper.sendMessageToChannel(
                                    channel,
                                    identity
                                            + " resolve _Technology Rider_ (with **Propagation**) by using the button to gain 3 command tokens.");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Keleres Rider")) {
                            StringBuilder sb = new StringBuilder(identity);
                            sb.append(" due to having a winning _Keleres Rider_, you have been given");
                            if (winningR.hasAbility("scheming")) {
                                sb.append(" 2 action cards  (**Scheming** increases this from the normal")
                                        .append(" 1 action card; discard buttons have been sent to")
                                        .append(" your `#cards-info` thread)");
                            } else {
                                sb.append(" 1 action card");
                            }
                            sb.append(" and 2 trade goods ")
                                    .append(winningR.gainTG(2))
                                    .append(".");
                            MessageHelper.sendMessageToChannel(channel, sb.toString());
                            ActionCardHelper.drawActionCardsSilent(winningR, 1);
                            ButtonHelperAbilities.pillageCheck(winningR, game);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, 2);
                        }
                        if (specificVote.contains("Politics Rider")) {
                            String message =
                                    identity + " due to having a winning _Politics Rider_, you have been given ";
                            if (winningR.hasAbility("scheming")) {
                                message += "4 action cards (**Scheming** increases this from the normal 1 action ";
                                message += "card; discard buttons have been sent to your `#cards-info` thread)";
                            } else {
                                message += "3 action cards";
                            }
                            message += " and the " + MiscEmojis.SpeakerToken + " Speaker Token";
                            game.setSpeakerUserID(winningR.getUserID());
                            game.setStoredValue("oldSpeakerExecutiveOrder", game.getSpeakerUserID());
                            MessageHelper.sendMessageToChannel(channel, message);
                            ActionCardHelper.drawActionCardsSilent(winningR, 3);
                        }
                        if (specificVote.contains("Diplomacy Rider")) {
                            String message = identity
                                    + ", you have a _Diplomacy Rider_ to resolve. Choose the system you wish to Diplo.";
                            List<Button> buttons = Helper.getPlanetSystemDiploButtons(winningR, game, true, null);
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Construction Rider")) {
                            String message = identity
                                    + ", you have a _Construction Rider_ to resolve. Please choose the planet you wish to place your space dock on.";
                            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(winningR, game, "sd", "place");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Warfare Rider")) {
                            String message = identity
                                    + ", you have a _Warfare Rider_ to resolve. Please choose the system where you wish to place the dreadnought.";
                            List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(
                                    winningR, game, "dreadnought", "placeOneNDone_skipbuild");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Armament Rider")) {
                            String message = identity
                                    + ", you have an _Armament Rider_ to resolve. Please choose the system in which you wish to produce 2 units each with cost 4 or less.";

                            List<Tile> tiles = CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                    game, winningR, UnitType.Spacedock);
                            List<Button> buttons = new ArrayList<>();
                            for (Tile tile : tiles) {
                                Button starTile = Buttons.green(
                                        "umbatTile_" + tile.getPosition(),
                                        tile.getRepresentationForButtons(game, winningR));
                                buttons.add(starTile);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Production Rider")) {
                            String message = identity
                                    + ", you have a _Production Rider_ to resolve. Please choose the system in which you wish to produce up to 2 units each with cost 4 or less.";

                            List<Tile> tiles = CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                    game, winningR, UnitType.Spacedock);
                            List<Button> buttons = new ArrayList<>();
                            for (Tile tile : tiles) {
                                Button starTile = Buttons.green(
                                        "umbatTile_" + tile.getPosition(),
                                        tile.getRepresentationForButtons(game, winningR));
                                buttons.add(starTile);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Defense Rider")) {
                            String message = identity
                                    + ", you have a _Defense Rider_ to resolve. Please choose up to 2 planets on which you wish to place PDS.";
                            List<Button> buttons = List.of(Buttons.green(
                                    winningR.factionButtonChecker() + "resolveDefenseRider", "Resolve Defense Rider"));
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }

                        if (specificVote.contains("Project Rider")) {
                            List<Button> buttons = List.of(Buttons.green(
                                    winningR.factionButtonChecker() + "resolveProjectRiderReward",
                                    "Resolve Project Rider"));
                            MessageHelper.sendMessageToChannelWithButtons(
                                    channel,
                                    identity
                                            + ", you have a _Project Rider_ to resolve. Use the button to retrieve the action cards you selected when you played it.",
                                    buttons);
                        }

                        if (specificVote.contains("Classified Rider")) {
                            List<Button> buttons = List.of(Buttons.green(
                                    winningR.factionButtonChecker() + "resolveClassifiedRider",
                                    "Resolve Classified Rider"));
                            MessageHelper.sendMessageToChannelWithButtons(
                                    channel,
                                    identity
                                            + ", you have a _Classified Rider_ to resolve. Use the button to choose 1 of the top 3 secret objectives.",
                                    buttons);
                        }

                        if (specificVote.contains("Trade Rider")) {
                            MessageHelper.sendMessageToChannel(
                                    channel,
                                    identity
                                            + ", due to having a winning _Trade Rider_, you have been given five trade goods "
                                            + winningR.gainTG(5) + ".");
                            ButtonHelperAbilities.pillageCheck(winningR, game);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, 5);
                        }
                        if (specificVote.contains("Frontier Rider")) {
                            ButtonHelperStats.replenishComms(event, game, winningR, true);
                            List<Button> buttons = ButtonHelperActionCards.getFrontierTokenButtons(game, winningR);
                            String message = identity
                                    + ", due to having a winning _Frontier Rider_, your commodities have been replenished"
                                    + " and you may explore a frontier token on the game board.";
                            if (buttons.isEmpty()) {
                                MessageHelper.sendMessageToChannel(
                                        channel, message + " There are no frontier tokens available to explore.");
                            } else {
                                MessageHelper.sendMessageToChannelWithButtons(
                                        channel, message + " Choose the system you wish to explore.", buttons);
                            }
                        }
                        if (specificVote.contains("Relic Rider")) {
                            MessageHelper.sendMessageToChannel(
                                    channel,
                                    identity + " due to having a winning _Relic Rider_, you have gained a Relic.");
                            RelicHelper.drawRelicAndNotify(winningR, event, game);
                        }
                        if (specificVote.contains("Exploration Rider")) {
                            String message = identity
                                    + ", you have a winning _Exploration Rider_. Choose a non-cultural planet to explore.";
                            MessageHelper.sendMessageToChannel(channel, message);
                            ButtonHelperActionCards.sendExplorationRiderButtons(winningR, game, 3, Set.of());
                        }
                        if (specificVote.contains("Radiance")) {
                            List<Tile> tiles = CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                    game, winningR, UnitType.Mech);
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, game, tiles);
                        }
                        if (specificVote.contains("Atokera Commander")) {
                            if (!getWinningVoters(winner, game).contains(winningR)
                                    && !getLosingVoters(winner, game).contains(winningR)) {
                                List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                                String message = identity + ", your current command tokens are "
                                        + winningR.getCCRepresentation()
                                        + ". Use buttons to gain command tokens.";
                                game.setStoredValue(
                                        "originalCCsFor" + winningR.getFaction(), winningR.getCCRepresentation());
                                MessageHelper.sendMessageToChannel(
                                        channel,
                                        identity
                                                + ", please resolve Ordagka, the Atokera commander, by using the button to gain 1 command token.");
                                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                            }
                        }
                        if (specificVote.contains("Tarrock Ability")) {
                            String message = winningR.getFactionEmoji() + " drew a secret objective.";
                            game.drawSecretObjective(winningR.getUserID());
                            if (winningR.hasAbility("plausible_deniability")) {
                                game.drawSecretObjective(winningR.getUserID());
                                message += " Drew a second secret objective due to **Plausible Deniability**.";
                            }
                            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, winningR);
                            MessageHelper.sendMessageToChannel(winningR.getCorrectChannel(), message);
                        }
                        if (specificVote.contains("Kyro Rider")) {
                            String message = winningR.getRepresentationUnfogged()
                                    + ", please choose which planet you wish to drop 3 infantry on";
                            List<Button> buttons = new ArrayList<>(
                                    Helper.getPlanetPlaceUnitButtons(winningR, game, "3gf", "placeOneNDone_skipbuild"));
                            MessageHelper.sendMessageToChannelWithButtons(
                                    winningR.getCorrectChannel(), message, buttons);
                        }
                        if (specificVote.contains("Edyn Rider")) {
                            List<Tile> tiles = new ArrayList<>();
                            for (Tile tile : game.getTileMap().values()) {
                                if (FoWHelper.playerHasUnitsInSystem(winningR, tile)) {
                                    tiles.add(tile);
                                }
                            }
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, game, tiles);
                        }
                        if (specificVote.contains(Constants.IMPERIAL_RIDER)) {
                            String msg = identity
                                    + " due to having a winning _Imperial Rider_, you have scored a victory point. Huzzah.\n";
                            int poIndex;
                            poIndex = game.addCustomPO(Constants.IMPERIAL_RIDER, 1);
                            msg += "Custom public objective _Imperial Rider_ has been added.\n";
                            game.scorePublicObjective(winningR.getUserID(), poIndex);
                            msg += winningR.getRepresentation() + " scored _Imperial Rider_.\n";
                            MessageHelper.sendMessageToChannel(channel, msg);
                            Helper.checkEndGame(game, winningR);
                        }
                        if (!winningRs.contains(winningR)) {
                            winningRs.add(winningR);
                        }
                    }
                }
                CryypterHelper.handleWinningRiders(game, winner);
            }
        }
        return winningRs;
    }

    private static List<Player> getRiders(Game game) {
        List<Player> riders = new ArrayList<>();

        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String s : outcomes.values()) {
            StringTokenizer vote_info = new StringTokenizer(s, ";");

            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf('_'));
                String vote = specificVote.substring(specificVote.indexOf('_') + 1);
                if (vote.contains("Rider") || vote.contains("Sanction")) {
                    Player rider = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (rider != null) {
                        riders.add(rider);
                    }
                }
            }
        }
        return riders;
    }

    private static List<Player> getLosers(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (Entry<String, String> entry : outcomes.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf('_'));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null && !losers.contains(loser)) {
                        losers.add(loser);
                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getWinningVoters(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (Entry<String, String> entry : outcomes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(winner)) {
                StringTokenizer voteInfo = new StringTokenizer(entry.getValue(), ";");

                while (voteInfo.hasMoreTokens()) {
                    String specificVote = voteInfo.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf('_'));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null
                            && !specificVote.contains("Rider")
                            && !specificVote.contains("Sanction")
                            && !specificVote.contains("Ability")) {
                        if (!losers.contains(loser)) {
                            losers.add(loser);
                        }
                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getLosingVoters(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (Entry<String, String> entry : outcomes.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf('_'));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser)
                                && !specificVote.contains("Rider")
                                && !specificVote.contains("Sanction")
                                && !specificVote.contains("Radiance")
                                && !specificVote.contains("Unity")
                                && !specificVote.contains("Hero")
                                && !specificVote.contains("Ability")) {
                            losers.add(loser);
                        }
                    }
                }
            }
        }
        return losers;
    }

    private static List<Player> getAbstainingVoters(String winner, Game game) {
        List<Player> abstainers = new ArrayList<>();
        List<Player> losers = getLosingVoters(winner, game);
        List<Player> winners = getWinningVoters(winner, game);
        for (Player player : game.getRealPlayers()) {
            int[] voteInfo = getVoteTotal(player, game);
            if (!losers.contains(player) && !winners.contains(player) && voteInfo[0] > 0) {
                abstainers.add(player);
            }
        }

        return abstainers;
    }

    public static void atokeraCommanderUnlockCheck(Game game) {
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        Player highestVoter = null;
        int highestVote = 0;

        for (String s : outcomes.values()) {
            StringTokenizer vote_info = new StringTokenizer(s, ";");

            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf('_'));
                Player voter = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                if (voter != null) {
                    if (!specificVote.contains("Rider")
                            && !specificVote.contains("Sanction")
                            && !specificVote.contains("Radiance")
                            && !specificVote.contains("Unity")
                            && !specificVote.contains("Ability")) {
                        String voteS = specificVote.split("_")[1];
                        if (NumberUtils.isDigits(voteS)) {
                            int vote = Integer.parseInt(voteS);

                            if (vote == highestVote) {
                                highestVoter = null;
                            } else {
                                if (vote > highestVote) {
                                    vote = highestVote;
                                    highestVoter = voter;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (highestVoter != null) {
            CommanderUnlockCheckService.checkPlayer(highestVoter, "atokera", "xan");
        }
    }

    public static List<Player> getPlayersWithMostPoints(Game game) {
        List<Player> losers = new ArrayList<>();
        int most = 0;
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() > most) {
                most = p.getTotalVictoryPoints();
            }
        }
        for (Player p : Helper.getSpeakerOrFullPriorityOrder(game)) {
            if (p.getTotalVictoryPoints() == most) {
                losers.add(p);
            }
        }
        return losers;
    }

    public static List<Player> getPlayersWithLeastPoints(Game game) {
        List<Player> losers = new ArrayList<>();
        int least = 20;
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() < least) {
                least = p.getTotalVictoryPoints();
            }
        }
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() == least) {
                losers.add(p);
            }
        }
        return losers;
    }

    static int[] getVoteTotal(Player player, Game game) {
        int hasXxchaAlliance = game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander") ? 1 : 0;
        int hasXxchaHero = player.hasLeaderUnlocked("xxchahero") ? 1 : 0;
        int voteCount = getTotalVoteCount(game, player);

        // Check if Player only has additional votes but not any "normal" votes, if so,
        // they can't vote
        if (getVoteCountFromPlanets(game, player) == 0) {
            voteCount = 0;
        }

        if (game.getLaws() != null
                && (game.getLaws().containsKey("rep_govt"))
                && game.getStoredValue("executiveOrder").isEmpty()) {
            voteCount = 1;
        }

        if ("nekro".equals(player.getFaction()) && hasXxchaAlliance == 0) {
            voteCount = 0;
        }
        List<Player> riders = getRiders(game);
        if (riders.contains(player)) {
            if (hasXxchaAlliance == 0) {
                voteCount = 0;
            }
        }

        if (hasXxchaAlliance == 0
                && (game.getStoredValue("AssassinatedReps").contains(player.getFaction())
                        || (game.getStoredValue("PublicExecution").contains(player.getFaction())
                                && game.getStoredValue("executiveOrder").isEmpty()))) {
            voteCount = 0;
        }

        if (game.isStellarAtomicsMode()
                && hasXxchaAlliance == 0
                && game.getRevealedPublicObjectives().get("Stellar Atomics") != null) {
            if (!game.getScoredPublicObjectives().get("Stellar Atomics").contains(player.getUserID())) {
                voteCount = 0;
            }
        }

        return new int[] {voteCount, hasXxchaHero, hasXxchaAlliance};
    }

    private static List<Player> getVotingOrder(Game game) {
        List<Player> orderList = Helper.getSpeakerOrFullPriorityOrder(game);
        String speakerName = game.getSpeakerUserID();
        Optional<Player> optSpeaker = orderList.stream()
                .filter(player -> player.getUserID().equals(speakerName))
                .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }
        if (game.isReverseSpeakerOrder()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }
        if (game.isHasHackElectionBeenPlayed()
                && game.getStoredValue("hackElectionFaction").isEmpty()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }

        // Check if Argent Flight is in the game - if it is, put it at the front of the
        // vote list.
        Optional<Player> argentPlayer = orderList.stream()
                .filter(player -> player.getFaction() != null && player.hasAbility("zeal"))
                .findFirst();
        if (argentPlayer.isPresent() && game.getStoredValue("executiveOrder").isEmpty()) {
            orderList.remove(argentPlayer.orElse(null));
            orderList.addFirst(argentPlayer.get());
        }
        String conspiratorsFaction = game.getStoredValue("conspiratorsFaction");
        if (!conspiratorsFaction.isEmpty()) {
            Player rhodun = game.getPlayerFromColorOrFaction(conspiratorsFaction);
            Optional<Player> speaker = orderList.stream()
                    .filter(player -> player.getFaction() != null
                            && game.getSpeakerUserID().equals(player.getUserID()))
                    .findFirst();
            if (speaker.isPresent() && rhodun != null) {
                orderList.remove(rhodun);
                orderList.add(orderList.indexOf(speaker.get()) + 1, rhodun);
            }
        }
        if (game.isHasHackElectionBeenPlayed()
                && !game.getStoredValue("hackElectionFaction").isEmpty()) {
            Player hacker = game.getPlayerFromColorOrFaction(game.getStoredValue("hackElectionFaction"));
            orderList.remove(hacker);
            orderList.add(hacker);
        }

        // Check if Player has Edyn Mandate faction tech - if it is, put it at the end of the vote list.
        Optional<Player> edynPlayer = orderList.stream()
                .filter(player -> player.getFaction() != null && player.hasTech("dsedyny"))
                .findFirst();
        if (edynPlayer.isPresent()) {
            orderList.remove(edynPlayer.orElse(null));
            orderList.add(edynPlayer.get());
        }
        return orderList;
    }

    private static Player getNextInLine(Player player1, List<Player> votingOrder, Game game) {
        boolean foundPlayer = false;
        if (player1 == null) {
            for (int x = 0; x < 6; x++) {
                if (x < votingOrder.size()) {
                    Player player = votingOrder.get(x);
                    if (player == null) {
                        BotLogger.warning(
                                new LogOrigin(game), "`getNextInLine` Hit a null player in game " + game.getName());
                        return null;
                    }

                    if (player.isRealPlayer()) {
                        return player;
                    } else {
                        BotLogger.warning(
                                new LogOrigin(game),
                                "`getNextInLine` Hit a notRealPlayer player in game " + game.getName() + " on player "
                                        + player.getUserName());
                    }
                }
            }
            return null;
        }
        for (Player player2 : votingOrder) {
            if (player2 == null || player2.isDummy()) {
                continue;
            }
            if (foundPlayer && player2.isRealPlayer()) {
                return player2;
            }
            if (player1.getColor().equalsIgnoreCase(player2.getColor())) {
                foundPlayer = true;
            }
        }

        return player1;
    }

    private static void checkForPoliticalSecret(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ps")
                    && player.getPromissoryNotesOwned().contains(player.getColor() + "_ps")) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        player.getRepresentation()
                                + ", this is a reminder that you don't currently hold your _Political Secret_."
                                + " Any \"when\"s or \"after\"s that you queue will be automatically canceled if it is played by another player.");
            }
            if (game.getCurrentAgendaInfo().contains("Player")
                    && IsPlayerElectedService.isPlayerElected(game, player, "committee")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("presetCommitteeFormation", "Preset Committee Formation"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        player.getRepresentation()
                                + ", you can use this button to preset _Committee Formation_ to resolve following all the \"after\".",
                        buttons);
            }
        }
    }

    @ButtonHandler("presetCommitteeFormation")
    public static void presetCommitteeFormation(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentation() + " you successfully preset a play of _Committee Formation_.");
        game.setStoredValue("CommFormPreset", player.getFaction());
    }

    @ButtonHandler("exhaustForVotes_")
    public static void exhaustForVotes(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        exhaustForVotes(event, player, game, buttonID, false);
    }

    private static void exhaustForVotes(
            ButtonInteractionEvent event, Player player, Game game, String buttonID, boolean finalRes) {
        String thing = buttonID.replace("exhaustForVotes_", "");

        boolean prevoting =
                !game.getStoredValue("preVoting" + player.getFaction()).isEmpty();
        if (!thing.contains("hacan") && !thing.contains("kyro") && !thing.contains("allPlanets")) {
            if (!finalRes) {
                player.addSpentThing(thing);
            }
            if (thing.contains("planet_") && !prevoting) {
                String planet = thing.replace("planet_", "");
                player.exhaustPlanet(planet);
                UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                if (uH != null) {
                    if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                        Tile tile = game.getTileFromPlanet(planet);
                        String msg = player.getRepresentation() + " added 1 infantry to " + planet
                                + " due to the _Arcane Citadel_.";
                        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    }
                }
            }
            if (thing.contains("dsghotg") && !prevoting) {
                player.exhaustTech("dsghotg");
            }

            if (thing.contains("predictive")) {
                game.setStoredValue("riskedPredictive", game.getStoredValue("riskedPredictive") + player.getFaction());
            }
            if (!finalRes) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            }
        } else {
            if (thing.contains("hacan")) {
                if (!prevoting) {
                    player.setTg(player.getTg() - 1);
                }
                if (!finalRes) {
                    player.increaseTgsSpentThisWindow(1);
                }
                if (player.getTg() < 1) {
                    ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
                }
            }
            if (thing.contains("kyro")) {
                player.increaseInfantrySpentThisWindow(1);
                if (!prevoting) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + " please remove 1 infantry to pay for Silas Deriga, the Kyro commander.",
                            ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "infantry"));
                }
            }
            if (thing.contains("allPlanets")) {
                List<String> unexhaustedPs = new ArrayList<>(player.getReadiedPlanets());
                for (String planet : unexhaustedPs) {
                    if (getSpecificPlanetsVoteWorth(player, game, planet) > 0) {
                        if (!finalRes) {
                            player.addSpentThing("planet_" + planet);
                        }
                        if (!prevoting) {
                            player.exhaustPlanet(planet);
                        }
                    }
                    if (!prevoting) {
                        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                        if (uH != null) {
                            if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                                Tile tile = game.getTileFromPlanet(planet);
                                String msg = player.getRepresentation() + " added 1 infantry to " + planet
                                        + " due to the _Arcane Citadel_.";
                                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
                                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                            }
                        }
                    }
                }
                if (!finalRes) {
                    ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
                }
            }
        }
        if (!finalRes) {
            String editedMessage = Helper.buildSpentThingsMessageForVoting(player, game, false);
            editedMessage = AgendaSummaryHelper.getSummaryOfVotes(game, true) + "\n\n" + editedMessage;
            event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static int getSpecificPlanetsVoteWorth(Player player, Game game, String planet) {
        int voteAmount = 0;
        Planet p = game.getPlanetsInfo().get(planet);
        if (p == null) return 0;

        int[] voteInfo = getVoteTotal(player, game);

        voteAmount += p.getInfluence();
        if (player.hasAbility("lithoids")) {
            voteAmount = p.getResources();
        }
        if (player.hasAbility("biophobic")) {
            voteAmount = 1;
        }

        boolean executive = player.getFaction().equals(game.getStoredValue("executiveOrder"));
        if (player.hasUnlockedBreakthrough("xxchabt") || executive) {
            voteAmount = Math.max(p.getResources(), p.getInfluence());
        }

        if (voteInfo[1] != 0) {
            voteAmount += p.getResources();
        }
        if (voteInfo[2] != 0) {
            voteAmount += 1;
        }

        if (player.hasAbility("policy_the_people_control")) {
            if (p.getPlanetTypes().contains(Constants.CULTURAL)) {
                voteAmount += 2;
            }
        }
        for (String attachment : p.getTokenList()) {
            if (attachment.contains("council_preserve")) {
                voteAmount += 5;
            }
        }
        if (game.getLaws().containsKey("absol_government")) {
            voteAmount = 1;
            if ("mr".equalsIgnoreCase(planet) || "mrte".equalsIgnoreCase(planet)) {
                voteAmount++;
            }
        }
        voteAmount += TaLeadersHandler.getLenPredeclaredVoteBonus(game, player, planet);
        return voteAmount;
    }

    private static List<Button> getPlanetButtonsVersion2(
            GenericInteractionCreateEvent event, Player player, Game game) {
        player.resetSpentThings();
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        int totalPlanetVotes = 0;
        for (String planet : planets) {
            int voteAmount = getSpecificPlanetsVoteWorth(player, game, planet);
            String planetNameProper = planet;
            PlanetModel planetModel = Mapper.getPlanet(planet);
            if (planetModel.getName() != null) {
                planetNameProper = planetModel.getName();
            } else {
                BotLogger.warning(
                        new LogOrigin(event),
                        "TEMP BOTLOG: A bad PlanetModel was found for planet: " + planet
                                + " - using the planet id instead of the model name");
            }
            if (voteAmount != 0) {
                Button button = Buttons.gray(
                        "exhaustForVotes_planet_" + planet,
                        planetNameProper + " (" + voteAmount + ")"
                                + TaLeadersHandler.getLenVoteLabelSuffix(game, player, planet),
                        PlanetEmojis.getPlanetEmoji(planet));
                planetButtons.add(button);
            }
            totalPlanetVotes += voteAmount;
        }
        if (!game.getLaws().containsKey("absol_government")) {
            if (player.hasAbility("zeal")
                    || (game.isOrdinianC1Mode() && player == ButtonHelper.getPlayerWhoControlsCoatl(game))) {
                int numPlayers = 0;
                for (Player player_ : game.getPlayers().values()) {
                    if (player_.isRealPlayer()) numPlayers++;
                }
                planetButtons.add(Buttons.blue(
                        "exhaustForVotes_zeal_" + numPlayers,
                        "Special Argent Votes (" + numPlayers + ")",
                        FactionEmojis.Argent));
            }

            if (player.hasTechReady("pi") || player.hasTechReady("absol_pi")) {
                planetButtons.add(Buttons.blue(
                        "exhaustForVotes_predictive_3",
                        "Use Predictive Intelligence Votes (3)",
                        TechEmojis.CyberneticTech));
            }

            if (game.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
                planetButtons.add(Buttons.gray(
                        "exhaustForVotes_hacanCommanderTg", "Spend Trade Goods for 2 Votes Each", FactionEmojis.Hacan));
            }
            if (player.isSpeaker() && !game.getStoredValue("executiveOrder").isEmpty()) {
                planetButtons.add(Buttons.gray(
                        "exhaustForVotes_hacanCommanderTg",
                        "Spend Trade Goods for 1 Vote Each",
                        FactionEmojis.Keleres));
            }

            if (game.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
                planetButtons.add(Buttons.gray(
                        "exhaustForVotes_kyrocommanderInf", "Kill Infantry for 1 Vote per Kill", FactionEmojis.kyro));
            }

            if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
                int count = player.getTechs().size() / 2;
                planetButtons.add(Buttons.gray(
                        "exhaustForVotes_augerscommander_" + count,
                        "Use Ilyxum Commander Votes (" + count + ")",
                        FactionEmojis.augers));
            }

            if (CollectionUtils.containsAny(
                    player.getRelics(),
                    List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
                int count = (int) player.getRelics().stream()
                        .filter(s -> s.contains("absol_shardofthethrone"))
                        .count();
                int shardVotes = 2 * count; // +2 votes per Absol shard
                Button button = Buttons.gray(
                        "exhaustForVotes_absolShard_" + shardVotes,
                        "Use Shard of the Throne Votes (" + shardVotes + ")",
                        SourceEmojis.Absol);
                planetButtons.add(button);
            }
            // Absol's Syncretone - +1 vote for each neighbour
            if (player.hasRelicReady("absol_syncretone")) {
                int count = game.getRealPlayers().size();
                Button button = Buttons.gray(
                        "exhaustForVotes_absolsyncretone_" + count,
                        "Use Syncretone Votes (" + count + ")",
                        SourceEmojis.Absol);
                planetButtons.add(button);
            }

            // Ghoti Wayfarer Tech
            if (player.hasTechReady("dsghotg")) {
                int fleetCC = player.getFleetCC();
                planetButtons.add(Buttons.gray(
                        "exhaustForVotes_dsghotg_" + fleetCC,
                        "Silly Ghoti Tech Votes (" + fleetCC + ")",
                        FactionEmojis.ghoti));
            }
        }
        planetButtons.add(Buttons.gray(
                "exhaustForVotes_allPlanets_" + totalPlanetVotes,
                "Exhaust All Voting Planets (" + totalPlanetVotes + ")"));
        planetButtons.add(
                Buttons.red(player.factionButtonChecker() + "proceedToFinalizingVote", "Done exhausting planets."));
        return planetButtons;
    }

    @ButtonHandler("refreshAgenda")
    public static void refreshAgenda(Game game) {
        String agendaDetails = game.getCurrentAgendaInfo();
        String agendaID = "CL";
        if (StringUtils.countMatches(agendaDetails, "_") > 2) {
            if (StringUtils.countMatches(agendaDetails, "_") > 3) {
                agendaID =
                        StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_");
            } else {
                agendaID = agendaDetails.split("_")[3];
            }
        }
        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        if (agendaModel == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Unable to refresh agenda; no active agenda found.");
            return;
        }
        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();

        String revealMessage = "Refreshed Agenda";
        MessageHelper.sendMessageToChannelWithEmbed(game.getMainGameChannel(), revealMessage, agendaEmbed);
        List<Button> proceedButtons = new ArrayList<>();
        String msg = "Buttons for various things";

        if (!game.isFowMode()) {
            listVoteCount(game, game.getMainGameChannel());
        }

        proceedButtons.add(Buttons.red("proceedToVoting", "Skip Waiting"));
        proceedButtons.add(Buttons.blue("transaction", "Transaction"));
        if (!game.isHiddenAgendaMode())
            proceedButtons.add(Buttons.red("eraseMyVote", "Erase my vote & have me vote again"));
        proceedButtons.add(Buttons.red("eraseMyRiders", "Erase my riders"));
        proceedButtons.add(Buttons.gray("refreshAgenda", "Refresh Agenda"));
        proceedButtons.add(Buttons.blue("pingNonresponders", "Ping Non-Responders"));

        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, proceedButtons);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), AgendaSummaryHelper.getSummaryOfVotes(game, true));
        }
    }

    @ButtonHandler("proceedToFinalizingVote")
    private static void proceedToFinalizingVote(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String votes = Helper.buildSpentThingsMessageForVoting(player, game, true);
        boolean prevoting =
                !game.getStoredValue("preVoting" + player.getFaction()).isEmpty();
        String msg = Helper.buildSpentThingsMessageForVoting(player, game, false) + "\n\n"
                + player.getRepresentation() + " you are currently " + (prevoting ? "pre-" : "") + "voting " + votes
                + " vote" + ("1".equals(votes) ? "" : "s");
        if (prevoting) {
            msg +=
                    ". You __must__ confirm this to have your pre-vote logged, or you may modify this number if the bot missed something.";
        } else {
            msg += ". You may confirm this, or you may modify this number if the bot missed something.";
        }
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            msg += " Any _Blood Pact_ votes will be automatically added.";
        }
        if (prevoting) {
            game.setStoredValue("preVoting" + player.getFaction(), votes);
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "resolveAgendaVote_" + votes,
                "Confirm " + votes + " vote" + ("1".equals(votes) ? "" : "s")));
        buttons.add(Buttons.blue(player.factionButtonChecker() + "distinguished_" + votes, "Modify Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    public static void resolveAbsolAgainstChecksNBalances(Game game) {
        StringBuilder message = new StringBuilder();
        // Integer poIndex = game.addCustomPO("Points Scored Prior to Absol Checks and
        // Balances Wipe", 1);
        // message.append("Custom PO 'Points Scored Prior to Absol Checks and Balances
        // Wipe' has been
        // added and people have scored it. \n");

        // game.scorePublicObjective(playerWL.getUserID(), poIndex);
        for (Player player : game.getRealPlayers()) {
            int currentPoints = player.getPublicVictoryPoints(false) + player.getSecretVictoryPoints();

            Integer poIndex = game.addCustomPO(
                    game.isFowMode()
                            ? ""
                            : StringUtils.capitalize(player.getColor()) + " VP Scored Prior to Agenda Wipe",
                    currentPoints);
            message.append("Custom public objective")
                    .append(
                            game.isFowMode()
                                    ? ""
                                    : " \""
                                            + StringUtils.capitalize(player.getColor()
                                                    + " VP Scored Prior to Agenda Wipe\" has been added and scored by that color, worth "
                                                    + currentPoints + " victory points"));
            message.append(".\n");
            game.scorePublicObjective(player.getUserID(), poIndex);
            Map<String, List<String>> playerScoredPublics = game.getScoredPublicObjectives();
            for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
                if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey())
                        || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())) {
                    if (scoredPublic.getValue().contains(player.getUserID())) {
                        game.unscorePublicObjective(player.getUserID(), scoredPublic.getKey());
                    }
                }
            }
            List<Integer> scoredSOs = new ArrayList<>(player.getSecretsScored().values());
            for (int soID : scoredSOs) {
                game.unscoreAndShuffleSecretObjective(player.getUserID(), soID);
            }
        }
        message.append(
                "All secret objectives have been returned to the deck and all public objectives scoring have been cleared. \n");

        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
    }

    private static void eraseVotesOfFaction(Game game, String faction) {
        if (game.getCurrentAgendaVotes().isEmpty()) {
            return;
        }
        Map<String, String> outcomes = new HashMap<>(game.getCurrentAgendaVotes());
        String voteSumm;

        for (Entry<String, String> entry : outcomes.entrySet()) {
            String outcome = entry.getKey();
            voteSumm = "";
            StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");

            StringBuilder voteSummBuilder = new StringBuilder(voteSumm);
            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction2 = specificVote.substring(0, specificVote.indexOf('_'));
                String vote = specificVote.substring(specificVote.indexOf('_') + 1);
                if (vote.contains("Rider")
                        || vote.contains("Sanction")
                        || vote.contains("Radiance")
                        || vote.contains("Unity Algorithm")
                        || vote.contains("Tarrock")
                        || vote.contains("Hero")) {
                    voteSummBuilder.append(";").append(specificVote);
                } else if (!faction2.equals(faction)) {
                    voteSummBuilder.append(";").append(specificVote);
                }
            }
            voteSumm = voteSummBuilder.toString();
            if ("".equalsIgnoreCase(voteSumm)) {
                game.removeOutcomeAgendaVote(outcome);
            } else {
                game.setCurrentAgendaVote(outcome, voteSumm);
            }
        }
    }

    private static String getWinner(Game game) {
        StringBuilder winner = new StringBuilder();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        int currentHighest = -1;
        for (Entry<String, String> entry : outcomes.entrySet()) {
            String outcome = entry.getKey();
            int totalVotes = 0;
            StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");
            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String vote = specificVote.split("_")[1];
                if (NumberUtils.isDigits(vote)) {
                    totalVotes += Integer.parseInt(vote);
                }
            }
            int votes = totalVotes;
            if (votes >= currentHighest && votes != 0) {
                if (votes == currentHighest) {
                    winner.append("*").append(outcome);
                } else {
                    currentHighest = votes;
                    winner = new StringBuilder(outcome);
                }
            }
        }
        return winner.toString();
    }

    static String getAgendaOutcomeName(Game game, String outcome, boolean capitalize) {
        String agendaDetails = game.getCurrentAgendaInfo();
        if (StringUtils.countMatches(agendaDetails, "_") > 1) {
            agendaDetails = agendaDetails.split("_")[1];
        }
        if (StringUtils.containsIgnoreCase(agendaDetails, "Secret")) {
            return Mapper.getSecretObjectivesJustNames().getOrDefault(outcome, outcome);
        }
        if (StringUtils.containsIgnoreCase(agendaDetails, "Elect Law")) {
            String lawTitle = Mapper.getAgendaTitleNoCap(outcome);
            if (StringUtils.isNotBlank(lawTitle)) {
                return lawTitle;
            }
        }
        if (StringUtils.containsIgnoreCase(agendaDetails, "unit upgrade") && Mapper.getTech(outcome) != null) {
            String techName = Mapper.getTech(outcome).getName();
            if (StringUtils.isNotBlank(techName)) {
                return techName;
            }
        }
        if (StringUtils.containsIgnoreCase(agendaDetails, "Elect Strategy Card") && NumberUtils.isDigits(outcome)) {
            return Helper.getSCName(Integer.parseInt(outcome), game);
        }
        if (StringUtils.containsIgnoreCase(agendaDetails, "Planet")) {
            PlanetModel planetModel = Mapper.getPlanet(outcome);
            if (planetModel != null && StringUtils.isNotBlank(planetModel.getName())) {
                return planetModel.getName();
            }
        }
        return capitalize ? StringUtils.capitalize(outcome) : outcome;
    }

    @ButtonHandler("ministerOfWar")
    public static void resolveMinisterOfWar(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        boolean success = game.removeLaw(game.getLaws().get("minister_war"));
        if (success) {
            String msg = "The _Minister of War_ law has been discarded.";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "## " + game.getPing() + " " + msg);
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
        }
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "ministerOfWar");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(
                channel, "Please choose which system you wish to remove your command token from.", buttons);
    }

    private static String getPlayerVoteText(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(game, player);
        Map<String, Integer> additionalVotes = getAdditionalVotesFromOtherSources(game, player);
        String additionalVotesText = getAdditionalVotesFromOtherSourcesText(additionalVotes);

        if (game.isFowMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat")
                && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            sb.append(" __not__ voting due to **Galactic Threat**");
            return sb.toString();
        } else if (game.isStellarAtomicsMode()
                && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")
                && (game.getScoredPublicObjectives().get("Stellar Atomics") == null
                        || (game.getRevealedPublicObjectives().get("Stellar Atomics") != null
                                && !game.getScoredPublicObjectives()
                                        .get("Stellar Atomics")
                                        .contains(player.getUserID())))) {
            sb.append(" __cannot__ vote due to having used _Stellar Atomics_.**");

        } else if (player.hasLeaderUnlocked("xxchahero")) {
            sb.append(" vote count: **" + MiscEmojis.ResInf + " ").append(voteCount);
        } else if (player.hasAbility("lithoids")) { // Vote with planet resources, not influence
            sb.append(" vote count: **" + MiscEmojis.resources + " ").append(voteCount);
        } else if (player.hasAbility("biophobic")) {
            sb.append(" vote count: **" + PlanetEmojis.SemLore + " ").append(voteCount);
        } else {
            sb.append(" vote count: **" + MiscEmojis.influence + " ").append(voteCount);
        }
        if (!additionalVotesText.isEmpty()) {
            int additionalVoteCount = additionalVotes.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            if (additionalVoteCount > 0) {
                sb.append(" + ").append(additionalVoteCount).append("** additional votes from: ");
            } else {
                sb.append("**");
            }
            sb.append("  ").append(additionalVotesText);
        } else sb.append("**");
        if (game.getLaws().containsKey("rep_govt")
                && game.getStoredValue("executiveOrder").isEmpty()) {
            sb = new StringBuilder();
            sb.append(" vote count (_Representative Government_): **1**");
        }
        if (game.getLaws().containsKey("absol_government")) {
            sb = new StringBuilder();
            sb.append(" vote count (_Representative Government_): **")
                    .append(voteCount)
                    .append("**");
        }
        return sb.toString();
    }

    private static int getTotalVoteCount(Game game, Player player) {
        return getVoteCountFromPlanets(game, player)
                + getAdditionalVotesFromOtherSources(game, player).values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
    }

    private static int getVoteCountFromPlanets(Game game, Player player) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int baseResourceCount = planets.stream()
                .map(planetsInfo::get)
                .filter(Objects::nonNull)
                .mapToInt(Planet::getResources)
                .sum();
        int baseInfluenceCount = planets.stream()
                .map(planetsInfo::get)
                .filter(Objects::nonNull)
                .mapToInt(Planet::getInfluence)
                .sum();
        int voteCount = baseInfluenceCount; // default

        // NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat") && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            return 0;
        }
        if (player.isNpc()) {
            return 0;
        }

        if (game.isStellarAtomicsMode()
                && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")
                && game.getRevealedPublicObjectives().get("Stellar Atomics") != null
                && !game.getScoredPublicObjectives().get("Stellar Atomics").contains(player.getUserID())) {
            return 0;
        }

        // KHRASK
        if (player.hasAbility("lithoids")) { // Vote with planet resources, not influence
            voteCount = baseResourceCount;
        }

        // ZELIAN PURIFIER BIOPHOBIC ABILITY - 1 planet = 1 vote
        if (player.hasAbility("biophobic")) {
            voteCount = planets.size();
        }

        // XXCHA
        if (player.hasLeaderUnlocked("xxchahero")) {
            voteCount = baseResourceCount + baseInfluenceCount;
        }

        boolean executive = player.getFaction().equalsIgnoreCase(game.getStoredValue("executiveOrder"));
        if (player.hasUnlockedBreakthrough("xxchabt") || executive) {
            voteCount = planets.stream()
                    .map(planetsInfo::get)
                    .filter(Objects::nonNull)
                    .mapToInt(Planet::getHigherofInfluenceOrResource)
                    .sum();
        }

        if (executive) {
            voteCount += player.getTg();
        }

        // Xxcha Alliance - +1 vote for each planet
        if (game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            int readyPlanetCount = planets.size();
            voteCount += readyPlanetCount;
        }

        // Olradin "Control" - +2 votes per cultural planet
        if (player.hasAbility("policy_the_people_control")) {
            List<String> cultPlanets = new ArrayList<>();
            for (String cplanet : planets) {
                Planet p = game.getPlanetsInfo().get(cplanet);
                if (p == null) continue;
                if (p.getPlanetTypes().contains(Constants.CULTURAL)) {
                    cultPlanets.add(cplanet);
                }
            }
            voteCount += (cultPlanets.size() * 2);
        }
        UnitHolder p;
        for (String cplanet : planets) {
            p = ButtonHelper.getUnitHolderFromPlanetName(cplanet, game);
            if (p == null) continue;
            for (String attachment : p.getTokenList()) {
                if (attachment.contains("council_preserve")) {
                    voteCount += 5;
                }
            }
        }

        if (game.getLaws().containsKey("absol_government")) {
            voteCount = planets.size();
            if (planets.contains("mr") || planets.contains("mrte")) {
                voteCount++;
            }
        }
        return voteCount;
    }

    private static String getAdditionalVotesFromOtherSourcesText(Map<String, Integer> additionalVotes) {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Integer> entry : additionalVotes.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("(+")
                        .append(entry.getValue())
                        .append(" for ")
                        .append(entry.getKey())
                        .append(")");
            } else {
                sb.append("(").append(entry.getKey()).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * @return (K, V) -> K = additionalVotes / V = text explanation of votes
     */
    private static Map<String, Integer> getAdditionalVotesFromOtherSources(Game game, Player player) {
        Map<String, Integer> additionalVotesAndSources = new LinkedHashMap<>();

        if (getVoteCountFromPlanets(game, player) == 0) {
            return additionalVotesAndSources;
        }
        // Argent Zeal
        if (player.hasAbility("zeal")
                || (game.isOrdinianC1Mode() && player == ButtonHelper.getPlayerWhoControlsCoatl(game))) {
            long playerCount = game.getPlayers().values().stream()
                    .filter(Player::isRealPlayer)
                    .count();
            additionalVotesAndSources.put(FactionEmojis.Argent + "Zeal", Math.toIntExact(playerCount));
        }

        // Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            additionalVotesAndSources.put(FactionEmojis.Empyrean + " " + CardEmojis.PN + "Blood Pact", 4);
        }
        if (player.getPromissoryNotesInPlayArea().contains("sigma_blood_pact")) {
            additionalVotesAndSources.put(FactionEmojis.Empyrean + " " + CardEmojis.PN + "Blood Pact", 6);
        }

        // Predictive Intelligence
        if (player.hasTechReady("pi") || player.hasTechReady("absol_pi")) {
            additionalVotesAndSources.put(TechEmojis.CyberneticTech + "_Predictive Intelligence_", 3);
        }

        // Xxcha Alliance
        if (player.hasLeaderUnlocked("xxchacommander")) {
            additionalVotesAndSources.put("with " + FactionEmojis.Xxcha + " commander accounted for", 0);
        } else if (game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            additionalVotesAndSources.put("with " + FactionEmojis.Xxcha + " _Alliance_ accounted for", 0);
        }

        // Hacan Alliance
        if (game.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
            additionalVotesAndSources.put(FactionEmojis.Hacan + " Alliance not calculated", 0);
        }
        // Kyro Alliance
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
            additionalVotesAndSources.put(FactionEmojis.kyro + " Alliance not calculated", 0);
        }

        // Absol Shard of the Throne
        int shardCount = (int) player.getRelics().stream()
                .filter(s -> s.contains("absol_shardofthethrone"))
                .count();
        if (shardCount > 0) { // +2 votes per Absol shard
            int shardVotes = 2 * shardCount;
            additionalVotesAndSources.put(
                    "(" + shardCount + "x)" + ExploreEmojis.Relic + "Shard of the Throne" + SourceEmojis.Absol,
                    shardVotes);
        }

        // Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = game.getRealPlayers().size();
            additionalVotesAndSources.put(ExploreEmojis.Relic + "Syncretone", count);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            int count = player.getTechs().size() / 2;
            additionalVotesAndSources.put(FactionEmojis.augers + "Ilyxum Commander", count);
        }

        // Dreaming Throne Commander
        if (game.playerHasLeaderUnlockedOrAlliance(player, "dreamcommander")) {
            int count = DreamButtonHandler.getDreamCommanderVoteCount(game, player);
            additionalVotesAndSources.put(FactionEmojis.dream + "Dreaming Throne Commander", count);
        }

        // Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            additionalVotesAndSources.put(TechEmojis.BioticTech + " Some Silly Ghoti Tech", fleetCC);
        }

        // Viability Patch Xxcha PN
        String viabilityFavor = game.getStoredValue("viabilityFavorXxcha");
        if (!viabilityFavor.isEmpty() && viabilityFavor.equals(player.getFaction())) {
            additionalVotesAndSources.put(FactionEmojis.Xxcha + " _Political Favor_", 5);
        }

        return additionalVotesAndSources;
    }

    @ButtonHandler("distinguishedReverse_")
    public static void distinguishedReverse(ButtonInteractionEvent event, String buttonID) {
        String voteMessage = "Please choose from the available buttons your total vote amount."
                + " If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
        String vote = buttonID.substring(buttonID.indexOf('_') + 1);
        int votes = Integer.parseInt(vote);
        List<Button> voteActionRow = getVoteButtonsVersion2(votes - 5, votes);
        voteActionRow.add(Buttons.gray("distinguishedReverse_" + (votes - 5), "Decrease Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("distinguished_")
    public static void distinguished(ButtonInteractionEvent event, String buttonID) {
        String voteMessage = "Please choose from the available buttons your total vote amount."
                + " If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
        String vote = buttonID.substring(buttonID.indexOf('_') + 1);
        int votes = Integer.parseInt(vote);
        List<Button> voteActionRow = getVoteButtonsVersion2(votes, votes + 5);
        voteActionRow.add(Buttons.gray("distinguishedReverse_" + votes, "Decrease Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveWithNoEffect")
    public static void resolveWithNoEffect(ButtonInteractionEvent event, Game game) {
        String voteMessage = "Resolving agenda with no effect. Click the buttons for next steps.";
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        Button flipNextAgenda = Buttons.blue("flip_agenda", "Flip Agenda #" + aCount);
        List<Button> resActionRow = new ArrayList<>();
        resActionRow.add(flipNextAgenda);
        if (!game.isOmegaPhaseMode()) {
            Button proceedToStrategyPhase = Buttons.green(
                    "proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            resActionRow.add(proceedToStrategyPhase);
        } else {
            Button proceedToScoring = Buttons.green("proceed_to_scoring", "Proceed to Scoring Objectives");
            resActionRow.add(proceedToScoring);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("outcome_")
    public static void outcome(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getLaws() != null
                && (game.getLaws().containsKey("rep_govt")
                        && game.getStoredValue("executiveOrder").isEmpty())) {
            player.resetSpentThings();
            player.addSpentThing("representative_1");
            String outcome = buttonID.substring(buttonID.indexOf('_') + 1);
            String formattedOutcome = getAgendaOutcomeName(game, outcome, true);
            String voteMessage = "Chose to vote for " + formattedOutcome;
            if (game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
                voteMessage = "Chose to vote for **" + formattedOutcome + "**";
            }
            game.setStoredValue("latestOutcomeVotedFor" + player.getFaction(), outcome);
            game.setLatestOutcomeVotedFor(outcome);
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
            proceedToFinalizingVote(game, player, event);
        } else {
            exhaustPlanetsForVotingVersion2(buttonID, event, game, player);
        }
    }

    @ButtonHandler("autoresolve_")
    static void autoResolve(@Nullable ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String result = buttonID.substring(buttonID.indexOf('_') + 1);
        if (result.contains("manual")) {
            if (game.getCurrentAgendaInfo().split("_").length < 2) {
                event.reply(
                                "This agenda resolution window has closed. If you still need to resolve this effect, please do so manually.")
                        .setEphemeral(true)
                        .queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }

            if (result.contains("committee")) {
                if (game.isACInDiscard("Confounding") && game.isACInDiscard("Confusing")) {
                    MessageHelper.sendMessageToChannel(
                            game.getActionsChannel(),
                            player.getFactionEmojiOrColor()
                                    + " has chosen to discard _Committee Formation_ to choose the winner."
                                    + " Note that \"afters\" may be played before this occurs."
                                    + " _Confounding Legal Text_ and/or _Confounding Legal Text_ would be playable, but they're both in the discard pile.");
                } else {
                    MessageHelper.sendMessageToChannel(
                            game.getActionsChannel(),
                            player.getFactionEmojiOrColor()
                                    + " has chosen to discard _Committee Formation_ to choose the winner."
                                    + " Note that \"afters\" may be played before this occurs, and that _Confounding Legal Text_ and/or _Confounding Legal Text_ may still be played."
                                    + " You should probably wait and confirm no Legal Texts before resolving.");
                    game.removeLaw(game.getLaws().get("committee"));
                    String message = "Please confirm no _Confusing/Confounding Legal Texts_.";
                    Button noLegalText = Buttons.blue("generic_button_id_3", "Refuse Legal Texts");
                    String inDiscard = "";
                    if (game.isACInDiscard("Confounding")) {
                        message = "Please confirm no _Confusing Legal Text_.";
                        noLegalText = Buttons.blue("generic_button_id_3", "Refuse Confusing Legal Text");
                        inDiscard = "Confounding";
                    } else if (game.isACInDiscard("Confusing")) {
                        message = "Please confirm no _Confounding Legal Text_.";
                        noLegalText = Buttons.blue("generic_button_id_3", "Refuse Confounding Legal Text");
                        inDiscard = "Confusing";
                    }
                    List<Button> buttons = List.of(noLegalText);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(
                            game.getMainGameChannel(),
                            message,
                            game,
                            buttons,
                            GameMessageType.AGENDA_CONFOUNDING_CONFUSING_LEGAL_TEXT);
                    if (!inDiscard.isEmpty()) {
                        MessageHelper.sendMessageToChannel(
                                game.getMainGameChannel(),
                                "_" + inDiscard + " Legal Text_ was found in the discard pile.");
                    }
                }
            }
            String resMessage3 = "Please choose the winner.";
            List<Button> deadlyActionRow3 = AgendaRiderHelper.getAgendaButtons(null, game, "agendaResolution");
            deadlyActionRow3.add(Buttons.red("resolveWithNoEffect", "Resolve With No Result"));
            MessageHelper.sendMessageToChannelWithButtons(game.getActionsChannel(), resMessage3, deadlyActionRow3);
        }
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
    }

    public static void drawAgenda(int count, boolean fromBottom, Game game, @NotNull Player player) {
        drawAgenda(count, fromBottom, game, player, false);
    }

    public static void drawAgenda(int count, Game game, @NotNull Player player) {
        drawAgenda(count, false, game, player, false);
    }

    public static void drawAgenda(int count, boolean fromBottom, Game game, @NotNull Player player, boolean discard) {
        if (game == null) return;
        String sb = player.getRepresentationUnfogged() + " here " + (count == 1 ? "is" : "are") + " the agenda"
                + (count == 1 ? "" : "s") + " you have drawn:";

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = fromBottom ? game.drawBottomAgenda() : game.drawAgenda();
            if (entry != null) {
                AgendaModel agenda = Mapper.getAgenda(entry.getKey());
                List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

                List<Button> buttons = agendaButtons(agenda, entry.getValue(), discard);
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                        player.getCardsInfoThread(), null, agendaEmbed, buttons);
            }
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(
                player, "__Note: if you put both agendas on top, the second one you put will be revealed first!__");
    }

    private static void drawSpecificAgenda(String agendaID, Game game, @NotNull Player player) {
        String sb = player.getRepresentationUnfogged() + " here is the agenda you have drawn:";
        if (game == null) return;

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);

        Map.Entry<String, Integer> entry = game.drawSpecificAgenda(agendaID);
        if (entry != null) {
            AgendaModel agenda = Mapper.getAgenda(entry.getKey());
            List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

            List<Button> buttons = agendaButtons(agenda, entry.getValue(), false);
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                    player.getCardsInfoThread(), null, agendaEmbed, buttons);
        }
    }

    private static List<Button> agendaButtons(AgendaModel agenda, Integer id, boolean discard) {
        List<Button> buttons = new ArrayList<>();
        Button topButton = Buttons.green(
                        "topAgenda_" + id, "Put " + agenda.getName() + " on the top of the agenda deck.")
                .withEmoji(Emoji.fromUnicode("🔼"));
        Button bottomButton = Buttons.red(
                        "bottomAgenda_" + id, "Put " + agenda.getName() + " on the bottom of the agenda deck.")
                .withEmoji(Emoji.fromUnicode("🔽"));
        Button discardButton = Buttons.red("discardAgenda_" + id, "Discard " + agenda.getName())
                .withEmoji(Emoji.fromUnicode("🗑️"));

        buttons.add(topButton);
        if (!discard) {
            buttons.add(bottomButton);
        } else {
            buttons.add(discardButton);
        }
        return buttons;
    }

    public static void revealAgenda(
            GenericInteractionCreateEvent event, boolean revealFromBottom, Game game, MessageChannel channel) {
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        }
        if (!game.getStoredValue("lastAgendaReactTime").isEmpty()
                && (System.currentTimeMillis() - Long.parseLong(game.getStoredValue("lastAgendaReactTime")))
                        < 10 * 60 * 10) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Sorry, the last agenda was flipped too recently, so the bot is stopping here to prevent a double flip. Do `/agenda reveal` if there's no button and this was a mistake.");
            return;
        }

        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        if (aCount > 1 && VeylorAbilitiesHandler.offerTightSchedulingRevealChoice(game, revealFromBottom)) {
            MessageHelper.sendMessageToChannel(
                    channel, game.getPing() + "The agenda reveal is waiting for Veylor to resolve _Tight Scheduling_.");
            return;
        }
        game.setStoredValue("agendaCount", aCount + "");
        if (aCount == 1 && game.isShowBanners() && !game.isOmegaPhaseMode()) {
            BannerGenerator.drawPhaseBanner("agenda", game.getRound(), game.getActionsChannel());
        }

        CryypterHelper.checkEnvoyUnlocks(game);

        game.setStoredValue("AssassinatedReps", "");
        game.setStoredValue("riskedPredictive", "");
        game.setStoredValue("conspiratorsFaction", "");
        game.setStoredValue("resolvedAgendaId", "");
        game.setStoredValue("resolvedAgendaOutcome", "");
        String agendaID = game.revealAgenda(revealFromBottom);
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        boolean action = false;
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            game.setPhaseOfGame("agendawaiting");
            if (aCount == 1) {
                GMService.logActivity(game, "**Agenda** Phase for Round " + game.getRound() + " started.", true);
                FowCommunicationThreadService.checkAllCommThreads(game);
            }
        } else {
            action = true;
        }

        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        String agendaTarget = agendaModel.getTarget();
        String agendaType = agendaModel.getType();
        String agendaName = agendaModel.getName();
        boolean cov = false;

        if ("Emergency Session".equalsIgnoreCase(agendaName)) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    game.getPing()
                            + " _Emergency Session_ revealed. This Agenda Phase will have an additional agenda compared to normal. Flipping next agenda.");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        snapshotAgendaStartVoteCounts(game);
        if ((agendaTarget.toLowerCase().contains("elect law") || "constitution".equalsIgnoreCase(agendaID))
                && game.getLaws().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    game.getPing() + "An \"elect law\" agenda (" + agendaName
                            + ") was revealed with no laws in play."
                            + " As such, the agenda has been discarded, and the next agenda is being flipped.");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("secret objective")) && game.getScoredSecrets() < 1) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    game.getPing() + ", an \"elect scored secret objective\" agenda (" + agendaName
                            + ") was revealed when no secret objectives have been scored."
                            + " As such, the agenda has been discarded, and the next agenda is being flipped.");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_" + uniqueID + "_" + agendaID);
        } else {
            boolean notEmergency = false;
            String agendaName2 = agendaName;
            while (!notEmergency) {
                if ("Emergency Session".equalsIgnoreCase(agendaName2)) {
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(
                            channel,
                            game.getPing()
                                    + " _Emergency Session_ revealed underneath _Covert Legislation_, discarding it.");
                }
                String id2 = game.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName2 = agendaDetails2.getName();
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName2);
                game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");
                if ((agendaTarget.toLowerCase().contains("elect law") || "constitution".equalsIgnoreCase(id2))
                        && game.getLaws().isEmpty()) {
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(
                            channel,
                            game.getPing() + ", an \"elect law\" agenda (" + agendaName2
                                    + ") was hidden under _Covert Legislation_ with no laws in play."
                                    + " As such, both that agenda and _Covert Legislation_ have been discarded, and the next agenda is being flipped.");
                    aCount -= 1;
                    game.setStoredValue("agendaCount", aCount + "");
                    revealAgenda(event, revealFromBottom, game, channel);
                    return;
                }
                if ((agendaTarget.toLowerCase().contains("secret objective")) && game.getScoredSecrets() < 1) {
                    MessageHelper.sendMessageToChannel(
                            channel,
                            game.getPing() + ", an \"elect scored secret objective\" agenda (" + agendaName2
                                    + ") was hidden under _Covert Legislation_ when no secret objectives have been scored."
                                    + " As such, both that agenda and _Covert Legislation_ have been discarded, and the next agenda is being flipped.");
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                    aCount -= 1;
                    game.setStoredValue("agendaCount", aCount + "");
                    revealAgenda(event, revealFromBottom, game, channel);
                    return;
                }

                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (game.getPlayer(game.getSpeakerUserID()) != null) {
                        speaker = game.getPlayers().get(game.getSpeakerUserID());
                    }
                    if (speaker != null) {
                        String sb = speaker.getRepresentationUnfogged()
                                + " this is the hidden agenda for _Covert Legislation_:";
                        List<MessageEmbed> embeds =
                                List.of(Mapper.getAgenda(id2).getRepresentationEmbed());
                        MessageHelper.sendMessageEmbedsToCardsInfoThread(speaker, sb, embeds);
                        if (revealFromBottom) {
                            id2 = game.revealAgenda(true);
                            game.putAgendaBackIntoDeckOnTop(id2);
                        }
                        game.drawAgenda();
                    }
                }
            }
        }
        game.setStoredValue("Pass On Shenanigans", "");
        game.setStoredValue("Abstain On Agenda", "");
        game.resetCurrentAgendaVotes();
        game.setHasHackElectionBeenPlayed(false);
        game.removeStoredValue("hackElectionFaction");
        game.removeStoredValue("viabilityFavorXxcha");
        game.setPlayersWhoHitPersistentNoAfter("");
        game.setPlayersWhoHitPersistentNoWhen("");
        game.setLatestOutcomeVotedFor("");
        for (Player p2 : game.getRealPlayers()) {
            game.setStoredValue("latestOutcomeVotedFor" + p2.getFaction(), "");
            game.setStoredValue("preVoting" + p2.getFaction(), "");
        }
        GameMessageManager.remove(game.getName(), GameMessageType.AGENDA_WHEN);
        GameMessageManager.remove(game.getName(), GameMessageType.AGENDA_AFTER);

        if (!action) {
            BannerGenerator.drawAgendaBanner(aCount, game);
        }
        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();
        String revealMessage = game.getPing() + ", an agenda has been revealed.";
        MessageHelper.sendMessageToChannelWithEmbed(channel, revealMessage, agendaEmbed);
        if (!action && aCount == 1) {
            VeylorAbilitiesHandler.offerTightScheduling(game);
        }

        AutoPingMetadataManager.setupAutoPing(game.getName());

        game.setStoredValue("lastAgendaReactTime", "" + System.currentTimeMillis());

        List<Button> proceedButtons = new ArrayList<>();
        String msg;

        if (action) {
            msg = "It seems likely you are resolving Midir, the Edyn hero, you may use this button to skip straight "
                    + "to the resolution.";
            proceedButtons.add(Buttons.red("autoresolve_manual", "Skip Straight To Resolution"));
        } else {
            listVoteCount(game, channel);
            msg = "These buttons can help with bugs/issues that occur during the Agenda Phase";
            proceedButtons.add(Buttons.red("proceedToVoting", "Skip Waiting"));
            proceedButtons.add(Buttons.blue("transaction", "Transaction"));
            if (!game.isHiddenAgendaMode())
                proceedButtons.add(Buttons.red("eraseMyVote", "Erase my vote & have me vote again"));
            proceedButtons.add(Buttons.red("eraseMyRiders", "Erase my riders"));
            proceedButtons.add(Buttons.gray("refreshAgenda", "Refresh Agenda"));
            proceedButtons.add(Buttons.blue("pingNonresponders", "Ping Non-Responders"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, proceedButtons);
        AgendaWhensAftersHelper.eraseAgendaQueues(event, game);
        if (!action) {
            AgendaWhensAftersHelper.offerEveryonePrepassOnShenanigans(game);
            AgendaWhensAftersHelper.offerEveryoneWhensQueue(game);
            checkForAssigningGeneticRecombination(game);
            CryypterHelper.checkForAssigningMentakEnvoy(game);
            checkForPoliticalSecret(game);
        }
        if (cov) {
            agendaTarget = CryypterHelper.handleCovert(agendaTarget);
            MessageHelper.sendMessageToChannel(
                    channel,
                    "# " + game.getPing() + " the agenda target is " + agendaTarget
                            + ". Sent the agenda to the speaker's `#cards-info` thread.");
        }

        if (!action && aCount == 1) {
            pingAboutDebt(game);
            String politicsHolder = "round" + game.getRound() + "PoliticsHolder";
            String key = "round" + game.getRound() + "AgendaPlacement";
            if (!game.getStoredValue(key).isEmpty() && !game.isFowMode()) {
                String message;
                if (!game.getStoredValue(politicsHolder).isEmpty()) {
                    message = "## " + game.getStoredValue(politicsHolder)
                            + " had **Politics** and placed the agendas in this order: "
                            + game.getStoredValue(key).replace("_", ", ") + ".";
                } else {
                    message = "## The **Politics** player placed the agendas in this order: "
                            + game.getStoredValue(key).replace("_", ", ") + ".";
                }
                MessageHelper.sendMessageToChannel(channel, message);
            }
        }
        if (!action) {
            for (Player player : game.getRealPlayers()) {
                if (player.hasLeader("veylorcommander") && !player.hasLeaderUnlocked("veylorcommander")) {
                    MessageHelper.sendMessageToChannelWithButton(
                            player.getCardsInfoThread(),
                            player.getRepresentationUnfogged()
                                    + ", you may press the button below to start the process of unlocking the Veylor commander",
                            VeylorLeadersHandler.offerVeylorCommanderUnlock(player));
                }
            }
        }
        for (Player player : game.getRealPlayers()) {
            if (!action
                    && game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                    && !ButtonHelperCommanders.resolveFlorzenCommander(player, game)
                            .isEmpty()
                    && aCount == 2) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + " you have Quaxdol Junitas, the Florzen commander, and may thus explore and ready a planet.",
                        ButtonHelperCommanders.resolveFlorzenCommander(player, game));
            }
        }
        if (!game.isFowMode() && !action) {
            game.setStoredValue(
                    "startTimeOfRound" + game.getRound() + "Agenda" + aCount, System.currentTimeMillis() + "");
            ButtonHelper.updateMap(game, event, "Start of agenda #" + aCount + " _" + agendaName + "_ .");
        }
        if (game.getCurrentAgendaInfo().contains("Secret")) {
            StringBuilder summary = new StringBuilder("## Scored Secret Objectives:\n");
            for (Player p2 : game.getRealPlayers()) {
                for (String soID : p2.getSecretsScored().keySet()) {
                    SecretObjectiveModel so = Mapper.getSecretObjective(soID);
                    if (so != null) {
                        summary.append("- ");
                        if (!game.isFowMode()) summary.append(p2.getFactionEmoji());
                        summary.append(CardEmojis.SecretObjective)
                                .append("__**")
                                .append(so.getName())
                                .append("**__: ")
                                .append(so.getText())
                                .append('\n');
                    }
                }
            }
            MessageHelper.sendMessageToChannel(channel, summary.toString());
        }
        if (!cov
                && game.getCurrentAgendaInfo().startsWith("Law")
                && game.getLaws().size() == 2) {
            MessageHelper.sendMessageToChannel(
                    channel, "## A reminder that there are currently 2 laws in play, so this would be the 3rd law.");
        }
        if (game.getLaws().size() > 2 && game.getStoredValue("executiveOrder").isEmpty()) {
            for (Player p : game.getRealPlayers()) {
                if (p.getSecretsUnscored().containsKey("dp")) {
                    MessageHelper.sendMessageToChannel(
                            p.getCardsInfoThread(),
                            p.getRepresentationUnfogged()
                                    + ", a reminder that you have _Dictate Policy_, and there are 3 laws in play.");
                }
            }
        }
    }

    public static void listVoteCount(SlashCommandInteractionEvent event, Game game) {
        listVoteCount(game, event.getChannel());
    }

    public static void listVoteCount(Game game, MessageChannel channel) {
        MessageHelper.sendMessageToChannel(channel, getVoteCountMessage(game));
    }

    static String getVoteCountMessage(Game game) {
        List<Player> orderList = getVotingOrder(game);
        int votes = 0;
        for (Player player : orderList) {
            votes += getTotalVoteCount(game, player);
        }
        boolean hideTotalVotes =
                game.getFowOption(FOWOption.HIDE_TOTAL_VOTES) || isRepresentativeGovernmentInEffect(game);
        boolean hideVoteOrder = game.getFowOption(FOWOption.HIDE_VOTE_ORDER);
        StringBuilder sb = new StringBuilder("# Vote Count");
        if (!hideTotalVotes) sb.append("\nTotal votes: ").append(votes);
        int itemNo = 1;
        // ensure correct numbering if message is broken into multiple chunks - haven't tested the threshold
        String format = orderList.size() > 12 ? "\n`%d.` " : "\n%d. ";
        for (Player player : orderList) {
            sb.append(String.format(format, itemNo));
            sb.append(hideVoteOrder ? "???" : player.getRepresentation(false, false));
            if (player.getUserID().equals(game.getSpeakerUserID())) sb.append(MiscEmojis.SpeakerToken);
            sb.append(getPlayerVoteText(game, player));
            itemNo++;
        }
        return sb.toString();
    }

    public static Map<String, Integer> getVoteCountByColor(Game game) {
        Map<String, Integer> voteCounts = new LinkedHashMap<>();
        for (Player player : getVotingOrder(game)) {
            if (player.getColor() != null && !player.getColor().isBlank()) {
                voteCounts.put(player.getColor(), getVoteTotal(player, game)[0]);
            }
        }
        return voteCounts;
    }

    private static void snapshotAgendaStartVoteCounts(Game game) {
        try {
            game.setStoredValue(
                    AGENDA_START_VOTE_COUNTS, JsonMapperManager.basic().writeValueAsString(getVoteCountByColor(game)));
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Could not snapshot agenda start vote counts", e);
        }
    }

    public static Map<String, Integer> getAgendaStartVoteCounts(Game game) {
        String value = game.getStoredValue(AGENDA_START_VOTE_COUNTS);
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JsonMapperManager.basic().readValue(value, new TypeReference<LinkedHashMap<String, Integer>>() {});
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Could not read agenda start vote counts", e);
            return new LinkedHashMap<>();
        }
    }

    public static String getCurrentAgendaId(Game game) {
        String currentAgendaInfo = game.getCurrentAgendaInfo();
        if (currentAgendaInfo == null || currentAgendaInfo.isBlank()) {
            return null;
        }

        // Format: type_target_uniqueID_agendaID; the agenda ID itself may contain underscores.
        String[] parts = currentAgendaInfo.split("_", 4);
        if (parts.length < 4) {
            return null;
        }
        return Mapper.isValidAgenda(parts[3]) ? parts[3] : null;
    }

    private static boolean isRepresentativeGovernmentInEffect(Game game) {
        return game.getLaws() != null
                && game.getLaws().containsKey("rep_govt")
                && game.getStoredValue("executiveOrder").isEmpty();
    }

    public static void putTop(int agendaID, Game game) {
        boolean success = game.putAgendaTop(agendaID);
        if (game.isFowMode()) {
            return;
        }
        if (!success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Agenda ID found");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Agenda put on top.");
        ButtonHelper.sendMessageToRightStratThread(
                game.getPlayer(game.getActivePlayerID()), game, "Agenda put on top.", "politics");
    }

    public static void putBottom(int agendaID, Game game) {
        boolean success = game.putAgendaBottom(agendaID);
        if (game.isFowMode()) {
            return;
        }
        if (!success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Agenda ID found");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Agenda put on bottom.");
        ButtonHelper.sendMessageToRightStratThread(
                game.getPlayer(game.getActivePlayerID()), game, "Agenda put on bottom.", "politics");
    }

    public static void putBottom(String agendaID, Game game) {
        boolean success = game.putAgendaBottom(agendaID);
        if (game.isFowMode()) {
            return;
        }
        if (!success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Agenda ID found");
        }
    }

    public static void showDiscards(Game game, GenericInteractionCreateEvent event) {
        if (!RiftSetModeService.deckInfoAvailable(game.getPlayer(event.getUser().getId()), game)) {
            return;
        }
        StringBuilder sb2 = new StringBuilder();
        String sb = "### __**Discarded Agendas:**__";
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : discardAgendas.entrySet()) {
            AgendaModel agenda = Mapper.getAgenda(entry.getKey());
            if (agenda == null) {
                BotLogger.error("showDiscards: unknown agenda id '" + entry.getKey() + "' in game " + game.getName());
                continue;
            }
            agendaEmbeds.add(agenda.getRepresentationEmbed());
            sb2.append(agenda.getName())
                    .append(" (ID: ")
                    .append(entry.getValue())
                    .append(")\n");
        }
        MessageHelper.sendMessageToChannelWithEmbeds(event.getMessageChannel(), sb, agendaEmbeds);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb2.toString());
    }

    public static void doResearch(GenericInteractionCreateEvent event, Game game) {
        List<Player> players = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
                for (Player p2 : game.getRealPlayers()) {
                    if (FoWHelper.playerHasShipsInSystem(p2, tile) && !players.contains(p2)) {
                        players.add(p2);
                    }
                }
            }
            if (FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition())) {
                UnitHolder uH = tile.getUnitHolders().get(Constants.SPACE);
                for (UnitKey key : uH.getUnitKeys()) {
                    game.getPlayerByUnitKey(key).ifPresent(p -> {
                        if (p.getUnitFromUnitKey(key).getIsShip()) {
                            int amt = uH.getUnitCount(key);
                            DestroyUnitService.destroyUnit(event, tile, game, key, amt, uH, false);
                        }
                    });
                }
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelper.checkFleetInEveryTile(p2, game);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.getMainGameChannel(),
                "Removed all ships from systems with alphas or betas wormholes. \nYou may use the button to get your technology.",
                List.of(Buttons.GET_A_TECH));
        Player nekro = Helper.getPlayerFromAbility(game, "propagation");
        StringBuilder msg = new StringBuilder(" may research a technology due to _Wormhole Research_.");
        if (game.isFowMode()) {
            for (Player p2 : players) {
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), p2.getRepresentation() + msg);
            }
        } else {
            for (Player p2 : players) {
                msg.insert(0, p2.getRepresentation());
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg.toString());
        }
        if (nekro != null && players.contains(nekro)) {
            Player player = nekro;
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation()
                    + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                    + " Your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(nekro.getCorrectChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
    }

    public static void doSwords(Player player, GenericInteractionCreateEvent event, Game game) {
        List<String> planets = player.getPlanets();
        String ident = player.getFactionEmoji();
        StringBuilder message = new StringBuilder();
        int oldTg = player.getTg();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (planets.contains(unitHolder.getName())) {
                    int numInf = unitHolder.getUnitCount(UnitType.Infantry, player);
                    UnitKey infKey = Units.getUnitKey(UnitType.Infantry, player.getColorID());
                    if (numInf > 0) {
                        int numTG = (numInf + 1) / 2;
                        int cTG = player.getTg();
                        int fTG = cTG + numTG;
                        player.setTg(fTG);
                        message.append(ident)
                                .append(" removed ")
                                .append(numTG)
                                .append(" infantry from ")
                                .append(Helper.getPlanetRepresentation(unitHolder.getName(), game))
                                .append(" and gained ")
                                .append(numTG)
                                .append(" trade goods (")
                                .append(cTG)
                                .append("->")
                                .append(fTG)
                                .append("). \n");
                        DestroyUnitService.destroyUnit(event, tile, game, infKey, numTG, unitHolder, false);
                    }
                }
            }
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        ButtonHelperAgents.resolveArtunoCheck(player, player.getTg() - oldTg);
        ButtonHelperAbilities.pillageCheck(player, game);
    }

    public static void sendTopAgendaToCardsInfoSkipCovert(Game game, Player player) {
        sendTopAgendaToCardsInfoSkipCovert(game, player, 1);
    }

    public static void sendTopAgendaToCardsInfoSkipCovert(Game game, Player player, int count) {
        int covert = 0;
        for (int x = 0; x < count; x++) {
            StringBuilder sb = new StringBuilder();
            if (x == 0) {
                sb.append("__**Top Agenda:**__");
            } else {
                sb.append("__**Agenda At Location ").append(x + 1).append(":**__");
            }
            String agendaID = game.lookAtTopAgenda(x + covert);
            MessageEmbed embed = null;
            if (game.getSentAgendas().get(agendaID) != null) {
                if (game.getCurrentAgendaInfo().contains("_CL_")
                        && game.getPhaseOfGame().startsWith("agenda")) {
                    sb.append(
                            "You are currently voting on _Covert Legislation_, and the top agenda is in the speaker's hand.");
                    sb.append(" Showing the next agenda per the rules.\n");
                    agendaID = game.lookAtTopAgenda(x + 1);
                    covert = 1;

                    if (game.getSentAgendas().get(agendaID) != null) {
                        embed = AgendaModel.agendaIsInSomeonesHandEmbed();
                    } else if (agendaID != null) {
                        embed = Mapper.getAgenda(agendaID).getRepresentationEmbed();
                    }
                } else {
                    sb.append(
                            "The top agenda is currently in somebody's hand."
                                    + " If you are not currently resolving the _Covert Legislation_ agenda, and the **Politics** player (if any) is done assigning agendas,"
                                    + " fix this situation by running the command: `/agenda reset_draw_state_for_deck confirm:YES`.");
                }
            } else if (agendaID != null) {
                embed = Mapper.getAgenda(agendaID).getRepresentationEmbed();
            } else {
                sb.append("Could not find agenda.");
            }
            if (embed != null) {
                MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), sb.toString(), embed);
            } else {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
            }
        }
    }

    @ButtonHandler("topAgenda_")
    public static void topAgenda(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String agendaNumID = buttonID.substring(buttonID.indexOf('_') + 1);
        putTop(Integer.parseInt(agendaNumID), game);

        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "top");
        } else {
            if (game.getStoredValue(key).contains("_")) {
                game.setStoredValue(key, game.getStoredValue(key).split("_")[1]);
            }
            game.setStoredValue(key, game.getStoredValue(key) + "_top");
        }

        recordPoliticsPlayer(game, player);

        AgendaModel agenda = Mapper.getAgenda(game.lookAtTopAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(
                event.getChannel(),
                "Put " + agenda.getName()
                        + " on the top of the agenda deck. You may use this button to undo that and reassign it.",
                reassign);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("retrieveAgenda_")
    public static void retrieveAgenda(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String agendaID = buttonID.substring(buttonID.indexOf('_') + 1);
        drawSpecificAgenda(agendaID, game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("discardAgenda_")
    public static void discardAgenda(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf('_') + 1);
        String agendaID = game.revealAgenda(false);
        AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
        String agendaName = agendaDetails.getName();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmojiOrColor() + "discarded " + agendaName + " using "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Allant, the Edyn" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                        + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bottomAgenda_")
    public static void bottomAgenda(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String agendaNumID = buttonID.substring(buttonID.indexOf('_') + 1);
        putBottom(Integer.parseInt(agendaNumID), game);
        AgendaModel agenda = Mapper.getAgenda(game.lookAtBottomAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(
                event.getChannel(),
                "Put " + agenda.getName()
                        + " on the bottom of the agenda deck. You may use this button to undo that and reassign it.",
                reassign);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "bottom");
        } else {
            if (game.getStoredValue(key).contains("_")) {
                game.setStoredValue(key, game.getStoredValue(key).split("_")[1]);
            }
            game.setStoredValue(key, game.getStoredValue(key) + "_bottom");
        }

        recordPoliticsPlayer(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static void recordPoliticsPlayer(Game game, Player player) {
        String politicsHolder = "round" + game.getRound() + "PoliticsHolder";
        if (game.getStoredValue(politicsHolder).isEmpty()) {
            game.setStoredValue(politicsHolder, player.getRepresentation(false, false));
        }
    }

    @ButtonHandler("proceedToVoting")
    public static void proceedToVoting(ButtonInteractionEvent event, Game game, Player player) {
        String msg =
                "Decided to skip waiting for \"after\"s and proceed to voting. Note that this is not advised unless a bug has occurred. ";
        if (player != null) {
            msg = player.getRepresentation() + " " + msg;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), msg);
        try {
            startTheVoting(game);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event, game), "Could not start the voting", e);
        }
    }

    @ButtonHandler("resolveVeto")
    public static void resolveVeto(ButtonInteractionEvent event, Game game) {
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 0;
        } else {
            aCount = Integer.parseInt(agendaCount) - 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "# The hidden agenda was " + agendaName + "! You may find it in the discard.");
        }
        flipAgenda(event, game);
    }

    @ButtonHandler("flip_agenda")
    private static void flipAgenda(ButtonInteractionEvent event, Game game) {

        if ("agendas_br".equalsIgnoreCase(game.getAgendaDeckID())) {
            String mandateID = game.getMandates().getFirst();
            AgendaModel agenda = Mapper.getAgenda(mandateID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getSpeaker().getCardsInfoThread(),
                    game.getSpeaker().getRepresentation() + " the Mandate on top is attached.",
                    agenda.getRepresentationEmbed());
        } else {
            revealAgenda(event, false, game, event.getChannel());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hack_election")
    public static void hackElection(ButtonInteractionEvent event, Game game) {
        game.setHasHackElectionBeenPlayed(false);
        game.removeStoredValue("hackElectionFaction");
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set Order Back To Normal");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refreshVotes_")
    public static void refreshVotes(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String votes = buttonID.replace("refreshVotes_", "");
        List<Button> voteActionRow = Helper.getPlanetRefreshButtons(player, game);
        Button concludeRefreshing =
                Buttons.red(player.factionButtonChecker() + "votes_" + votes, "Done Readying Planets");
        voteActionRow.add(concludeRefreshing);
        String voteMessage2 =
                "Use the buttons to ready planets. When you're done it will prompt the next player to vote.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), voteMessage2, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forceAbstainForPlayer_")
    public static void forceAbstainForPlayer(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String faction = buttonID.replace("forceAbstainForPlayer_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                (game.isFowMode() ? "A player" : p2.getRepresentation()) + " was forcefully abstained by "
                        + player.getRepresentationNoPing() + ".");
        resolvingAnAgendaVote("resolveAgendaVote_0", event, game, p2);
    }

    @ButtonHandler("eraseMyVote")
    public static void eraseMyVote(Player player, Game game) {
        if ("agendaWaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "You cannot erase your vote until voting has started. Skip waiting if something has gone wrong.");
            return;
        }
        String pfaction = player.getFaction();
        if (game.isFowMode()) {
            pfaction = player.getColor();
        }
        Helper.refreshPlanetsOnTheRevote(player, game);
        eraseVotesOfFaction(game, pfaction);
        game.setStoredValue("preVoting" + player.getFaction(), "");
        String eraseMsg = "Erased previous votes made by " + player.getFactionEmoji()
                + " and readied the planets they previously exhausted\n\n"
                + AgendaSummaryHelper.getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), eraseMsg);
        Button vote = Buttons.blue(
                player.factionButtonChecker() + "vote", player.getFlexibleDisplayName() + " Choose To Vote");
        Button abstain = Buttons.red(
                player.factionButtonChecker() + "resolveAgendaVote_0",
                player.getFlexibleDisplayName() + " Choose To Abstain");
        Button forcedAbstain =
                Buttons.gray("forceAbstainForPlayer_" + player.getFaction(), "(For Others) Abstain For This Player");

        String buttonMsg =
                "Use buttons to vote again. Reminder that this erasing of old votes did not ready any exhausted planets.";
        List<Button> buttons = Arrays.asList(vote, abstain, forcedAbstain);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), buttonMsg, buttons);
    }

    public static void ministerOfIndustryCheck(
            Player player, Game game, Tile tile, GenericInteractionCreateEvent event) {

        if (tile.isScar(game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + " you just placed a space dock in an entropic scar -- it will not be able to use its PRODUCTION ability while it's in there, due to scars turning off unit abilities.");
        }
        if (IsPlayerElectedService.isPlayerElected(game, player, "minister_industry") && !tile.isScar(game)) {
            String msg = player.getRepresentationUnfogged()
                    + "since you have _Minister of Industry_, you may build in tile "
                    + tile.getRepresentationForButtons(game, player) + ". You have "
                    + Helper.getProductionValue(player, game, tile, false) + " PRODUCTION Value in the system.";
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    msg,
                    Helper.getPlaceUnitButtons(event, player, game, tile, "ministerBuild", "place"));
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has the opportunity to resolve a Minister of Industry build.");
        }
        if (player.hasAbility("quantum_fabrication") && !tile.isScar(game)) {
            XanAbilityHandler.offerQuantumFabrication(player, game, event, tile);
        }
    }
}
