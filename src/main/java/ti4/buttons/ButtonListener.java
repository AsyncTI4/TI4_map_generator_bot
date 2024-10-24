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
        Game game = context.getGame();
        MessageChannel privateChannel = context.getPrivateChannel();
        MessageChannel mainGameChannel = context.getMainGameChannel();
        MessageChannel actionsChannel = context.getActionsChannel();

        // hacking
        Player nullable = player; // why?

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
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            UnfiledButtonHandlers.movedNExplored(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("strategicAction_")) {
            UnfiledButtonHandlers.strategicAction(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap", ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, game, true));
        } else if (buttonID.startsWith("milty_")) {
            game.getMiltyDraftManager().doMiltyPick(event, game, buttonID, player);
        } else if (buttonID.startsWith("showMiltyDraft")) {
            game.getMiltyDraftManager().repostDraftInformation(game);
        } else if (nullable != null && buttonID.startsWith("miltyFactionInfo_")) {
            UnfiledButtonHandlers.miltyFactionInfo(player, buttonID, game);
        } else if (buttonID.startsWith("agendaResolution_")) {
            AgendaHelper.resolveAgenda(game, buttonID, event, mainGameChannel);
        } else if (buttonID.startsWith("jmfA_") || buttonID.startsWith("jmfN_")) {
            game.initializeMiltySettings().parseButtonInput(event);








  




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
        } else {
            switch (buttonID) { // TODO Convert all switch case to use @ButtonHandler
                case "resolveSeizeArtifactStep1" -> ButtonHelperActionCards.resolveSeizeArtifactStep1(player, game, event, "no");
                case "refreshInfoButtons" -> MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), null, Buttons.REFRESH_INFO_BUTTONS);
                case "factionEmbedRefresh" -> MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null, List.of(player.getRepresentationEmbed()), List.of(Buttons.FACTION_EMBED));
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
                case "acquireATech" -> ButtonHelper.acquireATech(player, game, event, false);
                case "acquireAUnitTechWithInf" -> ButtonHelper.acquireATech(player, game, event,  false, Set.of(Constants.UNIT), "inf");
                case "acquireATechWithSC" -> ButtonHelper.acquireATech(player, game, event, true);
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
