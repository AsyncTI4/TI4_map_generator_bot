package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Space;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.UnlockLeaderService;

public class CryypterHelper {
    // Revised Politics SC
    public static List<Button> getCryypterSC3Buttons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawCards = Buttons.gray("cryypterSC3Draw", "Draw Action Cards", CardEmojis.ActionCard);
        return List.of(drawCards, followButton, noFollowButton);
    }

    @ButtonHandler("cryypterSC3Draw")
    public static void resolveCryypterSC3Draw(ButtonInteractionEvent event, Game game, Player player) {
        drawXPickYActionCards(game, player, 3, true);
    }

    private static void drawXPickYActionCards(Game game, Player player, int draw, boolean addScheming) {
        if (draw > 10) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " drew " + draw + " action card" + (draw == 1 ? "" : "s") + ".";
        if (addScheming && player.hasAbility("scheming")) {
            draw++;
            message = player.getRepresentation() + " drew " + draw + " action card" + (draw == 1 ? "" : "s")
                    + " (**Scheming** increases this from the normal " + (draw - 1) + " action card"
                    + (draw == 2 ? "" : "s") + ").";
        }

        for (int i = 0; i < draw; i++) {
            game.drawActionCard(player.getUserID());
        }
        ActionCardHelper.sendActionCardInfo(game, player);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard 1 of the " + draw + " cards just drawn.",
                ActionCardHelper.getDiscardActionCardButtons(player, false));

        ButtonHelper.checkACLimit(game, player);
        if (addScheming && player.hasAbility("scheming")) ActionCardHelper.sendDiscardActionCardButtons(player, false);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }

    // VotC Setup
    public static void votcSetup(Game game, ButtonInteractionEvent event) {
        game.setVotcMode(true);
        game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_cryypter"));
        game.setTechnologyDeckID("techs_cryypter");
        game.swapInVariantTechs();
        game.setStrategyCardSet("votc");
        // TODO: Implement swap function to only replace specific ACs?
        game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_cryypter"));
        // TODO: swap Xxcha and Keleres!Xxcha heroes
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to Voices of the Council mode.");
    }

    // Envoys
    public static void checkEnvoyUnlocks(Game game) {
        // if (!game.isVotcMode())
        // {
        //    return;
        // }
        for (Player player : game.getRealPlayers()) {
            Leader envoy = player.getLeaderByType("envoy").orElse(null);
            if (envoy != null && envoy.isLocked()) {
                UnlockLeaderService.unlockLeader(envoy.getId(), game, player);
            }
        }
    }

    public static String argentEnvoyReminder(Player player, Game game) {
        Player argent = Helper.getPlayerFromUnlockedLeader(game, "argentenvoy");
        if (argent != null && argent != player) {
            return " Reminder that Argent's Envoy is in play, and you may not wish to abstain.";
        } else {
            return "";
        }
    }

    public static void checkForAssigningMentakEnvoy(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.setStoredValue("Mentak Envoy " + player.getFaction(), "");
            if (player.hasUnexhaustedLeader("mentakenvoy")) {
                String msg = player.getRepresentation()
                        + " you have the option to pre-assign the declaration of using your Envoy on someone."
                        + " When they are up to vote, it will ping them saying that you wish to use your Envoy, and then it will be your job to clarify."
                        + " Feel free to not preassign if you don't wish to use it on this agenda.";
                List<Button> buttons2 = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (!game.isFowMode()) {
                        buttons2.add(Buttons.gray(
                                "resolvePreassignment_Mentak Envoy " + player.getFaction() + "_" + p2.getFaction(),
                                p2.getFaction()));
                    } else {
                        buttons2.add(Buttons.gray(
                                "resolvePreassignment_Mentak Envoy " + player.getFaction() + "_" + p2.getFaction(),
                                p2.getColor()));
                    }
                }
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
            }
        }
    }

    public static void checkForMentakEnvoy(Player voter, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == voter) {
                continue;
            }
            Leader playerLeader = p2.getLeader("mentakenvoy").orElse(null);
            if (playerLeader != null) {
                if (game.getStoredValue("Mentak Envoy " + p2.getFaction()).contains(voter.getFaction())) {
                    envoyExhaustCheck(game, p2, "mentakenvoy");
                    String msg = p2.getRepresentation(false, true) + " is using the Mentak Envoy to force "
                            + voter.getRepresentation(false, true)
                            + " to vote a particular way. The Envoy has been exhausted, the owner should elaborate on which way to vote.";
                    MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), msg);
                    boolean hasPNs = voter.getPnCount() > 0;
                    boolean hasEnoughTGs = voter.getTg() > 1;
                    if (hasPNs || hasEnoughTGs) {
                        msg = voter.getRepresentation(false, true)
                                + " has the option to give 1 promissory note or 2 trade goods to ignore the effect of Mentak Envoy.";
                        List<Button> conclusionButtons = new ArrayList<>();
                        String buttonID =
                                voter.getFinsFactionCheckerPrefix() + "resolveMentakEnvoy_" + p2.getFaction() + "_";
                        Button accept = Buttons.blue(buttonID + "accept", "Vote For Chosen Outcome");
                        conclusionButtons.add(accept);
                        if (hasPNs) {
                            Button PNdecline = Buttons.red(buttonID + "PNdecline", "Send 1 promissory note");
                            conclusionButtons.add(PNdecline);
                        }
                        if (hasEnoughTGs) {
                            Button TGdecline = Buttons.red(buttonID + "TGdecline", "Send 2 trade goods");
                            conclusionButtons.add(TGdecline);
                        }

                        MessageHelper.sendMessageToChannelWithButtons(
                                voter.getCorrectChannel(), msg, conclusionButtons);
                    }
                }
            }
        }
    }

    @ButtonHandler("resolveMentakEnvoy_")
    public static void resolveMentakEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] fields = buttonID.split("_");
        Player mentakPlayer = game.getPlayerFromColorOrFaction(fields[1]);
        String choice = fields[2];
        if ("accept".equals(choice)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + " has chosen to vote for the outcome indicated by "
                            + mentakPlayer.getRepresentation()
                            + ".");
        } else if ("PNdecline".equals(choice)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + " has chosen to send a promissory note and may vote in any manner that they wish.");
            List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, mentakPlayer, player);
            String message = player.getRepresentationUnfogged()
                    + ", you have been forced to give a promissory note. Please select which promissory note you would like to send.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, stuffToTransButtons);
        } else {
            player.setTg(player.getTg() - 2);
            mentakPlayer.setTg(mentakPlayer.getTg() + 2);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation() + " has given 2 trade goods and may vote in any manner that they wish.");
        }
        event.getMessage().delete().queue();
    }

    public static void checkForAssigningYssarilEnvoy(
            GenericInteractionCreateEvent event, Game game, Player player, String acID) {
        if (player.hasUnexhaustedLeader("yssarilenvoy")
                && game.getPhaseOfGame() != null
                && game.getPhaseOfGame().startsWith("agenda")) {
            String msg = player.getRepresentation()
                    + " you have the option to user your Envoy on someone."
                    + " It will ping them saying that they may copy the effect of the action card you just played.";
            List<Button> buttons2 = new ArrayList<>();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                String buttonText = "offerYssarilEnvoy_" + player.getFaction() + "_" + p2.getFaction() + "_" + acID;
                if (!game.isFowMode()) {
                    buttons2.add(Buttons.gray(buttonText, p2.getFaction()));
                } else {
                    buttons2.add(Buttons.gray(buttonText, p2.getColor()));
                }
            }
            buttons2.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
        }
    }

    @ButtonHandler("offerYssarilEnvoy_")
    public static void offerYssarilEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] fields = buttonID.split("_");
        Player yssarilPlayer = game.getPlayerFromColorOrFaction(fields[1]);
        Player targetPlayer = game.getPlayerFromColorOrFaction(fields[2]);
        String acID = buttonID.replace("offerYssarilEnvoy_" + fields[1] + "_" + fields[2] + "_", "");

        String msg = yssarilPlayer.getRepresentation(false, true) + " is using the Yssaril Envoy to allow "
                + targetPlayer.getRepresentation(false, true)
                + " to copy the effect of their action card. The Envoy has been exhausted.";
        MessageHelper.sendMessageToChannel(targetPlayer.getCorrectChannel(), msg);

        msg = targetPlayer.getRepresentation(false, true)
                + " has the option to accept or ignore the effect of the Yssaril Envoy.";
        List<Button> conclusionButtons = new ArrayList<>();
        Button accept = Buttons.blue(
                targetPlayer.getFinsFactionCheckerPrefix() + "resolveYssarilEnvoy_accept_" + acID,
                "Copy " + Mapper.getActionCard(acID).getName());
        conclusionButtons.add(accept);

        Button decline =
                Buttons.red(targetPlayer.getFinsFactionCheckerPrefix() + "resolveYssarilEnvoy_decline", "Decline");
        conclusionButtons.add(decline);

        MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), msg, conclusionButtons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("resolveYssarilEnvoy_")
    public static void resolveYssarilEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] fields = buttonID.split("_");
        String choice = fields[1];
        if ("decline".equals(choice)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation() + " has chosen not to resolve the effect of the Yssaril Envoy.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation() + " has chosen to resolve the effect of the Yssaril Envoy.");
            ActionCardHelper.resolveActionCard(
                    event, game, player, buttonID.replace("resolveYssarilEnvoy_" + choice + "_", ""), -1, null);
        }

        event.getMessage().delete().queue();
    }

    private static void envoyExhaustCheck(Game game, Player player, String envoyID) {
        if ("keleresenvoy,l1z1xenvoy,letnevenvoy,mahactenvoy,mentakenvoy,naazenvoy".contains(envoyID)) {
            player.getLeader(envoyID)
                    .ifPresent(playerLeader -> ExhaustLeaderService.exhaustLeader(game, player, playerLeader));
        }
    }

    public static void addVotCAfterButtons(Game game, List<Button> afterButtons) {
        for (Player player : game.getPlayers().values()) {
            votcRiderButtons(player, afterButtons, true);
        }
    }

    public static void addVotCRiderQueueButtons(Player player, List<Button> buttons) {
        votcRiderButtons(player, buttons, false);
    }

    private static void votcRiderButtons(Player player, List<Button> buttons, boolean play) {
        for (Leader leader : player.getLeaders()) {
            LeaderModel leaderModel = leader.getLeaderModel().orElse(null);
            if (!leader.isLocked() && "After an agenda is revealed:".equals(leaderModel.getAbilityWindow())) {
                FactionModel factionModel = Mapper.getFaction(leaderModel.getFaction());
                String buttonID;
                if ("hero".equals(leaderModel.getType())) {
                    buttonID = "Keleres Xxcha Hero";
                } else {
                    buttonID = factionModel.getShortName() + " Envoy";
                }
                String buttonLabel =
                        leaderModel.getName() + " (" + factionModel.getShortName() + " " + leaderModel.getType() + ")";
                if (play) {
                    buttons.add(Buttons.gray(
                            player.getFinsFactionCheckerPrefix() + "play_after_" + buttonID,
                            "Play " + buttonLabel,
                            factionModel.getFactionEmoji()));
                } else {
                    buttons.add(Buttons.red("queueAfter_leader_" + buttonID, buttonLabel));
                }
            }
        }
    }

    public static Leader keleresHeroCheck(Player player, Leader playerLeader) {
        if (playerLeader == null) {
            playerLeader = player.getLeader("votc_keleresheroxxcha").orElse(null);
        }
        return playerLeader;
    }

    public static void handleWinningRiders(Game game, String winningOutcome) {
        // AgendaHelper.placeRider()
        // format of stored votes and outcomes (identifier can be either color or name): [faction
        // identifier]_[number];[faction identifier]_[rider name]
        if (game.isVotcMode()) {
            Map<String, Player> usedEnvoy = new HashMap<>();
            List<Player> committedWinner = new ArrayList<>();
            List<Player> committedLoser = new ArrayList<>();
            List<Player> counterWinners = new ArrayList<>();
            List<Player> counterLosers = new ArrayList<>();
            boolean empy = false;

            Map<String, String> outcomes = game.getCurrentAgendaVotes();
            for (Map.Entry<String, String> entry : outcomes.entrySet()) {
                StringTokenizer vote_info = new StringTokenizer(entry.getValue(), ";");
                while (vote_info.hasMoreTokens()) {
                    String voteOrRider = vote_info.nextToken();
                    if (Objects.equals(entry.getKey(), winningOutcome)) {
                        if (voteOrRider.contains("empyreanenvoy")) {
                            empy = true;
                        }
                        subHandleWinningRiders(game, voteOrRider, usedEnvoy, committedWinner, counterWinners);
                    } else {
                        subHandleWinningRiders(game, voteOrRider, usedEnvoy, committedLoser, counterLosers);
                    }
                }
            }

            for (Map.Entry<String, Player> e : usedEnvoy.entrySet()) {
                String key = e.getKey();
                Player envoyPlayer = e.getValue();
                MessageChannel channel = envoyPlayer.getCorrectChannel();
                if (key.contains("arborecenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Arborec Envoy to resolve. Choose the planet you wish to place an infantry on.";
                    List<Tile> arboTiles = ButtonHelper.getTilesOfPlayersSpecificUnits(
                            game, envoyPlayer, UnitType.Mech, UnitType.Infantry);
                    Map<Planet, Integer> eligiblePlanets = new HashMap<>();

                    for (Tile arbotile : arboTiles) {
                        Space space = arbotile.getSpaceUnitHolder();
                        int totalLetani = ButtonHelper.getNumberOfGroundForces(envoyPlayer, space);
                        List<Planet> arboPlanets = arbotile.getPlanetUnitHolders();
                        if (!arboPlanets.isEmpty()) {
                            for (Planet arboPlanet : arboPlanets) {
                                totalLetani += ButtonHelper.getNumberOfGroundForces(envoyPlayer, arboPlanet);
                            }
                            for (Planet arboPlanet : arboPlanets) {
                                arboEnvoyCollector(game, envoyPlayer, eligiblePlanets, arboPlanet, totalLetani);
                            }
                        }

                        Set<String> adjTiles =
                                FoWHelper.getAdjacentTiles(game, arbotile.getPosition(), null, false, false);
                        for (String adjTile : adjTiles) {
                            Tile tile = game.getTileByPosition(adjTile);
                            List<Planet> planets = tile.getPlanetUnitHolders();
                            for (Planet planet : planets) {
                                arboEnvoyCollector(game, envoyPlayer, eligiblePlanets, planet, totalLetani);
                            }
                        }
                    }

                    List<Button> planetButtons = new ArrayList<>();
                    for (Map.Entry<Planet, Integer> entry : eligiblePlanets.entrySet()) {
                        Planet planet = entry.getKey();
                        if (entry.getValue() > 1
                                && planet.getTokenList().stream().noneMatch(token -> token.contains("dmz"))) {
                            Button button = Buttons.green(
                                    envoyPlayer.getFinsFactionCheckerPrefix() + "placeOneNDone_skipbuild_infantry_"
                                            + planet.getName(),
                                    Helper.getPlanetRepresentation(planet.getName(), game));

                            planetButtons.add(button);
                        }
                    }

                    MessageHelper.sendMessageToChannelWithButtons(channel, message, planetButtons);
                }
                if (key.contains("cabalenvoy") && committedLoser.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Cabal Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("ghostenvoy") && committedLoser.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Creuss Envoy to resolve. Choose the system you wish to place a Creuss wormhole token in.";

                    List<Planet> eligiblePlanets = new ArrayList<>();
                    List<Button> buttons = new ArrayList<>();

                    for (Player counterPlayer : counterWinners) {
                        List<String> planets = counterPlayer.getPlanets();
                        for (String planetID : planets) {
                            Planet p = game.getUnitHolderFromPlanet(planetID);
                            if (!p.isHomePlanet()) {
                                PlanetModel tempPlanet = Mapper.getPlanet(planetID);
                                String tilePos =
                                        game.getTile(tempPlanet.getTileId()).getPosition();
                                buttons.add(Buttons.gray(
                                        "creussEnvoyType_" + tilePos,
                                        Helper.getPlanetRepresentation(p.getName(), game)));
                            }
                        }
                    }
                    MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                }
                if (key.contains("empyreanenvoy") && empy) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Empyrean Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("hacanenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Hacan Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("jolnarenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Jol Nar Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("l1z1xenvoy") && committedLoser.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the L1Z1X Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("letnevenvoy") && committedLoser.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Letnev Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("mahactenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Mahact Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("muaatenvoy")
                        && committedLoser.contains(envoyPlayer)
                        && Helper.getCCCount(game, envoyPlayer.getColor()) < 16) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", your Muaat Envoy resolved, and you have gained a command token in your strategy pool.";
                    // TODO: replace with correct buttons
                    envoyPlayer.setStrategicCC(envoyPlayer.getStrategicCC() + 1);
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("naazenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Naaz Rokha Envoy to resolve, and may look at the top card of the relic deck,"
                            + " then you may exhaust the envoy to either put that card on the bottom of the deck,"
                            + " or purge 2 relic fragments to gain that relic.";

                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            envoyPlayer.getFinsFactionCheckerPrefix() + "relic_look_top", "Look at top of Relic Deck"));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);

                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green(
                            "handleNaazEnvoy_Gain", "Purge 2 fragments to gain relic", ExploreEmojis.Relic));
                    buttons2.add(Buttons.gray("handleNaazEnvoy_Bottom", "Put relic on the bottom of the deck")
                            .withEmoji(Emoji.fromUnicode("🔽")));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons2);
                }
                if (key.contains("nekroenvoy")) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Nekro Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("sardakkenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the N'orr Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("saarenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Saar Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("solenvoy") && committedWinner.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Sol Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
                if (key.contains("titansenvoy") && committedLoser.contains(envoyPlayer)) {
                    String message = envoyPlayer.getRepresentationUnfogged()
                            + ", you have the Titans Envoy to resolve. This is not yet implemented in the bot, so you will need to resolve the effect manually.";
                    MessageHelper.sendMessageToChannel(channel, message);
                }
            }
        }
    }

    private static void subHandleWinningRiders(
            Game game, String voteOrRider, Map<String, Player> usedEnvoy, List<Player> committed, List<Player> voters) {
        String[] fields = voteOrRider.split("_");
        Player tempPlayer = game.getPlayerFromColorOrFaction(fields[0].toLowerCase());

        if (fields[1].contains("envoy")) {
            usedEnvoy.put(fields[1], tempPlayer);
            if (voteOrRider.contains("empyreanenvoy") && !committed.contains(tempPlayer)) {
                committed.add(tempPlayer);
            }
        } else if (NumberUtils.isDigits(fields[1])) {
            voters.add(tempPlayer);
            if (Integer.parseInt(fields[1]) >= 5 && !committed.contains(tempPlayer)) {
                committed.add(tempPlayer);
            }
        } else {
            if (fields[1].contains("Rider") || fields[1].contains("Sanction")) {
                if (tempPlayer != null && !committed.contains(tempPlayer)) {
                    committed.add(tempPlayer);
                }
            }
        }
    }

    @ButtonHandler("handleNaazEnvoy_")
    public static void handleNaazEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        envoyExhaustCheck(game, player, "naazenvoy");
        String[] fields = buttonID.split("_");
        switch (fields[1]) {
            case "Gain":
                if (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() == 2) {
                    List<String> playerFragments = player.getFragments();
                    List<String> fragmentsToPurge = new ArrayList<>(playerFragments);
                    StringBuilder message2 = new StringBuilder(player.getRepresentation() + " purged");
                    for (String fragid : fragmentsToPurge) {
                        player.removeFragment(fragid);
                        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                        switch (fragid) {
                            case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" ->
                                message2.append(" " + ExploreEmojis.CFrag);
                            case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" ->
                                message2.append(" " + ExploreEmojis.HFrag);
                            case "irf1", "irf2", "irf3", "irf4", "irf5" -> message2.append(" " + ExploreEmojis.IFrag);
                            case "urf1", "urf2", "urf3" -> message2.append(" " + ExploreEmojis.UFrag);
                            default -> message2.append(" ").append(fragid);
                        }
                    }
                    CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");
                    message2.append(" relic fragments.");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2.toString());
                } else {
                    String finChecker = player.getFinsFactionCheckerPrefix();
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (player.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (player.getIrf() > 0) {
                        Button transact =
                                Buttons.green(finChecker + "purge_Frags_IRF_1", "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (player.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (player.getUrf() > 0) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_1", "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                    purgeFragButtons.add(transact2);

                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + ", please purge 2 relic fragments.",
                            purgeFragButtons);
                }
                break;
            case "Bottom":
                ButtonHelperCommanders.uydaiCommanderBottom(
                        player, game, player.getFinsFactionCheckerPrefix() + "uydaiCommanderBottom_relic", event);
                break;
            default:
                break;
        }
    }

    private static void arboEnvoyCollector(
            Game game, Player envoyPlayer, Map<Planet, Integer> eligiblePlanets, Planet planet, Integer totalLetani) {
        if (eligiblePlanets.containsKey(planet)) {
            eligiblePlanets.merge(
                    planet, totalLetani - ButtonHelper.getNumberOfGroundForces(envoyPlayer, planet), Integer::sum);
        } else {
            List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet);
            players.remove(envoyPlayer);
            if (players.isEmpty()) {
                eligiblePlanets.merge(
                        planet, totalLetani - ButtonHelper.getNumberOfGroundForces(envoyPlayer, planet), Integer::sum);
            }
        }
    }

    @ButtonHandler("creussEnvoyType_")
    public static void creussEnvoyType(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String tilePos = buttonID.split("_")[1];
        String message = " choose which type of Creuss wormhole token to place in " + tilePos + ".";
        buttons.add(Buttons.red("handleCreussEnvoy_" + tilePos + "_alpha", "Alpha", MiscEmojis.CreussAlpha));
        buttons.add(Buttons.green("handleCreussEnvoy_" + tilePos + "_beta", "Beta", MiscEmojis.CreussBeta));
        buttons.add(Buttons.blue("handleCreussEnvoy_" + tilePos + "_gamma", "Gamma", MiscEmojis.CreussGamma));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("handleCreussEnvoy_")
    public static void handleCreussEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String tilePos = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = game.getTileByPosition(tilePos);
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        String msg = player.getRepresentation() + " moved " + MiscEmojis.getCreussWormhole(tokenName) + " " + type
                + " wormhole to " + tile.getRepresentationForButtons(game, player);
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                msg += " (from " + tile_.getRepresentationForButtons(game, player) + ")";
                break;
            }
        }
        msg += ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    public static String handleCovert(String target) {
        if (target.contains("(")) {
            return target.substring(0, target.indexOf('('));
        }
        return target;
    }

    // for later, WIP

    public static void handleAdditionalVoteSources(Player player, Map<String, Integer> additionalVotesAndSources) {
        if (player.hasTechReady("cryypter_pi")) {
            additionalVotesAndSources.put(TechEmojis.CyberneticTech + "_Predictive Intelligence_", 4);
        }
        // Nekro envoy
        if (player.getLeader("winnuenvoy").orElse(null) != null) {
            if (player.getExhaustedPlanets().contains("mr")) {
                additionalVotesAndSources.put(FactionEmojis.Winnu + " envoy with Mecatol Rex", 6);
            } else if (player.getExhaustedPlanets().contains("winnu")) {
                additionalVotesAndSources.put(FactionEmojis.Winnu + " envoy with Winnu", 4);
            }
        }
    }

    // TODO: Add "CryypterHelper.exhaustForVotes(player, thing);" to end of "if" clause of
    // AgendaHelper.exhaustForVotes(), currently line 2469
    public static void exhaustForVotes(Game game, Player player, String component, boolean prevoting) {
        // Yin envoy exhausting other player's planet
        if (component.contains("yinenvoy")) {
            if (!prevoting) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " please remove 1 infantry to pay for the Yin Envoy.",
                        ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "infantry"));
            }
        }
    }

    // getPlanetButtonsVersion2 2574
    public static void getVoteSourceButtons(Player player, List<Button> planetButtons) {
        if (player.hasTechReady("cryypter_pi")) {
            planetButtons.add(Buttons.blue(
                    "exhaustForVotes_cryypterpi_4",
                    "Use Predictive Intelligence Votes (4)",
                    TechEmojis.CyberneticTech));
        }
    }

    // AgendaHelper.getWinningRiders(), currently line 1832
    // public static void handleVotCRiders()
    // {
    //
    // }
}
