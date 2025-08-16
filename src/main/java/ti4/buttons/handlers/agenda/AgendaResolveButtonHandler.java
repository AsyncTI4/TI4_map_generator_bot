package ti4.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.agenda.resolver.AbolishmentAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AbsolAbolishmentAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AbsolArtifactAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AbsolChecksAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AbsolConstitutionAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AbsolMeasuresAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AbsolSeedsAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.AgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ArmsReductionAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ArticlesOfWarAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ArtifactAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ChecksAndBalancesFlagAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ClandestineAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ConstitutionAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ConventionsAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.CrisisAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.DefenseActAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.DisarmamentAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.EconomicEqualityAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ElectSecretAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.ExecutionDirectiveAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.GrantReallocationAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.IncentiveAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.MinisterAntiquitiesAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.MiscountMessageAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.MutinyAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.NexusAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.PlowsharesAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.PoliticalCensureAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.RearmamentAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.RedistributionAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.RegulationsAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.RepresentativeGovernmentAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.RevolutionFlagAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.SanctionsAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.SchematicsAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.SeedEmpireAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.SharedResearchAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.StandardizationAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.TravelBanAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.UnconventionalAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.VoiceOfTheCouncilLawAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.WarrantAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.WormholeReconAgendaResolver;
import ti4.buttons.handlers.agenda.resolver.WormholeResearchAgendaResolver;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class AgendaResolveButtonHandler {
    private static final Map<String, AgendaResolver> AGENDA_HANDLERS = new LinkedHashMap<>();

    static {
        AGENDA_HANDLERS.put("absol_checks", new AbsolChecksAgendaResolver());
        AGENDA_HANDLERS.put("absol_constitution", new AbsolConstitutionAgendaResolver());
        AGENDA_HANDLERS.put("absol_artifact", new AbsolArtifactAgendaResolver());
        AGENDA_HANDLERS.put("absol_measures", new AbsolMeasuresAgendaResolver());
        AGENDA_HANDLERS.put("absol_seeds", new AbsolSeedsAgendaResolver());
        AGENDA_HANDLERS.put("absol_censure", new PoliticalCensureAgendaResolver("absol_censure"));
        AGENDA_HANDLERS.put("absol_abolishment", new AbsolAbolishmentAgendaResolver());
        AGENDA_HANDLERS.put("absol_miscount", new MiscountMessageAgendaResolver("absol_miscount"));
        AGENDA_HANDLERS.put("little_omega_artifact", new ArtifactAgendaResolver("little_omega_artifact"));
        AGENDA_HANDLERS.put("voice_of_the_council", new VoiceOfTheCouncilLawAgendaResolver());

        AGENDA_HANDLERS.put("abolishment", new AbolishmentAgendaResolver());
        AGENDA_HANDLERS.put("arms_reduction", new ArmsReductionAgendaResolver());
        AGENDA_HANDLERS.put("articles_war", new ArticlesOfWarAgendaResolver());
        AGENDA_HANDLERS.put("artifact", new ArtifactAgendaResolver("artifact"));
        AGENDA_HANDLERS.put("censure", new PoliticalCensureAgendaResolver("censure"));
        AGENDA_HANDLERS.put("checks", new ChecksAndBalancesFlagAgendaResolver());
        AGENDA_HANDLERS.put("cladenstine", new ClandestineAgendaResolver());
        AGENDA_HANDLERS.put("conventions", new ConventionsAgendaResolver());
        AGENDA_HANDLERS.put("constitution", new ConstitutionAgendaResolver());
        AGENDA_HANDLERS.put("crisis", new CrisisAgendaResolver());
        AGENDA_HANDLERS.put("defense_act", new DefenseActAgendaResolver());
        AGENDA_HANDLERS.put("disarmamament", new DisarmamentAgendaResolver());
        AGENDA_HANDLERS.put("economic_equality", new EconomicEqualityAgendaResolver());
        AGENDA_HANDLERS.put("execution", new ExecutionDirectiveAgendaResolver());
        AGENDA_HANDLERS.put("grant_reallocation", new GrantReallocationAgendaResolver());
        AGENDA_HANDLERS.put("incentive", new IncentiveAgendaResolver());
        AGENDA_HANDLERS.put("minister_antiquities", new MinisterAntiquitiesAgendaResolver());
        AGENDA_HANDLERS.put("miscount", new MiscountMessageAgendaResolver("miscount"));
        AGENDA_HANDLERS.put("mutiny", new MutinyAgendaResolver());
        AGENDA_HANDLERS.put("nexus", new NexusAgendaResolver());
        AGENDA_HANDLERS.put("plowshares", new PlowsharesAgendaResolver());
        AGENDA_HANDLERS.put("rearmament", new RearmamentAgendaResolver());
        AGENDA_HANDLERS.put("redistribution", new RedistributionAgendaResolver());
        AGENDA_HANDLERS.put("regulations", new RegulationsAgendaResolver());
        AGENDA_HANDLERS.put("rep_govt", new RepresentativeGovernmentAgendaResolver());
        AGENDA_HANDLERS.put("revolution", new RevolutionFlagAgendaResolver());
        AGENDA_HANDLERS.put("sanctions", new SanctionsAgendaResolver());
        AGENDA_HANDLERS.put("schematics", new SchematicsAgendaResolver());
        AGENDA_HANDLERS.put("secret", new ElectSecretAgendaResolver());
        AGENDA_HANDLERS.put("seed_empire", new SeedEmpireAgendaResolver());
        AGENDA_HANDLERS.put("shared_research", new SharedResearchAgendaResolver());
        AGENDA_HANDLERS.put("standardization", new StandardizationAgendaResolver());
        AGENDA_HANDLERS.put("travel_ban", new TravelBanAgendaResolver());
        AGENDA_HANDLERS.put("unconventional", new UnconventionalAgendaResolver());
        AGENDA_HANDLERS.put("warrant", new WarrantAgendaResolver());
        AGENDA_HANDLERS.put("wormhole_recon", new WormholeReconAgendaResolver());
        AGENDA_HANDLERS.put("wormhole_research", new WormholeResearchAgendaResolver());
    }

    @ButtonHandler("agendaResolution_")
    public static void resolveAgenda(Game game, String buttonID, ButtonInteractionEvent event) {
        String winner = buttonID.substring(buttonID.indexOf('_') + 1);
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if (guardDoublePress(game, winner, agendaid)) return;

        int aID = computeAgendaNumericId(game, agendaid);
        String agID = getAgendaId(game, aID);

        // Pre-resolution
        handlePredictiveIntelligence(game, winner);

        // Resolution
        handleLaw(game, event, aID, winner);
        AgendaResolver handler = AGENDA_HANDLERS.get(agID.toLowerCase());
        // NOTE: not every agenda has a 'handler'. Some agendas just add the law (from handleLaw above) and that's it.
        if (handler != null) {
            handler.handle(game, event, aID, winner);
        }

        // Post-resolution
        if (game.getCurrentAgendaInfo().contains("Secret")) {
            handleSecretToPublicLaw(game, aID, winner, event);
        }
        if (!game.getLaws().isEmpty()) {
            CommanderUnlockCheckService.checkAllPlayersInGame(game, "edyn");
        }
        List<Player> riders = AgendaHelper.getWinningRiders(winner, game, event);
        List<Player> voters = AgendaHelper.getWinningVoters(winner, game);
        notifyIndoctrinationTeam(game, voters, event);
        checkFlorzenUnlock(game, voters, riders);
        processRiders(game, riders);
        String resMes = buildResolutionMessage(game, winner);
        int aCount = computeNextAgendaCount(game);
        List<Button> buttons = buildNextButtons(game, aCount);
        String voteMessage = buildVoteMessage(game, aCount);
        if (!"miscount".equalsIgnoreCase(agID) && !"absol_miscount".equalsIgnoreCase(agID)) {
            sendNextStepUi(game, event, resMes, voteMessage, buttons);
        } else {
            handleMiscountRevote(game, winner, event);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean guardDoublePress(Game game, String winner, String agendaId) {
        String key = "agendaRes" + game.getRound() + game.getDiscardAgendas().size();
        if (game.getStoredValue(key).equalsIgnoreCase(winner + agendaId)) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Double press suspected, stopping resolution here.");
            return true;
        }
        game.setStoredValue(key, winner + agendaId);
        return false;
    }

    private static int computeAgendaNumericId(Game game, String agendaid) {
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "# The hidden agenda was " + agendaName + "! You can find it added as a law or in the discard.");
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getMainGameChannel(), "Hidden Agenda", agendaDetails.getRepresentationEmbed());
            return discardAgendas.get(id2);
        } else {
            return Integer.parseInt(agendaid);
        }
    }

    private static String getAgendaId(Game game, int agendaNumericId) {
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
            Integer value = agendas.getValue();
            if (Integer.valueOf(agendaNumericId).equals(value)) {
                return agendas.getKey();
            }
        }
        return "";
    }

    private static void handlePredictiveIntelligence(Game game, String winner) {
        List<Player> predictiveCheck = AgendaHelper.getLosingVoters(winner, game);
        AgendaHelper.atokeraCommanderUnlockCheck(game);
        for (Player playerWL : predictiveCheck) {
            if (game.getStoredValue("riskedPredictive").contains(playerWL.getFaction()) && playerWL.hasTech("pi")) {
                playerWL.exhaustTech("pi");
                MessageHelper.sendMessageToChannel(
                        playerWL.getCorrectChannel(),
                        playerWL.getRepresentation()
                                + " _Predictive Intelligence_ was exhausted since you voted for a losing outcome while using it.");
            }
        }
        game.setStoredValue("riskedPredictive", "");
    }

    private static void handleLaw(Game game, ButtonInteractionEvent event, int aID, String winner) {
        if (!game.getCurrentAgendaInfo().startsWith("Law")) {
            return;
        }
        if (game.getCurrentAgendaInfo().contains("Player")) {
            Player player2 = game.getPlayerFromColorOrFaction(winner);
            if (player2 != null) {
                game.addLaw(aID, winner);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), "# Added Law with " + winner + " as the elected!");
        } else {
            if ("for".equalsIgnoreCase(winner)) {
                game.addLaw(aID, null);
                MessageHelper.sendMessageToChannel(event.getChannel(), game.getPing() + " Added law to map!");
            }
        }
    }

    private static void handleSecretToPublicLaw(Game game, int aID, String winner, ButtonInteractionEvent event) {
        game.addLaw(aID, Mapper.getSecretObjectivesJustNames().get(winner));
        Player playerWithSO = null;
        int soID = 0;
        for (Map.Entry<String, Player> playerEntry : game.getPlayers().entrySet()) {
            Player player_ = playerEntry.getValue();
            Map<String, Integer> secretsScored = new LinkedHashMap<>(player_.getSecretsScored());
            for (Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                if (soEntry.getKey().equals(winner)) {
                    playerWithSO = player_;
                    soID = soEntry.getValue();
                    break;
                }
            }
        }
        if (playerWithSO == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }
        if (winner.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Can make only scored secret objective to public objective.");
            return;
        }
        game.addToSoToPoList(winner);
        playerWithSO.removeSecretScored(soID);
        Integer poIndex = game.addCustomPO(Mapper.getSecretObjectivesJustNames().get(winner), 1);
        game.scorePublicObjective(playerWithSO.getUserID(), poIndex);
        String sb = "_" + Mapper.getSecretObjectivesJustNames().get(winner)
                + "_ has been made in to a public objective (" + poIndex + ").";
        if (!game.isFowMode()) {
            sb += "\n-# " + playerWithSO.getRepresentationUnfogged()
                    + " has been marked as having scored this, and it no longer counts towards their secret objective limit.";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, playerWithSO, event);
    }

    private static void notifyIndoctrinationTeam(Game game, List<Player> voters, ButtonInteractionEvent event) {
        for (Player voter : voters) {
            if (voter.hasTech("dskyrog")) {
                MessageHelper.sendMessageToChannel(
                        voter.getCorrectChannel(),
                        voter.getFactionEmoji() + " gets to drop 2 infantry on a planet due to _Indoctrination Team_.");
                List<Button> buttons = new ArrayList<>(
                        Helper.getPlanetPlaceUnitButtons(voter, game, "2gf", "placeOneNDone_skipbuild"));
                MessageHelper.sendMessageToChannelWithButtons(
                        voter.getCorrectChannel(), "Please use buttons to drop 2 infantry on a planet.", buttons);
            }
        }
    }

    private static void checkFlorzenUnlock(Game game, List<Player> voters, List<Player> riders) {
        List<Player> everyone = new ArrayList<>(voters);
        everyone.addAll(riders);
        for (Player player : everyone) {
            CommanderUnlockCheckService.checkPlayer(player, "florzen");
        }
    }

    private static void processRiders(Game game, List<Player> riders) {
        String ridSum = "People had Riders to resolve.";
        Player machinations = null;
        for (Player rid : riders) {
            String rep = rid.getRepresentationUnfogged();
            String message;
            if (rid.hasAbility("future_sight")) {
                message = rep
                        + " you have a Rider to resolve or you voted for the correct outcome. Either way a trade good has been added to your total due to your **Future Sight** ability "
                        + rid.gainTG(1, true) + ".";
                ButtonHelperAgents.resolveArtunoCheck(rid, 1);
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getPromissoryNotesInPlayArea().contains("sigma_machinations")) {
                        machinations = player2;
                    }
                }
            } else {
                message = rep + " you have a Rider to resolve.";
            }
            if (rid.hasTech("dsatokcr") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, rid, "cruiser", true) < 8) {
                MessageHelper.sendMessageToChannel(
                        rid.getCorrectChannel(),
                        rid.getFactionEmoji() + " may DEPLOY 1 cruiser to a system that contains their ships.");
                List<Button> buttons = new ArrayList<>(
                        Helper.getTileWithShipsPlaceUnitButtons(rid, game, "cruiser", "placeOneNDone_skipbuild"));
                MessageHelper.sendMessageToChannelWithButtons(
                        rid.getCorrectChannel(),
                        "Use buttons to DEPLOY 1 cruiser to a system that contains your ships.",
                        buttons);
            }
            if (game.isFowMode()) {
                MessageHelper.sendPrivateMessageToPlayer(rid, game, message);
                if (machinations != null) {
                    MessageHelper.sendPrivateMessageToPlayer(
                            machinations,
                            game,
                            machinations.getRepresentationUnfogged()
                                    + ", you've gained a trade good from _Machinations_ " + machinations.gainTG(1, true)
                                    + ".");
                }
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                if (machinations != null) {
                    MessageHelper.sendMessageToChannel(
                            game.getMainGameChannel(),
                            machinations.getRepresentationUnfogged()
                                    + ", you've also gained a trade good from _Machinations_ "
                                    + machinations.gainTG(1, true) + ".");
                }
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Sent pings to all those who Rider'd.");
        } else if (!riders.isEmpty()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), ridSum);
        }
    }

    private static String buildResolutionMessage(Game game, String winner) {
        String resMes = "Resolving vote for \"" + StringUtils.capitalize(winner) + "\".";
        if (game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
            resMes = "Resolving vote for \"**" + Helper.getSCName(Integer.parseInt(winner), game) + "**\".";
        }
        return resMes;
    }

    private static int computeNextAgendaCount(Game game) {
        String agendaCount = game.getStoredValue("agendaCount");
        if (agendaCount.isEmpty()) {
            return 1;
        }
        return Integer.parseInt(agendaCount) + 1;
    }

    private static List<Button> buildNextButtons(Game game, int aCount) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("flip_agenda", "Flip Agenda #" + aCount));

        if (!game.isOmegaPhaseMode()) {
            buttons.add(Buttons.green(
                    "proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)"));
        } else {
            Button proceedToScoring;
            Button electVoiceOfTheCouncil;
            String currentVotC = game.getLawsInfo().get(Constants.VOICE_OF_THE_COUNCIL_ID);
            boolean mustElectVoice = currentVotC == null || currentVotC.isEmpty();
            String electVoiceText = "Elect Voice of the Council";
            if (mustElectVoice) {
                electVoiceText += " (required before scoring)";
            } else {
                electVoiceText += " (optional)";
            }
            if (aCount < 3) {
                electVoiceOfTheCouncil = Buttons.gray("elect_voice_of_the_council", electVoiceText);
                proceedToScoring = Buttons.gray("proceed_to_scoring", "Proceed to scoring objectives");
            } else {
                electVoiceOfTheCouncil = Buttons.green("elect_voice_of_the_council", electVoiceText);
                if (mustElectVoice) {
                    proceedToScoring = Buttons.gray("proceed_to_scoring", "Proceed to Scoring Objectives");
                } else {
                    String speakerUserID = game.getSpeakerUserID();
                    Player speaker = game.getPlayer(speakerUserID);
                    if (speaker != null) {
                        MessageChannel speakerCardsInfoThread = speaker.getCardsInfoThread();
                        MessageHelper.sendMessageToChannel(
                                speakerCardsInfoThread,
                                "These are the current votes available for the _Voice of the Council_ vote.");
                        AgendaHelper.listVoteCount(game, speakerCardsInfoThread);
                    }
                    proceedToScoring = Buttons.green("proceed_to_scoring", "Proceed to Scoring Objectives");
                }
            }
            buttons.add(electVoiceOfTheCouncil);
            buttons.add(proceedToScoring);
        }
        return buttons;
    }

    private static String buildVoteMessage(Game game, int aCount) {
        String voteMessage = "Click the buttons for next steps after you're done resolving Riders.";
        if (game.isOmegaPhaseMode() && aCount == 3) {
            String previousElectee = game.getLawsInfo().get(Constants.VOICE_OF_THE_COUNCIL_ID);
            voteMessage += " The bot believes this is the third agenda, which in Omega Phase means you"
                    + (previousElectee == null ? "'ll" : " might") + " vote on the _Voice of the Council_.";
            if (previousElectee == null) {
                voteMessage +=
                        " Since no player currently has the _Voice of the Council_, it must be voted on once before proceeding to scoring.";
            } else {
                Player previousPlayer = game.getPlayerFromColorOrFaction(previousElectee);
                voteMessage += " Since somebody (specifically, " + previousPlayer.getRepresentationNoPing()
                        + ") currently has the _Voice of the Council_, the Speaker chooses whether to vote on it this round or not.";
            }
            voteMessage +=
                    " If this is not actually the third agenda yet, please remember this when that agenda is reached.";
        }
        return voteMessage;
    }

    private static void sendNextStepUi(
            Game game, ButtonInteractionEvent event, String resMes, String voteMessage, List<Button> buttons) {
        MessageHelper.sendMessageToChannel(event.getChannel(), resMes);
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, buttons);
        }
    }

    private static void handleMiscountRevote(Game game, String winner, ButtonInteractionEvent event) {
        game.removeLaw(winner);
        game.putAgendaBackIntoDeckOnTop(winner);
        AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
    }
}
