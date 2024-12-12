package ti4.service.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PlayerTitleHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.BannerGenerator;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.listeners.UserJoinServerListener;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.StatusCleanupService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.ListTurnOrderService;
import ti4.service.turn.EndTurnService;
import ti4.service.turn.StartTurnService;

@UtilityClass
public class StartPhaseService {

    public static void startPhase(GenericInteractionCreateEvent event, Game game, String phase) {
        switch (phase) {
            case "strategy" -> startStrategyPhase(event, game);
            case "voting", "agendaVoting" -> AgendaHelper.startTheVoting(game);
            case "finSpecial" -> ButtonHelper.fixAllianceMembers(game);
            // case "P1Special" -> new RepositoryDispatchEvent("archive_game_channel", Map.of("channel", "1082164664844169256")).sendEvent();
            case "shuffleDecks" -> game.shuffleDecks();
            case "publicObj" -> ListPlayerInfoService.displayerScoringProgression(game, true, event.getMessageChannel(), "both");
            case "publicObjAll" -> ListPlayerInfoService.displayerScoringProgression(game, false, event.getMessageChannel(), "1");
            case "ixthian" -> AgendaHelper.rollIxthian(game, false);
            case "gameTitles" -> PlayerTitleHelper.offerEveryoneTitlePossibilities(game);
            case "giveAgendaButtonsBack" -> Helper.giveMeBackMyAgendaButtons(game);
            case "finSpecialSomnoFix" -> Helper.addBotHelperPermissionsToGameChannels(event);
            case "finSpecialAbsol" -> AgendaHelper.resolveAbsolAgainstChecksNBalances(game);
            case "finFixSecrets" -> game.fixScrewedSOs();
            case "statusScoring" -> {
                EndTurnService.showPublicObjectivesWhenAllPassed(event, game, game.getMainGameChannel());
                game.updateActivePlayer(null);
            }
            case "endOfGameSummary" -> {
                StringBuilder endOfGameSummary = new StringBuilder();

                for (int x = 1; x < game.getRound() + 1; x++) {
                    StringBuilder summary = new StringBuilder();
                    for (Player player : game.getRealPlayers()) {
                        if (!game.getStoredValue("endofround" + x + player.getFaction()).isEmpty()) {
                            summary.append(player.getFactionEmoji()).append(": ").append(game.getStoredValue("endofround" + x + player.getFaction())).append("\n");
                        }
                    }
                    if (!summary.isEmpty()) {
                        summary.insert(0, "**__Round " + x + " Summary__**\n");
                        endOfGameSummary.append(summary);
                    }
                }
                if (!endOfGameSummary.isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), endOfGameSummary.toString());
                }
            }
            case "statusHomework" -> startStatusHomework(event, game);
            case "agendaResolve" -> AgendaHelper.resolveTime(game, null);
            case "pbd1000decks" -> game.pbd1000decks();
            case "action" -> startActionPhase(event, game);
            case "playerSetup" -> ButtonHelper.offerPlayerSetupButtons(event.getMessageChannel(), game);
            default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find phase: `" + phase + "`");
        }
    }

    public static void startStrategyPhase(GenericInteractionCreateEvent event, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (game.getStoredValue("LastMinuteDeliberation") != null
                && game.getStoredValue("LastMinuteDeliberation").contains(p2.getFaction())
                && p2.getActionCards().containsKey("last_minute_deliberation")) {
                ActionCardHelper.playAC(event, game, p2, "last minute deliberation", game.getMainGameChannel());
                return;
            }
            if (game.getStoredValue("SpecialSession") != null
                && game.getStoredValue("SpecialSession").contains(p2.getFaction())
                && p2.getActionCards().containsKey("special_session")) {
                ActionCardHelper.playAC(event, game, p2, "special session", game.getMainGameChannel());
                return;
            }

        }
        int round = game.getRound();
        if (game.isHasHadAStatusPhase()) {
            round++;
            game.setRound(round);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Started Round " + round);
        if (game.isShowBanners()) {
            BannerGenerator.drawPhaseBanner("strategy", round, game.getActionsChannel());
        }
        if (game.getRealPlayers().size() == 6) {
            game.setStrategyCardsPerPlayer(1);
        }
        ButtonHelperFactionSpecific.checkForNaaluPN(game);
        for (Player p2 : game.getRealPlayers()) {
            if (game.getStoredValue("Summit") != null
                && game.getStoredValue("Summit").contains(p2.getFaction())
                && p2.getActionCards().containsKey("summit")) {
                ActionCardHelper.playAC(event, game, p2, "summit", game.getMainGameChannel());
            }

            if (game.getStoredValue("Investments") != null
                && game.getStoredValue("Investments").contains(p2.getFaction())
                && p2.getActionCards().containsKey("investments")) {
                ActionCardHelper.playAC(event, game, p2, "investments", game.getMainGameChannel());
            }

            if (game.getStoredValue("PreRevolution") != null
                && game.getStoredValue("PreRevolution").contains(p2.getFaction())
                && p2.getActionCards().containsKey("revolution")) {
                ActionCardHelper.playAC(event, game, p2, "revolution", game.getMainGameChannel());
            }
            if (game.getStoredValue("Deflection") != null
                && game.getStoredValue("Deflection").contains(p2.getFaction())
                && p2.getActionCards().containsKey("deflection")) {
                ActionCardHelper.playAC(event, game, p2, "deflection", game.getMainGameChannel());
            }
            if (p2.hasLeader("zealotshero") && p2.getLeader("zealotshero").get().isActive()) {
                if (!game.getStoredValue("zealotsHeroTechs").isEmpty()) {
                    String list = game.getStoredValue("zealotsHeroTechs");
                    List<Button> buttons = new ArrayList<>();
                    for (String techID : list.split("-")) {
                        buttons.add(Buttons.green("purgeTech_" + techID, "Purge " + Mapper.getTech(techID).getName()));
                    }
                    String msg = p2.getRepresentationUnfogged() + " due to Saint Binal, the Rhodun hero, you have to purge 2 techs. Use buttons to purge ";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg + "the first tech.", buttons);
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg + "the second tech.", buttons);
                    p2.removeLeader("zealotshero");
                    game.setStoredValue("zealotsHeroTechs", "");
                }
            }
        }
        if (!game.getStoredValue("agendaConstitution").isEmpty()) {
            game.setStoredValue("agendaConstitution", "");
            for (Player p2 : game.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (game.getTileFromPlanet(planet) == p2.getHomeSystemTile()) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# Exhausted all home systems due to that one agenda");
        }
        if (!game.getStoredValue("agendaArmsReduction").isEmpty()) {
            game.setStoredValue("agendaArmsReduction", "");
            for (Player p2 : game.getRealPlayers()) {
                for (String planet : p2.getPlanets()) {
                    if (planet.contains("custodia") || planet.contains("ghoti")) {
                        continue;
                    }
                    if (ButtonHelper.isPlanetTechSkip(planet, game)) {
                        p2.exhaustPlanet(planet);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# Exhausted all tech skip planets due to that one agenda.");
        }
        if (!game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            game.setStoredValue("agendaChecksNBalancesAgainst", "");
            for (Player p2 : game.getRealPlayers()) {
                String message = p2.getRepresentation() + " Click the names of up to 3 planets you wish to ready after Checks and Balances resolved against.";
                List<Button> buttons = Helper.getPlanetRefreshButtons(event, p2, game);
                buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets")); // spitItOut
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, buttons);
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# Sent buttons to refresh 3 planets due to Checks and Balances.");
        }
        if (!game.getStoredValue("agendaRevolution").isEmpty()) {
            game.setStoredValue("agendaRevolution", "");
            for (Player p2 : game.getRealPlayers()) {
                String message = p2.getRepresentation() + " Exhaust 1 planet for each tech you own (" + p2.getTechs().size() + ")";

                List<Button> buttons = Helper.getPlanetExhaustButtons(p2, game);
                buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting")); // spitItOut
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, buttons);
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# Sent buttons to exhaust 1 planet for each tech due to Anti-Intellectual Revolution resolving against.");
        }
        if (!game.getStoredValue("agendaRepGov").isEmpty()) {
            for (Player p2 : game.getRealPlayers()) {
                if (game.getStoredValue("agendaRepGov").contains(p2.getFaction())) {
                    for (String planet : p2.getPlanets()) {
                        Planet p = game.getPlanetsInfo().get(planet);
                        if (p != null && p.getPlanetTypes().contains("cultural")) {
                            p2.exhaustPlanet(planet);
                        }
                    }
                }
            }
            game.setStoredValue("agendaRepGov", "");
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# Exhausted all cultural planets of those who voted against on that one agenda");
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Pinged speaker to pick a strategy card.");
        }
        Player speaker;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Speaker not found. Can't proceed");
            return;
        }
        String message = speaker.getRepresentationUnfogged() + " UP TO PICK SC\n";
        game.updateActivePlayer(speaker);
        game.setPhaseOfGame("strategy");
        String pickSCMsg = "Use buttons to pick a strategy card.";
        if (game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks")) {
            pickSCMsg = "Use buttons to pick the strategy card you want to give someone else.";
        }
        ButtonHelperAbilities.giveKeleresCommsNTg(game, event);
        game.setStoredValue("startTimeOfRound" + game.getRound() + "Strategy", System.currentTimeMillis() + "");
        if (game.isFowMode()) {
            if (!game.isHomebrewSCMode()) {
                MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(), message + pickSCMsg, Helper.getRemainingSCButtons(event, game, speaker));
            } else {
                MessageHelper.sendPrivateMessageToPlayer(speaker, game, message);
            }
        } else {
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message + pickSCMsg,
                Helper.getRemainingSCButtons(event, game, speaker));
        }
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event, "Start of Strategy Phase For Round #" + game.getRound());
        }
        for (Player player2 : game.getRealPlayers()) {
            if (player2.getActionCards() != null && player2.getActionCards().containsKey("summit")) {
                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(), player2.getRepresentationUnfogged() + "Reminder this is the window to play Summit.");
            }
            for (String pn : player2.getPromissoryNotes().keySet()) {
                if (!player2.ownsPromissoryNote("scepter") && "scepter".equalsIgnoreCase(pn)) {
                    PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
                    Player owner = game.getPNOwner(pn);
                    Button transact = Buttons.green("resolvePNPlay_" + pn, "Play " + promissoryNote.getName(), owner.getFactionEmoji());
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    String cyberMessage = player2.getRepresentationUnfogged() + " reminder this is the window to play Mahact PN if you want (button should work)";
                    MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), cyberMessage, buttons);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "You should all pause for a potential mahact PN play here if you think it relevant");
                    }
                }
            }
        }
        if (game.getTile("SIG02") != null && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Please destroy all units in the pulsar.");
        }
        if ("action_deck_2".equals(game.getAcDeckID()) && game.getRound() > 1) {
            handleStartOfStrategyForAcd2(game);
        }
    }

    private static void handleStartOfStrategyForAcd2(Game game) {
        boolean deflectionDiscarded = game.isACInDiscard("Deflection");
        boolean revolutionDiscarded = game.isACInDiscard("Revolution");
        StringJoiner stringJoiner = new StringJoiner(" and ");
        if (!deflectionDiscarded)
            stringJoiner.add("*Deflection*");
        if (!revolutionDiscarded)
            stringJoiner.add("*Revolution*");
        String acd2Shenanigans;
        if (stringJoiner.length() > 0) {
            acd2Shenanigans = "This is the window for " + stringJoiner + "! " + game.getPing();
            handleStartOfStrategyForAcd2Player(game);
        } else {
            acd2Shenanigans = "*Deflection* and *Revolution* are in the discard pile. Feel free to move forward.";
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), acd2Shenanigans);
    }

    private static void handleStartOfStrategyForAcd2Player(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (player.getActionCards().containsKey("deflection")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentationUnfogged() + "Reminder this is the window to play Deflection.");
            }
            if (player.getActionCards().containsKey("revolution")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentationUnfogged() + "Reminder this is the window to play Revolution.");
            }
        }
    }

    public static void startStatusHomework(GenericInteractionCreateEvent event, Game game) {
        game.setPhaseOfGame("statusHomework");
        game.setStoredValue("startTimeOfRound" + game.getRound() + "StatusHomework", System.currentTimeMillis() + "");
        // first do cleanup if necessary
        int playersWithSCs = 0;
        for (Player player : game.getRealPlayers()) {
            if (player.getSCs() != null && !player.getSCs().isEmpty() && !player.getSCs().contains(0)) {
                playersWithSCs++;
            }
        }

        if (playersWithSCs > 0) {
            StatusCleanupService.runStatusCleanup(game);
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + " **Status Cleanup Run!**");
            if (!game.isFowMode()) {
                MapRenderPipeline.queue(game, event, DisplayType.map,
                    fileUpload -> MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload));
            }
        }

        for (Player player : game.getRealPlayers()) {
            if (game.getRound() < 4) {
                StringBuilder preferences = new StringBuilder();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    String old = game.getStoredValue(p2.getUserID() + "anonDeclare");
                    if (!old.isEmpty() && !old.toLowerCase().contains("strong")) {
                        preferences.append(old).append("; ");
                    }
                }
                if (!preferences.isEmpty()) {
                    preferences = new StringBuilder(preferences.substring(0, preferences.length() - 2));
                    preferences = new StringBuilder(player.getRepresentation() + " this is a reminder that at the start of the game, your fellow players stated a preference for the following environments:\n" +
                        preferences + "\nYou are under no special obligation to abide by that preference, but it may be a nice thing to keep in mind as you play");
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), preferences.toString());
                }
            }

            Leader playerLeader = player.getLeader("naaluhero").orElse(null);
            if (player.hasLeader("naaluhero") && player.getLeaderByID("naaluhero").isPresent() && playerLeader != null && !playerLeader.isLocked()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("naaluHeroInitiation", "Play Naalu Hero", LeaderEmojis.NaaluHero));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + " Reminder this is the window to play The Oracle, the Naalu Hero. You may use the buttons to start the process.", buttons);
            }
            if (player.getRelics() != null && player.hasRelic("mawofworlds") && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " Reminder this is the window to do Maw of Worlds, after you do your status homework things. Maw of worlds is technically start of agenda, but can be done now for efficiency");
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + " You may use these buttons to resolve Maw Of Worlds.", ButtonHelper.getMawButtons());
            }
            if (player.getRelics() != null && player.hasRelic("twilight_mirror") && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " Reminder this is the window to do Twilight Mirror");
                List<Button> playerButtons = new ArrayList<>();
                playerButtons.add(Buttons.green("resolveTwilightMirror", "Purge Twilight Mirror", ExploreEmojis.Relic));
                playerButtons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + " You may use these buttons to resolve Twilight Mirror.", playerButtons);
            }
            if (player.getRelics() != null && player.hasRelic("emphidia")) {
                for (String pl : player.getPlanets()) {
                    Tile tile = game.getTile(AliasHandler.resolveTile(pl));
                    if (tile == null) {
                        continue;
                    }
                    UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                    if (unitHolder != null && unitHolder.getTokenList() != null && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + "Reminder this is the window to purge Crown of Emphidia if you want to.");
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + " You may use these buttons to resolve Crown of Emphidia.", ButtonHelper.getCrownButtons());
                    }
                }
            }

            if (player.getActionCards() != null && player.getActionCards().containsKey("stability")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + "Reminder this is the window to play Political Stability.");
            }

            if (player.getActionCards() != null && player.getActionCards().containsKey("abs") && game.isCustodiansScored()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + "Reminder this is the window to play Ancient Burial Sites.");
            }

            for (String pn : player.getPromissoryNotes().keySet()) {
                if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    String cyberMessage = "# " + player.getRepresentationUnfogged() + " reminder to use cybernetic enhancements!";
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), cyberMessage);
                }
            }
        }
        String message2 = "Resolve status homework using the buttons. \n ";
        game.setCurrentACDrawStatusInfo("");
        Button draw1AC = Buttons.green("drawStatusACs", "Draw Status Phase ACs", CardEmojis.ActionCard);
        Button getCCs = Buttons.green("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs").withEmoji(Emoji.fromFormatted("🔺"));
        Button yssarilPolicy = null;
        for (Player player : game.getRealPlayers()) {
            if (ButtonHelper.isPlayerElected(game, player, "minister_policy") && player.hasAbility("scheming")) {
                yssarilPolicy = Buttons.gray(player.getFinsFactionCheckerPrefix() + "yssarilMinisterOfPolicy", "Draw Minister of Policy AC", FactionEmojis.Yssaril);
            }
        }
        boolean custodiansTaken = game.isCustodiansScored();
        Button passOnAbilities;
        if (custodiansTaken) {
            passOnAbilities = Buttons.red("pass_on_abilities", "Ready For Agenda");
            message2 = message2 + "This is the moment when you should resolve: \n- Political Stability \n- Ancient Burial Sites\n- Maw of Worlds \n- The Oracle, the Naalu hero\n- Crown of Emphidia";
        } else {
            passOnAbilities = Buttons.red("pass_on_abilities", "Ready For Strategy Phase");
            message2 = message2 + "Ready For Strategy Phase means you are done playing/passing on: \n- Political Stability \n- Summit \n- Manipulate Investments ";
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(draw1AC);
        buttons.add(getCCs);
        buttons.add(passOnAbilities);
        if (yssarilPolicy != null) {
            buttons.add(yssarilPolicy);
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message2, buttons);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# Remember to click Ready for " + (custodiansTaken ? "Agenda" : "Strategy Phase") + " when done with homework!");
        }
        UserJoinServerListener.checkIfCanCloseGameLaunchThread(game, false);
    }

    public static void startActionPhase(GenericInteractionCreateEvent event, Game game) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);
        String msg;
        game.setStoredValue("willRevolution", "");
        game.setPhaseOfGame("action");
        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        Collection<Player> activePlayers = game.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .toList();
        Player nextPlayer = null;
        int lowestSC = 100;
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelperActionCards.checkForAssigningCoup(game, p2);
            if (game.getStoredValue("Play Naalu PN") != null
                && game.getStoredValue("Play Naalu PN").contains(p2.getFaction())) {
                if (!p2.getPromissoryNotesInPlayArea().contains("gift")
                    && p2.getPromissoryNotes().containsKey("gift")) {
                    PromissoryNoteHelper.resolvePNPlay("gift", p2, game, event);
                }
            }
        }
        for (Player player_ : activePlayers) {
            int playersLowestSC = player_.getLowestSC();
            String scNumberIfNaaluInPlay = game.getSCNumberIfNaaluInPlay(player_,
                Integer.toString(playersLowestSC));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                nextPlayer = player_; // no further processing, this player has the 0 token
                break;
            }
            if (playersLowestSC < lowestSC) {
                lowestSC = playersLowestSC;
                nextPlayer = player_;
            }
        }

        // INFORM FIRST PLAYER IS UP FOR ACTION
        if (nextPlayer != null) {
            msgExtra += " " + nextPlayer.getRepresentation() + " is up for an action";
            privatePlayer = nextPlayer;
            game.updateActivePlayer(nextPlayer);
            if (game.isFowMode()) {
                FoWHelper.pingAllPlayersWithFullStats(game, event, nextPlayer, "started turn");
            }
            ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, game);

            game.setPhaseOfGame("action");
        }

        msg = "";
        MessageHelper.sendMessageToChannel(nextPlayer.getCorrectChannel(), msg);
        if (isFowPrivateGame) {
            msgExtra = "Start phase command run";
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
            if (privatePlayer == null)
                return;
            msgExtra = privatePlayer.getRepresentationUnfogged() + " UP NEXT";
            game.updateActivePlayer(privatePlayer);

            if (!allPicked) {
                game.setPhaseOfGame("strategy");
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use buttons to pick a strategy card.", Helper.getRemainingSCButtons(event, game, privatePlayer));
            } else {

                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.", StartTurnService.getStartOfTurnButtons(privatePlayer, game, false, event));

                if (privatePlayer.getGenSynthesisInfantry() > 0) {
                    if (!ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(), "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation() + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether");
                    }
                }
            }

        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "All players have picked a strategy card.");
            if (game.isShowBanners()) {
                BannerGenerator.drawPhaseBanner("action", game.getRound(), game.getActionsChannel());
            }
            ListTurnOrderService.turnOrder(event, game);
            if (!msgExtra.isEmpty()) {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "\n Use Buttons to do turn.", StartTurnService.getStartOfTurnButtons(privatePlayer, game, false, event));

                if (privatePlayer.getGenSynthesisInfantry() > 0) {
                    if (!ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(), "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " infantry left to revive.", ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation() + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");
                    }
                }
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            List<Button> buttons = new ArrayList<>();
            if (p2.hasTechReady("qdn") && p2.getTg() > 2 && p2.getStrategicCC() > 0) {
                buttons.add(Buttons.green("startQDN", "Use Quantum Datahub Node", TechEmojis.CyberneticTech));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you have the opportunity to use QDN", buttons);
            }
            buttons = new ArrayList<>();
            if (ButtonHelper.isPlayerElected(game, p2, "arbiter")) {
                buttons.add(Buttons.green("startArbiter", "Use Imperial Arbiter", CardEmojis.Agenda));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you have the opportunity to use Imperial Arbiter", buttons);
            }
        }
        UserJoinServerListener.checkIfCanCloseGameLaunchThread(game, false);
    }
}
