package ti4.buttons;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.cardsac.PickACFromDiscard;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.ds.TrapReveal;
import ti4.commands.game.CreateGameButton;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.player.SCPick;
import ti4.commands.player.UnitInfo;
import ti4.commands.relic.RelicDraw;
import ti4.commands.search.SearchMyGames;
import ti4.commands.status.ListPlayerInfoButton;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperActionCardsWillHomebrew;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperRelics;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FrankenDraftHelper;
import ti4.helpers.TransactionHelper;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.context.ButtonContext;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class ButtonListener extends ListenerAdapter {
    public static ButtonListener instance = null;

    public static final Map<Guild, Map<String, Emoji>> emoteMap = new HashMap<>();
    private final Map<String, Consumer<ButtonContext>> knownButtons = new HashMap<>();

    public static ButtonListener getInstance() {
        if (instance == null)
            instance = new ButtonListener();
        return instance;
    }

    private ButtonListener() {
        knownButtons.putAll(AnnotationHandler.findKnownHandlers(ButtonContext.class, ButtonHandler.class));
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.").setEphemeral(true).queue();
            return;
        }
        if (!event.getButton().getId().endsWith("~MDL")) {
            event.deferEdit().queue();
        }
        BotLogger.logButton(event);
        long startTime = new Date().getTime();
        try {
            ButtonContext context = new ButtonContext(event);
            if (context.isValid()) {
                resolveButtonInteractionEvent(context);
            }
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }
        long endTime = new Date().getTime();
        if (endTime - startTime > 3000) {
            BotLogger.log(event, "This button command took longer than 3000 ms (" + (endTime - startTime) + ")");
        }
    }

    private boolean handleKnownButtons(ButtonContext context) {
        String buttonID = context.getButtonID();
        // Check for exact match first
        if (knownButtons.containsKey(buttonID)) {
            knownButtons.get(buttonID).accept(context);
            return true;
        }

        // Then check for prefix match
        String longestPrefixMatch = null;
        for (String key : knownButtons.keySet()) {
            if (buttonID.startsWith(key)) {
                if (longestPrefixMatch == null || key.length() > longestPrefixMatch.length()) {
                    longestPrefixMatch = key;
                }
            }
        }

        if (longestPrefixMatch != null) {
            knownButtons.get(longestPrefixMatch).accept(context);
            return true;
        }
        return false;
    }

    public void resolveButtonInteractionEvent(ButtonContext context) {
        // pull values from context for easier access
        ButtonInteractionEvent event = context.getEvent();
        Player player = context.getPlayer();
        String buttonID = context.getButtonID();
        String messageID = context.getMessageID();
        Game game = context.getGame();
        MessageChannel privateChannel = context.getPrivateChannel();
        MessageChannel mainGameChannel = context.getMainGameChannel();
        MessageChannel actionsChannel = context.getActionsChannel();

        // hacking
        Player nullable = player; // why?

        // Setup some additional helper values for buttons
        String finsFactionCheckerPrefix = nullable == null ? "FFCC_nullPlayer_" : nullable.getFinsFactionCheckerPrefix();

        // Check the list of buttons first
        if (handleKnownButtons(context)) return;

        // TODO Convert all else..if..startsWith to use @ButtonHandler
        if (false) {


        } else if (buttonID.startsWith("ac_discard_from_hand_")) {
            UnfiledButtonHandlers.acDiscardFromHand(event, buttonID, game, player, mainGameChannel);
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            UnfiledButtonHandlers.soScoreFromHand(event, buttonID, game, player, privateChannel, mainGameChannel, mainGameChannel);
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            UnfiledButtonHandlers.poScoring(event, player, buttonID, game, privateChannel);
        } else if (buttonID.startsWith("sc_follow_")) {
            ButtonHelperSCs.scFollow(messageID, game, player, event, buttonID);
        } else if (buttonID.startsWith("sc_no_follow_")) {
            ButtonHelperSCs.scNoFollow(messageID, game, player, event, buttonID);
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            UnfiledButtonHandlers.movedNExplored(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("strategicAction_")) {
            UnfiledButtonHandlers.strategicAction(event, player, buttonID, game, mainGameChannel);






        } else if (buttonID.startsWith("placeOneNDone_")) {
            ButtonHelperModifyUnits.placeUnitAndDeleteButton(buttonID, event, game, player);
        } else if (buttonID.startsWith("mitoMechPlacement_")) {
            ButtonHelperAbilities.resolveMitosisMechPlacement(buttonID, event, game, player);
        } else if (buttonID.startsWith("sendTradeHolder_")) {
            ButtonHelper.sendTradeHolderSomething(player, game, buttonID, event);
        } else if (buttonID.startsWith("place_")) {
            ButtonHelperModifyUnits.genericPlaceUnit(buttonID, event, game, player);
        } else if (buttonID.startsWith("yssarilcommander_")) {
            ButtonHelperCommanders.yssarilCommander(buttonID, event, game, player);
        } else if (buttonID.startsWith("setupHomebrew_")) {
            ButtonHelper.setUpHomebrew(game, event, buttonID);
        } else if (buttonID.startsWith("exploreFront_")) {
            ButtonHelperExplore.exploreFront(game, player, event, buttonID);
        } else if (buttonID.startsWith("nekroStealTech_")) {
            ButtonHelperFactionSpecific.nekroStealTech(game, player, event, buttonID);
        } else if (buttonID.startsWith("mentakCommander_")) {
            ButtonHelperCommanders.mentakCommander(player, game, event, buttonID);
        } else if (buttonID.startsWith("mahactStealCC_")) {
            ButtonHelperFactionSpecific.mahactStealCC(game, player, event, buttonID);
        } else if (buttonID.startsWith("returnFFToSpace_")) {
            ButtonHelperFactionSpecific.returnFightersToSpace(player, game, event, buttonID);
        } else if (buttonID.startsWith("cutTape_")) {
            ButtonHelper.cutTape(game, buttonID, event);
        } else if (buttonID.startsWith("ancientTradeRoutesStep2_")) {
            ButtonHelperActionCardsWillHomebrew.resolveAncientTradeRoutesStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("armsDealStep2_")) {
            ButtonHelperActionCardsWillHomebrew.resolveArmsDealStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("defenseInstallationStep2_")) {
            ButtonHelperActionCardsWillHomebrew.resolveDefenseInstallationStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("freelancersBuild_")) {
            ButtonHelperExplore.freelancersBuild(game, player, event, buttonID);
        } else if (buttonID.startsWith("arboCommanderBuild_")) {
            ButtonHelperCommanders.arboCommanderBuild(player, game, event, buttonID);
        } else if (buttonID.startsWith("tacticalActionBuild_")) {
            ButtonHelperTacticalAction.buildWithTacticalAction(player, game, event, buttonID);
        } else if (buttonID.startsWith("getModifyTiles")) {
            UnfiledButtonHandlers.getModifyTiles(player, game);
        } else if (buttonID.startsWith("genericModify_")) {
            UnfiledButtonHandlers.genericModify(event, player, buttonID, game);
        } else if (buttonID.startsWith("genericBuild_")) {
            UnfiledButtonHandlers.genericBuild(event, player, buttonID, game);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap", ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, game, true));
        } else if (buttonID.startsWith("planetAbilityExhaust_")) {
            UnfiledButtonHandlers.planetAbilityExhaust(event, player, buttonID, game);
        } else if (buttonID.startsWith("garboziaAbilityExhaust_")) {
            UnfiledButtonHandlers.garboziaAbilityExhaust(event, player, game);
        } else if (buttonID.startsWith("checksNBalancesPt2_")) {
            SCPick.resolvePt2ChecksNBalances(event, player, game, buttonID);
        } else if (buttonID.startsWith("freeSystemsHeroPlanet_")) {
            ButtonHelperHeroes.freeSystemsHeroPlanet(buttonID, event, game, player);
        } else if (buttonID.startsWith("scPick_")) {
            UnfiledButtonHandlers.scPick(event, game, player, buttonID);
        } else if (buttonID.startsWith("milty_")) {
            game.getMiltyDraftManager().doMiltyPick(event, game, buttonID, player);
        } else if (buttonID.startsWith("showMiltyDraft")) {
            game.getMiltyDraftManager().repostDraftInformation(game);
        } else if (nullable != null && buttonID.startsWith("miltyFactionInfo_")) {
            UnfiledButtonHandlers.miltyFactionInfo(player, buttonID, game);
        } else if (buttonID.startsWith("ring_")) {
            UnfiledButtonHandlers.ring(event, player, buttonID, game);
        } else if (buttonID.startsWith("getACFrom_")) {
            UnfiledButtonHandlers.getACFrom(event, player, buttonID, game);
        } else if (buttonID.startsWith("steal2tg_")) {
            new TrapReveal().steal2Tg(player, game, event, buttonID);
        } else if (buttonID.startsWith("steal3comm_")) {
            new TrapReveal().steal3Comm(player, game, event, buttonID);
        } else if (buttonID.startsWith("specialRex_")) {
            ButtonHelper.resolveSpecialRex(player, game, buttonID, event);
        } else if (buttonID.startsWith("doActivation_")) {
            UnfiledButtonHandlers.doActivation(event, player, buttonID, game);
        } else if (buttonID.startsWith("getTilesThisFarAway_")) {
            ButtonHelperTacticalAction.getTilesThisFarAway(player, game, event, buttonID);
        } else if (buttonID.startsWith("ringTile_")) {
            ButtonHelperTacticalAction.selectActiveSystem(player, game, event, buttonID);
        } else if (buttonID.startsWith("genericRemove_")) {
            UnfiledButtonHandlers.genericRemove(event, player, buttonID, game);
        } else if (buttonID.startsWith("tacticalMoveFrom_")) {
            ButtonHelperTacticalAction.selectTileToMoveFrom(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolvePreassignment_")) {
            ButtonHelper.resolvePreAssignment(player, game, event, buttonID);
        } else if (buttonID.startsWith("removePreset_")) {
            ButtonHelper.resolveRemovalOfPreAssignment(player, game, event, buttonID);
        } else if (buttonID.startsWith("purge_Frags_")) {
            ButtonHelperExplore.purgeFrags(game, player, event, buttonID);
        } else if (buttonID.startsWith("resolveEBSStep1_")) {
            ButtonHelperActionCards.resolveEBSStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveBlitz_")) {
            ButtonHelperActionCards.resolveBlitz(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveShrapnelTurrets_")) {// resolveShrapnelTurrets_
            ButtonHelperActionCardsWillHomebrew.resolveShrapnelTurrets(player, game, event, buttonID);
        } else if (buttonID.startsWith("unitTactical")) {
            ButtonHelperTacticalAction.movingUnitsInTacticalAction(buttonID, event, game, player);
        } else if (buttonID.startsWith("naaluHeroInitiation")) {
            ButtonHelperHeroes.resolveNaaluHeroInitiation(player, game, event);
        } else if (buttonID.startsWith("kyroHeroInitiation")) {
            ButtonHelperHeroes.resolveKyroHeroInitiation(player, game, event);
        } else if (buttonID.startsWith("starChartsStep1_")) {
            UnfiledButtonHandlers.starChartsStep1(event, player, buttonID, game);
        } else if (buttonID.startsWith("starChartsStep2_")) {
            ButtonHelper.starChartStep2(game, player, buttonID, event);
        } else if (buttonID.startsWith("starChartsStep3_")) {
            ButtonHelper.starChartStep3(game, player, buttonID, event);
        } else if (buttonID.startsWith("detTileAdditionStep2_")) {
            ButtonHelper.detTileAdditionStep2(game, player, buttonID, event);
        } else if (buttonID.startsWith("detTileAdditionStep3_")) {
            ButtonHelper.detTileAdditionStep3(game, player, buttonID, event);
        } else if (buttonID.startsWith("detTileAdditionStep4_")) {
            ButtonHelper.detTileAdditionStep4(game, player, buttonID, event);
        } else if (buttonID.startsWith("naaluHeroSend")) {
            ButtonHelperHeroes.resolveNaaluHeroSend(player, game, buttonID, event);
        } else if (buttonID.startsWith("landUnits_")) {
            ButtonHelperModifyUnits.landingUnits(buttonID, event, game, player);
        } else if (buttonID.startsWith("reparationsStep2_")) {
            ButtonHelperActionCards.resolveReparationsStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("seizeArtifactStep2_")) {
            ButtonHelperActionCards.resolveSeizeArtifactStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("diplomaticPressureStep2_")) {
            ButtonHelperActionCards.resolveDiplomaticPressureStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("decoyOperationStep2_")) {
            ButtonHelperActionCards.resolveDecoyOperationStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveDecoyOperationStep1_")) {
            ButtonHelperActionCards.resolveDecoyOperationStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("seizeArtifactStep3_")) {
            ButtonHelperActionCards.resolveSeizeArtifactStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("reparationsStep3_")) {
            ButtonHelperActionCards.resolveReparationsStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("uprisingStep2_")) {
            ButtonHelperActionCards.resolveUprisingStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("addAbsolOrbital_")) {
            ButtonHelper.addAbsolOrbital(game, player, event, buttonID);
        } else if (buttonID.startsWith("argentHeroStep2_")) {
            ButtonHelperHeroes.argentHeroStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("fogAllianceAgentStep2_")) {
            ButtonHelperAgents.fogAllianceAgentStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("fogAllianceAgentStep3_")) {
            UnfiledButtonHandlers.fogAllianceAgentStep3(event, player, buttonID, game);
        } else if (buttonID.startsWith("argentHeroStep3_")) {
            ButtonHelperHeroes.argentHeroStep3(game, player, event, buttonID);
        } else if (buttonID.startsWith("argentHeroStep4_")) {
            ButtonHelperHeroes.argentHeroStep4(game, player, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep2_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep3Exhaust_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep3Exhaust(player, game, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep4Exhaust_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep4Exhaust(player, game, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep3Ready_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep3Ready(player, game, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep4Ready_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep4Ready(player, game, event, buttonID);
        } else if (buttonID.startsWith("drawRelicAtPosition_")) {
            RelicDraw.resolveDrawRelicAtPosition(player, event, game, buttonID);
        } else if (buttonID.startsWith("setTrapStep2_")) {
            ButtonHelperAbilities.setTrapStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("removeTrapStep2_")) {
            ButtonHelperAbilities.removeTrapStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("revealTrapStep2_")) {
            ButtonHelperAbilities.revealTrapStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("setTrapStep3_")) {
            ButtonHelperAbilities.setTrapStep3(game, player, event, buttonID);
        } else if (buttonID.startsWith("setTrapStep4_")) {
            ButtonHelperAbilities.setTrapStep4(game, player, event, buttonID);
        } else if (buttonID.startsWith("lanefirATS_")) {
            ButtonHelperFactionSpecific.resolveLanefirATS(player, event, buttonID);
        } else if (buttonID.startsWith("rohdhnaIndustrious_")) {
            ButtonHelperFactionSpecific.resolveRohDhnaIndustrious(game, player, event, buttonID);
        } else if (buttonID.startsWith("rohdhnaRecycle_")) {
            ButtonHelperFactionSpecific.resolveRohDhnaRecycle(game, player, event, buttonID);
        } else if (buttonID.startsWith("stymiePlayerStep1_")) {
            ButtonHelperFactionSpecific.resolveStymiePlayerStep1(game, player, event, buttonID);
        } else if (buttonID.startsWith("stymiePlayerStep2_")) {
            ButtonHelperFactionSpecific.resolveStymiePlayerStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("prismStep2_")) {
            PlanetExhaustAbility.resolvePrismStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("prismStep3_")) {
            PlanetExhaustAbility.resolvePrismStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("showDeck_")) {
            ButtonHelper.resolveDeckChoice(game, event, buttonID, player);
        } else if (buttonID.startsWith("unlockCommander_")) {
            UnfiledButtonHandlers.unlockCommander(event, player, buttonID);
        } else if (buttonID.startsWith("setForThalnos_")) {
            ButtonHelper.resolveSetForThalnos(player, game, buttonID, event);
        } else if (buttonID.startsWith("rollThalnos_")) {
            ButtonHelper.resolveRollForThalnos(player, game, buttonID, event);
        } else if (buttonID.startsWith("startThalnos_")) {
            ButtonHelper.resolveThalnosStart(player, game, buttonID, event);
        } else if (buttonID.startsWith("showTextOfDeck_")) {
            ButtonHelper.resolveShowFullTextDeckChoice(game, event, buttonID, player);
        } else if (buttonID.startsWith("assRepsStep2_")) {
            ButtonHelperActionCards.resolveAssRepsStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("setupStep1_")) {
            ButtonHelper.resolveSetupStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("setupStep2_")) {
            ButtonHelper.resolveSetupStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("setupStep3_")) {
            ButtonHelper.resolveSetupStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("setupStep4_")) {
            ButtonHelper.resolveSetupStep4And5(game, event, buttonID);
        } else if (buttonID.startsWith("setupStep5_")) {
            ButtonHelper.resolveSetupStep4And5(game, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep2_")) {
            ButtonHelperActionCards.resolveSignalJammingStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("xxchaAgentRemoveInfantry_")) {
            ButtonHelperAgents.resolveXxchaAgentInfantryRemoval(player, game, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep3_")) {
            ButtonHelperActionCards.resolveSignalJammingStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("edynAgendaStuffStep2_")) {
            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("edynAgendaStuffStep3_")) {
            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep4_")) {
            ButtonHelperActionCards.resolveSignalJammingStep4(player, game, event, buttonID);
        } else if (buttonID.startsWith("reactorMeltdownStep2_")) {
            ButtonHelperActionCards.resolveReactorMeltdownStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("declareUse_")) {
            UnfiledButtonHandlers.declareUse(event, player, buttonID, game);
        } else if (buttonID.startsWith("spyStep2_")) {
            ButtonHelperActionCards.resolveSpyStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("olradinConnectStep2_")) {
            ButtonHelperAbilities.resolveOlradinConnectStep2(player, game, buttonID, event);
        } else if (buttonID.startsWith("olradinPreserveStep2_")) {
            ButtonHelperAbilities.resolveOlradinPreserveStep2(buttonID, event, game, player);
        } else if (buttonID.startsWith("insubStep2_")) {
            ButtonHelperActionCards.resolveInsubStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("absStep2_")) {
            ButtonHelperActionCards.resolveABSStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("ghostShipStep2_")) {
            ButtonHelperActionCards.resolveGhostShipStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("strandedShipStep2_")) {
            ButtonHelperActionCardsWillHomebrew.resolveStrandedShipStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("tacticalBombardmentStep2_")) {
            ButtonHelperActionCards.resolveTacticalBombardmentStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("probeStep2_")) {
            ButtonHelperActionCards.resolveProbeStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("salvageStep2_")) {
            ButtonHelperActionCards.resolveSalvageStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("salvageOps_")) {
            ButtonHelperFactionSpecific.resolveSalvageOps(player, event, buttonID, game);
        } else if (buttonID.startsWith("psStep2_")) {
            ButtonHelperActionCards.resolvePSStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("plagueStep2_")) {
            ButtonHelperActionCards.resolvePlagueStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("micrometeoroidStormStep2_")) {
            ButtonHelperActionCards.resolveMicrometeoroidStormStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("crippleStep2_")) {
            ButtonHelperActionCards.resolveCrippleStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("infiltrateStep2_")) {
            ButtonHelperActionCards.resolveInfiltrateStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("spyStep3_")) {
            ButtonHelperActionCards.resolveSpyStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("plagueStep3_")) {
            ButtonHelperActionCards.resolvePlagueStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("micrometeoroidStormStep3_")) {
            ButtonHelperActionCards.resolveMicrometeoroidStormStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("crippleStep3_")) {
            ButtonHelperActionCards.resolveCrippleStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("infiltrateStep3_")) {
            ButtonHelperActionCards.resolveInfiltrateStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("reactorMeltdownStep3_")) {
            ButtonHelperActionCards.resolveReactorMeltdownStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("uprisingStep3_")) {
            ButtonHelperActionCards.resolveUprisingStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("axisHeroStep3_")) {
            ButtonHelperHeroes.resolveAxisHeroStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("axisHeroStep2_")) {
            ButtonHelperHeroes.resolveAxisHeroStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("purgeKortaliHero_")) {
            UnfiledButtonHandlers.purgeKortaliHero(event, player, buttonID, game);
        } else if (buttonID.startsWith("purgeCeldauriHero_")) {
            ButtonHelperHeroes.purgeCeldauriHero(player, game, event, buttonID);
        } else if (buttonID.startsWith("purgeMentakHero_")) {
            ButtonHelperHeroes.purgeMentakHero(player, game, event, buttonID);
        } else if (buttonID.startsWith("asnStep2_")) {
            ButtonHelperFactionSpecific.resolveASNStep2(game, player, buttonID, event);
        } else if (buttonID.startsWith("unstableStep2_")) {// "titansConstructionMechDeployStep2_"
            ButtonHelperActionCards.resolveUnstableStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("titansConstructionMechDeployStep2_")) {
            ButtonHelperFactionSpecific.handleTitansConstructionMechDeployStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("titansConstructionMechDeployStep1")) {
            ButtonHelperFactionSpecific.handleTitansConstructionMechDeployStep1(game, player, event, messageID);
        } else if (buttonID.startsWith("unstableStep3_")) {
            ButtonHelperActionCards.resolveUnstableStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("spaceUnits_")) {
            ButtonHelperModifyUnits.spaceLandedUnits(buttonID, event, game, player);
        } else if (buttonID.startsWith("resetSpend_")) {
            UnfiledButtonHandlers.resetSpend_(event, player, buttonID, game);
        } else if (buttonID.startsWith("reinforcements_cc_placement_")) {
            UnfiledButtonHandlers.reinforcementsCCPlacement(event, game, player, buttonID);
        } else if (buttonID.startsWith("placeHolderOfConInSystem_")) {
            UnfiledButtonHandlers.placeHolderOfConInSystem(event, game, player, buttonID);
        } else if (buttonID.startsWith("greyfire_")) {
            ButtonHelperFactionSpecific.resolveGreyfire(player, game, buttonID, event);
        } else if (buttonID.startsWith("concludeMove_")) {
            ButtonHelperTacticalAction.finishMovingForTacticalAction(player, game, event, buttonID);
        } else if (buttonID.startsWith("transactWith_") || buttonID.startsWith("resetOffer_")) {
            UnfiledButtonHandlers.transactWith(event, player, buttonID, game);
        } else if (buttonID.startsWith("rejectOffer_")) {
            TransactionHelper.rejectOffer(event, player, buttonID, game);
        } else if (buttonID.startsWith("rescindOffer_")) {
            TransactionHelper.rescindOffer(event, player, buttonID, game);
        } else if (buttonID.startsWith("acceptOffer_")) {
            TransactionHelper.acceptOffer(event, game, player, buttonID);
        } else if (buttonID.startsWith("play_after_")) {
            UnfiledButtonHandlers.play_after(event, game, player, buttonID);
        } else if (buttonID.startsWith("ultimateUndo_")) {
            UnfiledButtonHandlers.ultimateUndo_(event, game, player, buttonID);
        } else if (buttonID.startsWith("addIonStorm_")) {
            ButtonHelper.addIonStorm(game, buttonID, event, player);
        } else if (buttonID.startsWith("flipIonStorm_")) {
            ButtonHelper.flipIonStorm(game, buttonID, event);
        } else if (buttonID.startsWith("terraformPlanet_")) {
            ButtonHelperFactionSpecific.terraformPlanet(player, buttonID, event, game);
        } else if (buttonID.startsWith("automatonsPlanet_")) {// "bentorPNPlanet_"
            ButtonHelperFactionSpecific.automatonsPlanet(buttonID, event, game);
        } else if (buttonID.startsWith("bentorPNPlanet_")) {// "bentorPNPlanet_"
            ButtonHelperFactionSpecific.bentorPNPlanet(buttonID, event, game);
        } else if (buttonID.startsWith("gledgeBasePlanet_")) {
            ButtonHelperFactionSpecific.gledgeBasePlanet(buttonID, event, game);
        } else if (buttonID.startsWith("veldyrAttach_")) {
            ButtonHelperFactionSpecific.resolveBranchOffice(buttonID, event, game, player);
        } else if (buttonID.startsWith("nanoforgePlanet_")) {
            UnfiledButtonHandlers.nanoforgePlanet(event, buttonID, game);
        } else if (buttonID.startsWith("resolvePNPlay_")) {
            UnfiledButtonHandlers.resolvePNPlay(event, player, buttonID, game);
        } else if (buttonID.startsWith("send_")) {
            UnfiledButtonHandlers.send(event, player, buttonID, game);
        } else if (buttonID.startsWith("replacePDSWithFS_")) {
            ButtonHelperFactionSpecific.replacePDSWithFS(buttonID, event, game, player);
        } else if (buttonID.startsWith("putSleeperOnPlanet_")) {
            ButtonHelperAbilities.putSleeperOn(buttonID, event, game, player);
        } else if (buttonID.startsWith("frankenDraftAction;")) {
            FrankenDraftHelper.resolveFrankenDraftAction(game, player, event, buttonID);
        } else if (buttonID.startsWith("presetEdynAgentStep1")) {
            ButtonHelperAgents.presetEdynAgentStep1(game, player);
        } else if (buttonID.startsWith("presetEdynAgentStep2_")) {
            ButtonHelperAgents.presetEdynAgentStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("presetEdynAgentStep3_")) {
            ButtonHelperAgents.presetEdynAgentStep3(game, player, event, buttonID);
        } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
            ButtonHelperAbilities.removeSleeper(buttonID, event, game, player);
        } else if (buttonID.startsWith("replaceSleeperWith_")) {
            ButtonHelperAbilities.replaceSleeperWith(buttonID, event, game, player);
        } else if (buttonID.startsWith("relicSwapStep2")) {
            ButtonHelperHeroes.resolveRelicSwapStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("relicSwapStep1")) {
            ButtonHelperHeroes.resolveRelicSwapStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("retrieveAgenda_")) {
            UnfiledButtonHandlers.retrieveAgenda(event, player, buttonID, game);
        } else if (buttonID.startsWith("agendaResolution_")) {
            AgendaHelper.resolveAgenda(game, buttonID, event, mainGameChannel);
        } else if (buttonID.startsWith("jmfA_") || buttonID.startsWith("jmfN_")) {
            game.initializeMiltySettings().parseButtonInput(event);
        } else {
            switch (buttonID) { // TODO Convert all switch case to use @ButtonHandler
                case "resolveSeizeArtifactStep1" -> ButtonHelperActionCards.resolveSeizeArtifactStep1(player, game, event, "no");
                case "refreshInfoButtons" -> MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), null, Buttons.REFRESH_INFO_BUTTONS);
                case "factionEmbedRefresh" -> MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, List.of(player.getRepresentationEmbed()), List.of(Buttons.FACTION_EMBED));
                case "warfareBuild" -> ButtonHelperSCs.warfareBuild(game, player, event, buttonID, messageID);
                case "diploRefresh2" -> ButtonHelperSCs.diploRefresh2(game, player, event, buttonID, messageID);
                case "sc_ac_draw" -> ButtonHelperSCs.scACDraw(game, player, event, buttonID, messageID);
                case "gain_1_comms" -> ButtonHelperStats.gainComms(event, game, player, 1, true);
                case "gain_2_comms" -> ButtonHelperStats.gainComms(event, game, player, 2, true);
                case "gain_3_comms" -> ButtonHelperStats.gainComms(event, game, player, 3, true);
                case "gain_4_comms" -> ButtonHelperStats.gainComms(event, game, player, 4, true);
                case "gain_1_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 1, false);
                case "gain_2_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 2, false);
                case "gain_3_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 3, false);
                case "gain_4_comms_stay" -> ButtonHelperStats.gainComms(event, game, player, 4, false);
                case "convert_1_comms" -> ButtonHelperStats.convertComms(event, game, player, 1);
                case "convert_2_comms" -> ButtonHelperStats.convertComms(event, game, player, 2);
                case "convert_3_comms" -> ButtonHelperStats.convertComms(event, game, player, 3);
                case "convert_4_comms" -> ButtonHelperStats.convertComms(event, game, player, 4);
                case "convert_2_comms_stay" -> ButtonHelperStats.convertComms(event, game, player, 2, false);
                case "refreshPNInfo" -> PNInfo.sendPromissoryNoteInfo(game, player, true, event);
                case Constants.REFRESH_UNIT_INFO -> UnitInfo.sendUnitInfo(game, player, event, false);
                case Constants.REFRESH_ALL_UNIT_INFO -> UnitInfo.sendUnitInfo(game, player, event, true);
                case "acquireATech" -> ButtonHelper.acquireATech(player, game, event, messageID, false);
                case "acquireAUnitTechWithInf" -> ButtonHelper.acquireATech(player, game, event, messageID, false, Set.of(Constants.UNIT), "inf");
                case "acquireATechWithSC" -> ButtonHelper.acquireATech(player, game, event, messageID, true);
                case "nekroFollowTech" -> ButtonHelperSCs.nekroFollowTech(game, player, event, buttonID, messageID);
                case "sc_draw_so" -> ButtonHelperSCs.scDrawSO(game, player, event, buttonID, messageID);
                case "sc_trade_follow", "sc_follow_trade" -> ButtonHelperSCs.followTrade(game, player, event, buttonID, messageID);
                case "sc_refresh" -> ButtonHelperSCs.refresh(game, player, event, buttonID, messageID);
                case "sc_refresh_and_wash" -> ButtonHelperSCs.refreshAndWash(game, player, event, buttonID, messageID);
                case "score_imperial" -> ButtonHelperSCs.scoreImperial(game, player, event, buttonID, messageID);
                case "play_when" -> UnfiledButtonHandlers.playWhen(event, game, mainGameChannel);
                case "gain_1_tg" -> UnfiledButtonHandlers.gain1TG(event, player, game, mainGameChannel);
                case "gain1tgFromLetnevCommander" -> UnfiledButtonHandlers.gain1tgFromLetnevCommander(event, player, game, mainGameChannel);
                case "gain1tgFromMuaatCommander" -> UnfiledButtonHandlers.gain1tgFromMuaatCommander(event, player, game, mainGameChannel);
                case "gain1tgFromCommander" -> UnfiledButtonHandlers.gain1tgFromCommander(event, player, game, mainGameChannel); // should be deprecated
                case "decline_explore" -> UnfiledButtonHandlers.declineExplore(event, player, game, mainGameChannel);
                case "resolveHarness" -> ButtonHelperStats.replenishComms(event, game, player, false);
                case "pass_on_abilities" -> ButtonHelper.addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                case "lastMinuteDeliberation" -> UnfiledButtonHandlers.lastMinuteDeliberation(event, player, game, actionsChannel);
                case "pay1tgforKeleres" -> ButtonHelperCommanders.pay1tgToUnlockKeleres(player, game, event, false);
                case "pay1tgforKeleresU" -> ButtonHelperCommanders.pay1tgToUnlockKeleres(player, game, event, true);
                case "declinePDS" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmojiOrColor() + " officially declines to fire PDS");
                case "searchMyGames" -> SearchMyGames.searchGames(event.getUser(), event, false, false, false, true, false, true, false, false);
                case "checkWHView" -> ButtonHelper.showFeatureType(event, game, DisplayType.wormholes);
                case "checkAnomView" -> ButtonHelper.showFeatureType(event, game, DisplayType.anomalies);
                case "checkLegendView" -> ButtonHelper.showFeatureType(event, game, DisplayType.legendaries);
                case "checkEmptyView" -> ButtonHelper.showFeatureType(event, game, DisplayType.empties);
                case "checkAetherView" -> ButtonHelper.showFeatureType(event, game, DisplayType.aetherstream);
                case "checkCannonView" -> ButtonHelper.showFeatureType(event, game, DisplayType.spacecannon);
                case "checkTraitView" -> ButtonHelper.showFeatureType(event, game, DisplayType.traits);
                case "checkTechSkipView" -> ButtonHelper.showFeatureType(event, game, DisplayType.techskips);
                case "checkAttachmView" -> ButtonHelper.showFeatureType(event, game, DisplayType.attachments);
                case "checkShiplessView" -> ButtonHelper.showFeatureType(event, game, DisplayType.shipless);
                default -> MessageHelper.sendMessageToEventChannel(event, "Button " + ButtonHelper.getButtonRepresentation(event.getButton()) + " pressed. This button does not do anything.");
            }
        }
    }
}
