package ti4.buttons;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.DrawAC;
import ti4.commands.cardsac.PickACFromDiscard;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.cardsso.DealSOToAll;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.combat.StartCombat;
import ti4.commands.ds.TrapReveal;
import ti4.commands.ds.ZelianHero;
import ti4.commands.explore.ExploreFrontier;
import ti4.commands.explore.ExplorePlanet;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.franken.FrankenApplicator;
import ti4.commands.game.CreateGameButton;
import ti4.commands.game.GameEnd;
import ti4.commands.game.StartPhase;
import ti4.commands.game.Swap;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetInfo;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.AbilityInfo;
import ti4.commands.player.Pass;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Stats;
import ti4.commands.player.TurnEnd;
import ti4.commands.player.TurnStart;
import ti4.commands.player.UnitInfo;
import ti4.commands.relic.RelicDraw;
import ti4.commands.relic.RelicInfo;
import ti4.commands.search.SearchMyGames;
import ti4.commands.special.FighterConscription;
import ti4.commands.special.NaaluCommander;
import ti4.commands.special.NovaSeed;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.status.Cleanup;
import ti4.commands.status.ListPlayerInfoButton;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tech.TechExhaust;
import ti4.commands.tech.TechInfo;
import ti4.commands.tokens.AddCC;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands.uncategorized.ShowGame;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
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
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FrankenDraftHelper;
import ti4.helpers.Helper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.PlayerTitleHelper;
import ti4.helpers.Storage;
import ti4.helpers.TransactionHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.context.ButtonContext;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

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
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.")
                .setEphemeral(true).queue();
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
        Player nullable = player;

        // Setup some additional helper values for buttons
        String buttonLabel = event.getButton().getLabel();
        String lastchar = StringUtils.right(buttonLabel, 2).replace("#", "");
        String finsFactionCheckerPrefix = nullable == null ? "FFCC_nullPlayer_"
            : nullable.getFinsFactionCheckerPrefix();
        String trueIdentity = null;
        String fowIdentity = null;
        String ident = null;
        if (nullable != null) {
            trueIdentity = player.getRepresentationUnfogged();
            fowIdentity = player.getRepresentation(false, true);
            ident = player.getFactionEmoji();
        }

        // Check the list of buttons first
        if (handleKnownButtons(context))
            return;

        // find the button
        // TODO (Jazz): convert everything
        if (buttonID.startsWith(Constants.AC_PLAY_FROM_HAND)) {
            UnfiledButtonHandlers.acPlayFromHand(event, buttonID, game, player, mainGameChannel, fowIdentity);
        } else if (buttonID.startsWith("ac_discard_from_hand_")) {
            UnfiledButtonHandlers.acDiscardFromHand(event, buttonID, game, player, mainGameChannel);
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            UnfiledButtonHandlers.soScoreFromHand(event, buttonID, game, player, privateChannel, mainGameChannel, mainGameChannel);
        } else if (buttonID.startsWith("dihmohnfs_")) {
            ButtonHelperFactionSpecific.resolveDihmohnFlagship(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("dsdihmy_")) {
            ButtonHelper.deleteMessage(event);
            ButtonHelperFactionSpecific.resolveImpressmentPrograms(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("swapToFaction_")) {
            String faction = buttonID.replace("swapToFaction_", "");
            Swap.secondHalfOfSwap(game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
        } else if (buttonID.startsWith("yinHeroInfantry_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("drawSpecificSO_")) {
            DiscardSO.drawSpecificSO(event, player, buttonID.split("_")[1], game);
        } else if (buttonID.startsWith("tnelisHeroAttach_")) {
            ButtonHelperHeroes.resolveTnelisHeroAttach(player, game, buttonID.split("_")[1], event);
        } else if (buttonID.startsWith("arcExp_")) {
            ButtonHelperActionCards.resolveArcExpButtons(game, player, buttonID, event, trueIdentity);
        } else if (buttonID.startsWith("rollForAmbush_")) {
            ButtonHelperFactionSpecific.rollAmbush(player, game, game.getTileByPosition(buttonID.split("_")[1]), event);
        } else if (buttonID.startsWith("creussIFFStart_")) {
            ButtonHelperFactionSpecific.resolveCreussIFFStart(game, player, buttonID, ident, event);
        } else if (buttonID.startsWith("acToSendTo_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("yinHeroPlanet_")) {
            ButtonHelperHeroes.yinHeroPlanet(event, buttonID, game, finsFactionCheckerPrefix, trueIdentity);
        } else if (buttonID.startsWith("yinHeroTarget_")) {
            ButtonHelperHeroes.yinHeroTarget(event, buttonID, game, finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("getAgentSelection_")) {
            UnfiledButtonHandlers.getAgentSelection(event, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("hacanAgentRefresh_")) {
            ButtonHelperAgents.hacanAgentRefresh(buttonID, event, game, player, ident, trueIdentity);
        } else if (buttonID.startsWith("cheiranAgentStep2_")) {
            ButtonHelperAgents.resolveCheiranAgentStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("freeSystemsAgentStep2_")) {
            ButtonHelperAgents.resolveFreeSystemsAgentStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("florzenAgentStep2_")) {
            ButtonHelperAgents.resolveFlorzenAgentStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("florzenHeroStep2_")) {
            ButtonHelperHeroes.resolveFlorzenHeroStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("florzenAgentStep3_")) {
            ButtonHelperAgents.resolveFlorzenAgentStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("attachAttachment_")) {
            ButtonHelperHeroes.resolveAttachAttachment(player, game, buttonID, event);
        } else if (buttonID.startsWith("findAttachmentInDeck_")) {
            ButtonHelperHeroes.findAttachmentInDeck(player, game, buttonID, event);
        } else if (buttonID.startsWith("cheiranAgentStep3_")) {
            ButtonHelperAgents.resolveCheiranAgentStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("celdauriAgentStep3_")) {
            ButtonHelperAgents.resolveCeldauriAgentStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("kolleccAgentResStep2_")) {
            ButtonHelperAgents.kolleccAgentResStep2(buttonID, event, game, player);
        } else if (buttonID.startsWith("hitOpponent_")) {
            ButtonHelperModifyUnits.resolveGettingHit(game, event, buttonID);
        } else if (buttonID.startsWith("getPsychoButtons")) {
            UnfiledButtonHandlers.getPsychoButtons(player, game, trueIdentity);
        } else if (buttonID.startsWith("retreatGroundUnits_")) {
            ButtonHelperModifyUnits.retreatGroundUnits(buttonID, event, game, player, ident, buttonLabel);
        } else if (buttonID.startsWith("resolveShipOrder_")) {
            ButtonHelperAbilities.resolveAxisOrderExhaust(player, game, event, buttonID);
        } else if (buttonID.startsWith("buyAxisOrder_")) {
            ButtonHelperAbilities.resolveAxisOrderBuy(player, game, event, buttonID);
        } else if (buttonID.startsWith("bindingDebtsRes_")) {
            ButtonHelperAbilities.bindingDebtRes(game, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep1_")) {
            ButtonHelperAbilities.mercenariesStep1(game, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep2_")) {
            ButtonHelperAbilities.mercenariesStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep3_")) {
            ButtonHelperAbilities.mercenariesStep3(game, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep4_")) {
            ButtonHelperAbilities.mercenariesStep4(game, player, event, buttonID);
        } else if (buttonID.startsWith("rallyToTheCauseStep2_")) {
            ButtonHelperAbilities.rallyToTheCauseStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("startRallyToTheCause")) {
            ButtonHelperAbilities.startRallyToTheCause(game, player, event);
        } else if (buttonID.startsWith("startFacsimile_")) {
            ButtonHelperAbilities.startFacsimile(game, player, event, buttonID);
        } else if (buttonID.startsWith("facsimileStep2_")) {
            ButtonHelperAbilities.resolveFacsimileStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("naaluCommander")) {
            new NaaluCommander().secondHalfOfNaaluCommander(event, game, player);
        } else if (buttonID.startsWith("mahactMechHit_")) {
            UnfiledButtonHandlers.mahactMechHit(event, player, buttonID, game);
        } else if (buttonID.startsWith("nullificationField_")) {
            UnfiledButtonHandlers.nullificationField(event, player, buttonID, game);
        } else if (buttonID.startsWith("benedictionStep1_")) {
            UnfiledButtonHandlers.benedictionStep1(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("mahactBenedictionFrom_")) {
            UnfiledButtonHandlers.mahactBenedictionFrom(event, player, buttonID, game, ident);
        } else if (buttonID.startsWith("retreatUnitsFrom_")) {
            UnfiledButtonHandlers.retreatUnitsFrom(event, player, buttonID, game, trueIdentity, ident);
        } else if (buttonID.startsWith("retreat_")) {
            UnfiledButtonHandlers.retreat(event, player, buttonID, game, trueIdentity, ident);
        } else if (buttonID.startsWith("exhaustAgent_")) {
            ButtonHelperAgents.exhaustAgent(buttonID, event, game, player);
        } else if (buttonID.startsWith("exhaustTCS_")) {
            ButtonHelperFactionSpecific.resolveTCSExhaust(buttonID, event, game, player);
        } else if (buttonID.startsWith("swapSCs_")) {
            ButtonHelperFactionSpecific.resolveSwapSC(player, game, event, buttonID);
        } else if (buttonID.startsWith("domnaStepThree_")) {
            ButtonHelperModifyUnits.resolveDomnaStep3Buttons(event, game, player, buttonID);
        } else if (buttonID.startsWith("domnaStepTwo_")) {
            ButtonHelperModifyUnits.offerDomnaStep3Buttons(event, game, player, buttonID);
        } else if (buttonID.startsWith("domnaStepOne_")) {
            ButtonHelperModifyUnits.offerDomnaStep2Buttons(event, game, player, buttonID);
        } else if (buttonID.startsWith("selectBeforeSwapSCs_")) {
            ButtonHelperFactionSpecific.resolveSelectedBeforeSwapSC(player, game, buttonID);
        } else if (buttonID.startsWith("sardakkcommander_")) {
            ButtonHelperCommanders.resolveSardakkCommander(game, player, buttonID, event, ident);
        } else if (buttonID.startsWith("olradinCommanderStep2_")) {
            ButtonHelperCommanders.olradinCommanderStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("useOmenDie_")) {
            ButtonHelperAbilities.useOmenDie(game, player, event, buttonID);
        } else if (buttonID.startsWith("peaceAccords_")) {
            ButtonHelperAbilities.resolvePeaceAccords(buttonID, ident, player, game, event);
        } else if (buttonID.startsWith("gheminaLordHero_")) {
            ButtonHelperHeroes.resolveGheminaLordHero(buttonID, ident, player, game, event);
        } else if (buttonID.startsWith("gheminaLadyHero_")) {
            ButtonHelperHeroes.resolveGheminaLadyHero(player, game, event, buttonID);
        } else if (buttonID.startsWith("get_so_discard_buttons")) {
            UnfiledButtonHandlers.getSODiscardButtons(event, player, game);
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            UnfiledButtonHandlers.poScoring(event, player, buttonID, game, privateChannel);
        } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
            UnfiledButtonHandlers.sc3AssignSpeaker(event, player, buttonID, game);
        } else if (buttonID.startsWith("assignSpeaker_")) {
            UnfiledButtonHandlers.assignSpeaker(event, player, buttonID, game);
        } else if (buttonID.startsWith("reveal_stage_")) {
            UnfiledButtonHandlers.revealPOStage(event, buttonID, game);
        } else if (buttonID.startsWith("exhaustRelic_")) {
            UnfiledButtonHandlers.exhaustRelic(event, player, buttonID, game);
        } else if (buttonID.startsWith("scepterE_follow_") || buttonID.startsWith("mahactA_follow_")) {
            ButtonHelperSCs.mahactAndScepterFollow(game, player, event, buttonID, ident, lastchar, trueIdentity);
        } else if (buttonID.startsWith("spendAStratCC")) {
            UnfiledButtonHandlers.spendAStratCC(event, player, game);
        } else if (buttonID.startsWith("sc_follow_")) {
            ButtonHelperSCs.scFollow(messageID, game, player, event, ident, buttonID, nullable, lastchar);
        } else if (buttonID.startsWith("sc_no_follow_")) {
            ButtonHelperSCs.scNoFollow(messageID, game, player, event, ident, buttonID, nullable, lastchar);
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            UnfiledButtonHandlers.movedNExplored(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("resolveExp_Look_")) {
            UnfiledButtonHandlers.resolveExpLook(event, player, buttonID, game);
        } else if (buttonID.startsWith("discardExploreTop_")) {
            UnfiledButtonHandlers.discardExploreTop(event, player, buttonID, game);
        } else if (buttonID.startsWith("relic_look_top")) {
            UnfiledButtonHandlers.relicLookTop(event, game, player);
        } else if (buttonID.startsWith("explore_look_All")) {
            UnfiledButtonHandlers.exploreLookAll(event, player, game);
        } else if (buttonID.startsWith("distant_suns_")) {
            ButtonHelperAbilities.distantSuns(buttonID, event, game, player);
        } else if (buttonID.startsWith("deep_mining_")) {
            ButtonHelperAbilities.deepMining(buttonID, event, game, player);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {// "autoAssignGroundHits_"
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("autoAssignSpaceHits_")) {
            UnfiledButtonHandlers.autoAssignSpaceHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("autoAssignSpaceCannonOffenceHits_")) {
            UnfiledButtonHandlers.autoAssignSpaceCannonOffenceHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("cancelSpaceHits_")) {
            UnfiledButtonHandlers.cancelSpaceHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("cancelGroundHits_")) {
            UnfiledButtonHandlers.cancelGroundHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("cancelPdsOffenseHits_")) {
            UnfiledButtonHandlers.cancelPDSOffenseHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("cancelAFBHits_")) {
            UnfiledButtonHandlers.cancelAFBHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("autoAssignAFBHits_")) {// "autoAssignGroundHits_"
        UnfiledButtonHandlers.autoAssignAFBHits(event, player, buttonID, game);
        } else if (buttonID.startsWith("getPlagiarizeButtons")) {
            UnfiledButtonHandlers.getPlagiarizeButtons(event, player, game);
        } else if (buttonID.startsWith("forceARefresh_")) {
            ButtonHelper.forceARefresh(game, player, event, buttonID);
        } else if (buttonID.startsWith("arboHeroBuild_")) {
            ButtonHelperHeroes.resolveArboHeroBuild(game, player, event, buttonID);
        } else if (buttonID.startsWith("saarHeroResolution_")) {
            ButtonHelperHeroes.resolveSaarHero(game, player, event, buttonID);
        } else if (buttonID.startsWith("refreshWithOlradinAgent_")) {
            ButtonHelperAgents.resolveRefreshWithOlradinAgent(game, player, event, buttonID);
        } else if (buttonID.startsWith("resolveGrace_")) {
            ButtonHelperAbilities.resolveGrace(game, player, buttonID, event);
        } else if (buttonID.startsWith("endTurnWhenAllReactedTo_")) {
            ButtonHelper.endTurnWhenAllReacted(game, player, event, buttonID);
        } else if (buttonID.startsWith("increaseTGonSC_")) {
            UnfiledButtonHandlers.increaseTGonSC(event, buttonID, game);
        } else if (buttonID.startsWith("strategicAction_")) {
            UnfiledButtonHandlers.strategicAction(event, player, buttonID, game, mainGameChannel);
        } else if (buttonID.startsWith("resolve_explore_")) {
            UnfiledButtonHandlers.resolveExplore(event, player, buttonID, game);
        } else if (buttonID.startsWith("refresh_")) {
            UnfiledButtonHandlers.refresh(event, player, buttonID, game, ident);
        } else if (buttonID.startsWith("assignDamage_")) {// removeThisTypeOfUnit_
            ButtonHelperModifyUnits.assignDamage(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("removeThisTypeOfUnit_")) {
            ButtonHelperModifyUnits.removeThisTypeOfUnit(buttonID, event, game, player);
        } else if (buttonID.startsWith("repairDamage_")) {
            ButtonHelperModifyUnits.repairDamage(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("assCannonNDihmohn_")) {
            ButtonHelperModifyUnits.resolveAssaultCannonNDihmohnCommander(buttonID, event, player, game);
        } else if (buttonID.startsWith("refreshViewOfSystem_")) {
            UnfiledButtonHandlers.refreshViewOfSystem(event, buttonID, game);
        } else if (buttonID.startsWith("getDamageButtons_")) {// "repealLaw_"
        UnfiledButtonHandlers.getDamageButtons(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("getRepairButtons_")) {
            UnfiledButtonHandlers.getRepairButtons(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("codexCardPick_")) {
            PickACFromDiscard.pickACardFromDiscardStep1(event, game, player);
        } else if (buttonID.startsWith("cymiaeHeroStep2_")) {
            ButtonHelperHeroes.resolveCymiaeHeroStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("cymiaeHeroStep3_")) {
            ButtonHelperHeroes.resolveCymiaeHeroStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("cymiaeHeroAutonetic")) {
            UnfiledButtonHandlers.cymiaeHeroAutonetic(event, player, game);
        } else if (buttonID.startsWith("cymiaeHeroStep1_")) {
            ButtonHelperHeroes.resolveCymiaeHeroStart(buttonID, event, game, player);
        } else if (buttonID.startsWith("autoneticMemoryStep2_")) {
            ButtonHelperAbilities.autoneticMemoryStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryDecline_")) {
            ButtonHelperAbilities.autoneticMemoryDecline(game, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryStep3")) {
            UnfiledButtonHandlers.autoneticMemoryStep3(event, player, buttonID, game);
        } else if (buttonID.startsWith("assignHits_")) {
            ButtonHelperModifyUnits.assignHits(buttonID, event, game, player, ident, buttonLabel);
        } else if (buttonID.startsWith("startDevotion_")) {
            ButtonHelperModifyUnits.startDevotion(player, game, event, buttonID);
        } else if (buttonID.startsWith("purgeTech_")) {
            ButtonHelperHeroes.purgeTech(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveDevote_")) {
            ButtonHelperModifyUnits.resolveDevote(player, game, event, buttonID);
        } else if (buttonID.startsWith("prophetsTears_")) {
            ButtonHelperRelics.prophetsTears(player, buttonID, game, event);
        } else if (buttonID.startsWith("swapTechs_")) {
            ButtonHelperHeroes.resolveAJolNarSwapStep2(player, game, buttonID, event);
        } else if (buttonID.startsWith("jnHeroSwapOut_")) {
            ButtonHelperHeroes.resolveAJolNarSwapStep1(player, game, buttonID, event);
        } else if (buttonID.startsWith("jolNarAgentRemoval_")) {
            ButtonHelperAgents.resolveJolNarAgentRemoval(player, game, buttonID, event);
        } else if (buttonID.startsWith("biostimsReady_")) {
            ButtonHelper.bioStimsReady(game, event, player, buttonID);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("nekroHeroStep2_")) {
            ButtonHelperHeroes.resolveNekroHeroStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("refreshVotes_")) {
            UnfiledButtonHandlers.refreshVotes(event, game, player, buttonID);
        } else if (buttonID.startsWith("getAllTechOfType_")) {
            UnfiledButtonHandlers.getAllTechOfType(event, player, buttonID, game);
        } else if (buttonID.startsWith("cabalVortextCapture_")) {
            ButtonHelperFactionSpecific.resolveVortexCapture(buttonID, player, game, event);
        } else if (buttonID.startsWith("takeAC_")) {
            ButtonHelperFactionSpecific.mageon(buttonID, event, game, player, trueIdentity);
        } else if (buttonID.startsWith("moult_")) {
            ButtonHelperAbilities.resolveMoult(buttonID, event, game, player);
        } else if (buttonID.startsWith("spend_")) {
            UnfiledButtonHandlers.spend(event, player, buttonID, game);
        } else if (buttonID.startsWith("finishTransaction_")) {
            UnfiledButtonHandlers.finishTransaction(event, player, buttonID, game);
        } else if (buttonID.startsWith("demandSomething_")) {
            UnfiledButtonHandlers.demandSomething(event, player, buttonID, game);
        } else if (buttonID.startsWith("sabotage_")) {
            UnfiledButtonHandlers.sabotage(event, player, buttonID, game, ident);
        } else if (buttonID.startsWith("reduceTG_")) {
            UnfiledButtonHandlers.reduceTG(event, player, buttonID, game);
        } else if (buttonID.startsWith("reduceComm_")) {
            UnfiledButtonHandlers.reduceComm(event, player, buttonID, game, ident);
        } else if (buttonID.startsWith("lanefirAgentRes_")) {
            ButtonHelperAgents.resolveLanefirAgent(player, game, event, buttonID);
        } else if (buttonID.startsWith("absolsdn_")) {
            ButtonHelper.resolveAbsolScanlink(player, game, event, buttonID);
        } else if (buttonID.startsWith("resFrontier_")) {
            UnfiledButtonHandlers.resFrontier(event, player, buttonID, game);
        } else if (buttonID.startsWith("finishComponentAction_")) {
            UnfiledButtonHandlers.finishComponentAction(event, player, game);
        } else if (buttonID.startsWith("pillage_")) {
            ButtonHelperAbilities.pillage(buttonID, event, game, player, ident, finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("mykoheroSteal_")) {
            ButtonHelperHeroes.resolveMykoHero(game, player, event, buttonID);
        } else if (buttonID.startsWith("exhaust_")) {
            AgendaHelper.exhaustStuffForVoting(buttonID, event, game, player, ident, buttonLabel);
        } else if (buttonID.startsWith("exhaustViaDiplomats_")) {
            ButtonHelperAbilities.resolveDiplomatExhaust(buttonID, event, game, player);
        } else if (buttonID.startsWith("exhaustForVotes_")) {
            AgendaHelper.exhaustForVotes(event, player, game, buttonID);
        } else if (buttonID.startsWith("deflectSC_")) {
            UnfiledButtonHandlers.deflectSC(event, buttonID, game);
        } else if (buttonID.startsWith("diplo_")) {
            ButtonHelper.resolveDiploPrimary(game, player, event, buttonID);
        } else if (buttonID.startsWith("doneLanding_")) {
            ButtonHelperModifyUnits.finishLanding(buttonID, event, game, player);
        } else if (buttonID.startsWith("doneWithOneSystem_")) {
            ButtonHelperTacticalAction.finishMovingFromOneTile(player, game, event, buttonID);
        } else if (buttonID.startsWith("cavStep2_")) {
            ButtonHelperFactionSpecific.resolveCavStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("resolveAgendaVote_")) {
            AgendaHelper.resolvingAnAgendaVote(buttonID, event, game, player);
        } else if (buttonID.startsWith("bombardConfirm_")) {
            UnfiledButtonHandlers.bombardConfirm(event, player, buttonID);
        } else if (buttonID.startsWith("combatRoll_")) {
            UnfiledButtonHandlers.combatRoll(event, player, buttonID, game);
        } else if (buttonID.startsWith("automateGroundCombat_")) {
            UnfiledButtonHandlers.automateGroundCombat(event, game, player, buttonID);
        } else if (buttonID.startsWith("transitDiodes_")) {
            ButtonHelper.resolveTransitDiodesStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("novaSeed_")) {
            UnfiledButtonHandlers.novaSeed(event, player, buttonID, game);
        } else if (buttonID.startsWith("celestialImpact_")) {
            UnfiledButtonHandlers.celestialImpact(event, player, buttonID, game);
        } else if (buttonID.startsWith("echoPlaceFrontier_")) {
            ButtonHelper.resolveEchoPlaceFrontier(game, player, event, buttonID);
        } else if (buttonID.startsWith("forceAbstainForPlayer_")) {
            UnfiledButtonHandlers.forceAbstainForPlayer(event, buttonID, game);
        } else if (buttonID.startsWith("useRelic_")) {
            UnfiledButtonHandlers.useRelic(event, player, buttonID, game);
        } else if (buttonID.startsWith("useTech_")) {
            UnfiledButtonHandlers.useTech(event, player, buttonID, game);
        } else if (buttonID.startsWith("absolX89Nuke_")) {
            UnfiledButtonHandlers.absolX89Nuke(event, player, buttonID, game);
        } else if (buttonID.startsWith("exhaustTech_")) {
            UnfiledButtonHandlers.exhaustTech(event, player, buttonID, game);
        } else if (buttonID.startsWith("planetOutcomes_")) {
            UnfiledButtonHandlers.planetOutcomes(event, buttonID, game);
        } else if (buttonID.startsWith("indoctrinate_")) {
            ButtonHelperAbilities.resolveFollowUpIndoctrinationQuestion(player, game, buttonID, event);
        } else if (buttonID.startsWith("assimilate_")) {
            UnfiledButtonHandlers.assimilate(event, player, buttonID, game);
        } else if (buttonID.startsWith("letnevMechRes_")) {
            ButtonHelperFactionSpecific.resolveLetnevMech(player, game, buttonID, event);// winnuPNPlay
        } else if (buttonID.startsWith("winnuPNPlay_")) {
            ButtonHelperFactionSpecific.resolveWinnuPN(player, game, buttonID, event);
        } else if (buttonID.startsWith("initialIndoctrination_")) {
            ButtonHelperAbilities.resolveInitialIndoctrinationQuestion(player, game, buttonID, event);
        } else if (buttonID.startsWith("utilizeSolCommander_")) {
            ButtonHelperCommanders.resolveSolCommander(player, game, buttonID, event);
        } else if (buttonID.startsWith("resolveVadenMech_")) {
            ButtonHelperFactionSpecific.resolveVadenMech(player, game, buttonID, event);
        } else if (buttonID.startsWith("mercerMove_")) {
            ButtonHelperAgents.resolveMercerMove(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("tiedPlanets_")) {
            UnfiledButtonHandlers.tiedPlanets(event, buttonID, game);
        } else if (buttonID.startsWith("planetRider_")) {
            UnfiledButtonHandlers.planetRider(event, buttonID, game, finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("distinguished_")) {
            UnfiledButtonHandlers.distinguished(event, buttonID);
        } else if (buttonID.startsWith("distinguishedReverse_")) {
            UnfiledButtonHandlers.distinguishedReverse(event, buttonID);
        } else if (buttonID.startsWith("startCabalAgent_")) {
            ButtonHelperAgents.startCabalAgent(player, game, buttonID, event);
        } else if (buttonID.startsWith("stellarConvert_")) {
            ButtonHelper.resolveStellar(player, game, event, buttonID);
        } else if (buttonID.startsWith("forwardSupplyBaseStep2_")) {
            ButtonHelperActionCards.resolveForwardSupplyBaseStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("divertFunding@")) {
            ButtonHelperActionCards.divertFunding(game, player, buttonID, event);
        } else if (buttonID.startsWith("newPrism@")) {
            PlanetExhaustAbility.newPrismPart2(game, player, buttonID, event);
        } else if (buttonID.startsWith("cabalAgentCapture_")) {
            ButtonHelperAgents.resolveCabalAgentCapture(buttonID, player, game, event);
        } else if (buttonID.startsWith("cabalRelease_")) {
            ButtonHelperFactionSpecific.resolveReleaseButton(player, game, buttonID, event);
        } else if (buttonID.startsWith("kolleccRelease_")) {
            ButtonHelperFactionSpecific.resolveKolleccReleaseButton(player, game, buttonID, event);
        } else if (buttonID.startsWith("shroudOfLithStart")) {
            UnfiledButtonHandlers.shroudOfLithStart(event, player, game);
        } else if (buttonID.startsWith("getReleaseButtons")) {
            UnfiledButtonHandlers.getReleaseButtons(event, player, game, trueIdentity);
        } else if (buttonID.startsWith("ghotiHeroIn_")) {
            UnfiledButtonHandlers.ghotiHeroIn(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("glimmersHeroIn_")) {
            UnfiledButtonHandlers.glimmersHeroIn(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("arboAgentIn_")) {
            UnfiledButtonHandlers.arboAgentIn(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("saarMechRes_")) {
            ButtonHelperFactionSpecific.placeSaarMech(player, game, event, buttonID);
        } else if (buttonID.startsWith("cymiaeCommanderRes_")) {
            ButtonHelperCommanders.cymiaeCommanderRes(player, game, event, buttonID);
        } else if (buttonID.startsWith("arboAgentPutShip_")) {
            ButtonHelperAgents.arboAgentPutShip(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("setAutoPassMedian_")) {
            UnfiledButtonHandlers.setAutoPassMedian(event, player, buttonID);
        } else if (buttonID.startsWith("arboAgentOn_")) {
            UnfiledButtonHandlers.arboAgentOn(event, player, buttonID, game, trueIdentity);
        } else if (buttonID.startsWith("glimmersHeroOn_")) {
            UnfiledButtonHandlers.glimmerHeroOn(event, player, buttonID, game, ident);
        } else if (buttonID.startsWith("resolveWithNoEffect")) {
            UnfiledButtonHandlers.resolveWithNoEffect(event, game);
        } else if (buttonID.startsWith("outcome_")) {
            UnfiledButtonHandlers.outcome(event, player, buttonID, game);
        } else if (buttonID.startsWith("orbitalMechDrop_")) {
            ButtonHelperAbilities.orbitalMechDrop(buttonID, event, game, player);
        } else if (buttonID.startsWith("dacxive_")) {
            UnfiledButtonHandlers.daxcive(event, player, buttonID, game, ident);
        } else if (buttonID.startsWith("autoresolve_")) {
            UnfiledButtonHandlers.autoResolve(event, player, buttonID, game);
        } else if (buttonID.startsWith("deleteButtons")) {
            UnfiledButtonHandlers.deleteButtons(event, buttonID, buttonLabel, game, player, mainGameChannel, trueIdentity);
        } else if (buttonID.startsWith("reverse_")) {
            AgendaHelper.reverseRider(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("moveGlory_")) {
            ButtonHelperAgents.moveGlory(game, player, event, buttonID);
        } else if (buttonID.startsWith("moveGloryStart_")) {
            ButtonHelperAgents.offerMoveGloryOptions(game, player, event);
        } else if (buttonID.startsWith("placeGlory_")) {
            ButtonHelperAgents.placeGlory(game, player, event, buttonID);
        } else if (buttonID.startsWith("rider_")) {
            AgendaHelper.placeRider(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("startToScuttleAUnit_")) {
            ButtonHelperActionCards.resolveScuttleStart(player, game, event, buttonID);
        } else if (buttonID.startsWith("startToLuckyShotAUnit_")) {
            ButtonHelperActionCards.resolveLuckyShotStart(player, game, event);
        } else if (buttonID.startsWith("endScuttle_")) {
            ButtonHelperActionCards.resolveScuttleEnd(player, game, event, buttonID);
        } else if (buttonID.startsWith("scuttleOn_")) {
            ButtonHelperActionCards.resolveScuttleRemoval(player, game, event, buttonID);
        } else if (buttonID.startsWith("luckyShotOn_")) {
            ButtonHelperActionCards.resolveLuckyShotRemoval(player, game, event, buttonID);
        } else if (buttonID.startsWith("scuttleIn_")) {
            ButtonHelperActionCards.resolveScuttleTileSelection(player, game, event, buttonID);
        } else if (buttonID.startsWith("luckyShotIn_")) {
            ButtonHelperActionCards.resolveLuckyShotTileSelection(player, game, event, buttonID);
        } else if (buttonID.startsWith("winnuHero_")) {
            ButtonHelperHeroes.resolveWinnuHeroSC(player, game, event, buttonID);
        } else if (buttonID.startsWith("construction_")) {
            ButtonHelperSCs.construction(game, player, event, buttonID, trueIdentity, messageID);
        } else if (buttonID.startsWith("jrStructure_")) {
            UnfiledButtonHandlers.jrStructure(event, player, buttonID, game, trueIdentity, ident);
        } else if (buttonID.startsWith("resolveReverse_")) {
            ButtonHelperActionCards.resolveReverse(game, player, buttonID, event);
        } else if (buttonID.startsWith("showObjInfo_")) {
            ListPlayerInfoButton.showObjInfo(event, buttonID, game);
        } else if (buttonID.startsWith("meteorSlings_")) {
            ButtonHelperAbilities.meteorSlings(player, buttonID, game, event);
        } else if (buttonID.startsWith("offerInfoButtonStep2_")) {
            ListPlayerInfoButton.resolveOfferInfoButtonStep2(event, buttonID, game);
        } else if (buttonID.startsWith("offerInfoButtonStep3_")) {
            ListPlayerInfoButton.resolveOfferInfoButtonStep3(event, buttonID, game, player);
        } else if (buttonID.startsWith("removeAllStructures_")) {
            UnfiledButtonHandlers.removeAllStructures(event, player, buttonID, game);
        } else if (buttonID.startsWith("winnuStructure_")) {
            UnfiledButtonHandlers.winnuStructure(event, player, buttonID, game);
        } else if (buttonID.startsWith("produceOneUnitInTile_")) {
            UnfiledButtonHandlers.produceOneUnitInTile(event, player, buttonID, game);
        } else if (buttonID.startsWith("yinagent_")) {
            ButtonHelperAgents.yinAgent(buttonID, event, game, player, ident, trueIdentity);
        } else if (buttonID.startsWith("resolveMaw")) {
            ButtonHelper.resolveMaw(game, player, event);
        } else if (buttonID.startsWith("resolveTwilightMirror")) {
            ButtonHelper.resolveTwilightMirror(game, player, event);
        } else if (buttonID.startsWith("resolveCrownOfE")) {
            ButtonHelper.resolveCrownOfE(game, player, event);
        } else if (buttonID.startsWith("yssarilAgentAsJr")) {
            ButtonHelperFactionSpecific.yssarilAgentAsJr(game, player, event);
        } else if (buttonID.startsWith("sarMechStep1_")) {
            ButtonHelper.resolveSARMechStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("sarMechStep2_")) {
            ButtonHelper.resolveSARMechStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("integratedBuild_")) {
            UnfiledButtonHandlers.integratedBuild(event, player, buttonID, game);
        } else if (buttonID.startsWith("deployMykoSD_")) {
            ButtonHelperFactionSpecific.deployMykoSD(player, game, event, buttonID);
        } else if (buttonID.startsWith("ravenMigration")) {
            ButtonHelperCommanders.handleRavenCommander(context);
        } else if (buttonID.startsWith("jrResolution_")) {
            ButtonHelperRelics.jrResolution(player, buttonID, game, event);
        } else if (buttonID.startsWith("colonialRedTarget_")) {
            AgendaHelper.resolveColonialRedTarget(game, buttonID, event);
        } else if (buttonID.startsWith("createGameChannels")) {
            CreateGameButton.decodeButtonMsg(event);
        } else if (buttonID.startsWith("yssarilHeroRejection_")) {
            ButtonHelperHeroes.yssarilHeroRejection(game, player, event, buttonID);
        } else if (buttonID.startsWith("yssarilHeroInitialOffering_")) {
            ButtonHelperHeroes.yssarilHeroInitialOffering(game, player, event, buttonID, buttonLabel);
        } else if (buttonID.startsWith("statusInfRevival_")) {
            ButtonHelper.placeInfantryFromRevival(game, event, player, buttonID);
        } else if (buttonID.startsWith("genericReact")) {
            UnfiledButtonHandlers.genericReact(event, game);
        } else if (buttonID.startsWith("placeOneNDone_")) {
            ButtonHelperModifyUnits.placeUnitAndDeleteButton(buttonID, event, game, player, ident, trueIdentity);
        } else if (buttonID.startsWith("mitoMechPlacement_")) {
            ButtonHelperAbilities.resolveMitosisMechPlacement(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("sendTradeHolder_")) {
            ButtonHelper.sendTradeHolderSomething(player, game, buttonID, event);
        } else if (buttonID.startsWith("place_")) {
            ButtonHelperModifyUnits.genericPlaceUnit(buttonID, event, game, player, ident, trueIdentity, finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("yssarilcommander_")) {
            ButtonHelperCommanders.yssarilCommander(buttonID, event, game, player, ident);
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
            ButtonHelper.resolveSpecialRex(player, game, buttonID, ident, event);
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
            ButtonHelperTacticalAction.movingUnitsInTacticalAction(buttonID, event, game, player, buttonLabel);
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
            ButtonHelperModifyUnits.landingUnits(buttonID, event, game, player, ident, buttonLabel);
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
            UnfiledButtonHandlers.declareUse(event, player, buttonID, game, trueIdentity, ident);
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
            ButtonHelperModifyUnits.spaceLandedUnits(buttonID, event, game, player, ident, buttonLabel);
        } else if (buttonID.startsWith("resetSpend_")) {
            UnfiledButtonHandlers.resetSpend(event, player, buttonID, game);
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
        } else if (buttonID.startsWith("rejectOffer_")) { // TODO: P1 finish refactor
            Player p1 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
            if (p1 != null) {
                MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(), p1.getRepresentation() + " your offer to " + player.getRepresentation(false, false) + " has been rejected.");
                ButtonHelper.deleteMessage(event);
            }
            int i = 10;

        } else if (buttonID.startsWith("rescindOffer_")) {
            Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
            if (p2 != null) {
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), p2.getRepresentation() + " the latest offer from " + player.getRepresentation(false, false) + " has been rescinded.");
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + "you rescinded the latest offer to " + p2.getRepresentation(false, false));
                player.clearTransactionItemsWithPlayer(p2);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("acceptOffer_")) {
            Player p1 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
            if (buttonID.split("_").length > 2) {
                String offerNum = buttonID.split("_")[2];
                String key = "offerFrom" + p1.getFaction() + "To" + player.getFaction();
                String oldOffer = game.getStoredValue(key);
                if (!offerNum.equalsIgnoreCase(oldOffer)) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Only the most recent offer is acceptable. This is an old transaction offer and it can no longer be accepted");
                    return;
                }
            }
            TransactionHelper.acceptTransactionOffer(p1, player, game, event);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("play_after_")) {
            String riderName = buttonID.replace("play_after_", "");
            ButtonHelper.addReaction(event, true, true, "Playing " + riderName, riderName + " Played");
            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            if ("Keleres Rider".equalsIgnoreCase(riderName) || "Edyn Rider".equalsIgnoreCase(riderName)
                || "Kyro Rider".equalsIgnoreCase(riderName)) {
                if ("Keleres Rider".equalsIgnoreCase(riderName)) {
                    String pnKey = "fin";
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        if (pn.contains("rider")) {
                            pnKey = pn;
                        }
                    }
                    if ("fin".equalsIgnoreCase(pnKey)) {
                        MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have a Keleres Rider");
                        return;
                    }
                    PlayPN.resolvePNPlay(pnKey, player, game, event);
                }
                if ("Edyn Rider".equalsIgnoreCase(riderName)) {
                    String pnKey = "fin";
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        if (pn.contains("dspnedyn")) {
                            pnKey = pn;
                        }
                    }
                    if ("fin".equalsIgnoreCase(pnKey)) {
                        MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have a Edyn Rider");
                        return;
                    }
                    PlayPN.resolvePNPlay(pnKey, player, game, event);
                }
                if ("Kyro Rider".equalsIgnoreCase(riderName)) {
                    String pnKey = "fin";
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        if (pn.contains("dspnkyro")) {
                            pnKey = pn;
                        }
                    }
                    if ("fin".equalsIgnoreCase(pnKey)) {
                        MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have a Kyro Rider");
                        return;
                    }
                    PlayPN.resolvePNPlay(pnKey, player, game, event);
                }
            } else {
                if (riderName.contains("Unity Algorithm")) {
                    player.exhaustTech("dsedyng");
                }
                if ("conspirators".equalsIgnoreCase(riderName)) {
                    game.setStoredValue("conspiratorsFaction", player.getFaction());
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        game.getPing()
                            + " The conspirators ability has been used, which means the player will vote after the speaker. This ability may be used once per agenda phase.");
                    if (!game.isFowMode()) {
                        ListVoteCount.turnOrder(event, game, game.getMainGameChannel());
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
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                    message + " - Odlynn Myrr, the Keleres (Xxcha) hero, has been purged.");
                            } else {
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                    "Odlynn Myrr, the Keleres (Xxcha) hero, was not purged - something went wrong.");
                            }
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                    "Please indicate no afters again.", game, afterButtons, "after");
            }
            // "dspnedyn"
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("ultimateUndo_")) {
            if (game.getSavedButtons().size() > 0) {
                String buttonString = game.getSavedButtons().get(0);
                String colorOrFaction = buttonString.split(";")[0];
                Player p = game.getPlayerFromColorOrFaction(colorOrFaction);
                if (p != null && player != p && !colorOrFaction.equals("null")) {
                    // if the last button was pressed by a non-faction player, allow anyone to undo
                    // it
                    String msg = "You were not the player who pressed the latest button. Use /game undo if you truly want to undo "
                        + game.getLatestCommand();
                    MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                    return;
                }
            }
            String highestNumBefore = buttonID.split("_")[1];
            File mapUndoDirectory = Storage.getMapUndoDirectory();
            if (!mapUndoDirectory.exists()) {
                return;
            }
            String mapName = game.getName();
            String mapNameForUndoStart = mapName + "_";
            String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
            if (mapUndoFiles != null && mapUndoFiles.length > 0) {
                List<Integer> numbers = Arrays.stream(mapUndoFiles)
                    .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                    .map(fileName -> fileName.replace(Constants.TXT, ""))
                    .map(Integer::parseInt).toList();
                int maxNumber = numbers.isEmpty() ? 0 : numbers.stream().mapToInt(value -> value).max().orElseThrow(NoSuchElementException::new);
                if (highestNumBefore.equalsIgnoreCase((maxNumber) + "")) {
                    ButtonHelper.deleteMessage(event);
                }
            }

            GameSaveLoadManager.undo(game, event);

            String msg = "You undid something, the details of which can be found in the undo-log thread";
            List<ThreadChannel> threadChannels = game.getMainGameChannel().getThreadChannels();
            for (ThreadChannel threadChannel_ : threadChannels) {
                if (threadChannel_.getName().equals(game.getName() + "-undo-log")) {
                    msg = msg + ": " + threadChannel_.getJumpUrl();
                }
            }
            event.getHook().sendMessage(msg).setEphemeral(true).queue();

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
            String planet = buttonID.replace("nanoforgePlanet_", "");
            Planet planetReal = game.getPlanetsInfo().get(planet);
            planetReal.addToken("attachment_nanoforge.png");
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Attached Nano-Forge to " + Helper.getPlanetRepresentation(planet, game));
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("resolvePNPlay_")) {
            String pnID = buttonID.replace("resolvePNPlay_", "");

            if (pnID.contains("ra_")) {
                String tech = AliasHandler.resolveTech(pnID.replace("ra_", ""));
                TechnologyModel techModel = Mapper.getTech(tech);
                pnID = pnID.replace("_" + tech, "");
                String message = ident + " Acquired The Tech " + techModel.getRepresentation(false)
                    + " via Research Agreement";
                player.addTech(tech);
                String key = "RAForRound" + game.getRound() + player.getFaction();
                if (game.getStoredValue(key).isEmpty()) {
                    game.setStoredValue(key, tech);
                } else {
                    game.setStoredValue(key, game.getStoredValue(key) + "." + tech);
                }
                ButtonHelperCommanders.resolveNekroCommanderCheck(player, tech, game);
                if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
                    CommanderUnlockCheck.checkPlayer(player, game, "jolnar", event);
                }
                if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
                    CommanderUnlockCheck.checkPlayer(player, game, "nekro", event);
                }
                if (player.getLeaderIDs().contains("mirvedacommander")
                    && !player.hasLeaderUnlocked("mirvedacommander")) {
                    CommanderUnlockCheck.checkPlayer(player, game, "mirveda", event);
                }
                if (player.getLeaderIDs().contains("dihmohncommander")
                    && !player.hasLeaderUnlocked("dihmohncommander")) {
                    CommanderUnlockCheck.checkPlayer(player, game, "dihmohn", event);
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            }
            PlayPN.resolvePNPlay(pnID, player, game, event);
            if (!"bmfNotHand".equalsIgnoreCase(pnID)) {
                ButtonHelper.deleteMessage(event);
            }

            var posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID,
                player.getNumberTurns());
            if (posssibleCombatMod != null) {
                player.addNewTempCombatMod(posssibleCombatMod);
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Combat modifier will be applied next time you push the combat roll button.");
            }

        } else if (buttonID.startsWith("send_")) {
            TransactionHelper.resolveSpecificTransButtonPress(game, player, buttonID, event, true);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("replacePDSWithFS_")) {
            ButtonHelperFactionSpecific.replacePDSWithFS(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("putSleeperOnPlanet_")) {
            ButtonHelperAbilities.putSleeperOn(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("frankenDraftAction;")) {
            FrankenDraftHelper.resolveFrankenDraftAction(game, player, event, buttonID);
        } else if (buttonID.startsWith("presetEdynAgentStep1")) {
            ButtonHelperAgents.presetEdynAgentStep1(game, player);
        } else if (buttonID.startsWith("presetEdynAgentStep2_")) {
            ButtonHelperAgents.presetEdynAgentStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("presetEdynAgentStep3_")) {
            ButtonHelperAgents.presetEdynAgentStep3(game, player, event, buttonID);
        } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
            ButtonHelperAbilities.removeSleeper(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("replaceSleeperWith_")) {
            ButtonHelperAbilities.replaceSleeperWith(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("relicSwapStep2")) {
            ButtonHelperHeroes.resolveRelicSwapStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("relicSwapStep1")) {
            ButtonHelperHeroes.resolveRelicSwapStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("retrieveAgenda_")) {
            String agendaID = buttonID.substring(buttonID.indexOf("_") + 1);
            DrawAgenda.drawSpecificAgenda(agendaID, game, player);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("topAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            new PutAgendaTop().putTop(event, Integer.parseInt(agendaNumID), game);
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
        } else if (buttonID.startsWith("resolveCounterStroke_")) {
            ButtonHelperActionCards.resolveCounterStroke(game, player, event, buttonID);
        } else if (buttonID.startsWith("sendTGTo_")) {
            AgendaHelper.erase1DebtTo(game, buttonID, event, player);
        } else if (buttonID.startsWith("primaryOfWarfare")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "warfare");
            MessageChannel channel = player.getCorrectChannel();
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        } else if (buttonID.startsWith("mahactCommander")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "mahactCommander");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.",
                buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("useTA_")) {
            String ta = buttonID.replace("useTA_", "") + "_ta";
            PlayPN.resolvePNPlay(ta, player, game, event);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("combatDroneConvert_")) {
            ButtonHelperModifyUnits.resolvingCombatDrones(event, game, player, ident, buttonID);
        } else if (buttonID.startsWith("cloakedFleets_")) {// kolleccMechCapture_
            ButtonHelperModifyUnits.resolveCloakedFleets(buttonID, event, game, player);
        } else if (buttonID.startsWith("kolleccMechCapture_")) {// kolleccMechCapture_
            ButtonHelperModifyUnits.resolveKolleccMechCapture(buttonID, event, game, player);
        } else if (buttonID.startsWith("refreshLandingButtons")) {
            List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
            event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        } else if (buttonID.startsWith("resolveMirvedaCommander_")) {
            ButtonHelperModifyUnits.resolvingMirvedaCommander(event, game, player, ident, buttonID);
        } else if (buttonID.startsWith("removeCCFromBoard_")) {
            ButtonHelper.resolveRemovingYourCC(player, game, event, buttonID);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("bottomAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            new PutAgendaBottom().putBottom(event, Integer.parseInt(agendaNumID), game);
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
        } else if (buttonID.startsWith("discardAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            String agendaID = game.revealAgenda(false);
            AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                ButtonHelper.getIdentOrColor(player, game) + "discarded " + agendaName + " using "
                    + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Allant, the Edyn" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                    + " agent.");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("agendaResolution_")) {
            AgendaHelper.resolveAgenda(game, buttonID, event, mainGameChannel);
        } else if (buttonID.startsWith("rollIxthian")) {
            if (game.getSpeaker().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
                AgendaHelper.rollIxthian(game, true);
            } else {
                Button ixthianButton = Buttons.green("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact", Emojis.Mecatol);
                String msg = "The speaker should roll for Ixthain Artifact. Click this button to roll anyway!";
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, ixthianButton);
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("applytempcombatmod__" + "tech" + "__")) {
            String acAlias = "sc";
            TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier("tech", acAlias,
                player.getNumberTurns());
            if (combatModAC != null) {
                player.addNewTempCombatMod(combatModAC);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    player.getFactionEmoji()
                        + " +1 Modifier will be applied the next time you push the combat roll button due to supercharge.");
            }
            player.exhaustTech("sc");
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("applytempcombatmod__" + Constants.AC + "__")) {
            String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
            TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier(Constants.AC,
                acAlias,
                player.getNumberTurns());
            if (combatModAC != null) {
                player.addNewTempCombatMod(combatModAC);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Combat modifier will be applied next time you push the combat roll button.");
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("drawActionCards_")) {
            try {
                int count = Integer.parseInt(buttonID.replace("drawActionCards_", ""));
                DrawAC.drawActionCards(game, player, count, true);
                ButtonHelper.deleteTheOneButton(event);
            } catch (Exception ignored) {
            }
        } else if (buttonID.startsWith("jmfA_") || buttonID.startsWith("jmfN_")) {
            game.initializeMiltySettings().parseButtonInput(event);
        } else if (buttonID.startsWith("frankenItemAdd")) {
            FrankenApplicator.resolveFrankenItemAddButton(event, buttonID, player);
        } else if (buttonID.startsWith("frankenItemRemove")) {
            FrankenApplicator.resolveFrankenItemRemoveButton(event, buttonID, player);
        } else if (buttonID.startsWith("addToken_")) {
            ButtonHelper.addTokenToTile(event, game, player, buttonID);
        } else if (buttonID.startsWith("geneticRecombination")) {
            ButtonHelperFactionSpecific.resolveGeneticRecombination(buttonID, event, game, player);
        } else {
            switch (buttonID) {
                // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
                case Constants.PO_NO_SCORING -> {
                    String message = player.getRepresentation()
                        + " - no Public Objective scored.";
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = game.isFowMode() ? "No public objective scored" : null;
                    ButtonHelper.addReaction(event, false, false, reply, "");
                    String key2 = "queueToScorePOs";
                    String key3 = "potentialScorePOBlockers";
                    if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key2,
                            game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                    }
                    if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key3,
                            game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                        String key3b = "potentialScoreSOBlockers";
                        if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                            Helper.resolvePOScoringQueue(game, event);
                            // Helper.resolveSOScoringQueue(game, event);
                        }
                    }

                }
                case "refreshInfoButtons" -> MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), null,
                    Buttons.REFRESH_INFO_BUTTONS);
                case "factionEmbedRefresh" -> MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), null,
                    List.of(player.getRepresentationEmbed()), List.of(Buttons.FACTION_EMBED));
                case "gameInfoButtons" -> ListPlayerInfoButton.offerInfoButtons(event);
                case "refreshPNInfo" -> PNInfo.sendPromissoryNoteInfo(game, player, true, event);
                case "refreshSOInfo" -> SOInfo.sendSecretObjectiveInfo(game, player, event);
                case "refreshAbilityInfo" -> AbilityInfo.sendAbilityInfo(game, player, event);
                case Constants.REFRESH_RELIC_INFO -> RelicInfo.sendRelicInfo(game, player, event);
                case Constants.REFRESH_TECH_INFO -> TechInfo.sendTechInfo(game, player, event);
                case Constants.REFRESH_UNIT_INFO -> UnitInfo.sendUnitInfo(game, player, event, false);
                case Constants.REFRESH_ALL_UNIT_INFO -> UnitInfo.sendUnitInfo(game, player, event, true);
                case Constants.REFRESH_LEADER_INFO -> LeaderInfo.sendLeadersInfo(game, player, event);
                case Constants.REFRESH_PLANET_INFO -> PlanetInfo.sendPlanetInfo(player);
                case "warfareBuild" -> ButtonHelperSCs.warfareBuild(game, player, event, buttonID, trueIdentity, messageID);
                case "getKeleresTechOptions" -> ButtonHelperFactionSpecific.offerKeleresStartingTech(player, game, event);
                case "transaction" -> {
                    List<Button> buttons;
                    buttons = TransactionHelper.getPlayersToTransact(game, player);
                    String message = player.getRepresentation()
                        + " Use the buttons to select which player you wish to transact with";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);

                }
                case "combatDrones" -> ButtonHelperModifyUnits.offerCombatDroneButtons(event, game, player);
                case "offerMirvedaCommander" -> ButtonHelperModifyUnits.offerMirvedaCommanderButtons(event, game, player);
                case "acquireAFreeTech" -> { // Buttons.GET_A_FREE_TECH
                    List<Button> buttons = new ArrayList<>();
                    game.setComponentAction(true);
                    Button propulsionTech = Buttons.blue(finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay", "Get a Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                    buttons.add(propulsionTech);

                    Button bioticTech = Buttons.green(finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay", "Get a Green Tech");
                    bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                    buttons.add(bioticTech);

                    Button cyberneticTech = Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay", "Get a Yellow Tech");
                    cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    buttons.add(cyberneticTech);

                    Button warfareTech = Buttons.red(finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay", "Get a Red Tech");
                    warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(warfareTech);

                    Button unitupgradesTech = Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay", "Get A Unit Upgrade Tech");
                    unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
                    buttons.add(unitupgradesTech);
                    String message = player.getRepresentation() + " What type of tech would you want?";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    ButtonHelper.deleteMessage(event);
                }
                case "acquireATech" -> ButtonHelper.acquireATech(player, game, event, messageID, false);
                case "acquireAUnitTechWithInf" -> ButtonHelper.acquireATech(player, game, event, messageID, false, Set.of(Constants.UNIT), "inf");
                case "acquireATechWithSC" -> ButtonHelper.acquireATech(player, game, event, messageID, true);
                case Constants.SO_NO_SCORING -> {
                    String message = player.getRepresentation()
                        + " - no Secret Objective scored.";

                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    String key2 = "queueToScoreSOs";
                    String key3 = "potentialScoreSOBlockers";
                    if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key2,
                            game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                    }
                    if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key3,
                            game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                        String key3b = "potentialScorePOBlockers";
                        if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                            Helper.resolvePOScoringQueue(game, event);
                            // Helper.resolveSOScoringQueue(game, event);
                        }
                    }
                }
                case "no_sabotage" -> {
                    String message = game.isFowMode() ? "No Sabotage" : null;
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "titansCommanderUsage" -> ButtonHelperCommanders.titansCommanderUsage(buttonID, event, game, player, ident);
                case "ghotiATG" -> ButtonHelperAgents.ghotiAgentForTg(buttonID, event, game, player);
                case "ghotiAProd" -> ButtonHelperAgents.ghotiAgentForProduction(buttonID, event, game, player);
                case "getHomebrewButtons" -> ButtonHelper.offerHomeBrewButtons(game, event);
                case "passForRound" -> {
                    Pass.passPlayerForRound(event, game, player);
                    ButtonHelper.deleteMessage(event);
                }
                case "proceedToVoting" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Decided to skip waiting for afters and proceed to voting.");
                    try {
                        AgendaHelper.startTheVoting(game);
                    } catch (Exception e) {
                        BotLogger.log(event, "Could not start the voting", e);
                    }
                }
                case "forceACertainScoringOrder" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
                        + "Players will be forced to score in order. Players will not be prevented from declaring they don't score, and are in fact encouraged to do so without delay if that is the case. This forced scoring order also does not yet affect SOs, it only restrains POs");
                    game.setStoredValue("forcedScoringOrder", "true");
                    ButtonHelper.deleteMessage(event);
                }
                case "turnOffForcedScoring" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
                        + "Forced scoring order has been turned off. Any queues will not be resolved.");
                    game.setStoredValue("forcedScoringOrder", "");
                    ButtonHelper.deleteMessage(event);
                }
                case "proceedToFinalizingVote" -> {
                    AgendaHelper.proceedToFinalizingVote(game, player, event);
                }
                case "drawAgenda_2" -> {
                    if (!game.getStoredValue("hasntSetSpeaker").isEmpty() && !game.isHomebrewSCMode()) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " you need to assign speaker first before drawing agendas. You can override this restriction with /agenda draw");
                        return;
                    }
                    DrawAgenda.drawAgenda(event, 2, game, player);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " drew 2 agendas");
                    ButtonHelper.deleteMessage(event);
                }
                case "nekroFollowTech" -> ButtonHelperSCs.nekroFollowTech(game, player, event, buttonID, trueIdentity, messageID);
                case "diploRefresh2" -> ButtonHelperSCs.diploRefresh2(game, player, event, buttonID, trueIdentity, messageID);
                case "getOmenDice" -> ButtonHelperAbilities.offerOmenDiceButtons(game, player);
                case "leadershipExhaust" -> {
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                    Button doneExhausting = Buttons.red("deleteButtons_leadership", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "nekroTechExhaust" -> {
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
                    Button doneExhausting = Buttons.red("deleteButtons_technology", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    ButtonHelper.deleteMessage(event);
                }
                case "deployTyrant" -> {
                    String message = "Use buttons to put a tyrant with your ships";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message,
                        Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament",
                            "placeOneNDone_skipbuild"));
                    ButtonHelper.deleteTheOneButton(event);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " is deploying the Tyrants Lament");
                    player.addOwnedUnitByID("tyrantslament");
                }
                case "startStrategyPhase" -> {
                    StartPhase.startPhase(event, game, "strategy");
                    ButtonHelper.deleteMessage(event);
                }
                case "endOfTurnAbilities" -> {
                    String msg = "Use buttons to do an end of turn ability";
                    List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, game);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                    // ButtonHelper.deleteTheOneButton(event);
                }
                case "redistributeCCButtons" -> { // Buttons.REDISTRIBUTE_CCs
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                    Button getTactic = Buttons.green(finsFactionCheckerPrefix + "increase_tactic_cc",
                        "Gain 1 Tactic CC");
                    Button getFleet = Buttons.green(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Buttons.green(finsFactionCheckerPrefix + "increase_strategy_cc",
                        "Gain 1 Strategy CC");
                    Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc",
                        "Lose 1 Tactic CC");
                    Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet CC");
                    Button loseStrat = Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc",
                        "Lose 1 Strategy CC");

                    Button doneGainingCC = Buttons.red(finsFactionCheckerPrefix + "deleteButtons",
                        "Done Redistributing CCs");
                    Button resetCC = Buttons.gray(finsFactionCheckerPrefix + "resetCCs",
                        "Reset CCs");
                    List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, loseTactic, loseFleet,
                        loseStrat,
                        doneGainingCC, resetCC);
                    if (player.hasAbility("deliberate_action") && game.getPhaseOfGame().contains("status")) {
                        buttons = Arrays.asList(getTactic, getFleet, getStrat,
                            doneGainingCC, resetCC);
                    }
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }

                    if (!game.isFowMode() && "statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                        ButtonHelper.addReaction(event, false, false, "", "");
                    }

                    if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                        boolean cyber = false;
                        for (String pn : player.getPromissoryNotes().keySet()) {
                            if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                                cyber = true;
                            }
                        }
                        if (player.hasAbility("versatile") || player.hasTech("hm") || cyber) {
                            int properGain = 2;
                            String reasons = "";
                            if (player.hasAbility("versatile")) {
                                properGain = properGain + 1;
                                reasons = "Versatile ";
                            }
                            if (player.hasTech("hm")) {
                                properGain = properGain + 1;
                                reasons = reasons + "Hypermetabolism ";
                            }
                            if (cyber) {
                                properGain = properGain + 1;
                                reasons = reasons + "Cybernetic Enhancements (L1Z1X PN) ";
                            }
                            if (properGain > 2) {
                                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                    player.getRepresentationUnfogged() + " heads up, bot thinks you should gain "
                                        + properGain + " CC now due to: " + reasons);
                            }
                        }
                        if (game.isCcNPlasticLimit()) {
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                "Your highest fleet count in a system is currently "
                                    + ButtonHelper.checkFleetInEveryTile(player, game, event)
                                    + ". That's how many fleet CC you need to avoid removing ships");
                        }
                    }
                }
                case "leadershipGenerateCCButtons" -> {
                    ButtonHelperSCs.leadershipGenerateCCButtons(game, player, event, buttonID, trueIdentity);
                }
                case "spyNetYssarilChooses" -> ButtonHelperFactionSpecific.resolveSpyNetYssarilChooses(player, game, event);
                case "spyNetPlayerChooses" -> ButtonHelperFactionSpecific.resolveSpyNetPlayerChooses(player, game, event);
                case "diploSystem" -> {
                    String message = trueIdentity + " Click the name of the planet whose system you wish to diplo";
                    List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "diplomacy", buttons);
                }
                case "sc_ac_draw" -> {
                    ButtonHelperSCs.scACDraw(game, player, event, buttonID, trueIdentity, messageID);
                }
                case "draw2 AC" -> {
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    String message = hasSchemingAbility
                        ? "Drew 3 Action Cards (Scheming) - please discard 1 action card from your hand"
                        : "Drew 2 Action cards";
                    int count = hasSchemingAbility ? 3 : 2;
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";

                    } else {
                        for (int i = 0; i < count; i++) {
                            game.drawActionCard(player.getUserID());
                        }
                        ACInfo.sendActionCardInfo(game, player, event);
                        ButtonHelper.checkACLimit(game, event, player);
                    }

                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentationUnfogged() + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(game, player, false));
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                        CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                    }
                    ButtonHelper.deleteTheOneButton(event);

                }
                case "resolveDistinguished" -> ButtonHelperActionCards.resolveDistinguished(player, game, event);
                case "resolveMykoMech" -> ButtonHelperFactionSpecific.resolveMykoMech(player, game);
                case "offerNecrophage" -> ButtonHelperFactionSpecific.offerNekrophageButtons(player, event);
                case "resolveMykoCommander" -> ButtonHelperCommanders.mykoCommanderUsage(player, game, event);
                case "checkForAllACAssignments" -> ButtonHelperActionCards.checkForAllAssignmentACs(game, player);
                case "sc_draw_so" -> ButtonHelperSCs.scDrawSO(game, player, event, buttonID, trueIdentity, messageID);
                case "non_sc_draw_so" -> {
                    String message = "Drew A Secret Objective";
                    game.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player.getUserID());
                        message += ". Drew a second SO due to Plausible Deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(game, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "edynCommanderSODraw" -> {
                    if (!game.playerHasLeaderUnlockedOrAlliance(player, "edyncommander")) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getFactionEmoji() + " you don't have Kadryn, the Edyn commander, silly.");
                    }
                    String message = "Drew A Secret Objective instead of scoring PO, using Kadryn, the Edyn commander.";
                    game.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player.getUserID());
                        message += ". Drew a second SO due to Plausible Deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(game, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "sc_trade_follow", "sc_follow_trade" -> ButtonHelperSCs.followTrade(game, player, event, buttonID, trueIdentity, messageID);
                case "flip_agenda" -> {
                    RevealAgenda.revealAgenda(event, false, game, event.getChannel());
                    ButtonHelper.deleteMessage(event);
                }
                case "refreshAgenda" -> AgendaHelper.refreshAgenda(game, event);
                case "resolveVeto" -> {
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
                    RevealAgenda.revealAgenda(event, false, game, event.getChannel());
                    ButtonHelper.deleteMessage(event);

                }
                case "hack_election" -> {
                    game.setHasHackElectionBeenPlayed(false);
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Set Order Back To Normal.");
                    ButtonHelper.deleteMessage(event);
                }
                case "proceed_to_strategy" -> {
                    Map<String, Player> players = game.getPlayers();
                    if (game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
                        for (Player player_ : players.values()) {
                            player_.cleanExhaustedPlanets(false);
                        }
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Refreshed all planets at the end of the agenda phase");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Did not refresh planets due to the Checks and Balances resolving against. Players have been sent buttons to refresh up to 3 planets.");
                    }
                    StartPhase.startStrategyPhase(event, game);
                    ButtonHelper.deleteMessage(event);

                }
                case "sc_refresh" -> ButtonHelperSCs.refresh(game, player, event, buttonID, trueIdentity, messageID);
                case "sc_refresh_and_wash" -> ButtonHelperSCs.refreshAndWash(game, player, event, buttonID, trueIdentity, messageID);
                case "trade_primary" -> ButtonHelper.tradePrimary(game, event, player);
                case "score_imperial" -> ButtonHelperSCs.scoreImperial(game, player, event, buttonID, trueIdentity, messageID);
                case "play_when" -> {
                    UnfiledButtonHandlers.clearAllReactions(event);
                    ButtonHelper.addReaction(event, true, true, "Playing When", "When Played");
                    List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
                    Date newTime = new Date();
                    game.setLastActivePlayerPing(newTime);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                        "Please indicate no whens again.", game, whenButtons, "when");
                    List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                        "Please indicate no afters again.", game, afterButtons, "after");
                    ButtonHelper.deleteMessage(event);
                }
                case "no_when" -> {
                    String message = game.isFowMode() ? "No whens" : null;
                    game.removePlayersWhoHitPersistentNoWhen(player.getFaction());
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_after" -> {
                    String message = game.isFowMode() ? "No afters" : null;
                    game.removePlayersWhoHitPersistentNoAfter(player.getFaction());
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_after_persistent" -> {
                    String message = game.isFowMode() ? "No afters (locked in)" : null;
                    game.addPlayersWhoHitPersistentNoAfter(player.getFaction());
                    ButtonHelper.addReaction(event, false, false, message, "");
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        "You hit no afters for this entire agenda. If you change your mind, you can just play an after or remove this setting by hitting no afters (for now)");
                }
                case "no_when_persistent" -> {
                    String message = game.isFowMode() ? "No whens (locked in)" : null;
                    game.addPlayersWhoHitPersistentNoWhen(player.getFaction());
                    ButtonHelper.addReaction(event, false, false, message, "");
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        "You hit no whens for this entire agenda. If you change your mind, you can just play a when or remove this setting by hitting no whens (for now)");

                }
                case "munitionsReserves" -> {
                    ButtonHelperAbilities.munitionsReserves(event, game, player);
                }
                case "deal2SOToAll" -> {
                    DealSOToAll.dealSOToAll(event, 2, game);
                    ButtonHelper.deleteMessage(event);
                }
                case "startOfGameObjReveal" -> {
                    Player speaker = null;
                    if (game.getPlayer(game.getSpeaker()) != null) {
                        speaker = game.getPlayers().get(game.getSpeaker());
                    }
                    for (Player p : game.getRealPlayers()) {
                        if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Please ensure everyone has discarded secrets before hitting this button. ");
                            return;
                        }
                    }
                    if (speaker == null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Please assign speaker before hitting this button.");
                        ButtonHelper.offerSpeakerButtons(game, player);
                        return;
                    }
                    RevealStage1.revealTwoStage1(event, game.getMainGameChannel());
                    StartPhase.startStrategyPhase(event, game);
                    PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(game, null);
                    ButtonHelper.deleteMessage(event);
                }
                case "startYinSpinner" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        player.getFactionEmoji() + " Chose to Use Yin Spinner");
                    List<Button> buttons = new ArrayList<>(
                        Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
                    String message = "Use buttons to drop 2 infantry on a planet. Technically you may also drop 2 infantry with your ships, but this ain't supported yet via button.";
                    ButtonHelper.deleteTheOneButton(event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                }
                // Gain/Convert comms
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
                // etc
                case "startPlayerSetup" -> ButtonHelper.resolveSetupStep0(player, game, event);
                case "comm_for_AC" -> {
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    int count2 = hasSchemingAbility ? 2 : 1;
                    String commOrTg;
                    if (player.getCommodities() > 0) {
                        commOrTg = "commodity";
                        player.setCommodities(player.getCommodities() - 1);

                    } else if (player.getTg() > 0) {
                        player.setTg(player.getTg() - 1);
                        commOrTg = "trade good";
                    } else {
                        ButtonHelper.addReaction(event, false, false, "Didn't have any comms/TGs to spend, no AC drawn",
                            "");
                        break;
                    }
                    String message = hasSchemingAbility
                        ? "Spent 1 " + commOrTg + " to draw " + count2
                            + " Action Card (Scheming) - please discard 1 action card from your hand"
                        : "Spent 1 " + commOrTg + " to draw " + count2 + " AC";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, count2);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                    } else {
                        for (int i = 0; i < count2; i++) {
                            game.drawActionCard(player.getUserID());
                        }
                        ButtonHelper.checkACLimit(game, event, player);
                        ACInfo.sendActionCardInfo(game, player, event);
                    }

                    if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                        CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                    }

                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentationUnfogged() + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(game, player, false));
                    }

                    ButtonHelper.addReaction(event, false, false, message, "");
                    ButtonHelper.deleteMessage(event);
                    if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(mainGameChannel, pF + " " + message);
                    }
                }
                case "increase_strategy_cc" -> {
                    player.setStrategicCC(player.getStrategicCC() + 1);
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "yssarilMinisterOfPolicy" -> {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " is drawing Minister of Policy AC(s)");
                    String message;
                    if (player.hasAbility("scheming")) {
                        game.drawActionCard(player.getUserID());
                        game.drawActionCard(player.getUserID());
                        message = player.getFactionEmoji() + " Drew 2 ACs With Scheming. Please Discard 1 AC.";
                        ACInfo.sendActionCardInfo(game, player, event);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentationUnfogged() + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(game, player, false));

                    } else if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                    } else {
                        game.drawActionCard(player.getUserID());
                        ACInfo.sendActionCardInfo(game, player, event);
                        message = player.getFactionEmoji() + " Drew 1 AC";
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                        CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                    }

                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    ButtonHelper.checkACLimit(game, event, player);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "resetProducedThings" -> {
                    Helper.resetProducedUnits(player, game, event);
                    event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, game)).queue();
                }
                case "exhauste6g0network" -> {
                    player.addExhaustedRelic("e6-g0_network");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " Chose to exhaust e6-g0_network");
                    String message;
                    if (player.hasAbility("scheming")) {
                        game.drawActionCard(player.getUserID());
                        game.drawActionCard(player.getUserID());
                        message = player.getFactionEmoji() + " Drew 2 ACs With Scheming. Please Discard 1 AC.";
                        ACInfo.sendActionCardInfo(game, player, event);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentationUnfogged() + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(game, player, false));

                    } else if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                    } else {
                        game.drawActionCard(player.getUserID());
                        ACInfo.sendActionCardInfo(game, player, event);
                        message = player.getFactionEmoji() + " Drew 1 AC";
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                        CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                    }

                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    ButtonHelper.checkACLimit(game, event, player);
                    ButtonHelper.deleteTheOneButton(event);

                }
                case "increase_tactic_cc" -> {

                    player.setTacticalCC(player.getTacticalCC() + 1);
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "resetCCs" -> {
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    ButtonHelper.resetCCs(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: 0";
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_fleet_cc" -> {
                    player.setFleetCC(player.getFleetCC() + 1);
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                    if (ButtonHelper.isLawInPlay(game, "regulations") && player.getFleetCC() > 4) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                            + " reminder that there is Fleet Regulations in place, which is limiting fleet pool to 4");
                    }
                }
                case "decrease_strategy_cc" -> {
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_tactic_cc" -> {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_fleet_cc" -> {
                    player.setFleetCC(player.getFleetCC() - 1);
                    String originalCCs = game
                        .getStoredValue("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                        + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "gain_1_tg" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
                    boolean failed = false;
                    if (labelP.contains("inf") && labelP.contains("mech")) {
                        message += ExploreHelper.checkForMechOrRemoveInf(planetName, game, player);
                        failed = message.contains("Please try again.");
                    }
                    if (!failed) {
                        message += "Gained 1TG " + player.gainTG(1, true) + ".";
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (!failed) {
                        ButtonHelper.deleteMessage(event);
                        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                            String pF = player.getFactionEmoji();
                            MessageHelper.sendMessageToChannel(mainGameChannel, pF + " " + message);
                        }
                    }
                }
                case "gain1tgFromLetnevCommander" -> {
                    String message = player.getRepresentation() + " Gained 1TG " + player.gainTG(1) + " from Rear Admiral Farran, the Letnev commander.";
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    MessageHelper.sendMessageToChannel(mainGameChannel, message);
                    ButtonHelper.deleteMessage(event);
                }
                case "gain1tgFromMuaatCommander" -> {
                    String message = player.getRepresentation() + " Gained 1TG " + player.gainTG(1) + " from Magmus, the Muaat commander.";
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    MessageHelper.sendMessageToChannel(mainGameChannel, message);
                    ButtonHelper.deleteMessage(event);
                }
                case "gain1tgFromCommander" -> { // should be deprecated
                    String message = player.getRepresentation() + " Gained 1TG " + player.gainTG(1) + " from their commander";
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    MessageHelper.sendMessageToChannel(mainGameChannel, message);
                    ButtonHelper.deleteMessage(event);
                }
                case "mallice_2_tg" -> {
                    String playerRep = player.getFactionEmoji();
                    String message = playerRep + " exhausted Mallice ability and gained 2TGs " + player.gainTG(2) + ".";
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 2);
                    CommanderUnlockCheck.checkPlayer(player, game, "hacan", event);
                    if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    ButtonHelper.deleteMessage(event);
                }
                case "mallice_convert_comm" -> {

                    String playerRep = player.getFactionEmoji();

                    String message = playerRep + " exhausted Mallice ability and converted comms to TGs (TGs: "
                        + player.getTg() + "->" + (player.getTg() + player.getCommodities()) + ").";
                    player.setTg(player.getTg() + player.getCommodities());
                    player.setCommodities(0);
                    if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    ButtonHelper.deleteMessage(event);
                }
                case "decline_explore" -> {
                    ButtonHelper.addReaction(event, false, false, "Declined Explore", "");
                    ButtonHelper.deleteMessage(event);
                    if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(mainGameChannel, pF + " declined explore");
                    }
                }
                case "temporaryPingDisable" -> {
                    game.setTemporaryPingDisable(true);
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn");
                    ButtonHelper.deleteMessage(event);
                }
                case "riseOfAMessiah" -> {
                    RiseOfMessiah.doRise(player, event, game);
                    ButtonHelper.deleteMessage(event);
                }
                case "fighterConscription" -> {
                    new FighterConscription().doFfCon(event, player, game);
                    ButtonHelper.deleteMessage(event);
                }
                case "shuffleExplores" -> {
                    game.shuffleExplores();
                    ButtonHelper.deleteMessage(event);
                }
                case "miningInitiative" -> ButtonHelperActionCards.miningInitiative(player, game, event);
                case "forwardSupplyBase" -> ButtonHelperActionCards.resolveForwardSupplyBaseStep1(player, game, event);
                case "economicInitiative" -> ButtonHelperActionCards.economicInitiative(player, game, event);
                case "breakthrough" -> ButtonHelperActionCardsWillHomebrew.resolveBreakthrough(player, game, event);
                case "sideProject" -> ButtonHelperActionCardsWillHomebrew.resolveSideProject(player, game, event);
                case "brutalOccupation" -> ButtonHelperActionCardsWillHomebrew.resolveBrutalOccupationStep1(player, game, event);
                case "getRepealLawButtons" -> {
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "Use buttons to select Law to repeal",
                        ButtonHelperActionCards.getRepealLawButtons(game, player));
                    ButtonHelper.deleteMessage(event);
                }
                case "resolveCounterStroke" -> ButtonHelperActionCards.resolveCounterStroke(game, player, event);
                case "getDivertFundingButtons" -> {
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "Use buttons to select tech to return",
                        ButtonHelperActionCards.getDivertFundingLoseTechOptions(player, game));
                    ButtonHelper.deleteMessage(event);
                }
                case "resolveResearch" -> ButtonHelperActionCards.resolveResearch(game, player, buttonID, event);
                case "focusedResearch" -> ButtonHelperActionCards.focusedResearch(game, player, buttonID, event);
                case "lizhoHeroFighterResolution" -> ButtonHelperHeroes.lizhoHeroFighterDistribution(player, game, event);
                case "resolveReparationsStep1" -> ButtonHelperActionCards.resolveReparationsStep1(player, game, event);
                case "resolveSeizeArtifactStep1" -> ButtonHelperActionCards.resolveSeizeArtifactStep1(player, game, event, "no");
                case "resolveDiplomaticPressureStep1" -> ButtonHelperActionCards.resolveDiplomaticPressureStep1(player, game, event, buttonID);
                case "resolveImpersonation" -> ButtonHelperActionCards.resolveImpersonation(player, game, event, buttonID);
                case "resolveUprisingStep1" -> ButtonHelperActionCards.resolveUprisingStep1(player, game, event, buttonID);
                case "setTrapStep1" -> ButtonHelperAbilities.setTrapStep1(game, player);
                case "revealTrapStep1" -> ButtonHelperAbilities.revealTrapStep1(game, player);
                case "removeTrapStep1" -> ButtonHelperAbilities.removeTrapStep1(game, player);
                case "offerDeckButtons" -> ButtonHelper.offerDeckButtons(game, event);
                case "resolveAssRepsStep1" -> ButtonHelperActionCards.resolveAssRepsStep1(player, game, event, buttonID);
                case "resolveSignalJammingStep1" -> ButtonHelperActionCards.resolveSignalJammingStep1(player, game, event, buttonID);
                case "resolvePlagueStep1" -> ButtonHelperActionCards.resolvePlagueStep1(player, game, event, buttonID);
                case "resolveMicrometeoroidStormStep1" -> ButtonHelperActionCards.resolveMicrometeoroidStormStep1(player, game, event, buttonID);
                case "resolveCrippleDefensesStep1" -> ButtonHelperActionCards.resolveCrippleDefensesStep1(player, game, event, buttonID);
                case "resolveInfiltrateStep1" -> ButtonHelperActionCards.resolveInfiltrateStep1(player, game, event, buttonID);
                case "resolveReactorMeltdownStep1" -> ButtonHelperActionCards.resolveReactorMeltdownStep1(player, game, event, buttonID);
                case "resolveSpyStep1" -> ButtonHelperActionCards.resolveSpyStep1(player, game, event, buttonID);
                case "resolveUnexpected" -> ButtonHelperActionCards.resolveUnexpectedAction(player, game, event, buttonID);
                case "resolveFrontline" -> ButtonHelperActionCards.resolveFrontlineDeployment(player, game, event, buttonID);
                case "resolveInsubStep1" -> ButtonHelperActionCards.resolveInsubStep1(player, game, event, buttonID);
                case "resolveUnstableStep1" -> ButtonHelperActionCards.resolveUnstableStep1(player, game, event, buttonID);
                case "resolveABSStep1" -> ButtonHelperActionCards.resolveABSStep1(player, game, event, buttonID);
                case "resolveWarEffort" -> ButtonHelperActionCards.resolveWarEffort(game, player, event);
                case "resolveFreeTrade" -> ButtonHelperActionCards.resolveFreeTrade(game, player, event);
                case "resolvePreparation" -> ButtonHelperActionCards.resolvePreparation(game, player, event);
                case "resolveInsiderInformation" -> ButtonHelperActionCards.resolveInsiderInformation(player, game, event);
                case "resolveEmergencyMeeting" -> ButtonHelperActionCards.resolveEmergencyMeeting(player, game, event);
                case "resolveSalvageStep1" -> ButtonHelperActionCards.resolveSalvageStep1(player, game, event, buttonID);
                case "resolveGhostShipStep1" -> ButtonHelperActionCards.resolveGhostShipStep1(player, game, event, buttonID);
                case "strandedShipStep1" -> ButtonHelperActionCardsWillHomebrew.resolveStrandedShipStep1(player, game, event);
                case "spatialCollapseStep1" -> ButtonHelperActionCardsWillHomebrew.resolveSpatialCollapseStep1(player, game, event);
                case "resolveTacticalBombardmentStep1" -> ButtonHelperActionCards.resolveTacticalBombardmentStep1(player, game, event, buttonID);
                case "resolveProbeStep1" -> ButtonHelperActionCards.resolveProbeStep1(player, game, event, buttonID);
                case "resolvePSStep1" -> ButtonHelperActionCards.resolvePSStep1(player, game, event, buttonID);
                case "resolveRally" -> ButtonHelperActionCards.resolveRally(game, player, event);
                case "resolveHarness" -> ButtonHelperStats.replenishComms(event, game, player, false);
                case "resolveSummit" -> ButtonHelperActionCards.resolveSummit(game, player, event);
                case "resolveRefitTroops" -> ButtonHelperActionCards.resolveRefitTroops(player, game, event,
                    buttonID, finsFactionCheckerPrefix);
                case "industrialInitiative" -> ButtonHelperActionCards.industrialInitiative(player, game, event);
                case "confirm_cc" -> {
                    if (player.getMahactCC().size() > 0) {
                        ButtonHelper.addReaction(event, true, false,
                            "Confirmed CCs: " + player.getTacticalCC() + "/" + player.getFleetCC() + "(+"
                                + player.getMahactCC().size() + ")/" + player.getStrategicCC(),
                            "");
                    } else {
                        ButtonHelper.addReaction(event, true, false, "Confirmed CCs: " + player.getTacticalCC() + "/"
                            + player.getFleetCC() + "/" + player.getStrategicCC(), "");
                    }
                }
                case "draw_1_AC" -> {
                    String message = "";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                    } else {
                        game.drawActionCard(player.getUserID());
                        if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                            CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(game, player, event);
                        message = "Drew 1 AC";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    ButtonHelper.checkACLimit(game, event, player);
                }
                case "drawStatusACs" -> ButtonHelper.drawStatusACs(game, player, event);
                case "draw_1_ACDelete" -> {
                    String message = "";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                    } else {
                        game.drawActionCard(player.getUserID());
                        if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                            CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(game, player, event);
                        message = "Drew 1 AC";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    ButtonHelper.deleteMessage(event);
                    ButtonHelper.checkACLimit(game, event, player);
                }
                case "draw_2_ACDelete" -> {
                    String message = "";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(game, player, 2);
                        message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                    } else {
                        game.drawActionCard(player.getUserID());
                        game.drawActionCard(player.getUserID());
                        if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                            CommanderUnlockCheck.checkPlayer(player, game, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(game, player, event);
                        message = "Drew 2 ACs With Scheming. Please Discard 1 AC.";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(game, player, false));

                    ButtonHelper.deleteMessage(event);
                    ButtonHelper.checkACLimit(game, event, player);
                }
                case "pass_on_abilities" -> ButtonHelper.addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                case "tacticalAction" -> {
                    ButtonHelperTacticalAction.selectRingThatActiveSystemIsIn(player, game, event);
                }
                case "lastMinuteDeliberation" -> {
                    ButtonHelper.deleteMessage(event);
                    String message = player.getRepresentation()
                        + " Click the names of up to 2 planets you wish to ready ";

                    List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, game);
                    buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets")); // spitItOut
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message, buttons);
                    RevealAgenda.revealAgenda(event, false, game, actionsChannel);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to ready 2 planets to the person who pressed the button");
                }
                case "willRevolution" -> {
                    ButtonHelper.deleteMessage(event);
                    game.setStoredValue("willRevolution", "active");
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Reversed SC Picking order");
                }
                case "ChooseDifferentDestination" -> {
                    String message = "Choosing a different system to activate. Please select the ring of the map that the system you want to activate is located in."
                        + " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
                    List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
                    ButtonHelper.deleteMessage(event);
                }
                case "componentAction" -> {
                    player.setWhetherPlayerShouldBeTenMinReminded(false);
                    String message = "Use Buttons to decide what kind of component action you want to do";
                    List<Button> systemButtons = ComponentActionHelper.getAllPossibleCompButtons(game, player, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    if (!game.isFowMode()) {
                        ButtonHelper.deleteMessage(event);
                    }
                }
                case "drawRelicFromFrag" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
                    RelicDraw.drawRelicAndNotify(player, event, game);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    ButtonHelper.deleteMessage(event);
                }
                case "drawRelic" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
                    RelicDraw.drawRelicAndNotify(player, event, game);
                    ButtonHelper.deleteMessage(event);
                }
                case "thronePoint" -> {
                    Integer poIndex = game.addCustomPO("Throne of the False Emperor", 1);
                    game.scorePublicObjective(player.getUserID(), poIndex);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation() + " scored a Secret (they'll specify which one)");
                    Helper.checkEndGame(game, player);
                    ButtonHelper.deleteMessage(event);
                }
                case "startArbiter" -> ButtonHelper.resolveImperialArbiter(event, game, player);
                case "resolveTombRaiders" -> ButtonHelperActionCardsWillHomebrew.resolveTombRaiders(player, game, event);
                case "pay1tgforKeleres" -> ButtonHelperCommanders.pay1tgToUnlockKeleres(player, game, event, false);
                case "pay1tgforKeleresU" -> ButtonHelperCommanders.pay1tgToUnlockKeleres(player, game, event, true);
                case "pay1tg" -> {
                    int oldTg = player.getTg();
                    if (player.getTg() > 0) {
                        player.setTg(oldTg - 1);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        ButtonHelper.getIdentOrColor(player, game) + " paid 1TG to announce a retreat " + "("
                            + oldTg + "->" + player.getTg() + ")");
                    ButtonHelper.deleteMessage(event);
                }
                case "announceARetreat" -> {
                    String msg = "# " + ident + " announces a retreat";
                    if (game.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
                        msg = msg + ". Since they have Jack Hallard, the Nokar commander, this means they may cancel 2 hits in this coming combat round.";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    if (game.getActivePlayer() != null && game.getActivePlayer() != player && game.getActivePlayer().hasAbility("cargo_raiders")) {
                        String combatName = "combatRoundTracker" + game.getActivePlayer().getFaction() + game.getActiveSystem() + "space";
                        if (game.getStoredValue(combatName).isEmpty()) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.green("pay1tg", "Pay 1TG"));
                            buttons.add(Buttons.red("deleteButtons", "I don't have to pay"));
                            String raiders = player.getRepresentation() + " reminder that your opponent has the cargo raiders ability, which means you might have to pay 1TG to announce a retreat if they choose.";
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), raiders, buttons);
                        }
                    }
                }
                case "declinePDS" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    ident + " officially declines to fire PDS");
                case "startQDN" -> ButtonHelperFactionSpecific.resolveQuantumDataHubNodeStep1(player, game, event);
                case "finishComponentAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    ButtonHelper.deleteMessage(event);
                }
                case "crownofemphidiaexplore" -> {
                    player.addExhaustedRelic("emphidia");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        ident + " Exhausted " + Emojis.Relic + "Crown of Emphidia");
                    List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore",
                        buttons);
                    ButtonHelper.deleteMessage(event);
                }
                case "doneWithTacticalAction" -> {
                    ButtonHelperTacticalAction.concludeTacticalAction(player, game, event);
                    // ButtonHelper.updateMap(activeMap, event);
                }
                case "doAnotherAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    ButtonHelper.deleteMessage(event);
                }
                case "ministerOfPeace" -> ButtonHelper.resolveMinisterOfPeace(player, game, event);
                case "ministerOfWar" -> AgendaHelper.resolveMinisterOfWar(game, player, event);
                case "concludeMove" -> {
                    ButtonHelperTacticalAction.finishMovingForTacticalAction(player, game, event, buttonID);
                }
                case "doneRemoving" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    ButtonHelper.deleteMessage(event);
                    ButtonHelper.updateMap(game, event);
                }
                case "mitosisMech" -> ButtonHelperAbilities.resolveMitosisMech(buttonID, event, game, player,
                    ident, finsFactionCheckerPrefix);
                case "searchMyGames" -> SearchMyGames.searchGames(event.getUser(), event, false, false, false, true, false, true, false, false);
                case "cardsInfo" -> CardsInfo.sendCardsInfo(game, player, event);
                case "showMap" -> ShowGame.showMap(game, event);
                case "showGameAgain" -> ShowGame.simpleShowGame(game, event);
                case "showPlayerAreas" -> ShowGame.showPlayArea(game, event);
                case "mitosisInf" -> ButtonHelperAbilities.resolveMitosisInf(buttonID, event, game, player, ident);
                case "doneLanding" -> ButtonHelperModifyUnits.finishLanding(buttonID, event, game, player);
                case "preVote" -> {
                    game.setStoredValue("preVoting" + player.getFaction(), "0");
                    AgendaHelper.firstStepOfVoting(game, event, player);
                }
                case "erasePreVote" -> {
                    game.setStoredValue("preVoting" + player.getFaction(), "");
                    player.resetSpentThings();
                    event.getMessage().delete().queue();
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green("preVote", "Pre-Vote"));
                    buttons.add(Buttons.blue("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
                    buttons.add(Buttons.red("deleteButtons", "Don't do anything"));
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Erased the pre-vote", buttons);
                }
                case "vote" -> AgendaHelper.firstStepOfVoting(game, event, player);
                case "turnEnd" -> {
                    if (game.isFowMode() && !player.equals(game.getActivePlayer())) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "You are not the active player. Force End Turn with /player turn_end.");
                        return;
                    }
                    CommanderUnlockCheck.checkPlayer(player, game, "hacan", event);
                    TurnEnd.pingNextPlayer(event, game, player);
                    event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);

                    ButtonHelper.updateMap(game, event, "End of Turn " + player.getTurnCount() + ", Round "
                        + game.getRound() + " for " + player.getFactionEmoji());
                }
                case "getDiplomatsButtons" -> ButtonHelperAbilities.resolveGetDiplomatButtons(buttonID, event, game, player);
                case "gameEnd" -> {
                    GameEnd.secondHalfOfGameEnd(event, game, true, true, false);
                    ButtonHelper.deleteMessage(event);
                }
                case "rematch" -> ButtonHelper.rematch(game, event);
                case "offerToGiveTitles" -> {
                    PlayerTitleHelper.offerEveryoneTitlePossibilities(game);
                    ButtonHelper.deleteMessage(event);
                }
                case "enableAidReacts" -> {
                    game.setBotFactionReacts(true);
                    ButtonHelper.deleteMessage(event);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Reacts have been enabled");
                }
                case "purgeHacanHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("hacanhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                        .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            message + " - Harrugh Gefhara, the Hacan hero, has been purged.");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Harrugh Gefhara, the Hacan hero, was not purged - something went wrong.");
                    }
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeSardakkHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("sardakkhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                        .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            message + " - Sh'val, Harbinger, the N'orr hero, has been purge.d");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Sh'val, Harbinger, the N'orr hero, was not purged - something went wrong.");
                    }
                    ButtonHelperHeroes.killShipsSardakkHero(player, game, event);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " All ships have been removed, continue to land troops.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "resolveDataArchive" -> ButtonHelperActionCardsWillHomebrew.resolveDataArchive(player, game, event);
                case "resolveDefenseInstallation" -> ButtonHelperActionCardsWillHomebrew.resolveDefenseInstallation(player, game, event);
                case "resolveAncientTradeRoutes" -> ButtonHelperActionCardsWillHomebrew.resolveAncientTradeRoutes(player, game, event);
                case "resolveSisterShip" -> ButtonHelperActionCardsWillHomebrew.resolveSisterShip(player, game, event);
                case "resolveBoardingParty" -> ButtonHelperActionCardsWillHomebrew.resolveBoardingParty(player, game, event);
                case "resolveMercenaryContract" -> ButtonHelperActionCardsWillHomebrew.resolveMercenaryContract(player, game, event);
                case "resolveRendezvousPoint" -> ButtonHelperActionCardsWillHomebrew.resolveRendezvousPoint(player, game, event);
                case "resolveFlawlessStrategy" -> ButtonHelperActionCardsWillHomebrew.resolveFlawlessStrategy(player, event);
                case "resolveChainReaction" -> ButtonHelperActionCardsWillHomebrew.resolveChainReaction(player, game, event);
                case "resolveArmsDeal" -> ButtonHelperActionCardsWillHomebrew.resolveArmsDeal(player, game, event);
                case "purgeRohdhnaHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("rohdhnahero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                        .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            message + " - Roh’Vhin Dhna mk4, the Roh'Dhna hero, has been purged.");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Roh’Vhin Dhna mk4, the Roh'Dhna hero, was not purged - something went wrong.");
                    }
                    List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game,
                        game.getTileByPosition(game.getActiveSystem()), "rohdhnaBuild", "place");
                    String message2 = player.getRepresentation() + " Use the buttons to produce units. ";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeVaylerianHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("vaylerianhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                        .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            message + " - Dyln Harthuul, the Vaylerian hero, has been purged.");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Dyln Harthuul, the Vaylerian hero, was not purged - something went wrong.");
                    }
                    if (!game.isNaaluAgent()) {
                        player.setTacticalCC(player.getTacticalCC() - 1);
                        AddCC.addCC(event, player.getColor(),
                            game.getTileByPosition(game.getActiveSystem()));
                        game.setStoredValue("vaylerianHeroActive", "true");
                    }
                    for (Tile tile : ButtonHelperAgents.getGloryTokenTiles(game)) {
                        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event,
                            "vaylerianhero");
                        if (buttons.size() > 0) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                "Use buttons to remove a token from the board", buttons);
                        }
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " may gain 1 CC");
                    List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                    String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                    ButtonHelper.deleteTheOneButton(event);
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                }
                case "purgeKeleresAHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("keleresherokuuasi");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                        .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            message + " - Kuuasi Aun Jalatai, the Keleres (Argent) hero, has been purged.");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Kuuasi Aun Jalatai, the Keleres (Argent) hero, was not purged - something went wrong.");
                    }
                    new AddUnits().unitParsing(event, player.getColor(),
                        game.getTileByPosition(game.getActiveSystem()), "2 cruiser, 1 flagship",
                        game);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getRepresentationUnfogged() + " 2 cruisers and 1 flagship added.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeDihmohnHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("dihmohnhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                        .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            message + " - Verrisus Ypru, the Dih-Mohn hero, has been purged.");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Verrisus Ypru, the Dih-Mohn hero, was not purged - something went wrong.");
                    }
                    ButtonHelperHeroes.resolvDihmohnHero(game);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged()
                        + " sustained everything. Reminder you do not take hits this round.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "quash" -> {
                    int stratCC = player.getStrategicCC();
                    player.setStrategicCC(stratCC - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Quashed agenda. Strategic CCs went from " + stratCC + " -> " + (stratCC - 1));
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Quash");
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
                            "# The hidden agenda was " + agendaName + "! You may find it in the discard.");
                    }
                    RevealAgenda.revealAgenda(event, false, game, game.getMainGameChannel());
                    ButtonHelper.deleteMessage(event);
                }
                case "scoreAnObjective" -> {
                    List<Button> poButtons = TurnEnd.getScoreObjectiveButtons(event, game,
                        finsFactionCheckerPrefix);
                    poButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                    MessageChannel channel = event.getMessageChannel();
                    if (game.isFowMode()) {
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective",
                        poButtons);
                }
                case "startChaosMapping" -> ButtonHelperFactionSpecific.firstStepOfChaos(game, player, event);
                case "useLawsOrder" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        ident + " is paying 1 influence to ignore laws for the turn.");
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                    Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "Click the names of the planets you wish to exhaust to pay the 1 influence", buttons);
                    ButtonHelper.deleteTheOneButton(event);
                    game.setStoredValue("lawsDisabled", "yes");
                }
                case "dominusOrb" -> {
                    game.setDominusOrb(true);
                    String purgeOrExhaust = "Purged ";
                    String relicId = "dominusorb";
                    player.removeRelic(relicId);
                    player.removeExhaustedRelic(relicId);
                    String relicName = Mapper.getRelic(relicId).getName();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        purgeOrExhaust + Emojis.Relic + " relic: " + relicName);
                    ButtonHelper.deleteMessage(event);
                    String message = "Choose a system to move from.";
                    List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, game, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                }
                case "ultimateUndo" -> {
                    if (game.getSavedButtons().size() > 0 && !game.getPhaseOfGame().contains("status")) {
                        String buttonString = game.getSavedButtons().get(0);
                        if (game.getPlayerFromColorOrFaction(buttonString.split(";")[0]) != null) {
                            boolean showGame = false;
                            for (String buttonString2 : game.getSavedButtons()) {
                                if (buttonString2.contains("Show Game")) {
                                    showGame = true;
                                    break;
                                }
                            }
                            if (player != game.getPlayerFromColorOrFaction(buttonString.split(";")[0])
                                && !showGame) {
                                MessageHelper.sendMessageToChannel(event.getChannel(),
                                    "You were not the player who pressed the latest button. Use /game undo if you truly want to undo "
                                        + game.getLatestCommand());
                                return;
                            }
                        }
                    }

                    GameSaveLoadManager.undo(game, event);
                    if ("action".equalsIgnoreCase(game.getPhaseOfGame())
                        || "agendaVoting".equalsIgnoreCase(game.getPhaseOfGame())) {
                        if (!event.getMessage().getContentRaw().contains(finsFactionCheckerPrefix)) {
                            List<ActionRow> actionRow2 = new ArrayList<>();
                            boolean dontDelete = false;
                            for (ActionRow row : event.getMessage().getActionRows()) {
                                List<ItemComponent> buttonRow = row.getComponents();
                                for (ItemComponent item : buttonRow) {
                                    if (item instanceof Button butt) {
                                        if (butt.getId().contains("doneLanding")
                                            || butt.getId().contains("concludeMove")) {
                                            dontDelete = true;
                                            break;
                                        }
                                    }
                                }

                            }
                            if (!dontDelete) {
                                ButtonHelper.deleteMessage(event);
                            }
                        }
                    }

                }
                // kick
                case "getDiscardButtonsACs" -> {
                    String msg = trueIdentity + " use buttons to discard";
                    List<Button> buttons = ACInfo.getDiscardActionCardButtons(game, player, false);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
                }
                case "eraseMyRiders" -> AgendaHelper.reverseAllRiders(event, game, player);
                case "chooseMapView" -> {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.blue("checkWHView", "Find Wormholes"));
                    buttons.add(Buttons.red("checkAnomView", "Find Anomalies"));
                    buttons.add(Buttons.green("checkLegendView", "Find Legendaries"));
                    buttons.add(Buttons.gray("checkEmptyView", "Find Empties"));
                    buttons.add(Buttons.blue("checkAetherView", "Determine Aetherstreamable Systems"));
                    buttons.add(Buttons.red("checkCannonView", "Calculate Space Cannon Offense Shots"));
                    buttons.add(Buttons.green("checkTraitView", "Find Traits"));
                    buttons.add(Buttons.green("checkTechSkipView", "Find Technology Specialties"));
                    buttons.add(Buttons.blue("checkAttachmView", "Find Attachments"));
                    buttons.add(Buttons.gray("checkShiplessView", "Show Map Without Ships"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", buttons);
                }
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
                case "resetSpend" -> {
                    Helper.refreshPlanetsOnTheRevote(player, game);
                    String whatIsItFor = "both";
                    if (buttonID.split("_").length > 2) {
                        whatIsItFor = buttonID.split("_")[2];
                    }
                    player.resetSpentThings();
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
                    List<ActionRow> actionRow2 = new ArrayList<>();
                    for (ActionRow row : event.getMessage().getActionRows()) {
                        List<ItemComponent> buttonRow = row.getComponents();
                        for (ItemComponent but : buttonRow) {
                            if (but instanceof Button butt) {
                                if (!buttons.contains(butt)) {
                                    buttons.add(butt);
                                }
                            }
                        }
                    }
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
                    event.getMessage().editMessage(exhaustedMessage)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons)).queue();
                }
                case "eraseMyVote" -> {
                    String pfaction = player.getFaction();
                    if (game.isFowMode()) {
                        pfaction = player.getColor();
                    }
                    Helper.refreshPlanetsOnTheRevote(player, game);
                    AgendaHelper.eraseVotesOfFaction(game, pfaction);
                    String eraseMsg = "Erased previous votes made by " + player.getFactionEmoji()
                        + " and readied the planets they previously exhausted\n\n"
                        + AgendaHelper.getSummaryOfVotes(game, true);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), eraseMsg);
                    Button vote = Buttons.green(finsFactionCheckerPrefix + "vote",
                        player.getFlexibleDisplayName() + " Choose To Vote");
                    Button abstain = Buttons.red(finsFactionCheckerPrefix + "resolveAgendaVote_0",
                        player.getFlexibleDisplayName() + " Choose To Abstain");
                    Button forcedAbstain = Buttons.gray("forceAbstainForPlayer_" + player.getFaction(),
                        "(For Others) Abstain for this player");

                    String buttonMsg = "Use buttons to vote again. Reminder that this erasing of old votes did not refresh any planets.";
                    List<Button> buttons = Arrays.asList(vote, abstain, forcedAbstain);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        buttonMsg, buttons);
                }
                case "setOrder" -> {
                    Helper.setOrder(game);
                    ButtonHelper.deleteMessage(event);
                }
                case "gain_CC" -> {
                    String message = "";

                    String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    game.setStoredValue("originalCCsFor" + player.getFaction(),
                        player.getCCRepresentation());
                    List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (!event.getMessage().getContentRaw().contains("fragment")) {
                        ButtonHelper.deleteMessage(event);
                    }
                }
                case "run_status_cleanup" -> {
                    new Cleanup().runStatusCleanup(game);
                    ButtonHelper.deleteTheOneButton(event);

                    ButtonHelper.addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");

                }
                default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
            }
        }
    }
}
