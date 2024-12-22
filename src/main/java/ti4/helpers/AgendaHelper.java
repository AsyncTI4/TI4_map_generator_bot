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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.commands2.planet.PlanetExhaust;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.BannerGenerator;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.PlanetModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.FowConstants;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.unit.AddUnitService;

public class AgendaHelper {

    @Nullable
    public static String watchPartyPing(Game game) {
        List<Role> roles = AsyncTI4DiscordBot.guildPrimary.getRolesByName("Ixthian Watch Party", true);
        if (!game.isFowMode() && !roles.isEmpty()) {
            return roles.getFirst().getAsMention();
        }
        return null;
    }

    @Nullable
    public static TextChannel watchPartyChannel(Game game) {
        List<TextChannel> channels = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("ixthian-watch-party", true);
        if (!game.isFowMode() && !channels.isEmpty()) {
            return channels.getFirst();
        }
        return null;
    }

    private static void sleep() {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (Exception ignored) {
        }
    }

    private static String drumroll(String ping, int drums) {
        StringBuilder sb = new StringBuilder();
        if (ping != null) {
            sb.append(ping).append("\n");
        }
        sb.append("# Drumroll please.... ").append(MiscEmojis.RollDice).append("\n");
        sb.append("# 🥁").append(" 🥁".repeat(drums));
        return sb.toString();
    }

    public static void offerEveryonePrepassOnShenanigans(Game game) {
        if (game.islandMode()) return;
        for (Player player : game.getRealPlayers()) {
            if (playerDoesNotHaveShenanigans(player)) {
                continue;
            }
            String msg = player.getRepresentation()
                + " you have the option to prepass on agenda shenanigans here. Agenda shenanigans are the action cards known as Bribery, Deadly Plot, and the Confounding/Confusing Legal Texts."
                + " Feel free not to pre-pass, this is simply an optional way to resolve agendas faster.";
            List<Button> buttons = new ArrayList<>();

            buttons.add(Buttons.green("resolvePreassignment_Pass On Shenanigans", "Pre-pass"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    private static boolean playerDoesNotHaveShenanigans(Player player) {
        Set<String> shenanigans = Set.of("deadly_plot", "bribery", "confounding", "confusing");
        return player.getActionCards().keySet().stream()
            .noneMatch(shenanigans::contains);
    }

    public static void offerEveryonePreAbstain(Game game) {
        for (Player player : game.getRealPlayers()) {
            int[] voteInfo = getVoteTotal(player, game);
            if (voteInfo[0] < 1) {
                continue;
            }
            offerPreVote(player);
        }
    }

    private static void offerPreVote(Player player) {
        String msg = player.getRepresentation()
            + " if you intend to preset an abstention or vote on this agenda, you have the option to preset it here. Feel free not to, this is simply an optional way to resolve agendas faster.";
        List<Button> buttons = new ArrayList<>();
        if (player.hasAbility("future_sight")) {
            msg += " Reminder that you have future sight and may not want to abstain.";
        }
        buttons.add(Buttons.green("preVote", "Pre-Vote"));
        buttons.add(Buttons.blue("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void rollIxthian(Game game, boolean publish) {
        String activeGamePing = game.getPing();
        TextChannel watchParty = watchPartyChannel(game);
        String watchPartyPing = watchPartyPing(game);
        Message watchPartyMsg = publish && watchParty != null ? watchParty.sendMessage(drumroll(watchPartyPing, 0)).complete() : null;

        MessageHelper.MessageFunction resolveIxthian = (msg) -> {
            int rand = 4 + ThreadLocalRandom.current().nextInt(4);
            if (ThreadLocalRandom.current().nextInt(5) == 0) { // random chance for an extra long wait
                rand += 8 + ThreadLocalRandom.current().nextInt(14);
            }

            // Sleep will sleep for 2 seconds now, many quick edits is bad for rate limit
            sleep();
            for (int i = 1; i <= rand; i++) {
                msg.editMessage(drumroll(activeGamePing, i)).queue(Consumers.nop(), BotLogger::catchRestError);
                if (publish && watchPartyMsg != null) {
                    watchPartyMsg.editMessage(drumroll(watchPartyPing, i)).queue(Consumers.nop(), BotLogger::catchRestError);
                }
                sleep();
            }
            msg.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            if (publish && watchPartyMsg != null) {
                watchPartyMsg.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            }
            resolveIxthianRoll(game, publish);
        };
        MessageHelper.splitAndSentWithAction(drumroll(activeGamePing, 0), game.getMainGameChannel(), resolveIxthian);
    }

    private static void resolveIxthianRoll(Game game, boolean publish) {
        TextChannel watchParty = watchPartyChannel(game);
        String watchPartyPing = watchPartyPing(game);

        Die d1 = new Die(6);
        String msg = "# Rolled a " + d1.getResult() + " for Ixthian!";
        if (d1.isSuccess()) {
            msg += TechEmojis.Propulsion3 + " " + TechEmojis.Biotic3 + " " + TechEmojis.Cybernetic3 + " " + TechEmojis.Warfare3;
        } else {
            msg += "💥 💥 💥 💥";
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        if (watchParty != null && publish) {
            String watchMsg = watchPartyPing + " " + game.getName() + " has finished rolling:\n" + msg;
            MessageHelper.sendMessageToChannel(watchParty, watchMsg);
        }
        if (d1.isSuccess() && !game.isFowMode()) {
            if (Helper.getPlayerFromAbility(game, "propagation") != null) {
                Player player = Helper.getPlayerFromAbility(game, "propagation");
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                    + " Your current command tokens are " + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
            MessageHelper.sendMessageToChannelWithButton(game.getMainGameChannel(),
                "You may use the button to get your technology.", Buttons.GET_A_TECH);
        } else if (!d1.isSuccess() && !game.isFowMode()) {
            Button modify = Buttons.gray("getModifyTiles", "Modify Units");
            MessageHelper.sendMessageToChannelWithButton(game.getMainGameChannel(),
                "Please remove units on or adjacent to Mecatol Rex.", modify);
        }
    }

    public static void pingAboutDebt(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player || (player.getTg() + player.getCommodities()) < 0 || p2.hasAbility("binding_debts") || p2.hasAbility("fine_print") || p2.getDebtTokenCount(player.getColor()) < 1) {
                    continue;
                }
                String msg = player.getRepresentation() + " This is a reminder that you owe debt to " + p2.getFactionEmojiOrColor() + " and now could be a good time to pay it (or get it cleared if it was paid already)";
                List<Button> buttons = new ArrayList<>();
                if (player.getTg() > 0) {
                    buttons.add(Buttons.green("sendTGTo_" + p2.getFaction() + "_tg", "Send 1 Trade Good"));
                }
                buttons.add(Buttons.blue("sendTGTo_" + p2.getFaction() + "_debt", "Erase 1 Debt"));
                buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
    }

    @ButtonHandler("sendTGTo_")
    public static void erase1DebtTo(Game game, String buttonID, ButtonInteractionEvent event, Player player) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String tgOrDebt = buttonID.split("_")[2];
        p2.clearDebt(player, 1);
        String msg = "1 debt owed by " + player.getRepresentation() + " to " + p2.getRepresentation() + " was cleared. " + p2.getDebtTokenCount(player.getColor()) + " debt remains.";
        if (tgOrDebt.equalsIgnoreCase("tg")) {
            player.setTg(player.getTg() - 1);
            p2.setTg(p2.getTg() + 1);
            msg = player.getRepresentation(false, false) + " sent 1 trade good to " + p2.getRepresentation(false, false) + ".\n" + msg;
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (p2.getDebtTokenCount(player.getColor()) < 1) {
            event.getMessage().delete().queue();
        }
    }

    public static void exhaustPlanetsForVotingVersion2(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
            + ". Click buttons to exhaust planets and use abilities for votes";
        game.setLatestOutcomeVotedFor(outcome);
        game.setStoredValue("latestOutcomeVotedFor" + player.getFaction(), outcome);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,
            getPlanetButtonsVersion2(event, player, game));
        ButtonHelper.deleteMessage(event);
    }

    public static void checkForAssigningGeneticRecombination(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.setStoredValue("Genetic Recombination " + player.getFaction(), "");
            if (player.hasTechReady("gr")) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-assign the declaration of using genetic recombination on someone."
                    + " When they are up to vote, it will ping them saying that you wish to use genetic recombination, and then it will be your job to clarify."
                    + " Feel free to not preassign if you don't want to use it on this agenda.";
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
        if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
            && !buttonID.contains("everything")) {
            PlanetExhaust.doAction(player, planetName, game, false);
        }
        if (buttonID.contains("everything")) {
            for (String planet : player.getPlanets()) {
                player.exhaustPlanet(planet);
            }
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (!buttonRow.isEmpty()) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
            && !buttonID.contains("everything")) {

            if ("Exhaust stuff".equalsIgnoreCase(totalVotesSoFar)) {
                totalVotesSoFar = "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            } else {
                int totalVotes = Integer.parseInt(
                    totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                    + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                    + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"))
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            }
            if (!actionRow2.isEmpty()) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
        } else {
            if ("Exhaust stuff".equalsIgnoreCase(totalVotesSoFar)) {
                totalVotesSoFar = "Total votes exhausted so far: " + votes
                    + "\n Planets exhausted so far are: all planets";
            } else {
                int totalVotes = Integer.parseInt(
                    totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                    + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                    + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"));
            }
            if (!actionRow2.isEmpty()) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
            String message;
            if (buttonID.contains("everything")) {
                message = "Exhausted all planets for " + votes + " vote" + (votes.equals("1") ? "" : "s");
            } else {
                message = "Used ability for " + votes + " vote" + (votes.equals("1") ? "" : "s");
            }
            ReactionService.addReaction(event, game, player, true, false, message);
        }
    }

    @ButtonHandler("resolveAgendaVote_")
    public static void resolvingAnAgendaVote(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        boolean resolveTime = false;
        String winner = "";
        String votes = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        MessageChannel channel;

        boolean prevoting = !game.getStoredValue("preVoting" + player.getFaction()).isEmpty();
        if (prevoting) {
            ButtonHelper.deleteMessage(event);
            game.setStoredValue("preVoting" + player.getFaction(), votes);
            List<Button> buttonsPV = new ArrayList<>();
            buttonsPV.add(Buttons.red("erasePreVote", "Erase Pre-Vote"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Successfully stored a pre-vote. You can erase it with this button", buttonsPV);
            return;
        }
        if (!buttonID.contains("outcomeTie*")) {
            if ("0".equalsIgnoreCase(votes)) {
                String pfaction2 = player.getFaction();
                if (pfaction2 != null) {
                    player.resetSpentThings();
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Abstained");
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
                if ((game.getLaws() == null || (!game.getLaws().containsKey("rep_govt") && !game.getLaws().containsKey("absol_government"))) &&
                        (player.ownsPromissoryNote("blood_pact") || player.getPromissoryNotesInPlayArea().contains("blood_pact"))) {
                    for (Player p2 : getWinningVoters(winner, game)) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.ownsPromissoryNote("blood_pact") || p2.getPromissoryNotesInPlayArea().contains("blood_pact")) {
                            player.addSpentThing("bloodPact_" + 4);
                            votes = (Integer.parseInt(votes) + 4) + "";
                            break;
                        }
                    }
                }
                if ((game.getLaws() == null || (!game.getLaws().containsKey("rep_govt") && !game.getLaws().containsKey("absol_government"))) &&
                        (player.ownsPromissoryNote("sigma_blood_pact") || player.getPromissoryNotesInPlayArea().contains("sigma_blood_pact"))) {
                    List<Player> winnners = new ArrayList<>();
                    for (String outcome : outcomes.keySet()) {
                        if (outcome.equalsIgnoreCase(winner)) {
                            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                            while (vote_info.hasMoreTokens()) {
                                String specificVote = vote_info.nextToken();
                                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                                Player p = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                                if (p != null &&!winnners.contains(p)) {
                                    winnners.add(p);
                                }
                            }
                        }
                    }
                    for (Player p2 : winnners) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.ownsPromissoryNote("sigma_blood_pact") || p2.getPromissoryNotesInPlayArea().contains("sigma_blood_pact")) {
                            player.addSpentThing("bloodPact_" + 6);
                            votes = (Integer.parseInt(votes) + 6) + "";
                            break;
                        }
                    }
                }
                if ("empty".equalsIgnoreCase(existingData)) {
                    existingData = identifier + "_" + votes;
                } else {
                    existingData = existingData + ";" + identifier + "_" + votes;
                }
                game.setCurrentAgendaVote(winner, existingData);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), Helper.buildSpentThingsMessageForVoting(player, game, false));
            }

            String message = " up to vote! Resolve using buttons.";
            Button eraseAndReVote = Buttons.red("eraseMyVote", "Erase my vote & have me vote again");
            String revoteMsg = "You may press this button to revote if you made a mistake, ignore it otherwise.";
            MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), revoteMsg, eraseAndReVote);
            Player nextInLine = getNextInLine(player, getVotingOrder(game), game);
            String realIdentity2 = nextInLine.getRepresentationUnfogged();

            int[] voteInfo = getVoteTotal(nextInLine, game);

            while ((voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor()))
                || game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction()) || !game.getStoredValue("preVoting" + nextInLine.getFaction()).isEmpty()) {
                String skippedMessage = nextInLine.getRepresentation(true, false)
                    + " You are being skipped because you cannot vote";
                if (game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                    skippedMessage = realIdentity2
                        + "You are being skipped because you told the bot you wanted to preset an abstain";
                    game.setStoredValue("Abstain On Agenda", game
                        .getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (!game.getStoredValue("preVoting" + nextInLine.getFaction()).isEmpty()) {
                    skippedMessage = realIdentity2
                        + " had logged a pre-vote";
                    votes = game.getStoredValue("preVoting" + nextInLine.getFaction());
                    game.setStoredValue("preVoting" + nextInLine.getFaction(), "");
                    for (String thing : nextInLine.getSpentThingsThisWindow()) {
                        if (thing.contains("tg_") || thing.contains("infantry_")) {
                            continue;
                        }
                        exhaustForVotes(event, nextInLine, game, "exhaustForVotes_" + thing, true);
                    }
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),
                            getSummaryOfVotes(game, true) + "\n ");
                    }
                    ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
                    MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                    resolvingAnAgendaVote("resolveAgendaVote_" + votes, event, game, nextInLine);
                    return;
                }
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),
                        getSummaryOfVotes(game, true) + "\n ");
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, game, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(nextInLine.getCorrectChannel(), skippedMessage);
                }
                player = nextInLine;
                nextInLine = getNextInLine(nextInLine, getVotingOrder(game), game);
                realIdentity2 = nextInLine.getRepresentationUnfogged();
                voteInfo = getVoteTotal(nextInLine, game);
            }

            if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                String realIdentity;
                realIdentity = nextInLine.getRepresentationUnfogged();
                String pFaction = nextInLine.getFlexibleDisplayName();
                String finChecker = "FFCC_" + nextInLine.getFaction() + "_";
                Button Vote = Buttons.green(finChecker + "vote", pFaction + " Choose To Vote");
                Button Abstain;
                if (nextInLine.hasAbility("future_sight")) {
                    Abstain = Buttons.red(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain (You have future sight)");
                } else {
                    Abstain = Buttons.red(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
                }
                Button ForcedAbstain = Buttons.gray("forceAbstainForPlayer_" + nextInLine.getFaction(),
                    "(For Others) Abstain for this player");
                game.updateActivePlayer(nextInLine);
                List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
                if (game.isFowMode()) {
                    if (nextInLine.getPrivateChannel() != null) {
                        MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),
                            getSummaryOfVotes(game, true) + "\n ");
                        MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(),
                            "\n " + realIdentity + message, buttons);
                        player.getCorrectChannel().sendMessage("Notified next in line").queue();
                    }
                } else {
                    message = getSummaryOfVotes(game, true) + "\n \n " + realIdentity + message;
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getCorrectChannel(), message, buttons);
                }
                ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
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
                            Button button = Buttons.blue("resolveAgendaVote_outcomeTie* " + tiedWinner, tiedWinner);
                            tiedWinners.add(button);
                        }
                    } else {
                        tiedWinners = getAgendaButtons(null, game, "resolveAgendaVote_outcomeTie*");
                    }
                    if (!tiedWinners.isEmpty()) {
                        channel = speaker.getCorrectChannel();
                        MessageHelper.sendMessageToChannelWithButtons(channel,
                            speaker.getRepresentationUnfogged() + " please decide the winner.", tiedWinners);
                    }
                }
            }
        } else {
            resolveTime = true;
            winner = buttonID.substring(buttonID.lastIndexOf("*") + 2);
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
        String summary2 = getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary2 + "\n \n");
        game.setPhaseOfGame("agendaEnd");
        game.setActivePlayerID(null);
        StringBuilder message = new StringBuilder();
        message.append(game.getPing()).append("\n");
        message.append("### Current winner is ").append(StringUtils.capitalize(winner)).append("\n");
        if (!"action_deck_2".equals(game.getAcDeckID())) {
            handleShenanigans(game, winner);
            message.append("When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.");
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

        if ((!game.isACInDiscard("Bribery") || !game.isACInDiscard("Deadly Plot")) && (!losers.isEmpty() || game.isAbsolMode())) {
            StringBuilder message = new StringBuilder("You may hold while people resolve shenanigans. If it is not an important agenda, you are encouraged to move on and float the shenanigans.\n");
            Button noDeadly = Buttons.blue("generic_button_id_1", "No Deadly Plot");
            Button noBribery = Buttons.blue("generic_button_id_2", "No Bribery");
            List<Button> deadlyActionRow = List.of(noBribery, noDeadly);
            if (!game.isFowMode()) {
                if (!game.isACInDiscard("Deadly Plot")) {
                    message.append("The following players (").append(losers.size()).append(") have the opportunity to play ").append(CardEmojis.ActionCard).append("Deadly Plot:\n");
                }
                for (Player loser : losers) {
                    message.append("> ").append(loser.getRepresentationUnfogged()).append("\n");
                }
                message.append("Please confirm you will not be playing Bribery or Deadly Plot");
            } else {
                message.append(losers.size()).append(" players have the opportunity to play ").append(CardEmojis.ActionCard).append("Deadly Plot.\n");
                MessageHelper.privatelyPingPlayerList(losers, game, "Please respond to Bribery/Deadly Plot window");
            }
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message.toString(), game, deadlyActionRow, "shenanigans");
            shenanigans = true;
        } else {
            String message = "Either both Bribery and Deadly Plot were in the discard or no player could legally play them.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }

        // Confounding & Confusing Legal Text
        if (game.getCurrentAgendaInfo().contains("Elect Player")) {
            if (!game.isACInDiscard("Confounding") || !game.isACInDiscard("Confusing")) {
                String message = game.getPing() + " please confirm no Confusing/Confounding Legal Texts.";
                Button noConfounding = Buttons.blue("generic_button_id_3", "Refuse Confounding Legal Text");
                Button noConfusing = Buttons.blue("genericReact4", "Refuse Confusing Legal Text");
                List<Button> buttons = List.of(noConfounding, noConfusing);
                MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message, game, buttons, "shenanigans");
                shenanigans = true;
            } else {
                String message = "Both *Confounding Legal Text* and *Confusing Legal Text* are in the discard pile.\nThere are no shenanigans possible. Please resolve the agenda.";
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
            }
        }

        if (!shenanigans) {
            String message = "There are no shenanigans possible. Please resolve the agenda.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
    }

    @ButtonHandler("reverse_")
    public static void reverseRider(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String choice = buttonID.substring(buttonID.indexOf("_") + 1);
        String voteMessage = player.getFactionEmojiOrColor() + " Chose to reverse the " + choice;
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData != null && !"empty".equalsIgnoreCase(existingData) && !"".equalsIgnoreCase(existingData)) {
                String[] votingInfo = existingData.split(";");
                StringBuilder totalBuilder = new StringBuilder();
                for (String onePiece : votingInfo) {
                    if (!onePiece.contains(choice)) {
                        totalBuilder.append(";").append(onePiece);
                    }
                }
                String total = totalBuilder.toString();
                if (!total.isEmpty() && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                game.setCurrentAgendaVote(outcome, total);
            }
        }
        player.getCorrectChannel().sendMessage(voteMessage).queue();
    }

    @ButtonHandler("eraseMyRiders")
    public static void reverseAllRiders(Game game, Player player) {
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        for (String outcome : outcomes.keySet()) {
            String existingData = outcomes.getOrDefault(outcome, "empty");
            if (existingData != null && !"empty".equalsIgnoreCase(existingData) && !"".equalsIgnoreCase(existingData)) {
                String[] votingInfo = existingData.split(";");
                StringBuilder totalBuilder = new StringBuilder();
                for (String onePiece : votingInfo) {
                    String identifier = onePiece.split("_")[0];
                    if (!identifier.equalsIgnoreCase(player.getFaction())
                        && !identifier.equalsIgnoreCase(player.getColor())) {
                        totalBuilder.append(";").append(onePiece);
                    } else {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getFactionEmoji() + " erased " + onePiece.split("_")[1]);
                    }
                }
                String total = totalBuilder.toString();
                if (!total.isEmpty() && total.charAt(0) == ';') {
                    total = total.substring(1);
                }
                game.setCurrentAgendaVote(outcome, total);
            }
        }
    }

    @ButtonHandler("rider_")
    public static void placeRider(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] choiceParams = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.lastIndexOf("_")).split(";");
        String choice = choiceParams[1];

        String rider = buttonID.substring(buttonID.lastIndexOf("_") + 1);
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];

        String cleanedChoice = choice;
        if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            cleanedChoice = Helper.getPlanetRepresentation(choice, game);
        }
        String voteMessage = "Chose to put a " + rider + " on " + StringUtils.capitalize(cleanedChoice);
        if (!game.isFowMode()) {
            voteMessage = player.getFactionEmojiOrColor() + " " + voteMessage;
        }
        String identifier;
        if (game.isFowMode()) {
            identifier = player.getColor();
        } else {
            identifier = player.getFaction();
        }
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        if ("empty".equalsIgnoreCase(existingData)) {
            existingData = identifier + "_" + rider;
        } else {
            existingData = existingData + ";" + identifier + "_" + rider;
        }
        game.setCurrentAgendaVote(choice, existingData);

        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        String summary2 = getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), summary2 + "\n \n");

        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getWhenButtons(Game game) {
        Button playWhen = Buttons.red("play_when", "Play When");
        Button noWhen = Buttons.blue("no_when", "No Whens (for now)", MiscEmojis.NoWhens);
        Button noWhenPersistent = Buttons.blue("no_when_persistent", "No Whens (for this agenda)", MiscEmojis.NoWhens);
        List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
        Player quasher = Helper.getPlayerFromAbility(game, "quash");
        if (quasher != null && quasher.getStrategicCC() > 0) {
            String finChecker = "FFCC_" + quasher.getFaction() + "_";
            Button quashButton = Buttons.red(finChecker + "quash", "Quash Agenda", FactionEmojis.Xxcha);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButton(quasher.getPrivateChannel(), "Use Button To Quash If You Want", quashButton);
            } else {
                whenButtons.add(quashButton);
            }
        }
        return whenButtons;
    }

    public static List<Button> getAfterButtons(Game game) {
        List<Button> afterButtons = new ArrayList<>();
        Button playAfter = Buttons.red("play_after_Non-AC Rider", "Play A Non-Action Card Rider");
        if (game.isFowMode()) {
            afterButtons.add(playAfter);
        }

        if (ButtonHelper.shouldKeleresRiderExist(game) && !game.isFowMode()) {
            afterButtons.add(Buttons.gray("play_after_Keleres Rider", "Play Keleres Rider", FactionEmojis.Keleres));
        }

        if (!game.isFowMode() && Helper.getDateDifference(game.getCreationDate(),
            Helper.getDateRepresentation(1705824000011L)) < 0) {
            for (Player player : game.getRealPlayers()) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                String planet = "tarrock";
                if (player.getPlanets().contains(planet) && !player.getExhaustedPlanetsAbilities().contains(planet)) {
                    afterButtons.add(Buttons.green(finChecker + "planetAbilityExhaust_" + planet, "Use Tarrock Ability", player.getFactionEmoji()));
                }
            }
        }

        if (game.getPNOwner("dspnedyn") != null && !game.isFowMode()) {
            afterButtons.add(Buttons.gray("play_after_Edyn Rider", "Play Edyn Rider", FactionEmojis.edyn));
        }

        if (game.getPNOwner("dspnkyro") != null && !game.isFowMode()) {
            afterButtons.add(Buttons.gray("play_after_Kyro Rider", "Play Kyro Rider", FactionEmojis.kyro));
        }

        if (Helper.getPlayerFromAbility(game, "galactic_threat") != null) {
            Player nekroProbably = Helper.getPlayerFromAbility(game, "galactic_threat");
            String finChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            afterButtons.add(Buttons.gray(finChecker + "play_after_Galactic Threat Rider", "Predict Outcome for Galactic Threat", FactionEmojis.Nekro));
        }

        // conspirators
        if (Helper.getPlayerFromAbility(game, "conspirators") != null && !game.isFowMode()) {
            Player nekroProbably = Helper.getPlayerFromAbility(game, "conspirators");
            String finChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            afterButtons.add(Buttons.gray(finChecker + "play_after_Conspirators", "Use Conspirators", FactionEmojis.zealots));
        }

        if (Helper.getPlayerFromUnlockedLeader(game, "keleresheroodlynn") != null) {
            Player keleresX = Helper.getPlayerFromUnlockedLeader(game, "keleresheroodlynn");
            String finChecker = "FFCC_" + keleresX.getFaction() + "_";
            afterButtons.add(Buttons.gray(finChecker + "play_after_Keleres Xxcha Hero", "Play Keleres (Xxcha)", FactionEmojis.Keleres));
        }

        if (Helper.getPlayerFromAbility(game, "radiance") != null) {
            Player edyn = Helper.getPlayerFromAbility(game, "radiance");
            String finChecker = "FFCC_" + edyn.getFaction() + "_";
            afterButtons.add(Buttons.gray(finChecker + "play_after_Edyn Radiance Ability", "Use Edyn Radiance Ability", FactionEmojis.edyn));
        }

        for (Player p1 : game.getRealPlayers()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            if (p1.hasTechReady("dsedyng")) {
                afterButtons.add(Buttons.gray(finChecker + "play_after_Edyn Unity Algorithm", "Use Edyn Unity Algorithm Technology", FactionEmojis.edyn));
            }
            if (game.getCurrentAgendaInfo().contains("Player") && ButtonHelper.isPlayerElected(game, p1, "committee")) {
                afterButtons.add(Buttons.gray(finChecker + "autoresolve_manualcommittee", "Use Committee Formation", CardEmojis.Agenda));
            }
        }

        afterButtons.add(Buttons.blue("no_after", "No Afters (for now)", MiscEmojis.NoAfters));
        afterButtons.add(Buttons.blue("no_after_persistent", "No Afters (for this agenda)", MiscEmojis.NoAfters));
        return afterButtons;
    }

    public static void ministerOfIndustryCheck(Player player, Game game, Tile tile,
        GenericInteractionCreateEvent event) {
        if (ButtonHelper.isPlayerElected(game, player, "minister_industry")) {
            String msg = player.getRepresentationUnfogged()
                + "since you have Minister of Industry, you may build in tile "
                + tile.getRepresentationForButtons(game, player) + ". You have "
                + Helper.getProductionValue(player, game, tile, false) + " PRODUCTION Value in the system.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                Helper.getPlaceUnitButtons(event, player, game, tile, "ministerBuild", "place"));
        }
    }

    public static List<Button> getVoteButtonsVersion2(int minVote, int voteTotal) {
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
        if (game.getCurrentAgendaInfo() != null) {
            String message = " up to vote! Resolve using buttons. \n \n" + getSummaryOfVotes(game, true);

            Player nextInLine = null;
            try {
                nextInLine = getNextInLine(null, getVotingOrder(game), game);
            } catch (Exception e) {
                BotLogger.log("Could not find next in line", e);
            }
            if (nextInLine == null) {
                BotLogger.log("`startTheVoting` is **null**");
                return;
            }
            String realIdentity = nextInLine.getRepresentationUnfogged();
            int[] voteInfo = getVoteTotal(nextInLine, game);
            int counter = 0;
            while ((voteInfo[0] < 1
                || game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction()) || !game.getStoredValue("preVoting" + nextInLine.getFaction()).isEmpty())
                && counter < game.getRealPlayers().size()) {
                String skippedMessage = nextInLine.getRepresentation(true, false) + "You are being skipped because the bot thinks you can't vote.";
                if (game.getStoredValue("Abstain On Agenda").contains(nextInLine.getFaction())) {
                    skippedMessage = realIdentity
                        + "You are being skipped because you told the bot you wanted to preset an abstain";
                    game.setStoredValue("Abstain On Agenda", game
                        .getStoredValue("Abstain On Agenda").replace(nextInLine.getFaction(), ""));
                    nextInLine.resetSpentThings();
                }
                if (!game.getStoredValue("preVoting" + nextInLine.getFaction()).isEmpty()) {
                    skippedMessage = realIdentity
                        + " had logged a pre-vote";
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
                counter = counter + 1;
            }

            String pFaction = StringUtils.capitalize(nextInLine.getFaction());
            message = realIdentity + message;
            String finChecker = "FFCC_" + nextInLine.getFaction() + "_";
            Button Vote = Buttons.green(finChecker + "vote", pFaction + " Choose To Vote");
            Button Abstain;
            if (nextInLine.hasAbility("future_sight")) {
                Abstain = Buttons.red(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain (You have future sight)");
            } else {
                Abstain = Buttons.red(finChecker + "resolveAgendaVote_0", pFaction + " Choose To Abstain");
            }
            Button ForcedAbstain = Buttons.gray("forceAbstainForPlayer_" + nextInLine.getFaction(),
                "(For Others) Abstain for this player");
            try {
                game.updateActivePlayer(nextInLine);
            } catch (Exception e) {
                BotLogger.log("Could not update active player", e);
            }

            List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
            if (game.isFowMode()) {
                if (nextInLine.getPrivateChannel() != null) {
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), message, buttons);
                    game.getMainGameChannel().sendMessage("Voting started. Notified first in line").queue();
                }
            } else {
                MessageHelper.sendMessageToChannelWithButtons(nextInLine.getCorrectChannel(), message, buttons);
            }
            ButtonHelperFactionSpecific.checkForGeneticRecombination(nextInLine, game);
        } else {
            game.getMainGameChannel().sendMessage("Cannot find voting info, sorry. Please resolve automatically").queue();
        }
    }

    public static List<Button> getLawOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> law : game.getLaws().entrySet()) {
            String lawName = Mapper.getAgendaTitleNoCap(law.getKey());
            Button button;
            if (rider == null) {
                button = Buttons.gray(prefix + "_" + law.getKey(), lawName);
            } else {
                button = Buttons.gray(prefix + "rider_law;" + law.getKey() + "_" + rider, lawName);
            }
            lawButtons.add(button);
        }
        return lawButtons;
    }

    public static List<Button> getSecretOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                Button button;
                String soName = Mapper.getSecretObjectivesJustNames().get(so.getKey());
                if (rider == null) {

                    button = Buttons.gray(prefix + "_" + so.getKey(), soName);
                } else {
                    button = Buttons.gray(prefix + "rider_so;" + so.getKey() + "_" + rider, soName);
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }

    public static List<Button> getUnitUpgradeOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (TechnologyModel tech : Helper.getAllNonFactionUnitUpgradeTech(game, player)) {
                Button button;
                if (rider == null) {
                    button = Buttons.gray(prefix + "_" + tech.getAlias(), tech.getName());
                } else {
                    button = Buttons.gray(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
                }
                buttons.add(button);
            }
        }
        return buttons;
    }

    public static List<Button> getUnitOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : Helper.getAllNonFactionUnitUpgradeTech(game)) {
            Button button;
            if (rider == null) {
                button = Buttons.gray(prefix + "_" + tech.getAlias(), tech.getName());
            } else {
                button = Buttons.gray(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
            }
            buttons.add(button);
        }
        return buttons;
    }

    public static List<Button> getStrategyOutcomeButtons(String rider, String prefix) {
        List<Button> strategyButtons = new ArrayList<>();
        for (int x = 1; x < 9; x++) {
            Button button;
            if (rider == null) {
                TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(x);
                if (scEmoji != CardEmojis.SCBackBlank) {
                    button = Buttons.gray(prefix + "_" + x, null, scEmoji);
                } else {
                    button = Buttons.gray(prefix + "_" + x, Integer.toString(x), scEmoji);
                }
            } else {
                button = Buttons.gray(prefix + "rider_sc;" + x + "_" + rider, x + "");
            }
            strategyButtons.add(button);
        }
        return strategyButtons;
    }

    public static List<Button> getPlanetOutcomeButtons(GenericInteractionCreateEvent event, Player player, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Button button;
            if (rider == null) {
                button = Buttons.gray(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, game));
            } else {
                button = Buttons.gray(prefix + "rider_planet;" + planet + "_" + rider,
                    Helper.getPlanetRepresentation(planet, game));
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    public static List<Button> getPlayerOutcomeButtons(Game game, String rider, String prefix, String planetRes) {
        List<Button> playerOutcomeButtons = new ArrayList<>();

        for (Player player : game.getRealPlayers()) {
            String faction = player.getFaction();
            Button button;
            if (!game.isFowMode() && !faction.contains("franken")) {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Buttons.gray(prefix + planetRes + "_" + faction + "_" + rider, " ");
                    } else {
                        button = Buttons.gray(prefix + "rider_player;" + faction + "_" + rider, " ");
                    }
                } else {
                    button = Buttons.gray(prefix + "_" + faction, " ");
                }
                String factionEmojiString = player.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
            } else {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Buttons.gray(planetRes + "_" + player.getColor() + "_" + rider, " ");
                    } else {
                        button = Buttons.gray(prefix + "rider_player;" + player.getColor() + "_" + rider,
                            player.getColor());
                    }
                } else {
                    button = Buttons.gray(prefix + "_" + player.getColor(), player.getColor());
                }
            }
            playerOutcomeButtons.add(button);
        }
        return playerOutcomeButtons;
    }

    public static List<Button> getAgendaButtons(String ridername, Game game, String prefix) {
        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
        List<Button> outcomeActionRow;
        if (agendaDetails.contains("For")) {
            outcomeActionRow = getForAgainstOutcomeButtons(ridername, prefix);
        } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
            outcomeActionRow = getPlayerOutcomeButtons(game, ridername, prefix, null);
        } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            if (ridername == null) {
                outcomeActionRow = getPlayerOutcomeButtons(game, null, "tiedPlanets_" + prefix, "planetRider");
            } else {
                outcomeActionRow = getPlayerOutcomeButtons(game, ridername, prefix, "planetRider");
            }
        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
            outcomeActionRow = getSecretOutcomeButtons(game, ridername, prefix);
        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
            outcomeActionRow = getStrategyOutcomeButtons(ridername, prefix);
        } else if (agendaDetails.contains("unit upgrade")) {
            outcomeActionRow = getUnitUpgradeOutcomeButtons(game, ridername, prefix);
        } else if (agendaDetails.contains("Unit") || agendaDetails.contains("unit")) {
            outcomeActionRow = getUnitOutcomeButtons(game, ridername, prefix);
        } else {
            outcomeActionRow = getLawOutcomeButtons(game, ridername, prefix);
        }

        return outcomeActionRow;
    }

    public static List<Button> getForAgainstOutcomeButtons(String rider, String prefix) {
        List<Button> voteButtons = new ArrayList<>();
        Button button;
        Button button2;
        if (rider == null) {
            button = Buttons.gray(prefix + "_for", "For");
            button2 = Buttons.red(prefix + "_against", "Against");
        } else {
            button = Buttons.blue(prefix + "rider_fa;for_" + rider, "For");
            button2 = Buttons.red(prefix + "rider_fa;against_" + rider, "Against");
        }
        voteButtons.add(button);
        voteButtons.add(button2);
        return voteButtons;
    }

    public static List<Player> getWinningRiders(String winner, Game game, GenericInteractionCreateEvent event) {
        List<Player> winningRs = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            StringTokenizer vote_info2 = new StringTokenizer(outcomes.get(outcome), ";");
            while (vote_info2.hasMoreTokens()) {
                String specificVote = vote_info2.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                Player keleres = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                if (keleres != null && specificVote.contains("Keleres Xxcha Hero")) {
                    int size = getLosingVoters(outcome, game).size();
                    String message = keleres.getRepresentation()
                        + " You have Odlynn Myrr, the Keleres (Xxcha) Hero, to resolve. There were " + size 
                        + " players who voted for a different outcome, so you get that many trade goods and command tokens. ";
                    MessageHelper.sendMessageToChannel(keleres.getCorrectChannel(), message);
                    if (size > 0) {
                        keleres.setTg(keleres.getTg() + size);
                        String msg2 = "Gained 3 trade goods (" + (keleres.getTg() - size) + " -> **" + keleres.getTg() + "**).";
                        ButtonHelperAbilities.pillageCheck(keleres, game);
                        ButtonHelperAgents.resolveArtunoCheck(keleres, size);
                        MessageHelper.sendMessageToChannel(keleres.getCorrectChannel(), msg2);
                        List<Button> buttons = ButtonHelper.getGainCCButtons(keleres);
                        String trueIdentity = keleres.getRepresentationUnfogged();
                        String msg3 = trueIdentity + "! Your current command tokens are " + keleres.getCCRepresentation()
                            + ". Use buttons to gain command tokens.";
                        game.setStoredValue("originalCCsFor" + keleres.getFaction(), keleres.getCCRepresentation());
                        MessageHelper.sendMessageToChannelWithButtons(keleres.getCorrectChannel(), msg3, buttons);
                    }

                }
            }
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player winningR = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (winningR != null && specificVote.contains("Sanction")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, game);
                        for (Player p2 : loseFleetPlayers) {
                            p2.setFleetCC(p2.getFleetCC() - 1);
                            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                                p2.getRepresentation()
                                    + ", you lost 1 command token from your fleet pool due to voting the same way as a _Sanction_.");
                            ButtonHelper.checkFleetInEveryTile(p2, game, event);
                        }
                    }
                    if (winningR != null && specificVote.contains("Corporate Lobbying")) {
                        List<Player> loseFleetPlayers = getWinningVoters(winner, game);
                        for (Player p2 : loseFleetPlayers) {
                            p2.setTg(p2.getTg() + 2);
                            ButtonHelperAgents.resolveArtunoCheck(p2, 2);
                            ButtonHelperAbilities.pillageCheck(p2, game);
                            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                                p2.getRepresentation()
                                    + " you gained trade goods due to voting the same way as _Corporate Lobbying_.");
                            ButtonHelper.checkFleetInEveryTile(p2, game, event);
                        }
                    }
                    if (winningR != null && (specificVote.contains("Rider") || winningR.hasAbility("future_sight")
                        || specificVote.contains("Radiance") || specificVote.contains("Tarrock Ability"))) {

                        MessageChannel channel = winningR.getCorrectChannel();
                        String identity = winningR.getRepresentationUnfogged();
                        if (specificVote.contains("Galactic Threat Rider")) {
                            List<Player> voters = getWinningVoters(winner, game);
                            List<String> potentialTech = new ArrayList<>();
                            for (Player techGiver : voters) {
                                potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(winningR,
                                    techGiver, potentialTech, game);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + ", please resolve **Galactic Threat** ability using the buttons.",
                                ButtonHelperAbilities.getButtonsForPossibleTechForNekro(winningR, potentialTech,
                                    game));
                        }
                        if (specificVote.contains("Technology Rider") && !winningR.hasAbility("propagation")) {

                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + ", please resolve _Technology Rider_ by using the button to research 1 technology.",
                                List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Schematics Rider")) {

                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                identity + ", please resolve _Schematics Rider_ by using the button to get the pre-selected technology.",
                                List.of(Buttons.GET_A_TECH));
                        }
                        if (specificVote.contains("Leadership Rider")) {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                            String message = identity + ", your current command tokens are " + winningR.getCCRepresentation()
                                + ". Use buttons to gain command tokens.";
                            game.setStoredValue("originalCCsFor" + winningR.getFaction(),
                                winningR.getCCRepresentation());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " resolve _Leadership Rider_ by using the button to gain 3 command tokens.");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Technology Rider") && winningR.hasAbility("propagation")) {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(winningR);
                            String message = winningR.getRepresentation() + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                                + " Your current command tokens are " + winningR.getCCRepresentation() + ". Use buttons to gain command tokens.";
                            game.setStoredValue("originalCCsFor" + winningR.getFaction(),
                                winningR.getCCRepresentation());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " resolve _Technology Rider_ (with **Propagation**) by using the button to gain 3 command tokens.");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Keleres Rider")) {
                            boolean scheming = winningR.hasAbility("scheming");
                            if (winningR.hasAbility("autonetic_memory")) {
                                ButtonHelperAbilities.autoneticMemoryStep1(game, winningR, 1);
                            } else {
                                game.drawActionCard(winningR.getUserID());

                                if (scheming) {
                                    game.drawActionCard(winningR.getUserID());
                                    MessageHelper.sendMessageToChannelWithButtons(winningR.getCardsInfoThread(),
                                        winningR.getRepresentationUnfogged() + " use buttons to discard.",
                                        ActionCardHelper.getDiscardActionCardButtons(winningR, false));
                                }
                                ButtonHelper.checkACLimit(game, winningR);
                                ActionCardHelper.sendActionCardInfo(game, winningR, event);
                            }

                            StringBuilder sb = new StringBuilder(identity);
                            sb.append("due to having a winning _Keleres Rider_, you have been given");
                            if (scheming) {
                                sb.append(" 2 action cards  (**Scheming** increases this from the normal 1 action card; discard buttons have been sent to your `#cards-info` thread)");
                            } else {
                                sb.append(" 1 action card");
                            }
                            sb.append(" and 2 trade goods ").append(winningR.gainTG(2)).append(".");
                            MessageHelper.sendMessageToChannel(channel, sb.toString());
                            ButtonHelperAbilities.pillageCheck(winningR, game);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, 2);
                        }
                        if (specificVote.contains("Politics Rider")) {
                            int amount = 3;

                            if (winningR.hasAbility("autonetic_memory")) {
                                ButtonHelperAbilities.autoneticMemoryStep1(game, winningR, 3);
                            } else {
                                game.drawActionCard(winningR.getUserID());
                                game.drawActionCard(winningR.getUserID());
                                game.drawActionCard(winningR.getUserID());
                                ButtonHelper.checkACLimit(game, winningR);
                                ActionCardHelper.sendActionCardInfo(game, winningR, event);
                            }
                            if (winningR.hasAbility("scheming")) {
                                amount = 4;
                                game.drawActionCard(winningR.getUserID());
                                MessageHelper.sendMessageToChannelWithButtons(winningR.getCardsInfoThread(),
                                    winningR.getRepresentationUnfogged() + " use buttons to discard.",
                                    ActionCardHelper.getDiscardActionCardButtons(winningR, false));
                            }

                            game.setSpeakerUserID(winningR.getUserID());
                            MessageHelper.sendMessageToChannel(channel,
                                identity + " due to having a winning _Politics Rider_, you have been given " + amount + " action cards" 
                                + (winningR.hasAbility("scheming") ? " (**Scheming** increases this from the normal 1 action card; discard buttons have been sent to your `#cards-info` thread)" : "")
                                + " and the " + MiscEmojis.SpeakerToken + " Speaker Token");
                        }
                        if (specificVote.contains("Diplomacy Rider")) {
                            String message = identity + ", you have a _Diplomacy Rider_ to resolve. Click the name of the planet whose system you wish to Diplo.";
                            List<Button> buttons = Helper.getPlanetSystemDiploButtons(winningR, game, true, null);
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Construction Rider")) {
                            String message = identity + ", you have a _Construction Rider_ to resolve. Click the name of the planet you wish to put your space dock on.";
                            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(winningR, game, "sd", "place");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Warfare Rider")) {
                            String message = identity + ", you have a _Warfare Rider_ to resolve. Select the system where you want to put the dreadnought.";
                            List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(winningR, game, "dreadnought", "placeOneNDone_skipbuild");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if (specificVote.contains("Armament Rider")) {
                            String message = identity + ", you have an _Armament Rider_ to resolve. Select the system in which you wish to produce 2 units each with cost 4 or less.";

                            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, winningR, UnitType.Spacedock, UnitType.CabalSpacedock);
                            List<Button> buttons = new ArrayList<>();
                            for (Tile tile : tiles) {
                                Button starTile = Buttons.green("umbatTile_" + tile.getPosition(), tile.getRepresentationForButtons(game, winningR));
                                buttons.add(starTile);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }

                        if (specificVote.contains("Trade Rider")) {
                            MessageHelper.sendMessageToChannel(channel, identity
                                + ", due to having a winning _Trade Rider_, you have been given five trade goods " + winningR.gainTG(5) + ".");
                            ButtonHelperAbilities.pillageCheck(winningR, game);
                            ButtonHelperAgents.resolveArtunoCheck(winningR, 5);
                        }
                        if (specificVote.contains("Relic Rider")) {
                            MessageHelper.sendMessageToChannel(channel, identity + " due to having a winning _Relic Rider_, you have gained a Relic.");
                            RelicHelper.drawRelicAndNotify(winningR, event, game);
                        }
                        if (specificVote.contains("Radiance")) {
                            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, winningR, UnitType.Mech);
                            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(winningR, game, tiles);
                        }
                        if (specificVote.contains("Tarrock Ability")) {
                            String message = winningR.getFactionEmoji() + " Drew A Secret Objective.";
                            game.drawSecretObjective(winningR.getUserID());
                            if (winningR.hasAbility("plausible_deniability")) {
                                game.drawSecretObjective(winningR.getUserID());
                                message = message + " Drew a second SO due to **Plausible Deniability**.";
                            }
                            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, winningR, event);
                            MessageHelper.sendMessageToChannel(winningR.getCorrectChannel(), message);
                        }
                        if (specificVote.contains("Kyro Rider")) {
                            String message = winningR.getRepresentationUnfogged()
                                + " Click the names of the planet you wish to drop 3 infantry on";
                            List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(winningR, game, "3gf", "placeOneNDone_skipbuild"));
                            MessageHelper.sendMessageToChannelWithButtons(winningR.getCorrectChannel(), message, buttons);
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
                            String msg = identity + " due to having a winning _Imperial Rider_, you have scored a victory point. Huzzah.\n";
                            int poIndex;
                            poIndex = game.addCustomPO(Constants.IMPERIAL_RIDER, 1);
                            msg = msg + "Custom public objective _Imperial Rider_ has been added.\n";
                            game.scorePublicObjective(winningR.getUserID(), poIndex);
                            msg = msg + winningR.getRepresentation() + " scored _Imperial Rider_.\n";
                            MessageHelper.sendMessageToChannel(channel, msg);
                            Helper.checkEndGame(game, winningR);

                        }
                        if (!winningRs.contains(winningR)) {
                            winningRs.add(winningR);
                        }

                    }

                }
            }
        }
        return winningRs;
    }

    public static List<Player> getRiders(Game game) {
        List<Player> riders = new ArrayList<>();

        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_") + 1);
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

    public static List<Player> getLosers(String winner, Game game) {
        List<Player> losers = new ArrayList<>();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
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

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null && !specificVote.contains("Rider") && !specificVote.contains("Sanction") && !specificVote.contains("Ability")) {
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

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = game.getPlayerFromColorOrFaction(faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser) && !specificVote.contains("Rider")
                            && !specificVote.contains("Sanction") && !specificVote.contains("Ability")) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static List<Player> getPlayersWithMostPoints(Game game) {
        List<Player> losers = new ArrayList<>();
        int most = 0;
        for (Player p : game.getRealPlayers()) {
            if (p.getTotalVictoryPoints() > most) {
                most = p.getTotalVictoryPoints();
            }
        }
        for (Player p : game.getRealPlayers()) {
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

    public static int[] getVoteTotal(Player player, Game game) {
        int hasXxchaAlliance = game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander") ? 1 : 0;
        int hasXxchaHero = player.hasLeaderUnlocked("xxchahero") ? 1 : 0;
        int voteCount = getTotalVoteCount(game, player);

        // Check if Player only has additional votes but not any "normal" votes, if so,
        // they can't vote
        if (getVoteCountFromPlanets(game, player) == 0) {
            voteCount = 0;
        }

        if (game.getLaws() != null && (game.getLaws().containsKey("rep_govt")
            || game.getLaws().containsKey("absol_government"))) {
            voteCount = 1;
            if (game.getLaws().containsKey("absol_government") && player.controlsMecatol(false)) {
                voteCount = 2;
            }
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
                || game.getStoredValue("PublicExecution").contains(player.getFaction()))) {
            voteCount = 0;
        }

        return new int[] { voteCount, hasXxchaHero, hasXxchaAlliance };
    }

    public static List<Player> getVotingOrder(Game game) {
        List<Player> orderList = new ArrayList<>(game.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList());
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
        if (game.isHasHackElectionBeenPlayed()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }

        // Check if Argent Flight is in the game - if it is, put it at the front of the
        // vote list.
        Optional<Player> argentPlayer = orderList.stream()
            .filter(player -> player.getFaction() != null && player.hasAbility("zeal")).findFirst();
        if (argentPlayer.isPresent()) {
            orderList.remove(argentPlayer.orElse(null));
            orderList.addFirst(argentPlayer.get());
        }
        String conspiratorsFaction = game.getStoredValue("conspiratorsFaction");
        if (!conspiratorsFaction.isEmpty()) {
            Player rhodun = game.getPlayerFromColorOrFaction(conspiratorsFaction);
            Optional<Player> speaker = orderList.stream()
                .filter(player -> player.getFaction() != null && game.getSpeakerUserID().equals(player.getUserID()))
                .findFirst();
            if (speaker.isPresent() && rhodun != null) {
                orderList.remove(rhodun);
                orderList.add(orderList.indexOf(speaker.get()) + 1, rhodun);
            }
        }

        // Check if Player has Edyn Mandate faction tech - if it is, put it at the end
        // of the vote list.
        Optional<Player> edynPlayer = orderList.stream()
            .filter(player -> player.getFaction() != null && player.hasTech("dsedyny")).findFirst();
        if (edynPlayer.isPresent()) {
            orderList.remove(edynPlayer.orElse(null));
            orderList.add(edynPlayer.get());
        }
        return orderList;
    }

    public static Player getNextInLine(Player player1, List<Player> votingOrder, Game game) {
        boolean foundPlayer = false;
        if (player1 == null) {
            for (int x = 0; x < 6; x++) {
                if (x < votingOrder.size()) {
                    Player player = votingOrder.get(x);
                    if (player == null) {
                        BotLogger.log("`getNextInLine` Hit a null player in game " + game.getName());
                        return null;
                    }

                    if (player.isRealPlayer()) {
                        return player;
                    } else {
                        BotLogger.log("`getNextInLine` Hit a notRealPlayer player in game "
                            + game.getName() + " on player " + player.getUserName());
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

    public static void checkForPoliticalSecret(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ps")
                && player.getPromissoryNotesOwned().contains(player.getColor() + "_ps")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation()
                    + " this is a reminder that you don't currently hold your Political Secret, and thus may want to wait until the holder indicates \"no whens\" before you do any afters.");
            }
        }
    }

    @ButtonHandler("exhaustForVotes_")
    public static void exhaustForVotes(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        exhaustForVotes(event, player, game, buttonID, false);
    }

    public static void exhaustForVotes(ButtonInteractionEvent event, Player player, Game game, String buttonID, boolean finalRes) {
        String thing = buttonID.replace("exhaustForVotes_", "");

        boolean prevoting = !game.getStoredValue("preVoting" + player.getFaction()).isEmpty();
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
                            + " due to the arcane citadel";
                        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    }
                }
            }
            if (thing.contains("dsghotg") && !prevoting) {
                player.exhaustTech("dsghotg");
            }
            if (thing.contains("predictive")) {
                game.setStoredValue("riskedPredictive",
                    game.getStoredValue("riskedPredictive") + player.getFaction());
            }
            if (!finalRes) {
                ButtonHelper.deleteTheOneButton(event);
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
                    ButtonHelper.deleteTheOneButton(event);
                }
            }
            if (thing.contains("kyro")) {
                player.increaseInfantrySpentThisWindow(1);
                if (!prevoting) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation() + " please remove 1 infantry to pay for Silas Deriga, the Kyro commander.",
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
                                    + " due to the Arcane Citadel";
                                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
                                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                            }
                        }
                    }

                }
                if (!finalRes) {
                    ButtonHelper.deleteTheOneButton(event);
                }
            }
        }
        if (!finalRes) {
            String editedMessage = Helper.buildSpentThingsMessageForVoting(player, game, false);
            editedMessage = getSummaryOfVotes(game, true) + "\n\n" + editedMessage;
            event.getMessage().editMessage(editedMessage).queue();
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
        return voteAmount;
    }

    public static List<Button> getPlanetButtonsVersion2(GenericInteractionCreateEvent event, Player player, Game game) {
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
                BotLogger.log(event.getChannel().getAsMention() + " TEMP BOTLOG: A bad PlanetModel was found for planet: " + planet + " - using the planet id instead of the model name");
            }
            if (voteAmount != 0) {
                Button button = Buttons.gray("exhaustForVotes_planet_" + planet, planetNameProper + " (" + voteAmount + ")", PlanetEmojis.getPlanetEmoji(planet));
                planetButtons.add(button);
            }
            totalPlanetVotes = totalPlanetVotes + voteAmount;
        }
        if (player.hasAbility("zeal")) {
            int numPlayers = 0;
            for (Player player_ : game.getPlayers().values()) {
                if (player_.isRealPlayer())
                    numPlayers++;
            }
            planetButtons.add(Buttons.blue("exhaustForVotes_zeal_" + numPlayers, "Special Argent Votes (" + numPlayers + ")", FactionEmojis.Argent));
        }

        if (player.hasTechReady("pi") || player.hasTechReady("absol_pi")) {
            planetButtons.add(Buttons.blue("exhaustForVotes_predictive_3", "Use Predictive Intelligence Votes (3)", TechEmojis.CyberneticTech));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "hacancommander")) {
            planetButtons.add(Buttons.gray("exhaustForVotes_hacanCommanderTg", "Spend Trade Goods for 2 Votes Each", FactionEmojis.Hacan));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "kyrocommander")) {
            planetButtons.add(Buttons.gray("exhaustForVotes_kyrocommanderInf", "Kill Infantry for 1 Vote per Kill", FactionEmojis.kyro));
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            int count = player.getTechs().size() / 2;
            planetButtons.add(Buttons.gray("exhaustForVotes_augerscommander_" + count, "Use Augurs Commander Votes (" + count + ")", FactionEmojis.augers));
        }

        if (CollectionUtils.containsAny(player.getRelics(),
            List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size();
            int shardVotes = 2 * count; // +2 votes per Absol shard
            Button button = Buttons.gray("exhaustForVotes_absolShard_" + shardVotes, "Use Shard of the Throne Votes (" + shardVotes + ")", SourceEmojis.Absol);
            planetButtons.add(button);
        }
        // Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = game.getRealPlayers().size();
            Button button = Buttons.gray("exhaustForVotes_absolsyncretone_" + count, "Use Syncretone Votes (" + count + ")", SourceEmojis.Absol);
            planetButtons.add(button);
        }

        // Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            planetButtons.add(Buttons.gray("exhaustForVotes_dsghotg_" + fleetCC, "Use Networked Command Votes (" + fleetCC + ")", FactionEmojis.ghoti));
        }
        planetButtons.add(Buttons.gray("exhaustForVotes_allPlanets_" + totalPlanetVotes, "Exhaust All Voting Planets (" + totalPlanetVotes + ")"));
        planetButtons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "proceedToFinalizingVote", "Done exhausting planets."));
        return planetButtons;
    }

    @ButtonHandler("refreshAgenda")
    public static void refreshAgenda(Game game, ButtonInteractionEvent event) {
        String agendaDetails = game.getCurrentAgendaInfo();
        String agendaID = "CL";
        if (StringUtils.countMatches(agendaDetails, "_") > 2) {
            if (StringUtils.countMatches(agendaDetails, "_") > 3) {
                agendaID = StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_");
            } else {
                agendaID = agendaDetails.split("_")[3];
            }
        }
        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();

        String revealMessage = "Refreshed Agenda";
        MessageHelper.sendMessageToChannelWithEmbed(game.getMainGameChannel(), revealMessage, agendaEmbed);
        List<Button> proceedButtons = new ArrayList<>();
        String msg = "Buttons for various things";

        listVoteCount(game, game.getMainGameChannel());

        proceedButtons.add(Buttons.red("proceedToVoting", "Skip waiting and start the voting for everyone"));
        proceedButtons.add(Buttons.blue("transaction", "Transaction"));
        proceedButtons.add(Buttons.red("eraseMyVote", "Erase my vote & have me vote again"));
        proceedButtons.add(Buttons.red("eraseMyRiders", "Erase my riders"));
        proceedButtons.add(Buttons.gray("refreshAgenda", "Refresh Agenda"));

        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, proceedButtons);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), getSummaryOfVotes(game, true));
    }

    @ButtonHandler("proceedToFinalizingVote")
    public static void proceedToFinalizingVote(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        String votes = Helper.buildSpentThingsMessageForVoting(player, game, true);
        String msg = Helper.buildSpentThingsMessageForVoting(player, game, false) + "\n\n"
            + player.getRepresentation() + " you are currently voting " + votes
            + " vote" + (votes.equals("1") ? "" : "s") + ". You may confirm this or you may modify this number if the bot missed something.";
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            msg = msg + " Any Blood Pact Votes will be automatically added";
        }
        boolean prevoting = !game.getStoredValue("preVoting" + player.getFaction()).isEmpty();
        if (prevoting) {
            game.setStoredValue("preVoting" + player.getFaction(), votes);
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveAgendaVote_" + votes,
            "Vote " + votes + " vote" + (votes.equals("1") ? "" : "s")));
        buttons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "distinguished_" + votes, "Modify Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    public static void resolveAbsolAgainstChecksNBalances(Game game) {
        StringBuilder message = new StringBuilder();
        // Integer poIndex = game.addCustomPO("Points Scored Prior to Absol C&B
        // Wipe", 1);
        // message.append("Custom PO 'Points Scored Prior to Absol C&B Wipe' has been
        // added and people have scored it. \n");

        // game.scorePublicObjective(playerWL.getUserID(), poIndex);
        for (Player player : game.getRealPlayers()) {
            int currentPoints = player.getPublicVictoryPoints(false) + player.getSecretVictoryPoints();

            Integer poIndex = game.addCustomPO(
                game.isFowMode() ? ""
                    : StringUtils.capitalize(player.getColor())
                        + " VP Scored Prior to Agenda Wipe",
                currentPoints);
            message.append("Custom public objective").append(
                game.isFowMode() ? ""
                    : " \"" + StringUtils.capitalize(player.getColor()
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
        message.append("All secret objectives have been returned to the deck and all public objectives scoring have been cleared. \n");

        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message.toString());
    }

    public static void eraseVotesOfFaction(Game game, String faction) {
        if (game.getCurrentAgendaVotes().isEmpty()) {
            return;
        }
        Map<String, String> outcomes = new HashMap<>(game.getCurrentAgendaVotes());
        String voteSumm;

        for (String outcome : outcomes.keySet()) {
            voteSumm = "";
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

            StringBuilder voteSummBuilder = new StringBuilder(voteSumm);
            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction2 = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                if (vote.contains("Rider") || vote.contains("Sanction") || vote.contains("Radiance")
                    || vote.contains("Unity Algorithm") || vote.contains("Tarrock") || vote.contains("Hero")) {
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

    public static String getWinner(Game game) {
        StringBuilder winner = new StringBuilder();
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        int currentHighest = -1;
        for (String outcome : outcomes.keySet()) {
            int totalVotes = 0;
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
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

    public static String getSummaryOfVotes(Game game, boolean capitalize) {
        String summary;
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String agendaDetails = game.getCurrentAgendaInfo();
        String agendaName;
        if (StringUtils.countMatches(agendaDetails, "_") > 2)
            if (StringUtils.countMatches(agendaDetails, "_") > 3) {
                agendaName = Mapper.getAgendaTitleNoCap(
                    StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_"));
            } else {
                agendaName = Mapper.getAgendaTitleNoCap(agendaDetails.split("_")[3]);
            }
        else {
            agendaName = "Not Currently Tracked";
        }

        if (outcomes.isEmpty()) {
            summary = "# Agenda Name: " + agendaName + "\nNo current Riders or votes have been cast yet.";
        } else {
            StringBuilder summaryBuilder = new StringBuilder(
                "# Agenda Name: " + agendaName + "\nCurrent status of votes and outcomes is: \n");
            for (String outcome : outcomes.keySet()) {
                if (StringUtils.countMatches(game.getCurrentAgendaInfo(), "_") > 1) {
                    agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
                } else {
                    agendaDetails = game.getCurrentAgendaInfo();
                }

                int totalVotes = 0;
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                String outcomeSummary;
                if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                    outcome = Mapper.getSecretObjectivesJustNames().get(outcome);
                } else if (agendaDetails.contains("Elect Law") || agendaDetails.contains("elect law")) {
                    outcome = Mapper.getAgendaTitleNoCap(outcome);
                } else if (agendaDetails.toLowerCase().contains("unit upgrade")) {
                    outcome = Mapper.getTech(outcome).getName();
                } else if (capitalize) {
                    outcome = StringUtils.capitalize(outcome);
                }
                StringBuilder outcomeSummaryBuilder = new StringBuilder();
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    if (capitalize) {
                        Player p2 = game.getPlayerFromColorOrFaction(faction);
                        faction = FactionEmojis.getFactionIcon(faction).toString();
                        if (p2 != null) {
                            faction = p2.getFactionEmoji();
                        }
                        if (game.isFowMode()) {
                            faction = "Someone";
                        }
                        String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                        if (NumberUtils.isDigits(vote)) {
                            totalVotes += Integer.parseInt(vote);
                        }
                        outcomeSummaryBuilder.append(faction).append("-").append(vote).append(", ");
                    } else {
                        String vote = specificVote.substring(specificVote.indexOf("_") + 1);
                        if (NumberUtils.isDigits(vote)) {
                            totalVotes += Integer.parseInt(vote);
                            outcomeSummaryBuilder.append(faction).append(" voted ").append(vote).append(" votes. ");
                        } else {
                            outcomeSummaryBuilder.append(faction).append(" cast a ").append(vote).append(". ");
                        }

                    }

                }
                outcomeSummary = outcomeSummaryBuilder.toString();
                if (capitalize) {
                    if (outcomeSummary.length() > 2) {
                        outcomeSummary = outcomeSummary.substring(0, outcomeSummary.length() - 2);
                    }

                    if (!game.isFowMode() && game.getCurrentAgendaInfo().contains("Elect Player")) {
                        summaryBuilder.append(FactionEmojis.getFactionIcon(outcome.toLowerCase())).append(" ")
                            .append(outcome).append(": ").append(totalVotes).append(". (").append(outcomeSummary)
                            .append(")\n");

                    } else if (!game.isHomebrewSCMode()
                        && game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
                        summaryBuilder.append(CardEmojis.getSCFrontFromInteger(Integer.parseInt(outcome))).append(" ")
                            .append(outcome).append(": ").append(totalVotes).append(". (").append(outcomeSummary)
                            .append(")\n");
                    } else {
                        summaryBuilder.append(outcome).append(": ").append(totalVotes).append(". (")
                            .append(outcomeSummary).append(")\n");

                    }
                } else {
                    summaryBuilder.append(outcome).append(": Total votes ").append(totalVotes).append(". ")
                        .append(outcomeSummary).append("\n");
                }

            }
            summary = summaryBuilder.toString();
        }
        return summary;
    }

    @ButtonHandler("ministerOfWar")
    public static void resolveMinisterOfWar(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelper.deleteTheOneButton(event);
        boolean success = game.removeLaw(game.getLaws().get("minister_war"));
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Minister of War Law removed");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
        }
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "ministerOfWar");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
    }

    public static String getPlayerVoteText(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(game, player);
        Map<String, Integer> additionalVotes = getAdditionalVotesFromOtherSources(game, player);
        String additionalVotesText = getAdditionalVotesFromOtherSourcesText(additionalVotes);

        if (game.isFowMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat")
            && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            sb.append(" NOT VOTING (Galactic Threat)");
            return sb.toString();
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
            int additionalVoteCount = additionalVotes.values().stream().mapToInt(Integer::intValue).sum();
            if (additionalVoteCount > 0) {
                sb.append(" + ").append(additionalVoteCount).append("** additional votes from: ");
            } else {
                sb.append("**");
            }
            sb.append("  ").append(additionalVotesText);
        } else
            sb.append("**");
        if (game.getLaws().containsKey("rep_govt") || game.getLaws().containsKey("absol_government")) {
            sb = new StringBuilder();
            if (game.getLaws().containsKey("absol_government") && player.controlsMecatol(false)) {
                sb.append(" vote count (Representative Government while controlling Mecatol Rex): **2**");
            } else {
                sb.append(" vote count (Representative Government): **1**");
            }

        }
        return sb.toString();
    }

    public static int getTotalVoteCount(Game game, Player player) {
        return getVoteCountFromPlanets(game, player) + getAdditionalVotesFromOtherSources(game, player)
            .values().stream().mapToInt(Integer::intValue).sum();
    }

    public static int getVoteCountFromPlanets(Game game, Player player) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; // default

        // NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat")
            && !game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
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
            if (p == null)
                continue;
            for (String attachment : p.getTokenList()) {
                if (attachment.contains("council_preserve")) {
                    voteCount += 5;
                }
            }
        }

        return voteCount;
    }

    public static String getAdditionalVotesFromOtherSourcesText(Map<String, Integer> additionalVotes) {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Integer> entry : additionalVotes.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("(+").append(entry.getValue()).append(" for ").append(entry.getKey()).append(")");
            } else {
                sb.append("(").append(entry.getKey()).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * @return (K, V) -> K = additionalVotes / V = text explanation of votes
     */
    public static Map<String, Integer> getAdditionalVotesFromOtherSources(Game game, Player player) {
        Map<String, Integer> additionalVotesAndSources = new LinkedHashMap<>();

        if (getVoteCountFromPlanets(game, player) == 0) {
            return additionalVotesAndSources;
        }
        // Argent Zeal
        if (player.hasAbility("zeal")) {
            long playerCount = game.getPlayers().values().stream().filter(Player::isRealPlayer).count();
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
            additionalVotesAndSources.put(TechEmojis.CyberneticTech + "Predictive Intelligence", 3);
        }

        // Xxcha Alliance
        if (game.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            additionalVotesAndSources.put(FactionEmojis.Xxcha + "Alliance has been counted for", 0);
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
        if (CollectionUtils.containsAny(player.getRelics(),
            List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size();
            // +2 votes per Absol shard
            int shardVotes = 2 * count;
            additionalVotesAndSources.put("(" + count + "x)" + ExploreEmojis.Relic + "Shard of the Throne" + SourceEmojis.Absol, shardVotes);
        }

        // Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = game.getRealPlayers().size();
            additionalVotesAndSources.put(ExploreEmojis.Relic + "Syncretone", count);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            int count = player.getTechs().size() / 2;
            additionalVotesAndSources.put(FactionEmojis.augers + "Augers Commander", count);
        }

        // Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            additionalVotesAndSources.put(TechEmojis.BioticTech + "Exhaust Networked Command", fleetCC);
        }

        return additionalVotesAndSources;
    }

    public static EmbedBuilder buildAgendaEmbed(AgendaModel agenda) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(agenda.getSource().emoji() + " " + agenda.getName());

        StringBuilder desc = new StringBuilder("**").append(agenda.getType()).append(":** *").append(agenda.getTarget())
            .append("*\n");
        desc.append("> ").append(agenda.getText1().replace("For:", "**For:**")).append("\n");
        desc.append("> ").append(agenda.getText2().replace("Against:", "**Against:**"));
        eb.setDescription(desc.toString());
        eb.setFooter(agenda.footnote());

        return eb;
    }

    @ButtonHandler("planetRider_")
    public static void planetRider(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        buttonID = buttonID.replace("planetRider_", "");
        String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to Rider for one of " + factionOrColor + "'s planets. Use buttons to select which one.";
        List<Button> outcomeActionRow;
        buttonID = buttonID.replace(factionOrColor + "_", "");
        outcomeActionRow = getPlanetOutcomeButtons(event, planetOwner, game, player.getFinsFactionCheckerPrefix(), buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("distinguishedReverse_")
    public static void distinguishedReverse(ButtonInteractionEvent event, String buttonID) {
        String voteMessage = "Please select from the available buttons your total vote amount. If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
        String vote = buttonID.substring(buttonID.indexOf("_") + 1);
        int votes = Integer.parseInt(vote);
        List<Button> voteActionRow = getVoteButtonsVersion2(votes - 5, votes);
        voteActionRow.add(Buttons.gray("distinguishedReverse_" + (votes - 5), "Decrease Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("distinguished_")
    public static void distinguished(ButtonInteractionEvent event, String buttonID) {
        String voteMessage = "Please select from the available buttons your total vote amount. If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
        String vote = buttonID.substring(buttonID.indexOf("_") + 1);
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
        Button proceedToStrategyPhase = Buttons.green("proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
        List<Button> resActionRow = Arrays.asList(flipNextAgenda, proceedToStrategyPhase);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("outcome_")
    public static void outcome(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getLaws() != null && (game.getLaws().containsKey("rep_govt") || game.getLaws().containsKey("absol_government"))) {
            player.resetSpentThings();
            player.addSpentThing("representative_1");
            boolean playerHasMr = CollectionUtils.containsAny(player.getPlanets(), Constants.MECATOLS);
            if (game.getLaws().containsKey("absol_government") && playerHasMr) {
                player.addSpentThing("absolRexControlRepresentative_1");
            }
            String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
            String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome);
            game.setStoredValue("latestOutcomeVotedFor" + player.getFaction(), outcome);
            game.setLatestOutcomeVotedFor(outcome);
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
            proceedToFinalizingVote(game, player, event);
        } else {
            exhaustPlanetsForVotingVersion2(buttonID, event, game, player);
        }
    }

    @ButtonHandler("autoresolve_")
    public static void autoResolve(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String result = buttonID.substring(buttonID.indexOf("_") + 1);
        if (result.contains("manual")) {
            if (result.contains("committee")) {
                MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor()
                    + " has chosen to discard Committee Formation to choose the winner. Note that afters may be played before this occurs, and that confounding may still be played. You should probably wait and confirm no confounding before resolving");
                boolean success = game.removeLaw(game.getLaws().get("committee"));
                String message = game.getPing() + " please confirm no Confounding Legal Texts.";
                Button noConfounding = Buttons.blue("generic_button_id_3", "Refuse Confounding Legal Text");
                List<Button> buttons = List.of(noConfounding);
                MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message, game, buttons, "shenanigans");
                if (game.isACInDiscard("Confounding")) {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Confounding was found in the discard pile, so you should be good to resolve");
                }
            }
            String resMessage3 = "Please select the winner.";
            List<Button> deadlyActionRow3 = getAgendaButtons(null, game, "agendaResolution");
            deadlyActionRow3.add(Buttons.red("resolveWithNoEffect", "Resolve with no result"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3, deadlyActionRow3);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("play_after_")
    public static void play_after(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String riderName = buttonID.replace("play_after_", "");
        List<Button> riderButtons = getAgendaButtons(riderName, game, player.getFinsFactionCheckerPrefix());
        List<Button> afterButtons = getAfterButtons(game);
        MessageChannel mainGameChannel = game.getMainGameChannel();
        String pnKey = "fin";

        if ("Keleres Rider".equalsIgnoreCase(riderName) || "Edyn Rider".equalsIgnoreCase(riderName) || "Kyro Rider".equalsIgnoreCase(riderName)) {
            if ("Keleres Rider".equalsIgnoreCase(riderName)) {
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (pn.contains("rider")) {
                        pnKey = pn;
                    }
                }
                if ("fin".equalsIgnoreCase(pnKey)) {
                    MessageHelper.sendMessageToChannel(mainGameChannel, player.getRepresentation() + ", you don't have the _Keleres Rider_.");
                    return;
                }
                if (player.getFaction().contains("keleres")) {
                    MessageHelper.sendMessageToChannel(mainGameChannel, player.getRepresentation() + ", you cannot play your own promissory note.");
                    return;
                }
            } else if ("Edyn Rider".equalsIgnoreCase(riderName)) {
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (pn.contains("dspnedyn")) {
                        pnKey = pn;
                    }
                }
                if ("fin".equalsIgnoreCase(pnKey)) {
                    MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have the _Edyn Rider_.");
                    return;
                }
            } else if ("Kyro Rider".equalsIgnoreCase(riderName)) {
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (pn.contains("dspnkyro")) {
                        pnKey = pn;
                    }
                }
                if ("fin".equalsIgnoreCase(pnKey)) {
                    MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have the _Kyro Rider_.");
                    return;
                }
            }

            ReactionService.addReaction(event, game, player, true, true, "Playing " + riderName, riderName + " Played");
            PromissoryNoteHelper.resolvePNPlay(pnKey, player, game, event);
        } else {
            ReactionService.addReaction(event, game, player, true, true, "Playing " + riderName, riderName + " Played");

            if (riderName.contains("Unity Algorithm")) {
                player.exhaustTech("dsedyng");
            }
            if ("conspirators".equalsIgnoreCase(riderName)) {
                game.setStoredValue("conspiratorsFaction", player.getFaction());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing()
                    + " The **Conspirators** ability has been used, which means the player will vote after the speaker. This ability may be used once per agenda phase.");
                if (!game.isFowMode()) {
                    listVoteCount(game, game.getMainGameChannel());
                }
            } else {
                MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel,
                    "Please select your rider target",
                    game, player, riderButtons);
                if ("Keleres Xxcha Hero".equalsIgnoreCase(riderName)) {
                    Leader playerLeader = player.getLeader("keleresheroodlynn").orElse(null);
                    if (playerLeader != null) {
                        StringBuilder message = new StringBuilder(player.getRepresentation());
                        message.append(" played ");
                        message.append(Helper.getLeaderFullRepresentation(playerLeader));
                        boolean purged = player.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Odlynn Myrr, the Keleres (Xxcha) hero, has been purged.");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Odlynn Myrr, the Keleres (Xxcha) hero, was not purged - something went wrong.");
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", game, afterButtons, "after");
        }
        // "dspnedyn"
        ButtonHelper.deleteMessage(event);
    }

    public static void drawAgenda(GenericInteractionCreateEvent event, int count, Game game, @NotNull Player player) {
        drawAgenda(event, count, false, game, player);
    }

    public static void drawAgenda(GenericInteractionCreateEvent event, int count, boolean fromBottom, Game game, @NotNull Player player) {
        drawAgenda(count, fromBottom, game, player, false);
    }

    public static void drawAgenda(int count, Game game, @NotNull Player player) {
        drawAgenda(count, false, game, player, false);
    }

    public static void drawAgenda(int count, boolean fromBottom, Game game, @NotNull Player player, boolean discard) {
        if (game == null) return;
        String sb = player.getRepresentationUnfogged() + " here " + (count == 1 ? "is" : "are") + " the agenda" + (count == 1 ? "" : "s") + " you have drawn:";

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = fromBottom ? game.drawBottomAgenda() : game.drawAgenda();
            if (entry != null) {
                AgendaModel agenda = Mapper.getAgenda(entry.getKey());
                List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

                List<Button> buttons = agendaButtons(agenda, entry.getValue(), discard);
                MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, agendaEmbed, buttons);
            }
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, "__Note: if you put both agendas on top, the second one you put will be revealed first!__");
    }

    public static void drawSpecificAgenda(String agendaID, Game game, @NotNull Player player) {
        String sb = player.getRepresentationUnfogged() + " here is the agenda you have drawn:";
        if (game == null) return;

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);

        Map.Entry<String, Integer> entry = game.drawSpecificAgenda(agendaID);
        if (entry != null) {
            AgendaModel agenda = Mapper.getAgenda(entry.getKey());
            List<MessageEmbed> agendaEmbed = Collections.singletonList(agenda.getRepresentationEmbed());

            List<Button> buttons = agendaButtons(agenda, entry.getValue(), false);
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, agendaEmbed, buttons);
        }
    }

    private static List<Button> agendaButtons(AgendaModel agenda, Integer id, boolean discard) {
        List<Button> buttons = new ArrayList<>();
        Button topButton = Buttons.green("topAgenda_" + id, "Put " + agenda.getName() + " on the top of the agenda deck.").withEmoji(Emoji.fromUnicode("🔼"));
        Button bottomButton = Buttons.red("bottomAgenda_" + id, "Put " + agenda.getName() + " on the bottom of the agenda deck.").withEmoji(Emoji.fromUnicode("🔽"));
        Button discardButton = Buttons.red("discardAgenda_" + id, "Discard " + agenda.getName()).withEmoji(Emoji.fromUnicode("🗑️"));

        buttons.add(topButton);
        if (!discard) {
            buttons.add(bottomButton);
        } else {
            buttons.add(discardButton);
        }
        return buttons;
    }

    public static void revealAgenda(GenericInteractionCreateEvent event, boolean revealFromBottom, Game game, MessageChannel channel) {
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        }
        if (!game.getStoredValue("lastAgendaReactTime").isEmpty()
            && (System.currentTimeMillis() - Long.parseLong(game.getStoredValue("lastAgendaReactTime"))) < 10 * 60 * 10) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Sorry, the last agenda was flipped too recently, so the bot is stopping here to prevent a double flip. Do /agenda reveal if there's no button and this was a mistake.");
            return;
        }

        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        if (aCount == 1 && game.isShowBanners()) {
            BannerGenerator.drawPhaseBanner("agenda", game.getRound(), game.getActionsChannel());
        }

        CryypterHelper.checkEnvoyUnlocks(game);

        game.setStoredValue("AssassinatedReps", "");
        game.setStoredValue("riskedPredictive", "");
        game.setStoredValue("conspiratorsFaction", "");
        String agendaID = game.revealAgenda(revealFromBottom);
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        // Button manualResolve = Buttons.red("autoresolve_manual", "Resolve it Manually");
        boolean action = false;
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            game.setPhaseOfGame("agendawaiting");
        } else {
            action = true;
        }

        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        String agendaTarget = agendaModel.getTarget();
        String agendaType = agendaModel.getType();
        String agendaName = agendaModel.getName();
        boolean cov = false;

        if ("Emergency Session".equalsIgnoreCase(agendaName)) {
            MessageHelper.sendMessageToChannel(channel, "# " + game.getPing()
                + " __Emergency Session__ revealed.\n## This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("elect law") || agendaID.equalsIgnoreCase("constitution"))
            && game.getLaws().isEmpty()) {
            MessageHelper.sendMessageToChannel(channel,
                game.getPing() + "A Law Related Agenda (" + agendaName
                    + ") was revealed when no laws in play, flipping next agenda");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("secret objective"))
            && game.getScoredSecrets() < 1) {
            MessageHelper.sendMessageToChannel(channel,
                game.getPing() + "An Elect Secret Agenda (" + agendaName
                    + ") was revealed when no scored secrets were in play, flipping next agenda");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_" + uniqueID + "_" + agendaID);
        } else {
            boolean notEmergency = false;
            while (!notEmergency) {
                if ("Emergency Session".equalsIgnoreCase(agendaName)) {
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel, game.getPing()
                        + " Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName);
                String id2 = game.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaModel.getName();
                game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");
                if ((agendaTarget.toLowerCase().contains("elect law") || id2.equalsIgnoreCase("constitution"))
                    && game.getLaws().isEmpty()) {
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel,
                        game.getPing()
                            + " an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                if ((agendaTarget.toLowerCase().contains("secret objective"))
                    && game.getScoredSecrets() < 1) {
                    MessageHelper.sendMessageToChannel(channel,
                        game.getPing() + "An Elect Secret Agenda (" + agendaName
                            + ") was revealed under Covert when no scored secrets were in play, flipping next agenda");
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                }

                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (game.getPlayer(game.getSpeakerUserID()) != null) {
                        speaker = game.getPlayers().get(game.getSpeakerUserID());
                    }
                    if (speaker != null) {
                        String sb = speaker.getRepresentationUnfogged() +
                            " this is the top agenda for Covert Legislation:";
                        List<MessageEmbed> embeds = List.of(Mapper.getAgenda(id2).getRepresentationEmbed());
                        MessageHelper.sendMessageEmbedsToCardsInfoThread(speaker, sb, embeds);
                        game.drawAgenda();

                    }
                }
            }
        }
        game.setStoredValue("Pass On Shenanigans", "");
        game.setStoredValue("Abstain On Agenda", "");
        game.resetCurrentAgendaVotes();
        game.setHasHackElectionBeenPlayed(false);
        game.setPlayersWhoHitPersistentNoAfter("");
        game.setPlayersWhoHitPersistentNoWhen("");
        game.setLatestOutcomeVotedFor("");
        for (Player p2 : game.getRealPlayers()) {
            game.setStoredValue("latestOutcomeVotedFor" + p2.getFaction(), "");
            game.setStoredValue("preVoting" + p2.getFaction(), "");
        }
        game.setLatestWhenMsg("");
        game.setLatestAfterMsg("");
        if (!action) {
            offerEveryonePrepassOnShenanigans(game);
            offerEveryonePreAbstain(game);
            checkForAssigningGeneticRecombination(game);
            checkForPoliticalSecret(game);
        }

        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();
        String revealMessage = game.getPing() + "\nAn agenda has been revealed";
        MessageHelper.sendMessageToChannelWithEmbed(channel, revealMessage, agendaEmbed);
        if (!action) {
            BannerGenerator.drawAgendaBanner(aCount, game);
        }
        StringBuilder whensAftersMessage = new StringBuilder(
            "Please indicate whether you abstain from playing whens/afters below.\nIf you have an action card with those windows, you may simply play it.");
        if (action) {
            whensAftersMessage.append("\nYou may play afters during this agenda.");
        }

        AutoPingMetadataManager.setupAutoPing(game.getName());
        List<Button> whenButtons = getWhenButtons(game);
        List<Button> afterButtons = getAfterButtons(game);

        MessageHelper.sendMessageToChannel(channel, whensAftersMessage.toString());
        if (!action) {
            MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", game, whenButtons, "when");
        }
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", game, afterButtons, "after");

        game.setStoredValue("lastAgendaReactTime", "" + System.currentTimeMillis());

        List<Button> proceedButtons = new ArrayList<>();
        String msg;

        if (action) {
            msg = "It seems likely you are resolving Midir, the Edyn hero, you may use this button to skip straight to the resolution.";
            proceedButtons.add(Buttons.red("autoresolve_manual", "Skip Straight To Resolution"));
        } else {
            listVoteCount(game, channel);
            msg = "Press this button if the last person forgot to react, but verbally said no whens/afters";
            proceedButtons.add(Buttons.red("proceedToVoting", "Skip waiting and start the voting for everyone"));
            proceedButtons.add(Buttons.blue("transaction", "Transaction"));
            proceedButtons.add(Buttons.red("eraseMyVote", "Erase my vote & have me vote again"));
            proceedButtons.add(Buttons.red("eraseMyRiders", "Erase my riders"));
            proceedButtons.add(Buttons.gray("refreshAgenda", "Refresh Agenda"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, proceedButtons);
        if (cov) {
            MessageHelper.sendMessageToChannel(channel,
                "# " + game.getPing() + " the agenda target is " + agendaTarget
                    + ". Sent the agenda to the speakers cards info");
        }

        if (!action && aCount == 1) {
            pingAboutDebt(game);
            String key = "round" + game.getRound() + "AgendaPlacement";
            if (!game.getStoredValue(key).isEmpty() && !game.isFowMode()) {
                StringBuilder message = new StringBuilder("Politics holder did the following with the agendas in terms of topping or bottoming them:");
                for (String actionA : game.getStoredValue(key).split("_")) {
                    message.append(" ").append(software.amazon.awssdk.utils.StringUtils.capitalize(actionA));
                }
                MessageHelper.sendMessageToChannel(channel,
                    message.toString());

            }
        }
        for (Player player : game.getRealPlayers()) {
            if (!action && game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && !ButtonHelperCommanders.resolveFlorzenCommander(player, game).isEmpty() && aCount == 2) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                        + " you have Quaxdol Junitas, the Florzen commander, and may thus explore and ready a planet.",
                    ButtonHelperCommanders.resolveFlorzenCommander(player, game));
            }
        }
        if (!game.isFowMode() && !action) {
            ButtonHelper.updateMap(game, event,
                "Start of the agenda " + agendaName + " (Agenda #" + aCount + ")");
            game.setStoredValue("startTimeOfRound" + game.getRound() + "Agenda" + aCount, System.currentTimeMillis() + "");
        }
        if (game.getCurrentAgendaInfo().contains("Secret")) {
            StringBuilder summary = new StringBuilder("## Scored Secret Objectives:\n");
            for (Player p2 : game.getRealPlayers()) {
                for (String soID : p2.getSecretsScored().keySet()) {
                    SecretObjectiveModel so = Mapper.getSecretObjective(soID);
                    if (so != null) {
                        summary.append("- ");
                        if (!game.isFowMode()) summary.append(p2.getFactionEmoji());
                        summary.append(CardEmojis.SecretObjective).append("__**").append(so.getName()).append("**__: ").append(so.getText()).append("\n");
                    }
                }
            }
            MessageHelper.sendMessageToChannel(channel, summary.toString());
        }
    }

    public static void listVoteCount(SlashCommandInteractionEvent event, Game game) {
        listVoteCount(game, event.getChannel());
    }

    public static void listVoteCount(Game game, MessageChannel channel) {
        List<Player> orderList = getVotingOrder(game);
        int votes = 0;
        for (Player player : orderList) {
            votes = votes + getTotalVoteCount(game, player);
        }
        StringBuilder sb = new StringBuilder("**__Vote Count (Total votes: "
            + (Boolean.parseBoolean(game.getFowOption(FowConstants.HIDE_TOTAL_VOTES)) ? "???" : votes));
        sb.append("):__**\n");
        int itemNo = 1;
        for (Player player : orderList) {
            sb.append("`").append(itemNo).append(".` ");
            sb.append(player.getRepresentation(false, false));
            if (player.getUserID().equals(game.getSpeakerUserID())) sb.append(MiscEmojis.SpeakerToken);
            sb.append(getPlayerVoteText(game, player));
            sb.append("\n");
            itemNo++;
        }
        MessageHelper.sendMessageToChannel(channel, sb.toString());
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
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Agenda put on top");
        ButtonHelper.sendMessageToRightStratThread(game.getPlayer(game.getActivePlayerID()), game, "Agenda put on top", "politics");
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
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Agenda put on bottom");
        ButtonHelper.sendMessageToRightStratThread(game.getPlayer(game.getActivePlayerID()), game, "Agenda put on bottom", "politics");
    }

    public static void showDiscards(Game game, GenericInteractionCreateEvent event) {
        StringBuilder sb2 = new StringBuilder();
        String sb = "### __**Discarded Agendas:**__";
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        List<MessageEmbed> agendaEmbeds = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : discardAgendas.entrySet()) {
            agendaEmbeds.add(Mapper.getAgenda(entry.getKey()).getRepresentationEmbed());
            sb2.append(Mapper.getAgenda(entry.getKey()).getName()).append(" (ID: ").append(entry.getValue()).append(")\n");
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
                for (Player player : game.getRealPlayers()) {
                    uH.removeAllShips(player);
                }
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelper.checkFleetInEveryTile(p2, game, event);
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
            "Removed all ships from systems with alphas or betas wormholes. \nYou may use the button to get your technology.", List.of(Buttons.GET_A_TECH));
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
    }

    public static void doSwords(Player player, GenericInteractionCreateEvent event, Game game) {
        List<String> planets = player.getPlanets();
        String ident = player.getFactionEmoji();
        StringBuilder message = new StringBuilder();
        int oldTg = player.getTg();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (planets.contains(unitHolder.getName())) {
                    int numInf = 0;
                    String colorID = Mapper.getColorID(player.getColor());
                    UnitKey infKey = Mapper.getUnitKey("gf", colorID);
                    if (unitHolder.getUnits() != null) {
                        if (unitHolder.getUnits().get(infKey) != null) {
                            numInf = unitHolder.getUnits().get(infKey);
                        }
                    }
                    if (numInf > 0) {
                        int numTG = (numInf + 1) / 2;
                        int cTG = player.getTg();
                        int fTG = cTG + numTG;
                        player.setTg(fTG);
                        message.append(ident).append(" removed ").append(numTG).append(" infantry from ").append(Helper.getPlanetRepresentation(unitHolder.getName(), game))
                            .append(" and gained ").append(numTG).append(" trade goods (").append(cTG).append("->").append(fTG).append("). \n");
                        tile.removeUnit(unitHolder.getName(), infKey, numTG);
                        if (player.hasInf2Tech()) {
                            ButtonHelper.resolveInfantryDeath(player, numTG);
                        }
                        boolean cabalMech = player.hasAbility("amalgamation") && unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0 && player.hasUnit("cabal_mech") && !game.getLaws().containsKey("articles_war");
                        if (player.hasAbility("amalgamation") && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", player, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_1", player, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_2", player, tile) || cabalMech) && FoWHelper.playerHasUnitsOnPlanet(player, tile, unitHolder.getName())) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, numTG, "infantry", event);
                        }

                    }
                }
            }
        }
        if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
            ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        ButtonHelperAgents.resolveArtunoCheck(player, player.getTg() - oldTg);
        ButtonHelperAbilities.pillageCheck(player, game);
    }

    public static void sendTopAgendaToCardsInfoSkipCovert(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Top Agenda:**__");
        String agendaID = game.lookAtTopAgenda(0);
        MessageEmbed embed = null;
        if (game.getSentAgendas().get(agendaID) != null) {
            if (game.getCurrentAgendaInfo().contains("_CL_") && game.getPhaseOfGame().startsWith("agenda")) {
                sb.append("You are currently voting on Covert Legislation and the top agenda is in the speaker's hand.");
                sb.append(" Showing the next agenda because that's how it should be by the RULEZ\n");
                agendaID = game.lookAtTopAgenda(1);

                if (game.getSentAgendas().get(agendaID) != null) {
                    embed = AgendaModel.agendaIsInSomeonesHandEmbed();
                } else if (agendaID != null) {
                    embed = Mapper.getAgenda(agendaID).getRepresentationEmbed();
                }
            } else {
                sb.append("The top agenda is currently in somebody's hand. As per the RULEZ, you should not be able to see the next agenda until they are finished deciding top/bottom/discard");
            }
        } else if (agendaID != null) {
            embed = Mapper.getAgenda(agendaID).getRepresentationEmbed();
        } else {
            sb.append("Could not find agenda");
        }
        if (embed != null) {
            MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), sb.toString(), embed);
        } else {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
        }
    }

    @ButtonHandler("topAgenda_")
    public static void topAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.putTop(Integer.parseInt(agendaNumID), game);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "top");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_top");
        }
        AgendaModel agenda = Mapper.getAgenda(game.lookAtTopAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
            "Put " + agenda.getName()
                + " on the top of the agenda deck. You may use this button to undo that and reassign it.",
            reassign);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("retrieveAgenda_")
    public static void retrieveAgenda(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String agendaID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.drawSpecificAgenda(agendaID, game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("discardAgenda_")
    public static void discardAgenda(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        String agendaID = game.revealAgenda(false);
        AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
        String agendaName = agendaDetails.getName();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmojiOrColor() + "discarded " + agendaName + " using "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Allant, the Edyn" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bottomAgenda_")
    public static void bottomAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.putBottom(Integer.parseInt(agendaNumID), game);
        AgendaModel agenda = Mapper.getAgenda(game.lookAtBottomAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
            "Put " + agenda.getName()
                + " on the bottom of the agenda deck. You may use this button to undo that and reassign it.",
            reassign);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "bottom");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_bottom");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("proceedToVoting")
    public static void proceedToVoting(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Decided to skip waiting for afters and proceed to voting.");
        try {
            AgendaHelper.startTheVoting(game);
        } catch (Exception e) {
            BotLogger.log(event, "Could not start the voting", e);
        }
    }

    @ButtonHandler("resolveVeto")
    public static void resolveVeto(ButtonInteractionEvent event, Game game) {
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount = 0;
        if (agendaCount.isEmpty()) {
            aCount = 0;
        } else {
            aCount = Integer.parseInt(agendaCount) - 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# The hidden agenda was " + agendaName
                    + "! You may find it in the discard.");
        }
        flipAgenda(event, game);
    }

    @ButtonHandler("flip_agenda")
    public static void flipAgenda(ButtonInteractionEvent event, Game game) {
        AgendaHelper.revealAgenda(event, false, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hack_election")
    public static void hackElection(ButtonInteractionEvent event, Game game) {
        game.setHasHackElectionBeenPlayed(false);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set Order Back To Normal.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("no_when_persistent")
    public static void noWhenPersistent(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No whens (locked in)" : null;
        game.addPlayersWhoHitPersistentNoWhen(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You hit no whens for this entire agenda. If you change your mind, you can just play a when or remove this setting by hitting no whens (for now)");
    }

    @ButtonHandler("no_after_persistent")
    public static void noAfterPersistent(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No afters (locked in)" : null;
        game.addPlayersWhoHitPersistentNoAfter(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You hit no afters for this entire agenda. If you change your mind, you can just play an after or remove this setting by hitting no afters (for now)");
    }

    @ButtonHandler("no_after")
    public static void noAfter(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No afters" : null;
        game.removePlayersWhoHitPersistentNoAfter(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("no_when")
    public static void noWhen(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No whens" : null;
        game.removePlayersWhoHitPersistentNoWhen(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
    }

    public static void playWhen(ButtonInteractionEvent event, Game game, Player player, MessageChannel mainGameChannel) {
        UnfiledButtonHandlers.clearAllReactions(event);
        ReactionService.addReaction(event, game, player, true, true, "Playing When", "When Played");
        List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
        MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", game, whenButtons, "when");
        List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
        MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", game, afterButtons, "after");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refreshVotes_")
    public static void refreshVotes(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String votes = buttonID.replace("refreshVotes_", "");
        List<Button> voteActionRow = Helper.getPlanetRefreshButtons(player, game);
        Button concludeRefreshing = Buttons.red(player.getFinsFactionCheckerPrefix() + "votes_" + votes, "Done readying planets.");
        voteActionRow.add(concludeRefreshing);
        String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), voteMessage2, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forceAbstainForPlayer_")
    public static void forceAbstainForPlayer(ButtonInteractionEvent event, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Player was forcefully abstained");
        String faction = buttonID.replace("forceAbstainForPlayer_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        AgendaHelper.resolvingAnAgendaVote("resolveAgendaVote_0", event, game, p2);
    }

    @ButtonHandler("eraseMyVote")
    public static void eraseMyVote(Player player, Game game) {
        String pfaction = player.getFaction();
        if (game.isFowMode()) {
            pfaction = player.getColor();
        }
        Helper.refreshPlanetsOnTheRevote(player, game);
        AgendaHelper.eraseVotesOfFaction(game, pfaction);
        String eraseMsg = "Erased previous votes made by " + player.getFactionEmoji() + " and readied the planets they previously exhausted\n\n" + AgendaHelper.getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), eraseMsg);
        Button vote = Buttons.green(player.getFinsFactionCheckerPrefix() + "vote", player.getFlexibleDisplayName() + " Choose To Vote");
        Button abstain = Buttons.red(player.getFinsFactionCheckerPrefix() + "resolveAgendaVote_0", player.getFlexibleDisplayName() + " Choose To Abstain");
        Button forcedAbstain = Buttons.gray("forceAbstainForPlayer_" + player.getFaction(), "(For Others) Abstain for this player");

        String buttonMsg = "Use buttons to vote again. Reminder that this erasing of old votes did not ready any exhausted planets.";
        List<Button> buttons = Arrays.asList(vote, abstain, forcedAbstain);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), buttonMsg, buttons);
    }
}
