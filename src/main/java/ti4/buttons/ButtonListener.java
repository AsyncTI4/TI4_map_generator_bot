package ti4.buttons;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
import ti4.commands.cardsac.DiscardACRandom;
import ti4.commands.cardsac.DrawAC;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.DealSOToAll;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.DrawSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.combat.StartCombat;
import ti4.commands.custom.PeakAtStage1;
import ti4.commands.custom.PeakAtStage2;
import ti4.commands.ds.TrapReveal;
import ti4.commands.ds.ZelianHero;
import ti4.commands.explore.DrawRelic;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.explore.RelicInfo;
import ti4.commands.franken.FrankenApplicator;
import ti4.commands.game.CreateGameButton;
import ti4.commands.game.GameEnd;
import ti4.commands.game.StartPhase;
import ti4.commands.game.Swap;
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
import ti4.commands.units.MoveUnits;
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
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.helpers.FrankenDraftHelper;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.context.ButtonContext;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.RelicModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

public class ButtonListener extends ListenerAdapter {
    public static ButtonListener instance = null;

    public static final Map<Guild, Map<String, Emoji>> emoteMap = new HashMap<>();
    private static final Map<String, Set<Player>> playerUsedSC = new HashMap<>();
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
            trueIdentity = player.getRepresentation(true, true);
            fowIdentity = player.getRepresentation(false, true);
            ident = player.getFactionEmoji();
        }

        // Check the list of buttons first
        if (handleKnownButtons(context)) return;

        // find the button
        // TODO (Jazz): convert everything
        if (buttonID.startsWith(Constants.AC_PLAY_FROM_HAND)) {
            acPlayFromHand(event, buttonID, game, player, mainGameChannel, fowIdentity);
        } else if (buttonID.startsWith("ac_discard_from_hand_")) {
            acDiscardFromHand(event, buttonID, game, player, mainGameChannel);
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            soScoreFromHand(event, buttonID, game, player, privateChannel, mainGameChannel, mainGameChannel);
        } else if (buttonID.startsWith("SODISCARD_")) {
            soDiscard(event, buttonID, game, player, privateChannel, mainGameChannel, mainGameChannel, ident);
        } else if (buttonID.startsWith("mantleCrack_")) {
            ButtonHelperAbilities.mantleCracking(player, game, event, buttonID);
        } else if (buttonID.startsWith("umbatTile_")) {
            ButtonHelperAgents.umbatTile(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("dihmohnfs_")) {
            ButtonHelperFactionSpecific.resolveDihmohnFlagship(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("dsdihmy_")) {
            ButtonHelper.deleteMessage(event);
            ButtonHelperFactionSpecific.resolveImpressmentPrograms(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("spendStratNReadyAgent_")) {
            ButtonHelperAgents.resolveAbsolHyperAgentReady(buttonID, event, game, player);
        } else if (buttonID.startsWith("get_so_score_buttons")) {
            getSoScoreButtons(event, game, player);
        } else if (buttonID.startsWith("swapToFaction_")) {
            String faction = buttonID.replace("swapToFaction_", "");
            new Swap().secondHalfOfSwap(game, player, game.getPlayerFromColorOrFaction(faction),
                event.getUser(), event);
        } else if (buttonID.startsWith("yinHeroInfantry_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("contagion_")) {
            ButtonHelperAbilities.lastStepOfContagion(buttonID, event, game, player);
        } else if (buttonID.startsWith("drawSpecificSO_")) {
            DiscardSO.drawSpecificSO(event, player, buttonID.split("_")[1], game);
        } else if (buttonID.startsWith("olradinHeroFlip_")) {
            ButtonHelperHeroes.olradinHeroFlipPolicy(buttonID, event, game, player);
        } else if (buttonID.startsWith("tnelisHeroAttach_")) {
            ButtonHelperHeroes.resolveTnelisHeroAttach(player, game, buttonID.split("_")[1], event);
        } else if (buttonID.startsWith("arcExp_")) {
            ButtonHelperActionCards.resolveArcExpButtons(game, player, buttonID, event, trueIdentity);
        } else if (buttonID.startsWith("augerHeroSwap.")) {
            ButtonHelperHeroes.augersHeroSwap(player, game, buttonID, event);
        } else if (buttonID.startsWith("hacanMechTradeStepOne_")) {
            ButtonHelperFactionSpecific.resolveHacanMechTradeStepOne(player, game, event, buttonID);
        } else if (buttonID.startsWith("rollForAmbush_")) {
            ButtonHelperFactionSpecific.rollAmbush(player, game,
                game.getTileByPosition(buttonID.split("_")[1]), event);
        } else if (buttonID.startsWith("raghsCallStepOne_")) {
            ButtonHelperFactionSpecific.resolveRaghsCallStepOne(player, game, event, buttonID);
        } else if (buttonID.startsWith("tnelisDeploy_")) {
            ButtonHelperFactionSpecific.tnelisDeploy(player, game, event, buttonID);
        } else if (buttonID.startsWith("gheminaMechStart_")) {
            ButtonHelperFactionSpecific.gheminaMechStart(player, game, buttonID, event);
        } else if (buttonID.startsWith("collateralizedLoans_")) {
            ButtonHelperFactionSpecific.collateralizedLoans(player, game, buttonID, event);
        } else if (buttonID.startsWith("raghsCallStepTwo_")) {
            ButtonHelperFactionSpecific.resolveRaghsCallStepTwo(player, game, event, buttonID);
        } else if (buttonID.startsWith("hacanMechTradeStepTwo_")) {
            ButtonHelperFactionSpecific.resolveHacanMechTradeStepTwo(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveDMZTrade_")) {
            ButtonHelper.resolveDMZTrade(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveAlliancePlanetTrade_")) {
            ButtonHelper.resolveAllianceMemberPlanetTrade(player, game, event, buttonID);
        } else if (buttonID.startsWith("augersHeroStart_")) {
            ButtonHelperHeroes.augersHeroResolution(player, game, buttonID);
        } else if (buttonID.startsWith("augersPeak_")) {
            if ("1".equalsIgnoreCase(buttonID.split("_")[1])) {
                new PeakAtStage1().secondHalfOfPeak(event, game, player, 1);
            } else {
                new PeakAtStage2().secondHalfOfPeak(event, game, player, 1);
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("initialPeak")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("augersPeak_1", "Peek At Next Stage 1"));
            buttons.add(Button.success("augersPeak_2", "Peek At Next Stage 2"));
            String msg = trueIdentity
                + " the bot doesn't know if the next objective is a stage 1 or a stage 2. Please help it out and click the right button.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        } else if (buttonID.startsWith("cabalHeroTile_")) {
            ButtonHelperHeroes.executeCabalHero(buttonID, player, game, event);
        } else if (buttonID.startsWith("creussMechStep1_")) {
            ButtonHelperFactionSpecific.creussMechStep1(game, player);
        } else if (buttonID.startsWith("nivynMechStep1_")) {
            ButtonHelperFactionSpecific.nivynMechStep1(game, player);
        } else if (buttonID.startsWith("creussMechStep2_")) {
            ButtonHelperFactionSpecific.creussMechStep2(game, player, buttonID, event);
        } else if (buttonID.startsWith("nivynMechStep2_")) {
            ButtonHelperFactionSpecific.nivynMechStep2(game, player, buttonID, event);
        } else if (buttonID.startsWith("creussMechStep3_")) {
            ButtonHelperFactionSpecific.creussMechStep3(game, player, buttonID, event);
        } else if (buttonID.startsWith("creussIFFStart_")) {
            ButtonHelperFactionSpecific.resolveCreussIFFStart(game, player, buttonID, ident, event);
        } else if (buttonID.startsWith("creussIFFResolve_")) {
            ButtonHelperFactionSpecific.resolveCreussIFF(game, player, buttonID, event);
        } else if (buttonID.startsWith("acToSendTo_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("creussHeroStep1_")) {
            ButtonHelperHeroes.getGhostHeroTilesStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("spatialCollapseStep2_")) {
            ButtonHelperActionCardsWillHomebrew.resolveSpatialCollapseStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("spatialCollapseStep3_")) {
            ButtonHelperActionCardsWillHomebrew.resolveSpatialCollapseStep3(game, player, event, buttonID);
        } else if (buttonID.startsWith("brutalOccupationStep2_")) {
            ButtonHelperActionCardsWillHomebrew.resolveBrutalOccupationStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveUpgrade_")) {
            ButtonHelperActionCards.resolveUpgrade(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveEmergencyRepairs_")) {
            ButtonHelperActionCards.resolveEmergencyRepairs(player, game, event, buttonID);
        } else if (buttonID.startsWith("creussHeroStep2_")) {
            ButtonHelperHeroes.resolveGhostHeroStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("yinCommanderStep1_")) {
            ButtonHelperCommanders.yinCommanderStep1(player, game, event);
        } else if (buttonID.startsWith("yinCommanderRemoval_")) {
            ButtonHelperCommanders.resolveYinCommanderRemoval(player, game, buttonID, event);
        } else if (buttonID.startsWith("cheiranCommanderBlock_")) {
            ButtonHelperCommanders.cheiranCommanderBlock(player, game, event);
        } else if (buttonID.startsWith("kortaliCommanderBlock_")) {
            ButtonHelperCommanders.kortaliCommanderBlock(player, game, event);
        } else if (buttonID.startsWith("placeGhostCommanderFF_")) {
            ButtonHelperCommanders.resolveGhostCommanderPlacement(player, game, buttonID, event);
        } else if (buttonID.startsWith("placeKhraskCommanderInf_")) {
            ButtonHelperCommanders.resolveKhraskCommanderPlacement(player, game, buttonID, event);
        } else if (buttonID.startsWith("yinHeroPlanet_")) {
            String planet = buttonID.replace("yinHeroPlanet_", "");
            if (planet.equalsIgnoreCase("lockedmallice")) {
                Tile tile = game.getTileFromPlanet("lockedmallice");
                planet = "mallice";
                tile = MoveUnits.flipMallice(event, tile, game);
            } else if (planet.equalsIgnoreCase("hexlockedmallice")) {
                Tile tile = game.getTileFromPlanet("hexlockedmallice");
                planet = "hexmallice";
                tile = MoveUnits.flipMallice(event, tile, game);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(),
                trueIdentity + " Chose to invade " + Helper.getPlanetRepresentation(planet, game));
            List<Button> buttons = new ArrayList<>();
            for (int x = 1; x < 4; x++) {
                buttons.add(Button
                    .success(finsFactionCheckerPrefix + "yinHeroInfantry_" + planet + "_" + x,
                        "Land " + x + " infantry")
                    .withEmoji(Emoji.fromFormatted(Emojis.infantry)));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                "Use buttons to select how many infantry you'd like to land on the planet", buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("yinHeroTarget_")) {
            String faction = buttonID.replace("yinHeroTarget_", "");
            List<Button> buttons = new ArrayList<>();
            Player target = game.getPlayerFromColorOrFaction(faction);
            if (target != null) {
                for (String planet : target.getPlanets()) {
                    buttons.add(Button.success(finsFactionCheckerPrefix + "yinHeroPlanet_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    "Use buttons to select which planet to invade", buttons);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("yinHeroStart")) {
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "yinHeroTarget", null);
            if (game.getTileByPosition("tl").getTileID().equalsIgnoreCase("82a")) {
                buttons.add(Button.success("yinHeroPlanet_lockedmallice", "Invade Mallice"));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                "Use buttons to select which player owns the planet you want to land on", buttons);
        } else if (buttonID.startsWith("psychoExhaust_")) {
            ButtonHelper.resolvePsychoExhaust(game, event, player, buttonID);
        } else if (buttonID.startsWith("productionBiomes_")) {
            ButtonHelperFactionSpecific.resolveProductionBiomesStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("getAgentSelection_")) {
            ButtonHelper.deleteTheOneButton(event);
            List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                trueIdentity + " choose the target of your agent", buttons);
        } else if (buttonID.startsWith("step2axisagent_")) {
            ButtonHelperAgents.resolveStep2OfAxisAgent(player, game, event, buttonID);
        } else if (buttonID.startsWith("vadenHeroClearDebt")) {
            ButtonHelperHeroes.vadenHeroClearDebt(game, player, event, buttonID);
        } else if (buttonID.startsWith("sendVadenHeroSomething_")) {
            ButtonHelperHeroes.sendVadenHeroSomething(player, game, buttonID, event);
        } else if (buttonID.startsWith("hacanAgentRefresh_")) {
            ButtonHelperAgents.hacanAgentRefresh(buttonID, event, game, player, ident, trueIdentity);
        } else if (buttonID.startsWith("vaylerianAgent_")) {
            ButtonHelperAgents.resolveVaylerianAgent(buttonID, event, game, player);
        } else if (buttonID.startsWith("nekroAgentRes_")) {
            ButtonHelperAgents.nekroAgentRes(buttonID, event, game, player);
        } else if (buttonID.startsWith("kolleccAgentRes_")) {
            ButtonHelperAgents.kolleccAgentResStep1(buttonID, event, game, player);
        } else if (buttonID.startsWith("scourPlanet_")) {
            ButtonHelperFactionSpecific.resolveScour(player, game, event, buttonID);
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
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                trueIdentity + " use buttons to get 1TG per planet exhausted.",
                ButtonHelper.getPsychoTechPlanets(game, player));
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
            String pos = buttonID.split("_")[1];
            String color = buttonID.split("_")[2];
            Tile tile = game.getTileByPosition(pos);
            Player attacker = game.getPlayerFromColorOrFaction(color);
            ButtonHelper.resolveMahactMechAbilityUse(player, attacker, game, tile, event);
        } else if (buttonID.startsWith("nullificationField_")) {
            String pos = buttonID.split("_")[1];
            String color = buttonID.split("_")[2];
            Tile tile = game.getTileByPosition(pos);
            Player attacker = game.getPlayerFromColorOrFaction(color);
            ButtonHelper.resolveNullificationFieldUse(player, attacker, game, tile, event);
        } else if (buttonID.startsWith("benedictionStep1_")) {
            String pos1 = buttonID.split("_")[1];
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                trueIdentity + " choose the tile you wish to send the ships in "
                    + game.getTileByPosition(pos1).getRepresentationForButtons(game, player)
                    + " to.",
                ButtonHelperHeroes.getBenediction2ndTileOptions(player, game, pos1));
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("mahactBenedictionFrom_")) {
            ButtonHelperHeroes.mahactBenediction(buttonID, event, game, player);
            String pos1 = buttonID.split("_")[1];
            String pos2 = buttonID.split("_")[2];
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ident + " moved all units in space from "
                    + game.getTileByPosition(pos1).getRepresentationForButtons(game, player)
                    + " to "
                    + game.getTileByPosition(pos2).getRepresentationForButtons(game, player)
                    + " using Airo Shir Aur, the Mahact hero. If they moved themselves and wish to move ground forces, they may do so either with slash command or modify units button.");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("retreatUnitsFrom_")) {
            ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, game, player);
            String both = buttonID.replace("retreatUnitsFrom_", "");
            String pos1 = both.split("_")[0];
            String pos2 = both.split("_")[1];
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ident + " retreated all units in space to "
                    + game.getTileByPosition(pos2).getRepresentationForButtons(game, player));
            String message = trueIdentity + " Use below buttons to move any ground forces or conclude retreat.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, event, pos1, pos2));
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("retreat_")) {
            String pos = buttonID.split("_")[1];
            boolean skilled = false;
            if (buttonID.contains("skilled")) {
                skilled = true;
                ButtonHelper.deleteMessage(event);
            }
            if (buttonID.contains("foresight")) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    ident + " lost a strategy CC to resolve the foresight ability");
                player.setStrategicCC(player.getStrategicCC() - 1);
                skilled = true;
            }
            String message = trueIdentity
                + " Use buttons to select a system to move to.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled));
        } else if (buttonID.startsWith("exhaustAgent_")) {
            ButtonHelperAgents.exhaustAgent(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("exhaustTCS_")) {
            ButtonHelperFactionSpecific.resolveTCSExhaust(buttonID, event, game, player);
        } else if (buttonID.startsWith("swapSCs_")) {
            ButtonHelperFactionSpecific.resolveSwapSC(player, game, event, buttonID);
        } else if (buttonID.startsWith("domnaStepThree_")) {
            ButtonHelperModifyUnits.resolveDomnaStep3Buttons(event, game, player, buttonID);
        } else if (buttonID.startsWith("domnaStepTwo_")) {
            ButtonHelperModifyUnits.offerDomnaStep3Buttons(event, game, player, buttonID);
        } else if (buttonID.startsWith("setHourAsAFK_")) {
            ButtonHelper.resolveSetAFKTime(game, player, buttonID, event);
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
            String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(game, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            // key2
            if ("true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScorePOs";
                String key3 = "potentialScorePOBlockers";
                String key3b = "potentialScoreSOBlockers";
                String message = "Drew Secret Objective";
                for (Player player2 : Helper.getInitativeOrder(game)) {
                    if (player2 == player) {
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2)
                                .replace(player.getFaction() + "*", ""));
                        }

                        String poID = buttonID.replace(Constants.PO_SCORING, "");
                        int poIndex = Integer.parseInt(poID);
                        ScorePublic.scorePO(event, privateChannel, game, player, poIndex);
                        ButtonHelper.addReaction(event, false, false, null, "");
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3)
                                .replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                                // Helper.resolveSOScoringQueue(game, event);
                            }
                        }
                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                        message = "Wants to score a PO but has people ahead of them in iniative order who need to resolve first. They have been queued and will automatically score their PO when everyone ahead of them is clear. ";
                        if (!game.isFowMode()) {
                            message = message + player2.getRepresentation(true, true)
                                + " is the one the game is currently waiting on";
                        }
                        String poID = buttonID.replace(Constants.PO_SCORING, "");
                        try {
                            int poIndex = Integer.parseInt(poID);
                            game.setStoredValue(player.getFaction() + "queuedPOScore", "" + poIndex);
                        } catch (Exception e) {
                            BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                            event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.")
                                .queue();
                        }
                        game.setStoredValue(key2,
                            game.getStoredValue(key2) + player.getFaction() + "*");
                        ButtonHelper.addReaction(event, false, false, message, "");
                        break;
                    }
                }

            } else {
                String poID = buttonID.replace(Constants.PO_SCORING, "");
                try {
                    int poIndex = Integer.parseInt(poID);
                    ScorePublic.scorePO(event, privateChannel, game, player, poIndex);
                    ButtonHelper.addReaction(event, false, false, null, "");
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                    event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.")
                        .queue();
                }
            }
        } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
            String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
            if (!player.getSCs().contains(3)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Only the player who played Politics may assign Speaker.");
                return;
            }
            if (game != null) {
                for (Player player_ : game.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        game.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: "
                            + player_.getRepresentation(false, true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                        if (game.isFowMode() && player != player_) {
                            MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                        }
                        if (!game.isFowMode()) {
                            ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                        }
                    }
                }
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("assignSpeaker_")) {
            String faction = StringUtils.substringAfter(buttonID, "assignSpeaker_");
            if (game != null && !game.isFowMode()) {
                for (Player player_ : game.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        game.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: "
                            + player_.getRepresentation(false, true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                        if (!game.isFowMode()) {
                            ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                        }
                    }
                }
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("reveal_stage_")) {
            String lastC = buttonID.replace("reveal_stage_", "");
            if (!game.isRedTapeMode()) {
                if ("2".equalsIgnoreCase(lastC)) {
                    new RevealStage2().revealS2(event, event.getChannel());
                } else if ("2x2".equalsIgnoreCase(lastC)) {
                    new RevealStage2().revealTwoStage2(event, event.getChannel());
                } else {
                    new RevealStage1().revealS1(event, event.getChannel());
                }
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "In Red Tape, no objective is revealed at this stage");
                int playersWithSCs = 0;
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getSCs() != null && player2.getSCs().size() > 0 && !player2.getSCs().contains(0)) {
                        playersWithSCs++;
                    }
                }
                if (playersWithSCs > 0) {
                    new Cleanup().runStatusCleanup(game);
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                        game.getPing() + " **Status Cleanup Run!**");
                }
            }

            ButtonHelper.startStatusHomework(event, game);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("exhaustRelic_")) {
            String relic = buttonID.replace("exhaustRelic_", "");
            if (player.hasRelicReady(relic)) {
                player.addExhaustedRelic(relic);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    player.getFactionEmoji() + " exhausted " + Mapper.getRelic(relic).getName());
                ButtonHelper.deleteTheOneButton(event);
                if ("absol_luxarchtreatise".equalsIgnoreCase(relic)) {
                    game.setStoredValue("absolLux", "true");
                }
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    player.getFactionEmoji() + " doesn't have an unexhausted " + relic);
            }

        } else if (buttonID.startsWith("scepterE_follow_") || buttonID.startsWith("mahactA_follow_")) {
            boolean setstatus = true;
            int scnum = 1;
            try {
                scnum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
            } catch (NumberFormatException e) {
                try {
                    scnum = Integer.parseInt(lastchar);
                } catch (NumberFormatException e2) {
                    setstatus = false;
                }
            }
            if (setstatus) {
                if (!player.getFollowedSCs().contains(scnum)) {
                    ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, game, event);
                }
                player.addFollowedSC(scnum, event);
            }
            MessageChannel channel = ButtonHelper.getSCFollowChannel(game, player, scnum);
            if (buttonID.contains("mahact")) {
                MessageHelper.sendMessageToChannel(channel,
                    ident + " exhausted " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " Agent, to follow " + Helper.getSCName(scnum, game));
                Leader playerLeader = player.unsafeGetLeader("mahactagent");
                if (playerLeader != null) {
                    playerLeader.setExhausted(true);
                    for (Player p2 : game.getPlayers().values()) {
                        for (Integer sc2 : p2.getSCs()) {
                            if (sc2 == scnum) {
                                List<Button> buttonsToRemoveCC = new ArrayList<>();
                                String finChecker = "FFCC_" + player.getFaction() + "_";
                                for (Tile tile : ButtonHelper.getTilesWithYourCC(p2, game, event)) {
                                    buttonsToRemoveCC.add(Button.success(
                                        finChecker + "removeCCFromBoard_mahactAgent" + p2.getFaction() + "_"
                                            + tile.getPosition(),
                                        tile.getRepresentationForButtons(game, player)));
                                }
                                MessageHelper.sendMessageToChannelWithButtons(channel,
                                    trueIdentity + " Use buttons to remove a CC", buttonsToRemoveCC);
                            }
                        }
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(channel,
                    trueIdentity + " exhausted Scepter of Silly Spelling to follow " + Helper.getSCName(scnum, game));
                player.addExhaustedRelic("emelpar");
            }
            Emoji emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());

            if (channel instanceof ThreadChannel) {
                game.getActionsChannel().addReactionById(channel.getId(), emojiToUse).queue();
            } else {
                MessageHelper.sendMessageToChannel(channel,
                    "Hey, something went wrong leaving a react, please just hit the no follow button on the strategy card to do so.");
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("spendAStratCC")) {
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
            }
            String message = deductCC(player, event);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("sc_follow_")) {

            int scNum = 1;
            boolean setStatus = true;
            try {
                scNum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
            } catch (NumberFormatException e) {
                try {
                    scNum = Integer.parseInt(lastchar);
                } catch (NumberFormatException e2) {
                    setStatus = false;
                }
            }
            if (nullable != null && player.getSCs().contains(scNum)) {
                String message = player.getRepresentation()
                    + " you currently hold this strategy card and therefore should not be spending a CC here.\nYou may override this protection by running `/player stats strategy_cc:-1`.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                return;
            }
            boolean used = addUsedSCPlayer(messageID, game, player, event, "");
            if (!used) {
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed " + Helper.getSCName(scNum, game));
                }
                String message = deductCC(player, event);

                if (setStatus) {
                    if (!player.getFollowedSCs().contains(scNum)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                    }
                    player.addFollowedSC(scNum, event);
                }
                ButtonHelper.addReaction(event, false, false, message, "");
            }
        } else if (buttonID.startsWith("sc_no_follow_")) {
            int scNum = 1;
            boolean setStatus = true;
            try {
                scNum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
            } catch (NumberFormatException e) {
                try {
                    scNum = Integer.parseInt(lastchar);
                } catch (NumberFormatException e2) {
                    setStatus = false;
                }
            }
            if (setStatus) {
                player.addFollowedSC(scNum, event);
                if (scNum == 7) {
                    ButtonHelper.postTechSummary(game);
                }
            }
            ButtonHelper.addReaction(event, false, false, "Not Following", "");
            Set<Player> players = playerUsedSC.get(messageID);
            if (players == null) {
                players = new HashSet<>();
            }
            players.remove(player);
            playerUsedSC.put(messageID, players);

            StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNum).orElse(null);
            if (scModel == null) {
                return;
            }
            if ("pok8imperial".equals(scModel.getBotSCAutomationID())) {// HANDLE SO QUEUEING
                String key = "factionsThatAreNotDiscardingSOs";
                String key2 = "queueToDrawSOs";
                String key3 = "potentialBlockers";
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }
                if (!game.getStoredValue(key).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
                }
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                    Helper.resolveQueue(game);
                }
            }
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            String bID = buttonID.replace("movedNExplored_", "");
            boolean dsdihmy = false;
            if (bID.startsWith("dsdihmy_")) {
                bID = bID.replace("dsdihmy_", "");
                dsdihmy = true;
            }
            String[] info = bID.split("_");
            Tile tile = game.getTileFromPlanet(info[1]);
            new ExpPlanet().explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2], player, false, game, 1, false);
            if (dsdihmy) {
                player.exhaustPlanet(info[1]);
                MessageHelper.sendMessageToChannel(mainGameChannel,
                    info[1] + " was exhausted by Impressment Programs!");
            }
            if (tile != null && player.getTechs().contains("dsdihmy")) {
                List<Button> produce = new ArrayList<>();
                String pos = tile.getPosition();
                produce.add(Button.primary("dsdihmy_" + pos, "Produce (1) Units"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation() + " You explored a planet and due to Impressment Programs you may produce 1 ship in the system.",
                    produce);
            }
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        } else if (buttonID.startsWith("resolveExp_Look_")) {
            String deckType = buttonID.replace("resolveExp_Look_", "");
            ButtonHelperFactionSpecific.resolveExpLook(player, game, event, deckType);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("discardExploreTop_")) {
            String deckType = buttonID.replace("discardExploreTop_", "");
            ButtonHelperFactionSpecific.resolveExpDiscard(player, game, event, deckType);
        } else if (buttonID.startsWith("relic_look_top")) {
            List<String> relicDeck = game.getAllRelics();
            if (relicDeck.isEmpty()) {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "Relic deck is empty");
                return;
            }
            String relicID = relicDeck.get(0);
            RelicModel relicModel = Mapper.getRelic(relicID);
            String rsb = "**Relic - Look at Top**\n" + player.getRepresentation() + "\n"
                + relicModel.getSimpleRepresentation();
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, rsb);
            ButtonHelper.addReaction(event, true, false, "Looked at top of the Relic deck.", "");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("explore_look_All")) {
            List<String> order = List.of("cultural", "industrial", "hazardous");
            for (String type : order) {
                List<String> deck = game.getExploreDeck(type);
                List<String> discard = game.getExploreDiscard(type);

                String traitNameWithEmoji = Emojis.getEmojiFromDiscord(type) + type;
                if (deck.isEmpty() && discard.isEmpty()) {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
                    continue;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
                ExploreModel exp = Mapper.getExplore(deck.get(0));
                sb.append(exp.textRepresentation());
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());
            }

            String playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Top of Cultural, Industrial, and Hazardous explore decks has been set to "
                    + playerFactionNameWithEmoji + " Cards info thread.");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("distant_suns_")) {
            ButtonHelperAbilities.distantSuns(buttonID, event, game, player);
        } else if (buttonID.startsWith("deep_mining_")) {
            ButtonHelperAbilities.deepMining(buttonID, event, game, player);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {// "autoAssignGroundHits_"
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, game, buttonID.split("_")[1],
                Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("autoAssignSpaceHits_")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game,
                    game.getTileByPosition(buttonID.split("_")[1]),
                    Integer.parseInt(buttonID.split("_")[2]), event, false));
        } else if (buttonID.startsWith("autoAssignSpaceCannonOffenceHits_")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game,
                    game.getTileByPosition(buttonID.split("_")[1]),
                    Integer.parseInt(buttonID.split("_")[2]), event, false, true));
        } else if (buttonID.startsWith("cancelSpaceHits_")) {
            Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
            int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
            Player opponent = player;
            String msg = "\n" + opponent.getRepresentation(true, true) + " cancelled 1 hit with an ability";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(
                Button.danger("getDamageButtons_" + tile.getPosition() + "_spacecombat", "Manually Assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Button.secondary("cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = "You may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit" + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n"
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true);
            event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
        } else if (buttonID.startsWith("cancelGroundHits_")) {
            Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
            int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
            Player opponent = player;
            String msg = "\n" + opponent.getRepresentation(true, true) + " cancelled 1 hit with an ability";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker + "autoAssignGroundHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(
                Button.danger("getDamageButtons_" + tile.getPosition() + "_groundcombat", "Manually Assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Button.secondary("cancelGroundHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = player.getRepresentation() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
            event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
        } else if (buttonID.startsWith("cancelPdsOffenseHits_")) {
            Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
            int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
            Player opponent = player;
            String msg = "\n" + opponent.getRepresentation(true, true) + " cancelled 1 hit with an ability";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Button.danger("getDamageButtons_" + tile.getPosition() + "_pds", "Manually Assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Button.secondary("cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = "You may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit" + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n"
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true, true);
            event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
        } else if (buttonID.startsWith("cancelAFBHits_")) {
            Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
            int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
            Player opponent = player;
            String msg = "\n" + opponent.getRepresentation(true, true) + " cancelled 1 hit with an ability.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Button.danger("getDamageButtons_" + tile.getPosition() + "_afb", "Manually Assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Button.secondary("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = "You may automatically assign " + h + " AFB hit" + (h == 1 ? "" : "s") + ".";
            event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
        } else if (buttonID.startsWith("autoAssignAFBHits_")) {// "autoAssignGroundHits_"
            ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(player, game, buttonID.split("_")[1],
                Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("getPlagiarizeButtons")) {
            game.setComponentAction(true);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Select the tech you want",
                ButtonHelperActionCards.getPlagiarizeButtons(game, player));
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
            Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(doneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                "Click the names of the planets you wish to exhaust to pay the 5 influence", buttons);
            ButtonHelper.deleteMessage(event);
            // "saarHeroResolution_"
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
            String sc = buttonID.replace("increaseTGonSC_", "");
            int scNum = Integer.parseInt(sc);
            Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
            int tgCount = scTradeGoods.get(scNum);
            game.setScTradeGood(scNum, (tgCount + 1));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Added 1TG to " + Helper.getSCName(scNum, game) + ". There are now " + (tgCount + 1) + " TG" + (tgCount == 0 ? "" : "s") + " on it.");
        } else if (buttonID.startsWith("strategicAction_")) {
            int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
            SCPlay.playSC(event, scNum, game, mainGameChannel, player);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("resolve_explore_")) {
            String bID = buttonID.replace("resolve_explore_", "");
            String[] info = bID.split("_");
            String cardID = info[0];
            String planetName = info[1];
            Tile tile = game.getTileFromPlanet(planetName);
            String tileName = tile == null ? "no tile" : tile.getPosition();
            String messageText = player.getRepresentation() + " explored " + "Planet "
                + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tileName + ")*:";
            if (buttonID.contains("_distantSuns")) {
                messageText = player.getFactionEmoji() + " chose to resolve: ";
            }
            ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("refresh_")) {
            String planetName = buttonID.split("_")[1];
            Player p2 = player;
            if (StringUtils.countMatches(buttonID, "_") > 1) {
                String faction = buttonID.split("_")[2];
                p2 = game.getPlayerFromColorOrFaction(faction);
            }

            PlanetRefresh.doAction(p2, planetName, game);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (totalVotesSoFar.contains("Readied")) {
                totalVotesSoFar += ", "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            } else {
                totalVotesSoFar = ident + " Readied "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("assignDamage_")) {// removeThisTypeOfUnit_
            ButtonHelperModifyUnits.assignDamage(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("removeThisTypeOfUnit_")) {
            ButtonHelperModifyUnits.removeThisTypeOfUnit(buttonID, event, game, player);
        } else if (buttonID.startsWith("repairDamage_")) {
            ButtonHelperModifyUnits.repairDamage(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("assCannonNDihmohn_")) {
            ButtonHelperModifyUnits.resolveAssaultCannonNDihmohnCommander(buttonID, event, player, game);
        } else if (buttonID.startsWith("refreshViewOfSystem_")) {
            String rest = buttonID.replace("refreshViewOfSystem_", "");
            String pos = rest.split("_")[0];
            Player p1 = game.getPlayerFromColorOrFaction(rest.split("_")[1]);
            Player p2 = game.getPlayerFromColorOrFaction(rest.split("_")[2]);
            String groundOrSpace = rest.split("_")[3];
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, 0, pos, event);
            MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
            List<Button> buttons = StartCombat.getGeneralCombatButtons(game, pos, p1, p2, groundOrSpace, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
        } else if (buttonID.startsWith("getDamageButtons_")) {// "repealLaw_"
            if (buttonID.contains("deleteThis")) {
                buttonID = buttonID.replace("deleteThis", "");
                ButtonHelper.deleteMessage(event);
            }
            String pos = buttonID.split("_")[1];
            String assignType = "combat";
            if (buttonID.split("_").length > 2) {
                assignType = buttonID.split("_")[2];
            }
            List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game,
                game.getTileByPosition(pos), assignType);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                trueIdentity + " Use buttons to resolve", buttons);
        } else if (buttonID.startsWith("repealLaw_")) {// "repealLaw_"
            ButtonHelperActionCards.repealLaw(game, player, buttonID, event);
        } else if (buttonID.startsWith("getRepairButtons_")) {
            String pos = buttonID.replace("getRepairButtons_", "");
            List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                trueIdentity + " Use buttons to resolve", buttons);
        } else if (buttonID.startsWith("codexCardPick_")) {
            ButtonHelper.deleteTheOneButton(event);
            ButtonHelper.pickACardFromDiscardStep1(game, player);

        } else if (buttonID.startsWith("pickFromDiscard_")) {
            ButtonHelper.pickACardFromDiscardStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("cymiaeHeroStep2_")) {
            ButtonHelperHeroes.resolveCymiaeHeroStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("cymiaeHeroStep3_")) {
            ButtonHelperHeroes.resolveCymiaeHeroStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("cymiaeHeroAutonetic")) {
            List<Button> buttons = new ArrayList<>();
            String msg2 = player.getFactionEmoji() + " is choosing to resolve their Autonetic Memory ability";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
            buttons.add(Button.success("autoneticMemoryStep3a", "Pick A Card From the Discard"));
            buttons.add(Button.primary("autoneticMemoryStep3b", "Drop 1 infantry"));
            String msg = player.getRepresentation(true, true)
                + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            buttons = new ArrayList<>();
            buttons.add(
                Button.success("cymiaeHeroStep1_" + (game.getRealPlayers().size()), "Resolve Cymiae Hero"));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " resolve hero after doing Autonetic Memory steps", buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("cymiaeHeroStep1_")) {
            ButtonHelperHeroes.resolveCymiaeHeroStart(buttonID, event, game, player);
        } else if (buttonID.startsWith("autoneticMemoryStep2_")) {
            ButtonHelperAbilities.autoneticMemoryStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryDecline_")) {
            ButtonHelperAbilities.autoneticMemoryDecline(game, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryStep3")) {
            if (buttonID.contains("autoneticMemoryStep3a")) {
                ButtonHelperAbilities.autoneticMemoryStep3a(game, player, event);
            } else {
                ButtonHelperAbilities.autoneticMemoryStep3b(game, player, event);
            }
        } else if (buttonID.startsWith("assignHits_")) {
            ButtonHelperModifyUnits.assignHits(buttonID, event, game, player, ident, buttonLabel);
        } else if (buttonID.startsWith("seedySpace_")) {
            ButtonHelper.resolveSeedySpace(game, buttonID, player, event);
        } else if (buttonID.startsWith("startDevotion_")) {
            ButtonHelperModifyUnits.startDevotion(player, game, event, buttonID);
        } else if (buttonID.startsWith("purgeTech_")) {
            ButtonHelperHeroes.purgeTech(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolveDevote_")) {
            ButtonHelperModifyUnits.resolveDevote(player, game, event, buttonID);
        } else if (buttonID.startsWith("prophetsTears_")) {
            player.addExhaustedRelic("prophetstears");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " Chose to exhaust The Prophets Tears");
            if (buttonID.contains("AC")) {
                String message;
                if (player.hasAbility("scheming")) {
                    game.drawActionCard(player.getUserID());
                    game.drawActionCard(player.getUserID());
                    message = player.getFactionEmoji()
                        + " Drew 2 ACs With Scheming. Please Discard 1 AC with the blue buttons";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentation(true, true) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(game, player, false));
                } else if (player.hasAbility("autonetic_memory")) {
                    ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
                    message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                } else {
                    game.drawActionCard(player.getUserID());
                    message = player.getFactionEmoji() + " Drew 1 AC";
                    ACInfo.sendActionCardInfo(game, player, event);
                }
                if (player.getLeaderIDs().contains("yssarilcommander")
                    && !player.hasLeaderUnlocked("yssarilcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
                }

                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                ButtonHelper.checkACLimit(game, event, player);
                ButtonHelper.deleteTheOneButton(event);
            } else {
                String msg = " exhausted the Prophet's Tears";
                String exhaustedMessage = event.getMessage().getContentRaw();
                List<ActionRow> actionRow2 = new ArrayList<>();
                for (ActionRow row : event.getMessage().getActionRows()) {
                    List<ItemComponent> buttonRow = row.getComponents();
                    int buttonIndex = buttonRow.indexOf(event.getButton());
                    if (buttonIndex > -1) {
                        buttonRow.remove(buttonIndex);
                    }
                    if (buttonRow.size() > 0) {
                        actionRow2.add(ActionRow.of(buttonRow));
                    }
                }
                if (!exhaustedMessage.contains("Click the names")) {
                    exhaustedMessage = exhaustedMessage + ", " + msg;
                } else {
                    exhaustedMessage = ident + msg;
                }
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            }
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
            String votes = buttonID.replace("refreshVotes_", "");
            List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, game);
            Button concludeRefreshing = Button.danger(finsFactionCheckerPrefix + "votes_" + votes,
                "Done readying planets.");
            voteActionRow.add(concludeRefreshing);
            String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("getAllTechOfType_")) {
            String techType = buttonID.replace("getAllTechOfType_", "");
            boolean noPay = false;
            if (techType.contains("_")) {
                techType = techType.split("_")[0];
                noPay = true;
            }
            List<TechnologyModel> techs = Helper.getAllTechOfAType(game, techType, player);
            List<Button> buttons = Helper.getTechButtons(techs, techType, player);
            if (noPay) {
                buttons = Helper.getTechButtons(techs, player, "nekro");
            }

            if (game.isComponentAction()) {
                buttons.add(Button.secondary("acquireATech", "Get Tech of a Different Type"));
            } else {
                buttons.add(Button.secondary("acquireATechWithSC", "Get Tech of a Different Type"));
            }

            String message = player.getRepresentation() + " Use the buttons to get the tech you want";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("getTech_")) {
            ButtonHelper.getTech(game, player, event, buttonID);
        } else if (buttonID.startsWith("riftUnit_")) {
            ButtonHelper.riftUnitButton(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("getRiftButtons_")) {
            Tile tile = game.getTileByPosition(buttonID.replace("getRiftButtons_", ""));
            MessageChannel channel = player.getCorrectChannel();
            String msg = ident + " use buttons to rift units";
            MessageHelper.sendMessageToChannel(channel, player.getFactionEmoji() + " is rifting some units");
            MessageHelper.sendMessageToChannelWithButtons(channel, msg,
                ButtonHelper.getButtonsForRiftingUnitsInSystem(player, game, tile));
        } else if (buttonID.startsWith("riftAllUnits_")) {
            ButtonHelper.riftAllUnitsButton(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("cabalVortextCapture_")) {
            ButtonHelperFactionSpecific.resolveVortexCapture(buttonID, player, game, event);
        } else if (buttonID.startsWith("takeAC_")) {
            ButtonHelperFactionSpecific.mageon(buttonID, event, game, player, trueIdentity);
        } else if (buttonID.startsWith("moult_")) {
            ButtonHelperAbilities.resolveMoult(buttonID, event, game, player);
        } else if (buttonID.startsWith("spend_")) {
            String planetName = buttonID.split("_")[1];
            String whatIsItFor = "both";
            if (buttonID.split("_").length > 2) {
                whatIsItFor = buttonID.split("_")[2];
            }
            PlanetExhaust.doAction(player, planetName, game);
            player.addSpentThing(planetName);

            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
            if (uH != null) {
                if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                    Tile tile = game.getTileFromPlanet(planetName);
                    String msg = player.getRepresentation() + " added 1 infantry to " + planetName
                        + " due to the arcane citadel";
                    new AddUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planetName, game);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                }
            }
            if (whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")) {
                String planet = planetName;
                if ((Mapper.getPlanet(planet).getTechSpecialties() != null
                    && Mapper.getPlanet(planet).getTechSpecialties().size() > 0)
                    || ButtonHelper.checkForTechSkips(game, planet)) {
                    String msg = player.getRepresentation()
                        + " due to your ancient knowledge ability, you may be eligible to receive a tech here if you exhausted this planet ("
                        + planet
                        + ") for its tech skip";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.primary("gain_1_comms", "Gain 1 Commodity")
                        .withEmoji(Emoji.fromFormatted(Emojis.comm)));
                    buttons.add(Button.danger("deleteButtons", "Didn't use it for tech speciality"));
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji()
                            + " may have the opportunity to gain a comm from their ancient knowledge ability due to exhausting a tech skip planet");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg,
                        buttons);
                }
            }
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else if (buttonID.startsWith("finishTransaction_")) {
            String player2Color = buttonID.split("_")[1];
            Player player2 = game.getPlayerFromColorOrFaction(player2Color);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAbilities.pillageCheck(player2, game);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("demandSomething_")) {
            String player2Color = buttonID.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(player2Color);
            if (p2 != null) {
                List<Button> buttons = ButtonHelper.getStuffToTransButtonsOld(game, p2, player);
                String message = p2.getRepresentation()
                    + " you have been given something on the condition that you give something in return. Hopefully the player explained what. If you don't hand it over, please return what they sent. Use buttons to send something to "
                    + ButtonHelper.getIdentOrColor(player, game);
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, buttons);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("sabotage_")) {
            String typeNName = buttonID.replace("sabotage_", "");
            String type = typeNName.substring(0, typeNName.indexOf("_"));
            String acName = typeNName.replace(type + "_", "");
            String message = "Cancelling the AC \"" + acName + "\" using ";
            Integer count = game.getAllActionCardsSabod().get(acName);
            if (count == null) {
                game.setSpecificActionCardSaboCount(acName, 1);
            } else {
                game.setSpecificActionCardSaboCount(acName, 1 + count);
            }
            if (game.getMessageIDsForSabo().contains(event.getMessageId())) {
                game.removeMessageIDForSabo(event.getMessageId());
            }
            boolean sendReact = true;
            if ("empy".equalsIgnoreCase(type)) {
                message = message + "a Watcher mech! The Watcher should be removed now by the owner.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Remove the watcher",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "mech"));
                ButtonHelper.deleteMessage(event);
            } else if ("xxcha".equalsIgnoreCase(type)) {
                message = message
                    + "the \"Instinct Training\" tech! The tech has been exhausted and a strategy CC removed.";
                if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                    player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                    if (player.getStrategicCC() > 0) {
                        player.setStrategicCC(player.getStrategicCC() - 1);
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                    }
                    ButtonHelper.deleteMessage(event);
                } else {
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Someone clicked the Instinct Training button but did not have the tech.");
                }
            } else if ("ac".equalsIgnoreCase(type)) {
                message = message + "A Sabotage!";
                boolean hasSabo = false;
                String saboID = "3";
                for (String AC : player.getActionCards().keySet()) {
                    if (AC.contains("sabo")) {
                        hasSabo = true;
                        saboID = "" + player.getActionCards().get(AC);
                        break;
                    }
                }
                if (hasSabo) {
                    PlayAC.playAC(event, game, player, saboID, game.getActionsChannel());
                } else {
                    message = "Tried to play a Sabo but found none in hand.";
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        player.getRepresentation()
                            + " You clicked the AC Sabo button but did not have a Sabotage in hand.");
                }
            }

            if (acName.contains("Rider") || acName.contains("Sanction")) {
                AgendaHelper.reverseRider("reverse_" + acName, event, game, player, ident);
                // MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Reversed
                // the rider "+ acName);
            }
            if (sendReact) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    message + "\n" + game.getPing());
            }
        } else if (buttonID.startsWith("reduceTG_")) {
            int tgLoss = Integer.parseInt(buttonID.split("_")[1]);

            String whatIsItFor = "both";
            if (buttonID.split("_").length > 2) {
                whatIsItFor = buttonID.split("_")[2];
            }
            if (tgLoss > player.getTg()) {
                String message = "You don't have " + tgLoss + " TG" + (tgLoss == 1 ? "" : "s") + ". No change made.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            } else {
                player.setTg(player.getTg() - tgLoss);
                player.increaseTgsSpentThisWindow(tgLoss);
            }
            if (tgLoss > player.getTg()) {
                ButtonHelper.deleteTheOneButton(event);
            }
            String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
            event.getMessage().editMessage(editedMessage).queue();
        } else if (buttonID.startsWith("reduceComm_")) {
            int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
            String whatIsItFor = "both";
            if (buttonID.split("_").length > 2) {
                whatIsItFor = buttonID.split("_")[2];
            }
            String message = ident + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
                + (player.getCommodities() - tgLoss) + ")";

            if (tgLoss > player.getCommodities()) {
                message = "You don't have " + tgLoss + " comm" + (tgLoss == 1 ? "" : "s") + ". No change made.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            } else {
                player.setCommodities(player.getCommodities() - tgLoss);
                player.addSpentThing(message);
            }
            String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
            Leader playerLeader = player.getLeader("keleresagent").orElse(null);
            if (playerLeader != null && !playerLeader.isExhausted()) {
                playerLeader.setExhausted(true);
                String messageText = player.getRepresentation() +
                    " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), messageText);

            }
            event.getMessage().editMessage(editedMessage).queue();
        } else if (buttonID.startsWith("lanefirAgentRes_")) {
            ButtonHelperAgents.resolveLanefirAgent(player, game, event, buttonID);
        } else if (buttonID.startsWith("absolsdn_")) {
            ButtonHelper.resolveAbsolScanlink(player, game, event, buttonID);
        } else if (buttonID.startsWith("resFrontier_")) {
            buttonID = buttonID.replace("resFrontier_", "");
            String[] stuff = buttonID.split("_");
            String cardChosen = stuff[0];
            String pos = stuff[1];
            String cardRefused = stuff[2];
            game.addExplore(cardRefused);
            new ExpFrontier().expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player,
                cardChosen);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("finishComponentAction_")) {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
            ButtonHelper.deleteMessage(event);
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
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.secondary(buttonID.replace("bombardConfirm_", ""), "Roll Bombardment"));
            String message = player.getRepresentation(true, true)
                + " please declare what units are bombarding what planet before hitting this button"
                + " (e.g. if you have two dreadnoughts and are splitting their bombardment across two planets, specify which planet the first one is hitting)."
                + " The bot does not track this.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        } else if (buttonID.startsWith("combatRoll_")) {
            ButtonHelper.resolveCombatRoll(player, game, event, buttonID);
            if (buttonID.contains("bombard")) {
                ButtonHelper.deleteTheOneButton(event);
            }
        } else if (buttonID.startsWith("automateGroundCombat_")) {
            String faction1 = buttonID.split("_")[1];
            String faction2 = buttonID.split("_")[2];
            Player p1 = game.getPlayerFromColorOrFaction(faction1);
            Player p2 = game.getPlayerFromColorOrFaction(faction2);
            Player opponent = null;
            String planet = buttonID.split("_")[3];
            String confirmed = buttonID.split("_")[4];
            if (player != p1 && player != p2) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "This button is only for combat participants");
                return;
            }
            if (player == p2) {
                opponent = p1;
            } else {
                opponent = p2;
            }
            ButtonHelper.deleteTheOneButton(event);
            if (opponent == null || opponent.isDummy() || confirmed.equalsIgnoreCase("confirmed")) {
                ButtonHelperModifyUnits.autoMateGroundCombat(p1, p2, planet, game, event);
            } else if (p1 != null && p2 != null) {
                Button automate = Button.success(opponent.getFinsFactionCheckerPrefix() + "automateGroundCombat_"
                    + p1.getFaction() + "_" + p2.getFaction() + "_" + planet + "_confirmed", "Automate Combat");
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(),
                    opponent.getRepresentation() + " Your opponent has voted to automate the entire combat. Press to confirm confirm",
                    automate);
            }
        } else if (buttonID.startsWith("transitDiodes_")) {
            ButtonHelper.resolveTransitDiodesStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("novaSeed_")) {
            new NovaSeed().secondHalfOfNovaSeed(player, event, game.getTileByPosition(buttonID.split("_")[1]),
                game);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("celestialImpact_")) {
            new ZelianHero().secondHalfOfCelestialImpact(player, event,
                game.getTileByPosition(buttonID.split("_")[1]), game);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("echoPlaceFrontier_")) {
            ButtonHelper.resolveEchoPlaceFrontier(game, player, event, buttonID);
        } else if (buttonID.startsWith("forceAbstainForPlayer_")) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Player was forcefully abstained");
            String faction = buttonID.replace("forceAbstainForPlayer_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            AgendaHelper.resolvingAnAgendaVote("resolveAgendaVote_0", event, game, p2);
        } else if (buttonID.startsWith("fixerVotes_")) {
            String voteMessage = "Thank you for specifying, please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes - 9, votes);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("useRelic_")) {
            String relic = buttonID.replace("useRelic_", "");
            ButtonHelper.deleteTheOneButton(event);
            if ("boon".equals(relic)) {// Sarween Tools
                player.addSpentThing("boon");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                event.getMessage().editMessage(exhaustedMessage).queue();
            }
        } else if (buttonID.startsWith("useTech_")) {
            String tech = buttonID.replace("useTech_", "");
            TechnologyModel techModel = Mapper.getTech(tech);
            if (!tech.equalsIgnoreCase("st")) {
                String useMessage = player.getRepresentation() + " used tech: " + techModel.getRepresentation(false);
                if (game.isShowFullComponentTextEmbeds()) {
                    MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), useMessage,
                        techModel.getRepresentationEmbed());
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), useMessage);
                }
            }
            switch (tech) {
                case "st" -> { // Sarween Tools
                    player.addSpentThing("sarween");
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                    ButtonHelper.deleteTheOneButton(event, event.getButton().getId(), false);
                    event.getMessage().editMessage(exhaustedMessage).queue();
                }
                case "absol_st" -> { // Absol's Sarween Tools
                    player.addSpentThing("absol_sarween");
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                    ButtonHelper.deleteTheOneButton(event, event.getButton().getId(), false);
                    event.getMessage().editMessage(exhaustedMessage).queue();
                }
                case "absol_pa" -> { // Absol's Psychoarcheology
                    List<Button> absolPAButtons = new ArrayList<>();
                    absolPAButtons.add(Button.primary("getDiscardButtonsACs", "Discard")
                        .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                    for (String planetID : player.getReadiedPlanets()) {
                        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetID, game);
                        if (planet != null && planet.getOriginalPlanetType() != null) {
                            List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
                            absolPAButtons.addAll(planetButtons);
                        }
                    }
                    ButtonHelper.deleteTheOneButton(event);
                    MessageHelper
                        .sendMessageToChannelWithButtons(player.getCorrectChannel(),
                            player.getRepresentation(true, true)
                                + " use buttons to discard 1 AC and explore a readied planet",
                            absolPAButtons);
                }
            }
        } else if (buttonID.startsWith("absolX89Nuke_")) {
            ButtonHelper.deleteMessage(event);
            String planet = buttonID.split("_")[1];
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFaction() + " used X-89 Bacterial Weapon to remove all ground forces on " + planet);
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Map<UnitKey, Integer> units = new HashMap<>();
            units.putAll(uH.getUnits());
            for (UnitKey unit : units.keySet()) {
                if (unit.getUnitType() == UnitType.Mech || unit.getUnitType() == UnitType.Infantry) {
                    uH.removeUnit(unit, units.get(unit));
                }
            }
        } else if (buttonID.startsWith("exhaustTech_")) {
            String tech = buttonID.replace("exhaustTech_", "");
            TechExhaust.exhaustTechAndResolve(event, game, player, tech);
        } else if (buttonID.startsWith("planetOutcomes_")) {
            String factionOrColor = buttonID.substring(buttonID.indexOf("_") + 1);
            Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
            String voteMessage = "Chose to vote for one of " + factionOrColor
                + "'s planets. Click buttons for which outcome to vote for.";
            List<Button> outcomeActionRow;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, game, "outcome", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("indoctrinate_")) {
            ButtonHelperAbilities.resolveFollowUpIndoctrinationQuestion(player, game, buttonID, event);
        } else if (buttonID.startsWith("assimilate_")) {
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(buttonID.split("_")[1], game);
            ButtonHelperModifyUnits.infiltratePlanet(player, game, uH, event);
            ButtonHelper.deleteTheOneButton(event);
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
            buttonID = buttonID.replace("tiedPlanets_", "");
            buttonID = buttonID.replace("resolveAgendaVote_outcomeTie*_", "");
            String factionOrColor = buttonID;
            Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
            String voteMessage = "Chose to break tie for one of " + factionOrColor
                + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, game,
                "resolveAgendaVote_outcomeTie*", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("planetRider_")) {
            buttonID = buttonID.replace("planetRider_", "");
            String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
            Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
            String voteMessage = "Chose to Rider for one of " + factionOrColor
                + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow;
            buttonID = buttonID.replace(factionOrColor + "_", "");
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, game,
                finsFactionCheckerPrefix, buttonID);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("distinguished_")) {
            String voteMessage = "Please select from the available buttons your total vote amount. If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtonsVersion2(votes, votes + 5);
            voteActionRow.add(Button.secondary("distinguishedReverse_" + votes, "Decrease Votes"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("distinguishedReverse_")) {
            String voteMessage = "Please select from the available buttons your total vote amount. If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtonsVersion2(votes - 5, votes);
            voteActionRow.add(Button.secondary("distinguishedReverse_" + (votes - 5), "Decrease Votes"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            ButtonHelper.deleteMessage(event);
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
            ButtonHelper.deleteTheOneButton(event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Select up to 2 ships and 2 ground forces to place in the space area",
                ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
        } else if (buttonID.startsWith("getReleaseButtons")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                trueIdentity
                    + " you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
                ButtonHelperFactionSpecific.getReleaseButtons(player, game));
        } else if (buttonID.startsWith("ghotiHeroIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game, event,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                trueIdentity + " select which unit you'd like to replace", buttons);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("glimmersHeroIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperHeroes.getUnitsToGlimmersHero(player, game, event,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                trueIdentity + " select which unit you'd like to duplicate", buttons);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("arboAgentIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game, event,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                trueIdentity + " select which unit you'd like to replace", buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("saarMechRes_")) {
            ButtonHelperFactionSpecific.placeSaarMech(player, game, event, buttonID);
        } else if (buttonID.startsWith("cymiaeCommanderRes_")) {
            ButtonHelperCommanders.cymiaeCommanderRes(player, game, event, buttonID);
        } else if (buttonID.startsWith("arboAgentPutShip_")) {
            ButtonHelperAgents.arboAgentPutShip(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("setAutoPassMedian_")) {
            String hours = buttonID.split("_")[1];
            int median = Integer.parseInt(hours);
            player.setAutoSaboPassMedian(median);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours");
            if (median > 0) {
                if (player.hasAbility("quash") || player.ownsPromissoryNote("rider")
                    || player.getPromissoryNotes().containsKey("riderm")
                    || player.hasAbility("radiance") || player.hasAbility("galactic_threat")
                    || player.hasAbility("conspirators")
                    || player.ownsPromissoryNote("riderx")
                    || player.ownsPromissoryNote("riderm") || player.ownsPromissoryNote("ridera")
                    || player.hasTechReady("gr")) {
                } else {
                    List<Button> buttons = new ArrayList<>();
                    String msg = player.getRepresentation()
                        + " The bot may also auto react for you when you have no whens/afters, using the same interval. Default for this is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                    buttons.add(Button.success("playerPrefDecision_true_agenda", "Turn on"));
                    buttons.add(Button.success("playerPrefDecision_false_agenda", "Turn off"));
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
                }
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("arboAgentOn_")) {
            String pos = buttonID.split("_")[1];
            String unit = buttonID.split("_")[2];
            List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(player, game, event,
                game.getTileByPosition(pos), unit);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                trueIdentity + " select which unit you'd like to place down", buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("glimmersHeroOn_")) {
            String pos = buttonID.split("_")[1];
            String unit = buttonID.split("_")[2];
            new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos), unit, game);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                ident + " chose to duplicate a " + unit + " in "
                    + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
            ButtonHelper.deleteMessage(event);

        } else if (buttonID.startsWith("resolveWithNoEffect")) {
            String voteMessage = "Resolving agenda with no effect. Click the buttons for next steps.";
            String agendaCount = game.getStoredValue("agendaCount");
            int aCount;
            if (agendaCount.isEmpty()) {
                aCount = 1;
            } else {
                aCount = Integer.parseInt(agendaCount) + 1;
            }
            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda #" + aCount);
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
                "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = Arrays.asList(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("outcome_")) {
            // AgendaHelper.offerVoteAmounts(buttonID, event, game, player, ident,
            // buttonLabel);
            if (game.getLaws() != null
                && (game.getLaws().containsKey("rep_govt") || game.getLaws().containsKey("absol_government"))) {
                player.resetSpentThings();
                player.addSpentThing("representative_1");
                boolean playerHasMr = CollectionUtils.containsAny(player.getPlanets(), Constants.MECATOLS);
                if (game.getLaws().containsKey("absol_government") && playerHasMr) {
                    player.addSpentThing("absolRexControlRepresentative_1");
                }
                String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
                String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome);
                game.setLatestOutcomeVotedFor(outcome);
                MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
                AgendaHelper.proceedToFinalizingVote(game, player, event);
            } else {
                AgendaHelper.exhaustPlanetsForVotingVersion2(buttonID, event, game, player);
            }
        } else if (buttonID.startsWith("votes_")) {
            AgendaHelper.exhaustPlanetsForVoting(buttonID, event, game, player, ident, buttonLabel,
                finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("dacxive_")) {
            String planet = buttonID.replace("dacxive_", "");
            new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)),
                "infantry " + planet, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), ident + " placed 1 infantry on "
                + Helper.getPlanetRepresentation(planet, game) + " via the tech Dacxive Animators");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("autoresolve_")) {
            String result = buttonID.substring(buttonID.indexOf("_") + 1);
            if (result.contains("manual")) {
                if (result.contains("committee")) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdentOrColor(player,
                        game)
                        + " has chosen to discard Committee Formation to choose the winner. Note that afters may be played before this occurs, and that confounding may still be played. You should probably wait and confirm no confounding before resolving");
                    boolean success = game.removeLaw(game.getLaws().get("committee"));
                    String message = game.getPing() + " please confirm no Confounding Legal Texts.";
                    Button noConfounding = Button.primary("generic_button_id_3", "Refuse Confounding Legal Text");
                    List<Button> buttons = List.of(noConfounding);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message, game, buttons, "shenanigans");
                    if (game.isACInDiscard("Confounding")) {
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Confounding was found in the discard pile, so you should be good to resolve");
                    }
                }
                String resMessage3 = "Please select the winner.";
                List<Button> deadlyActionRow3 = AgendaHelper.getAgendaButtons(null, game, "agendaResolution");
                deadlyActionRow3.add(Button.danger("resolveWithNoEffect", "Resolve with no result"));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3, deadlyActionRow3);
            }
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("deleteButtons")) {
            deleteButtons(event, buttonID, buttonLabel, game, player, mainGameChannel, trueIdentity);
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
            boolean used = addUsedSCPlayer(messageID, game, player, event, "");
            StrategyCardModel scModel = game.getStrategyCardModelByName("construction").orElse(null);
            boolean automationExists = scModel != null && scModel.usesAutomationForSCID("pok4construction");
            if (!used && scModel != null && !player.getFollowedSCs().contains(scModel.getInitiative())
                && automationExists && game.getPlayedSCs().contains(scModel.getInitiative())) {
                player.addFollowedSC(scModel.getInitiative(), event);
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed construction");
                }
                String message = deductCC(player, event);
                ButtonHelper.addReaction(event, false, false, message, "");
            }
            ButtonHelper.addReaction(event, false, false, "", "");
            String unit = buttonID.replace("construction_", "");
            String message = trueIdentity + " Click the name of the planet you wish to put your "
                + Emojis.getEmojiFromDiscord(unit) + " on for construction";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "place");
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }
        } else if (buttonID.startsWith("jrStructure_")) {
            String unit = buttonID.replace("jrStructure_", "");
            if (!"tg".equalsIgnoreCase(unit)) {
                String message = trueIdentity + " Click the name of the planet you wish to put your unit on";
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit,
                    "placeOneNDone_dontskip");
                if (!game.isFowMode()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                }
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    ident + " TGs increased by 1 (" + player.getTg() + "->" + (player.getTg() + 1) + ")");
                player.setTg(player.getTg() + 1);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
            }
            ButtonHelper.deleteMessage(event);// "resolveReverse_"
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
            ButtonHelper.deleteMessage(event);
            String planet = buttonID.split("_")[1];
            UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            plan.removeAllUnits(player.getColor());
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Removed all units on " + planet + " for " + player.getRepresentation());
            AddRemoveUnits.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
        } else if (buttonID.startsWith("winnuStructure_")) {
            String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
            String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)),
                unit + " " + planet, game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " Placed a " + unit + " on "
                    + Helper.getPlanetRepresentation(planet, game));

        } else if (buttonID.startsWith("produceOneUnitInTile_")) {
            buttonID = buttonID.replace("produceOneUnitInTile_", "");
            String type = buttonID.split("_")[1];
            String pos = buttonID.split("_")[0];
            List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
                "placeOneNDone_dontskip");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("yinagent_")) {
            ButtonHelperAgents.yinAgent(buttonID, event, game, player, ident, trueIdentity);
        } else if (buttonID.startsWith("resolveMaw")) {
            ButtonHelper.resolveMaw(game, player, event);
        } else if (buttonID.startsWith("resolveTwilightMirror")) {
            ButtonHelper.resolveTwilightMirror(game, player, event);
        } else if (buttonID.startsWith("playerPref_")) {
            ButtonHelper.resolvePlayerPref(player, event, buttonID, game);
        } else if (buttonID.startsWith("riskDirectHit_")) {
            ButtonHelper.resolveRiskDirectHit(game, player, event, buttonID);
        } else if (buttonID.startsWith("setPersonalAutoPingInterval_")) {
            int interval = Integer.parseInt(buttonID.split("_")[1]);
            player.setPersonalPingInterval(interval);
            Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
            for (Game game2 : mapList.values()) {
                for (Player player2 : game2.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                        player2.setPersonalPingInterval(interval);
                        GameSaveLoadManager.saveMap(game2, event);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set interval as " + interval + " hours");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("playerPrefDecision_")) {
            ButtonHelper.resolvePlayerPrefDecision(player, event, buttonID, game);
        } else if (buttonID.startsWith("resolveCrownOfE")) {
            ButtonHelper.resolveCrownOfE(game, player, event);
        } else if (buttonID.startsWith("yssarilAgentAsJr")) {
            ButtonHelperFactionSpecific.yssarilAgentAsJr(game, player, event);
        } else if (buttonID.startsWith("sarMechStep1_")) {
            ButtonHelper.resolveSARMechStep1(player, game, event, buttonID);
        } else if (buttonID.startsWith("sarMechStep2_")) {
            ButtonHelper.resolveSARMechStep2(player, game, event, buttonID);
        } else if (buttonID.startsWith("integratedBuild_")) {
            String planet = buttonID.split("_")[1];
            Tile tile = game.getTileFromPlanet(planet);
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            int resources = 0;
            if (uH instanceof Planet plan) {
                resources = plan.getResources();
            }
            List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "integrated" + planet,
                "place");
            String message = player.getRepresentation()
                + " Using Integrated Economy on " + Helper.getPlanetRepresentation(planet, game)
                + ". Use the buttons to produce units with a combined cost up to the planet (" + resources
                + ") resources.\n"
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                "Produce Units",
                buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("deployMykoSD_")) {
            ButtonHelperFactionSpecific.deployMykoSD(player, game, event, buttonID);
        } else if (buttonID.startsWith("ravenMigration")) {
            ButtonHelperCommanders.handleRavenCommander(context);
        } else if (buttonID.startsWith("jrResolution_")) {
            String faction2 = buttonID.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction2);
            if (p2 != null) {
                Button sdButton = Button.success("jrStructure_sd", "Place 1 space dock");
                sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                Button pdsButton = Button.success("jrStructure_pds", "Place 1 PDS");
                pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                Button tgButton = Button.success("jrStructure_tg", "Gain 1TG");
                List<Button> buttons = new ArrayList<>();
                buttons.add(sdButton);
                buttons.add(pdsButton);
                buttons.add(tgButton);
                String msg = p2.getRepresentation(true, true) + " Use buttons to decide what structure to build";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("colonialRedTarget_")) {
            AgendaHelper.resolveColonialRedTarget(game, buttonID, event);
        } else if (buttonID.startsWith("ruins_")) {
            ButtonHelper.resolveWarForgeRuins(game, buttonID, player, event);
        } else if (buttonID.startsWith("createGameChannels")) {
            CreateGameButton.decodeButtonMsg(event);
        } else if (buttonID.startsWith("yssarilHeroRejection_")) {
            String playerFaction = buttonID.replace("yssarilHeroRejection_", "");
            Player notYssaril = game.getPlayerFromColorOrFaction(playerFaction);
            if (notYssaril != null) {
                String message = notYssaril.getRepresentation(true, true)
                    + " Kyver, Blade and Key, the Yssaril hero, has rejected your offering and is forcing you to discard 3 random ACs. The ACs have been automatically discarded.";
                MessageHelper.sendMessageToChannel(notYssaril.getCardsInfoThread(), message);
                new DiscardACRandom().discardRandomAC(event, game, notYssaril, 3);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("yssarilHeroInitialOffering_")) {
            List<Button> acButtons = new ArrayList<>();
            buttonID = buttonID.replace("yssarilHeroInitialOffering_", "");
            String acID = buttonID.split("_")[0];
            String yssarilFaction = buttonID.split("_")[1];
            Player yssaril = game.getPlayerFromColorOrFaction(yssarilFaction);
            if (yssaril != null) {
                String offerName = player.getFaction();
                if (game.isFowMode()) {
                    offerName = player.getColor();
                }
                ButtonHelper.deleteMessage(event);
                acButtons.add(Button.success("takeAC_" + acID + "_" + player.getFaction(), buttonLabel)
                    .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                acButtons.add(Button.danger("yssarilHeroRejection_" + player.getFaction(),
                    "Reject " + buttonLabel + " and force them to discard of 3 random ACs"));
                String message = yssaril.getRepresentation(true, true) + " " + offerName
                    + " has offered you the action card " + buttonLabel
                    + " for Kyver, Blade and Key, the Yssaril hero. Use buttons to accept or reject it";
                MessageHelper.sendMessageToChannelWithButtons(yssaril.getCardsInfoThread(), message, acButtons);
                String acStringID = "";
                for (String acStrId : player.getActionCards().keySet()) {
                    if ((player.getActionCards().get(acStrId) + "").equalsIgnoreCase(acID)) {
                        acStringID = acStrId;
                    }
                }

                ActionCardModel ac = Mapper.getActionCard(acStringID);
                if (ac != null) {
                    MessageHelper.sendMessageToChannelWithEmbed(
                        yssaril.getCardsInfoThread(), "For your reference, the text of the AC offered reads as",
                        ac.getRepresentationEmbed());

                }

            }
        } else if (buttonID.startsWith("statusInfRevival_")) {
            ButtonHelper.placeInfantryFromRevival(game, event, player, buttonID);
        } else if (buttonID.startsWith("genericReact")) {
            String message = game.isFowMode() ? "Turned down window" : null;
            ButtonHelper.addReaction(event, false, false, message, "");
        } else if (buttonID.startsWith("placeOneNDone_")) {
            ButtonHelperModifyUnits.placeUnitAndDeleteButton(buttonID, event, game, player, ident, trueIdentity);
        } else if (buttonID.startsWith("mitoMechPlacement_")) {
            ButtonHelperAbilities.resolveMitosisMechPlacement(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("sendTradeHolder_")) {
            ButtonHelper.sendTradeHolderSomething(player, game, buttonID, event);
        } else if (buttonID.startsWith("place_")) {
            ButtonHelperModifyUnits.genericPlaceUnit(buttonID, event, game, player, ident, trueIdentity,
                finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("yssarilcommander_")) {
            ButtonHelperCommanders.yssarilCommander(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("setupHomebrew_")) {
            ButtonHelper.setUpHomebrew(game, event, buttonID);
        } else if (buttonID.startsWith("exploreFront_")) {
            String pos = buttonID.replace("exploreFront_", "");
            new ExpFrontier().expFront(event, game.getTileByPosition(pos), game, player);
            List<ActionRow> actionRow2 = new ArrayList<>();
            String exhaustedMessage = event.getMessage().getContentRaw();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if (exhaustedMessage == null || "".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Explore";
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            } else {
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("nekroStealTech_")) {
            String faction = buttonID.replace("nekroStealTech_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 != null) {
                List<String> potentialTech = new ArrayList<>();
                game.setComponentAction(true);
                potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, potentialTech, game);
                List<Button> buttons = ButtonHelperAbilities.getButtonsForPossibleTechForNekro(player, potentialTech, game);
                if (p2.getPromissoryNotesInPlayArea().contains("antivirus")) {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), trueIdentity + " the other player has antivirus, so you cannot gain tech from this combat.");
                } else if (buttons.size() > 0 && p2 != null) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), trueIdentity + " get enemy tech using the buttons", buttons);
                } else {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), trueIdentity + " there are no techs available to gain.");
                }
                ButtonHelper.deleteTheOneButton(event);
            }
        } else if (buttonID.startsWith("mentakCommander_")) {
            String color = buttonID.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(color);
            if (p2 != null) {
                List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p2);
                String message = p2.getRepresentation(true, true)
                    + " You've been hit by"
                    + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
                    + " S'ula Mentarion, the Mentak commander. Please select the PN you would like to send.";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Sent " + color + " the buttons for resolving S'ula Mentarion.");
                ButtonHelper.deleteTheOneButton(event);
            }
        } else if (buttonID.startsWith("mahactStealCC_")) {
            String color = buttonID.replace("mahactStealCC_", "");
            if (!player.getMahactCC().contains(color)) {
                player.addMahactCC(color);
                Helper.isCCCountCorrect(event, game, color);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    ident + " added a " + color + " CC to their fleet pool");
            } else {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    ident + " already had a " + color + " CC in their fleet pool");
            }
            if (player.getLeaderIDs().contains("mahactcommander") && !player.hasLeaderUnlocked("mahactcommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "mahact", event);
            }
            if (ButtonHelper.isLawInPlay(game, "regulations")
                && (player.getFleetCC() + player.getMahactCC().size()) > 4) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                    + " reminder that there is Fleet Regulations in place, which is limiting fleet pool to 4");
            }
            ButtonHelper.deleteTheOneButton(event);
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
            String planet = buttonID.replace("freelancersBuild_", "");
            List<Button> buttons;
            Tile tile = game.getTile(AliasHandler.resolveTile(planet));
            if (tile == null) {
                tile = game.getTileByPosition(planet);
            }
            buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "freelancers",
                "placeOneNDone_dontskipfreelancers");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("arboCommanderBuild_")) {
            String planet = buttonID.replace("arboCommanderBuild_", "");
            List<Button> buttons;
            Tile tile = game.getTile(AliasHandler.resolveTile(planet));
            if (tile == null) {
                tile = game.getTileByPosition(planet);
            }
            buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "arboCommander",
                "placeOneNDone_dontskiparboCommander");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("tacticalActionBuild_")) {
            ButtonHelperTacticalAction.buildWithTacticalAction(player, game, event, buttonID);
        } else if (buttonID.startsWith("getModifyTiles")) {
            List<Button> buttons = ButtonHelper.getTilesToModify(player, game);
            String message = player.getRepresentation()
                + " Use the buttons to select the tile in which you wish to modify units. ";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                buttons);
        } else if (buttonID.startsWith("genericModify_")) {
            String pos = buttonID.replace("genericModify_", "");
            Tile tile = game.getTileByPosition(pos);
            ButtonHelper.offerBuildOrRemove(player, game, event, tile);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("genericBuild_")) {
            String pos = buttonID.replace("genericBuild_", "");
            List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game,
                game.getTileByPosition(pos), "genericBuild", "place");
            String message = player.getRepresentation() + " Use the buttons to produce units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("starforgeTile_")) {
            ButtonHelperAbilities.starforgeTile(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("starforge_")) {
            ButtonHelperAbilities.starforge(buttonID, event, game, player, ident);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap",
                ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, game, true));
        } else if (buttonID.startsWith("planetAbilityExhaust_")) {
            String planet = buttonID.replace("planetAbilityExhaust_", "");
            PlanetExhaustAbility.doAction(event, player, planet, game, true);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("garboziaAbilityExhaust_")) {
            String planet = "garbozia";
            player.exhaustPlanetAbility(planet);
            new ExpPlanet().explorePlanet(event, game.getTileFromPlanet(planet), planet, "INDUSTRIAL", player,
                true, game, 1, false);
        } else if (buttonID.startsWith("checksNBalancesPt2_")) {// "freeSystemsHeroPlanet_"
            SCPick.resolvePt2ChecksNBalances(event, player, game, buttonID);
        } else if (buttonID.startsWith("freeSystemsHeroPlanet_")) {// "freeSystemsHeroPlanet_"
            ButtonHelperHeroes.freeSystemsHeroPlanet(buttonID, event, game, player);
        } else if (buttonID.startsWith("scPick_")) {
            String num = buttonID.replace("scPick_", "");
            int scpick = Integer.parseInt(num);
            if (game.getStoredValue("Public Disgrace") != null
                && game.getStoredValue("Public Disgrace").contains("_" + scpick)
                && (game.getStoredValue("Public Disgrace Only").isEmpty() || game
                    .getStoredValue("Public Disgrace Only").contains(player.getFaction()))) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (game.getStoredValue("Public Disgrace").contains(p2.getFaction())
                        && p2.getActionCards().containsKey("disgrace")) {
                        PlayAC.playAC(event, game, p2, "disgrace", game.getMainGameChannel());
                        game.setStoredValue("Public Disgrace", "");
                        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
                        int scNumber = scpick;
                        Integer tgCount = scTradeGoods.get(scNumber);
                        String msg = player.getRepresentation(true, true) +
                            "\n> Picked: " + Helper.getSCRepresentation(game, scNumber);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getRepresentation()
                                + " you have been Public Disgrace'd because someone preset it to occur when the number " + scpick
                                + " was chosen. If this is a mistake or the Public Disgrace is Sabo'd, feel free to pick the strategy card again. Otherwise, pick a different strategy card.");
                        return;
                    }
                }
            }

            if (game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks")) {
                SCPick.secondHalfOfSCPickWhenChecksNBalances(event, player, game, scpick);
            } else {
                boolean pickSuccessful = Stats.secondHalfOfPickSC(event, game, player, scpick);
                if (pickSuccessful) {
                    SCPick.secondHalfOfSCPick(event, player, game, scpick);
                    ButtonHelper.deleteMessage(event);
                }
            }

        } else if (buttonID.startsWith("milty_")) {
            game.getMiltyDraftManager().doMiltyPick(event, game, buttonID, player);
        } else if (buttonID.startsWith("showMiltyDraft")) {
            game.getMiltyDraftManager().repostDraftInformation(game);
        } else if (nullable != null && buttonID.startsWith("miltyFactionInfo_")) {
            String remainOrPicked = buttonID.replace("miltyFactionInfo_", "");
            List<FactionModel> displayFactions = new ArrayList<>();
            switch (remainOrPicked) {
                case "all" -> displayFactions.addAll(game.getMiltyDraftManager().allFactions());
                case "picked" -> displayFactions.addAll(game.getMiltyDraftManager().pickedFactions());
                case "remaining" -> displayFactions.addAll(game.getMiltyDraftManager().remainingFactions());
            }

            boolean first = true;
            List<MessageEmbed> embeds = displayFactions.stream().map(FactionModel::fancyEmbed).toList();
            for (MessageEmbed e : embeds) {
                String message = "";
                if (first)
                    message = player.getRepresentation(true, true) + " Here's an overview of the factions:";
                MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
                first = false;
            }
        } else if (buttonID.startsWith("ring_")) {
            List<Button> ringButtons = ButtonHelper.getTileInARing(player, game, buttonID, event);
            String num = buttonID.replace("ring_", "");
            String message;
            if (!"corners".equalsIgnoreCase(num)) {
                int ring = Integer.parseInt(num.charAt(0) + "");
                if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                    message = "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
                } else {
                    message = "Click the tile that you want to activate.";
                }
            } else {
                message = "Click the tile that you want to activate.";
            }

            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("getACFrom_")) {
            String faction = buttonID.replace("getACFrom_", "");
            Player victim = game.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(game, player, victim);
            ShowAllAC.showAll(victim, player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentation(true, true)
                    + " Select which AC you would like to steal",
                buttons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("steal2tg_")) {
            new TrapReveal().steal2Tg(player, game, event, buttonID);
        } else if (buttonID.startsWith("steal3comm_")) {
            new TrapReveal().steal3Comm(player, game, event, buttonID);
        } else if (buttonID.startsWith("specialRex_")) {
            ButtonHelper.resolveSpecialRex(player, game, buttonID, ident, event);
        } else if (buttonID.startsWith("doActivation_")) {
            String pos = buttonID.replace("doActivation_", "");
            ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false,
                event);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("getTilesThisFarAway_")) {
            ButtonHelperTacticalAction.getTilesThisFarAway(player, game, event, buttonID);
        } else if (buttonID.startsWith("ringTile_")) {
            ButtonHelperTacticalAction.selectActiveSystem(player, game, event, buttonID);
        } else if (buttonID.startsWith("genericRemove_")) {
            String pos = buttonID.replace("genericRemove_", "");
            game.resetCurrentMovedUnitsFrom1System();
            game.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, game,
                game.getTileByPosition(pos), "Remove");
            game.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from "
                + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use buttons to select the units you want to remove.", systemButtons);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("tacticalMoveFrom_")) {
            ButtonHelperTacticalAction.selectTileToMoveFrom(player, game, event, buttonID);
        } else if (buttonID.startsWith("resolvePreassignment_")) {
            ButtonHelper.resolvePreAssignment(player, game, event, buttonID);
        } else if (buttonID.startsWith("removePreset_")) {
            ButtonHelper.resolveRemovalOfPreAssignment(player, game, event, buttonID);
        } else if (buttonID.startsWith("purge_Frags_")) {
            String typeNAmount = buttonID.replace("purge_Frags_", "");
            String type = typeNAmount.split("_")[0];
            int count = Integer.parseInt(typeNAmount.split("_")[1]);
            List<String> fragmentsToPurge = new ArrayList<>();
            List<String> playerFragments = player.getFragments();
            for (String fragid : playerFragments) {
                if (fragid.contains(type.toLowerCase())) {
                    fragmentsToPurge.add(fragid);
                }
            }
            if (fragmentsToPurge.size() == count) {
                ButtonHelper.deleteTheOneButton(event);
            }
            while (fragmentsToPurge.size() > count) {
                fragmentsToPurge.remove(0);
            }

            for (String fragid : fragmentsToPurge) {
                player.removeFragment(fragid);
                game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
            }

            Player lanefirPlayer = game.getPlayers().values().stream()
                .filter(p -> p.getLeaderIDs().contains("lanefircommander")
                    && !p.hasLeaderUnlocked("lanefircommander"))
                .findFirst().orElse(null);

            if (lanefirPlayer != null) {
                ButtonHelper.commanderUnlockCheck(lanefirPlayer, game, "lanefir", event);
            }

            String message = player.getRepresentation() + " purged fragments: "
                + fragmentsToPurge;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            if (!game.isFowMode() && event.getMessageChannel() instanceof ThreadChannel) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            }

            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
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
            ButtonHelper.deleteMessage(event);
            ButtonHelper.starChartStep1(game, player, buttonID.split("_")[1]);
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
        } else if (buttonID.startsWith("bestowTitleStep1_")) {
            ButtonHelper.resolveBestowTitleStep1(game, player, event, buttonID);
        } else if (buttonID.startsWith("bestowTitleStep2_")) {
            ButtonHelper.resolveBestowTitleStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("argentHeroStep2_")) {
            ButtonHelperHeroes.argentHeroStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("fogAllianceAgentStep2_")) {
            ButtonHelperAgents.fogAllianceAgentStep2(game, player, event, buttonID);
        } else if (buttonID.startsWith("fogAllianceAgentStep3_")) {
            ButtonHelper.deleteMessage(event);
            ButtonHelperHeroes.argentHeroStep3(game, player, event, buttonID);
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
            DrawRelic.resolveDrawRelicAtPosition(player, event, game, buttonID);
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
            ButtonHelper.deleteTheOneButton(event);
            ButtonHelper.commanderUnlockCheck(player, game, buttonID.split("_")[1], event);
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
            String msg = ident + " is using " + buttonID.split("_")[1];
            if (msg.contains("Vaylerian")) {
                msg = msg + " to add +2 capacity to a ship with capacity";
            }
            if (msg.contains("Tnelis")) {
                msg = msg
                    + " to apply 1 hit against their **non-fighter** ships in the system and give **1** of their ships a +1 boost. This ability may only be used once per activation.";
                String pos = buttonID.split("_")[2];
                List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game,
                    game.getTileByPosition(pos));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    trueIdentity + " Use buttons to assign 1 hit", buttons);
                game.setStoredValue("tnelisCommanderTracker", player.getFaction());
            }
            if (msg.contains("Ghemina")) {
                msg = msg + " to gain 1TG after winning the space combat";
                player.setTg(player.getTg() + 1);
                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            if (msg.contains("Lightning")) {
                msg = msg + " Drives to boost the move value of each unit not transporting fighters or infantry by 1";
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteTheOneButton(event);
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
            Leader playerLeader = player.unsafeGetLeader("kortalihero");
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                .append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    message + " - Queen Nadalia, the Kortali hero, has been purged.");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Queen Nadalia, the Kortali hero, was not purged - something went wrong.");
            }
            ButtonHelperHeroes.offerStealRelicButtons(game, player, buttonID, event);
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
            boolean used = addUsedSCPlayer(messageID, game, player, event, "");
            StrategyCardModel scModel = game.getStrategyCardModelByName("construction").orElse(null);
            boolean construction = scModel != null && scModel.usesAutomationForSCID("pok4construction");
            if (!used && scModel != null && construction && !player.getFollowedSCs().contains(scModel.getInitiative())
                && game.getPlayedSCs().contains(scModel.getInitiative())) {
                player.addFollowedSC(scModel.getInitiative(), event);
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed construction");
                }
                String message = deductCC(player, event);
                ButtonHelper.addReaction(event, false, false, message, "");
            }
            ButtonHelperFactionSpecific.handleTitansConstructionMechDeployStep1(game, player);
        } else if (buttonID.startsWith("unstableStep3_")) {
            ButtonHelperActionCards.resolveUnstableStep3(player, game, event, buttonID);
        } else if (buttonID.startsWith("spaceUnits_")) {
            ButtonHelperModifyUnits.spaceLandedUnits(buttonID, event, game, player, ident, buttonLabel);
        } else if (buttonID.startsWith("resetSpend_")) {
            Helper.refreshPlanetsOnTheRespend(player, game);
            String whatIsItFor = "both";
            if (buttonID.split("_").length > 1) {
                whatIsItFor = buttonID.split("_")[1];
            }

            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                for (ItemComponent but : buttonRow) {
                    if (but instanceof Button butt) {
                        if (!Helper.doesListContainButtonID(buttons, butt.getId())) {
                            buttons.add(butt);
                        }
                    }
                }
            }
            String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
            event.getMessage().editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons)).queue();

        } else if (buttonID.startsWith("reinforcements_cc_placement_")) {
            String planet = buttonID.replace("reinforcements_cc_placement_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = game.getTile(tileID);
            if (tile == null) {
                tile = game.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            if (Mapper.isValidColor(color)) {
                AddCC.addCC(event, color, tile);
            }
            String message = ident + "Placed 1 CC from reinforcements in the "
                + Helper.getPlanetRepresentation(planet, game) + " system";
            ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("placeHolderOfConInSystem_")) {
            String planet = buttonID.replace("placeHolderOfConInSystem_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = game.getTile(tileID);
            if (tile == null) {
                tile = game.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            for (Player p2 : game.getRealPlayers()) {
                if (p2.getSCs().contains(4)) {
                    color = p2.getColor();
                }
            }

            if (Mapper.isValidColor(color)) {
                AddCC.addCC(event, color, tile);
            }
            String message = player.getRepresentation() + " Placed 1 " + StringUtils.capitalize(color) + "CC In The "
                + Helper.getPlanetRepresentation(planet, game)
                + " system due to use of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
            ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("greyfire_")) {
            ButtonHelperFactionSpecific.resolveGreyfire(player, game, buttonID, event);
        } else if (buttonID.startsWith("concludeMove_")) {
            ButtonHelperTacticalAction.finishMovingForTacticalAction(player, game, event, buttonID);
        } else if (buttonID.startsWith("transactWith_") || buttonID.startsWith("resetOffer_")) {
            String faction = buttonID.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 != null) {
                player.clearTransactionItemsWith(p2);
                List<Button> buttons = ButtonHelper.getStuffToTransButtonsOld(game, player, p2);
                if (!game.isFowMode() && game.isNewTransactionMethod()) {
                    buttons = ButtonHelper.getStuffToTransButtonsNew(game, player, player, p2);
                }
                String message = player.getRepresentation()
                    + " Use the buttons to select what you want to transact with " + p2.getRepresentation(false, false);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                ButtonHelper.checkTransactionLegality(game, player, p2);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("getNewTransaction_")) {
            ButtonHelper.getNewTransaction(game, player, buttonID, event);
        } else if (buttonID.startsWith("rejectOffer_")) {
            Player p1 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
            if (p1 != null) {
                MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(), p1.getRepresentation() + " your offer to " + player.getRepresentation(false, false) + " has been rejected.");
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("rescindOffer_")) {
            Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
            if (p2 != null) {
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), p2.getRepresentation() + " the latest offer from "
                    + player.getRepresentation(false, false) + " has been rescinded.");
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + "you rescinded the latest offer to "
                    + p2.getRepresentation(false, false));
                player.clearTransactionItemsWith(p2);
                ButtonHelper.deleteMessage(event);
            }
        } else if (buttonID.startsWith("acceptOffer_")) {
            Player p1 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
            Helper.acceptTransactionOffer(p1, player, game, event);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("sendOffer_")) {
            ButtonHelper.sendOffer(game, player, buttonID, event);
        } else if (buttonID.startsWith("offerToTransact_")) {
            ButtonHelper.resolveOfferToTransact(game, player, buttonID, event);
        } else if (buttonID.startsWith("newTransact_")) {
            ButtonHelper.resolveSpecificTransButtonsNew(game, player, buttonID, event);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("transact_")) {
            ButtonHelper.resolveSpecificTransButtonsOld(game, player, buttonID, event);
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
                    ButtonHelper.resolvePNPlay(pnKey, player, game, event);
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
                    ButtonHelper.resolvePNPlay(pnKey, player, game, event);
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
                    ButtonHelper.resolvePNPlay(pnKey, player, game, event);
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
        } else if (buttonID.startsWith("componentActionRes_")) {
            ButtonHelper.resolvePressedCompButton(game, player, event, buttonID);
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
                int maxNumber = numbers.isEmpty() ? 0
                    : numbers.stream().mapToInt(value -> value)
                        .max().orElseThrow(NoSuchElementException::new);
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
            ButtonHelper.addIonStorm(game, buttonID, event);
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
        } else if (buttonID.startsWith("resolveExpedition_")) {
            ButtonHelper.resolveExpedition(buttonID, game, player, event);
        } else if (buttonID.startsWith("resolveLocalFab_")) {
            ButtonHelper.resolveLocalFab(buttonID, game, player, event);
        } else if (buttonID.startsWith("resolveVolatile_")) {
            ButtonHelper.resolveVolatile(buttonID, game, player, event);
        } else if (buttonID.startsWith("resolveCoreMine_")) {
            ButtonHelper.resolveCoreMine(buttonID, game, player, event);
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
                    ButtonHelper.commanderUnlockCheck(player, game, "jolnar", event);
                }
                if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
                    ButtonHelper.commanderUnlockCheck(player, game, "nekro", event);
                }
                if (player.getLeaderIDs().contains("mirvedacommander")
                    && !player.hasLeaderUnlocked("mirvedacommander")) {
                    ButtonHelper.commanderUnlockCheck(player, game, "mirveda", event);
                }
                if (player.getLeaderIDs().contains("dihmohncommander")
                    && !player.hasLeaderUnlocked("dihmohncommander")) {
                    ButtonHelper.commanderUnlockCheck(player, game, "dihmohn", event);
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            }
            ButtonHelper.resolvePNPlay(pnID, player, game, event);
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
            ButtonHelper.resolveSpecificTransButtonPress(game, player, buttonID, event, true);
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
            Button reassign = Button.secondary("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
                "Put " + agenda.getName()
                    + " on the top of the agenda deck. You may use this button to undo that and reassign it.",
                reassign);
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("resolveCounterStroke_")) {
            ButtonHelperActionCards.resolveCounterStroke(game, player, event, buttonID);
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
            ButtonHelper.resolvePNPlay(ta, player, game, event);
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
            Button reassign = Button.secondary("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
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
                    + "Allant, the Edyn" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            ButtonHelper.deleteMessage(event);
        } else if (buttonID.startsWith("agendaResolution_")) {
            AgendaHelper.resolveAgenda(game, buttonID, event, mainGameChannel);
        } else if (buttonID.startsWith("rollIxthian")) {
            if (game.getSpeaker().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
                AgendaHelper.rollIxthian(game, true);
            } else {
                Button ixthianButton = Button.success("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact")
                    .withEmoji(Emoji.fromFormatted(Emojis.Mecatol));
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
                case "warfareBuild" -> {
                    List<Button> buttons;
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    StrategyCardModel scModel = game.getStrategyCardModelByName("warfare").orElse(null);
                    if (!used && scModel != null && scModel.usesAutomationForSCID("pok6warfare")
                        && !player.getFollowedSCs().contains(scModel.getInitiative())
                        && game.getPlayedSCs().contains(scModel.getInitiative())) {
                        int scNum = scModel.getInitiative();
                        player.addFollowedSC(scNum, event);
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                        if (player.getStrategicCC() > 0) {
                            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed warfare");
                        }
                        String message = deductCC(player, event);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    Tile tile = player.getHomeSystemTile();
                    buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "warfare", "place");
                    int val = Helper.getProductionValue(player, game, tile, true);
                    String message = player.getRepresentation()
                        + " Use the buttons to produce. Reminder that when following warfare, you may only use 1 space dock in your home system. "
                        + ButtonHelper.getListOfStuffAvailableToSpend(player, game) + "\n"
                        + "You have " + val + " PRODUCTION value in this system.";
                    if (val > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                        message = message
                            + ". You also have the That Which Molds Flesh, the Vuil'raith commander, which allows you to produce 2 fighters/infantry that don't count towards production limit. ";
                    }
                    if (val > 0 && ButtonHelper.isPlayerElected(game, player, "prophecy")) {
                        message = message
                            + "Reminder that you have Prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated.";
                    }
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units",
                            buttons);
                    } else {
                        MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message);
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Produce Units",
                            buttons);
                    }
                }
                case "getKeleresTechOptions" -> ButtonHelperFactionSpecific.offerKeleresStartingTech(player, game, event);
                case "transaction" -> {
                    List<Button> buttons;
                    buttons = ButtonHelper.getPlayersToTransact(game, player);
                    String message = player.getRepresentation()
                        + " Use the buttons to select which player you wish to transact with";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);

                }
                case "combatDrones" -> ButtonHelperModifyUnits.offerCombatDroneButtons(event, game, player);
                case "offerMirvedaCommander" -> ButtonHelperModifyUnits.offerMirvedaCommanderButtons(event, game, player);
                case "acquireAFreeTech" -> { // Buttons.GET_A_FREE_TECH
                    List<Button> buttons = new ArrayList<>();
                    game.setComponentAction(true);
                    Button propulsionTech = Button
                        .primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay", "Get a Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                    buttons.add(propulsionTech);

                    Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay",
                        "Get a Green Tech");
                    bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                    buttons.add(bioticTech);

                    Button cyberneticTech = Button.secondary(
                        finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay", "Get a Yellow Tech");
                    cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    buttons.add(cyberneticTech);

                    Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay",
                        "Get a Red Tech");
                    warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(warfareTech);

                    Button unitupgradesTech = Button.secondary(
                        finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay", "Get A Unit Upgrade Tech");
                    unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
                    buttons.add(unitupgradesTech);
                    String message = player.getRepresentation() + " What type of tech would you want?";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    ButtonHelper.deleteMessage(event);
                }
                case "acquireATech" -> { // Buttons.GET_A_TECH
                    ButtonHelper.acquireATech(player, game, event, buttonID, false);
                }
                case "acquireATechWithSC" -> { // Buttons.GET_A_TECH
                    ButtonHelper.acquireATech(player, game, event, buttonID, true);
                }
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
                // AFTER AN ACTION CARD HAS BEEN PLAYED
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
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Decided to skip waiting for afters and proceed to voting.");
                    try {
                        AgendaHelper.startTheVoting(game);
                    } catch (Exception e) {
                        BotLogger.log(event, "Could not start the voting", e);
                    }

                    // ButtonHelper.deleteMessage(event);
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
                    DrawAgenda.drawAgenda(event, 2, game, player);
                    ButtonHelper.deleteMessage(event);
                }
                case "nekroFollowTech" -> {
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    StrategyCardModel scModel = game.getStrategyCardModelByName("technology").orElse(null);
                    if (!used && scModel != null && scModel.usesAutomationForSCID("pok7technology")
                        && !player.getFollowedSCs().contains(scModel.getInitiative())
                        && game.getPlayedSCs().contains(scModel.getInitiative())) {
                        int scNum = scModel.getInitiative();
                        player.addFollowedSC(scNum, event);
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                        if (player.getStrategicCC() > 0) {
                            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed tech");
                        }
                        String message = deductCC(player, event);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                    Button exhaust = Button.danger("nekroTechExhaust", "Exhaust Planets");
                    Button doneGainingCC = Button.danger("deleteButtons_technology", "Done Gaining CCs");
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    Button resetCC = Button.secondary(finsFactionCheckerPrefix + "resetCCs",
                        "Reset CCs");
                    List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
                    List<Button> buttons2 = Collections.singletonList(exhaust);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Exhaust using this",
                            buttons2);
                    } else {

                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Exhaust using this",
                            buttons2);
                    }
                }
                case "diploRefresh2" -> {
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    StrategyCardModel scModel = game.getStrategyCardModelByName("diplomacy").orElse(null);
                    if (!used && scModel != null && scModel.usesAutomationForSCID("pok2diplomacy")
                        && !player.getFollowedSCs().contains(scModel.getInitiative())
                        && game.getPlayedSCs().contains(scModel.getInitiative())) {
                        int scNum = scModel.getInitiative();
                        player.addFollowedSC(scNum, event);
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                        if (player.getStrategicCC() > 0) {
                            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event,
                                "followed diplomacy");
                        }
                        String message = deductCC(player, event);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    if (scModel != null && !player.getFollowedSCs().contains(scModel.getInitiative())) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
                    }
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to ready";

                    List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, game);
                    Button doneRefreshing = Button.danger("deleteButtons_diplomacy", "Done Readying Planets"); // spitItOut
                    buttons.add(doneRefreshing);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    if (player.hasAbility("peace_accords")) {
                        List<Button> buttons2 = ButtonHelperAbilities.getXxchaPeaceAccordsButtons(game, player,
                            event, finsFactionCheckerPrefix);
                        if (!buttons2.isEmpty()) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(),
                                trueIdentity + " use buttons to resolve peace accords", buttons2);
                        }
                    }
                }
                case "getOmenDice" -> ButtonHelperAbilities.offerOmenDiceButtons(game, player);
                // wrapped into leadershipGenerateCCButtons now
                case "leadershipExhaust" -> {
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                    Button doneExhausting = Button.danger("deleteButtons_leadership", "Done Exhausting Planets");
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
                    Button doneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
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
                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                        "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                        "Gain 1 Strategy CC");
                    Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                        "Lose 1 Tactic CC");
                    Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet CC");
                    Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                        "Lose 1 Strategy CC");

                    Button doneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                        "Done Redistributing CCs");
                    Button resetCC = Button.secondary(finsFactionCheckerPrefix + "resetCCs",
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
                                    player.getRepresentation(true, true) + " heads up, bot thinks you should gain "
                                        + properGain + " CC now due to: " + reasons);
                            }

                        }
                    }
                }
                case "leadershipGenerateCCButtons" -> {
                    int leadershipInitiative = 1;
                    if (game.getStrategyCardSet().getStrategyCardModelByName("leadership").isPresent()) {
                        leadershipInitiative = game.getStrategyCardSet()
                            .getStrategyCardModelByName("leadership")
                            .get().getInitiative();
                    }

                    if (!player.getFollowedSCs().contains(leadershipInitiative)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, leadershipInitiative, game, event);
                    }
                    player.addFollowedSC(leadershipInitiative, event);
                    if (game.getName().equalsIgnoreCase("pbd1000")) {
                        for (int sc : player.getUnfollowedSCs()) {
                            if (sc % 10 == 1) {
                                player.addFollowedSC(sc, event);
                            }
                        }
                    }
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                    Button doneExhausting = Button.danger("deleteButtons_leadership", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    ButtonHelper.addReaction(event, false, false, "", "");
                    message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                        "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                        "Gain 1 Strategy CC");
                    // Button exhaust = Button.danger(finsFactionCheckerPrefix +
                    // "leadershipExhaust", "Exhaust Planets");
                    Button doneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons_leadership",
                        "Done Gaining CCs");
                    Button resetCC = Button.secondary(finsFactionCheckerPrefix + "resetCCs",
                        "Reset CCs");
                    List<Button> buttons2 = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
                    if (!game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons2);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons2);
                    }
                }
                case "spyNetYssarilChooses" -> ButtonHelperFactionSpecific.resolveSpyNetYssarilChooses(player, game, event);
                case "spyNetPlayerChooses" -> ButtonHelperFactionSpecific.resolveSpyNetPlayerChooses(player, game, event);
                case "diploSystem" -> {
                    String message = trueIdentity + " Click the name of the planet who's system you wish to diplo";
                    List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "diplomacy", buttons);
                }
                case "sc_ac_draw" -> {
                    boolean used2 = addUsedSCPlayer(messageID + "ac", game, player, event, "");
                    if (used2) {
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    StrategyCardModel scModel = game.getStrategyCardModelByName("politics").orElse(null);
                    if (!used && scModel != null && scModel.usesAutomationForSCID("pok3politics")
                        && !player.getFollowedSCs().contains(scModel.getInitiative())
                        && game.getPlayedSCs().contains(scModel.getInitiative())) {
                        int scNum = scModel.getInitiative();
                        player.addFollowedSC(scNum, event);
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                        if (player.getStrategicCC() > 0) {
                            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Politics");
                        }
                        String message = deductCC(player, event);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
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
                            player.getRepresentation(true, true) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(game, player, false));
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
                    }
                    if (player.hasAbility("contagion")) {
                        List<Button> buttons2 = ButtonHelperAbilities.getKyroContagionButtons(game, player,
                            event, finsFactionCheckerPrefix);
                        if (!buttons2.isEmpty()) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                player.getCardsInfoThread(),
                                trueIdentity + " use buttons to resolve contagion", buttons2);

                            if (Helper.getDateDifference(game.getCreationDate(),
                                Helper.getDateRepresentation(1711997257707L)) > 0) {
                                MessageHelper.sendMessageToChannelWithButtons(
                                    player.getCardsInfoThread(),
                                    trueIdentity
                                        + " use buttons to resolve contagion planet #2 (should not be the same as planet #1)",
                                    buttons2);
                            }
                        }
                    }
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
                            player.getRepresentation(true, true) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(game, player, false));
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
                    }
                    ButtonHelper.deleteTheOneButton(event);

                }
                case "resolveDistinguished" -> ButtonHelperActionCards.resolveDistinguished(player, game, event);
                case "resolveMykoMech" -> ButtonHelperFactionSpecific.resolveMykoMech(player, game);
                case "offerNecrophage" -> ButtonHelperFactionSpecific.offerNekrophageButtons(player, event);
                case "resolveMykoCommander" -> ButtonHelperCommanders.mykoCommanderUsage(player, game, event);
                case "checkForAllACAssignments" -> ButtonHelperActionCards.checkForAllAssignmentACs(game, player);
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    StrategyCardModel scModel = game.getStrategyCardModelByName("imperial").orElse(null);
                    if (!used && scModel != null && scModel.usesAutomationForSCID("pok8imperial")
                        && !player.getFollowedSCs().contains(scModel.getInitiative())
                        && game.getPlayedSCs().contains(scModel.getInitiative())) {
                        int scNum = scModel.getInitiative();
                        player.addFollowedSC(scNum, event);
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                        if (player.getStrategicCC() > 0) {
                            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Imperial");
                        }
                        String message = deductCC(player, event);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    boolean used2 = addUsedSCPlayer(messageID + "so", game, player, event,
                        " Drew a " + Emojis.SecretObjective);
                    if (used2) {
                        break;
                    }
                    if (!player.getFollowedSCs().contains(8)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 8, game, event);
                    }

                    Player imperialHolder = Helper.getPlayerWithThisSC(game, 8);
                    String key = "factionsThatAreNotDiscardingSOs";
                    String key2 = "queueToDrawSOs";
                    String key3 = "potentialBlockers";
                    String message = "Drew Secret Objective";
                    for (Player player2 : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
                        if (player2 == player) {
                            game.drawSecretObjective(player.getUserID());
                            if (player.hasAbility("plausible_deniability")) {
                                game.drawSecretObjective(player.getUserID());
                                message = message + ". Drew a second SO due to Plausible Deniability";
                            }
                            SOInfo.sendSecretObjectiveInfo(game, player, event);
                            break;
                        }
                        if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                            message = "Wants to draw an SO but has people ahead of them in speaker order who need to resolve first. They have been queued and will automatically draw an SO when everyone ahead of them is clear."
                                + " They may cancel this by hitting 'No Follow'/";
                            if (!game.isFowMode()) {
                                message = message + player2.getRepresentation(true, true)
                                    + " is the one the game is currently waiting on";
                            }
                            game.setStoredValue(key2,
                                game.getStoredValue(key2) + player.getFaction() + "*");
                            break;
                        }
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "non_sc_draw_so" -> {
                    String message = "Drew Secret Objective";
                    game.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player.getUserID());
                        message = message + ". Drew a second SO due to Plausible Deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(game, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "edynCommanderSODraw" -> {
                    if (!game.playerHasLeaderUnlockedOrAlliance(player, "edyncommander")) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getFactionEmoji() + " you don't have Kadryn, the Edyn commander, silly.");
                    }
                    String message = "Drew Secret Objective instead of scoring PO, using Kadryn, the Edyn commander.";
                    game.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        game.drawSecretObjective(player.getUserID());
                        message = message + ". Drew a second SO due to Plausible Deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(game, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "sc_trade_follow", "sc_follow_trade" -> {
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    if (used) {
                        break;
                    }
                    int tradeInitiative = 5;
                    if (game.getStrategyCardSet().getStrategyCardModelByName("trade").isPresent()) {
                        tradeInitiative = game.getStrategyCardSet()
                            .getStrategyCardModelByName("trade")
                            .get().getInitiative();
                    }

                    if (player.getStrategicCC() > 0) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed Trade");
                    }
                    String message = deductCC(player, event);
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, tradeInitiative, game, event);
                    }
                    player.addFollowedSC(tradeInitiative, event);
                    if (game.getName().equalsIgnoreCase("pbd1000")) {
                        for (int sc : player.getUnfollowedSCs()) {
                            if (sc % 10 == 5) {
                                player.addFollowedSC(sc, event);
                            }
                        }
                    }
                    ButtonHelperStats.replenishComms(event, game, player, true);

                    ButtonHelper.addReaction(event, false, false, message, "");
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "flip_agenda" -> {
                    RevealAgenda.revealAgenda(event, false, game, event.getChannel());
                    ButtonHelper.deleteMessage(event);

                }
                case "refreshAgenda" -> {
                    AgendaHelper.refreshAgenda(game, event);
                }
                case "resolveVeto" -> {
                    String agendaCount = game.getStoredValue("agendaCount");
                    int aCount = 0;
                    if (agendaCount.isEmpty()) {
                        aCount = 0;
                    } else {
                        aCount = Integer.parseInt(agendaCount) - 1;
                    }
                    game.setStoredValue("agendaCount", aCount + "");
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
                    ButtonHelper.startStrategyPhase(event, game);
                    ButtonHelper.deleteMessage(event);

                }
                case "sc_refresh" -> {
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "Replenish");
                    if (used) {
                        break;
                    }
                    int initComm = player.getCommodities();
                    player.setCommodities(player.getCommoditiesTotal());
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, game, event);
                    }
                    player.addFollowedSC(5, event);
                    if (game.getName().equalsIgnoreCase("pbd1000")) {
                        for (int sc : player.getUnfollowedSCs()) {
                            if (sc % 10 == 5) {
                                player.addFollowedSC(sc, event);
                            }
                        }
                    }
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(game, player);
                    ButtonHelperStats.afterGainCommsChecks(game, player, player.getCommodities() - initComm);
                }
                case "sc_refresh_and_wash" -> {
                    if (player.hasAbility("military_industrial_complex")) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player
                            .getRepresentation(true, true)
                            + " since you cannot send players commodities due to your faction ability, washing here seems likely an error. Nothing has been processed as a result. Try a different route if this correction is wrong");
                        return;
                    }

                    if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation(true,
                            true)
                            + " since you do not currently hold your TA, washing here seems likely an error and will mess with the TA resolution. Nothing has been processed as a result. Try a different route of washing your comms if this correction is wrong");
                        return;
                    }

                    boolean used = addUsedSCPlayer(messageID, game, player, event, "Replenish and Wash");
                    if (used) {
                        break;
                    }
                    int washedCommsPower = player.getCommoditiesTotal() + player.getTg();
                    int commoditiesTotal = player.getCommoditiesTotal();
                    int tg = player.getTg();
                    player.setTg(tg + commoditiesTotal);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    player.setCommodities(0);

                    for (Player p2 : game.getRealPlayers()) {
                        if (p2.getSCs().contains(5) && p2.getCommodities() > 0) {
                            if (p2.getCommodities() > washedCommsPower) {
                                p2.setTg(p2.getTg() + washedCommsPower);
                                p2.setCommodities(p2.getCommodities() - washedCommsPower);
                                ButtonHelperAbilities.pillageCheck(p2, game);
                                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                                    p2.getRepresentation(true, true) + " " + washedCommsPower
                                        + " of your commodities got washed in the process of washing "
                                        + ButtonHelper.getIdentOrColor(player, game));
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, player, p2,
                                    player.getCommoditiesTotal());
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p2, player,
                                    p2.getCommoditiesTotal());
                            } else {
                                p2.setTg(p2.getTg() + p2.getCommodities());
                                p2.setCommodities(0);
                                ButtonHelperAbilities.pillageCheck(p2, game);
                                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                                    p2.getRepresentation(true, true)
                                        + " your commodities got washed in the process of washing "
                                        + ButtonHelper.getIdentOrColor(player, game));
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, player, p2,
                                    player.getCommoditiesTotal());
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, p2, player,
                                    p2.getCommoditiesTotal());
                            }
                        }
                        if (p2.getSCs().contains(5)) {
                            ButtonHelper.checkTransactionLegality(game, player, p2);
                        }
                    }
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, game, event);
                    }
                    player.addFollowedSC(5, event);
                    if (game.getName().equalsIgnoreCase("pbd1000")) {
                        for (int sc : player.getUnfollowedSCs()) {
                            if (sc % 10 == 5) {
                                player.addFollowedSC(sc, event);
                            }
                        }
                    }
                    ButtonHelper.addReaction(event, false, false, "Replenishing and washing", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(game, player);
                }
                case "sc_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, game, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);

                    int scnum = 1;
                    boolean setstatus = true;
                    try {
                        scnum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
                    } catch (NumberFormatException e) {
                        try {
                            scnum = Integer.parseInt(lastchar);
                        } catch (NumberFormatException e2) {
                            setstatus = false;
                        }
                    }
                    if (setstatus) {
                        if (!player.getFollowedSCs().contains(scnum)) {
                            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, game, event);
                        }
                        player.addFollowedSC(scnum, event);
                    }
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed " + Helper.getSCName(scnum, game));
                    ButtonHelper.addReaction(event, false, false, message, "");

                }
                case "trade_primary" -> {
                    ButtonHelper.tradePrimary(game, event, player);
                }
                case "score_imperial" -> {
                    if (player == null || game == null) {
                        break;
                    }
                    if (!player.controlsMecatol(true)) {
                        MessageHelper.sendMessageToChannel(privateChannel,
                            "Only the player who controls Mecatol Rex may score the Imperial point.");
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID + "score_imperial", game, player, event,
                        " scored Imperial");
                    if (used) {
                        break;
                    }
                    ButtonHelperFactionSpecific.KeleresIIHQCCGainCheck(player, game);
                    ScorePublic.scorePO(event, privateChannel, game, player, 0);
                }
                // AFTER AN AGENDA HAS BEEN REVEALED
                case "play_when" -> {
                    clearAllReactions(event);
                    ButtonHelper.addReaction(event, true, true, "Playing When", "When Played");
                    List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
                    Date newTime = new Date();
                    game.setLastActivePlayerPing(newTime);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                        "Please indicate no whens again.", game, whenButtons, "when");
                    List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                        "Please indicate no afters again.", game, afterButtons, "after");
                    // addPersistentReactions(event, activeMap, "when");
                    ButtonHelper.deleteMessage(event);
                }
                case "no_when" -> {
                    String message = game.isFowMode() ? "No whens" : null;
                    if (game.getStoredValue("noWhenThisAgenda") == null) {
                        game.setStoredValue("noWhenThisAgenda", "");
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "offerPlayerPref" -> {
                    ButtonHelper.offerPlayerPreferences(player, event);
                }
                case "no_after" -> {
                    String message = game.isFowMode() ? "No afters" : null;
                    if (game.getStoredValue("noAfterThisAgenda") == null) {
                        game.setStoredValue("noAfterThisAgenda", "");
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_after_persistent" -> {
                    String message = game.isFowMode() ? "No afters (locked in)" : null;
                    game.addPlayersWhoHitPersistentNoAfter(player.getFaction());
                    if (game.getStoredValue("noAfterThisAgenda") == null) {
                        game.setStoredValue("noAfterThisAgenda", "");
                    }
                    // game.getStoredValue("noAfterThisAgenda").contains(player.getFaction())
                    if (!"".equalsIgnoreCase(game.getStoredValue("noAfterThisAgenda"))) {
                        game.setStoredValue("noAfterThisAgenda",
                            game.getStoredValue("noAfterThisAgenda") + "_"
                                + player.getFaction());
                    } else {
                        game.setStoredValue("noAfterThisAgenda", player.getFaction());
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_when_persistent" -> {
                    String message = game.isFowMode() ? "No whens (locked in)" : null;
                    game.addPlayersWhoHitPersistentNoWhen(player.getFaction());
                    if (game.getStoredValue("noWhenThisAgenda") == null) {
                        game.setStoredValue("noWhenThisAgenda", "");
                    }
                    if (!"".equalsIgnoreCase(game.getStoredValue("noWhenThisAgenda"))) {
                        game.setStoredValue("noWhenThisAgenda",
                            game.getStoredValue("noWhenThisAgenda") + "_"
                                + player.getFaction());
                    } else {
                        game.setStoredValue("noWhenThisAgenda", player.getFaction());
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
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
                    ButtonHelper.startStrategyPhase(event, game);
                    ButtonHelper.offerSetAutoPassOnSaboButtons(game, null);
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
                        ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
                    }

                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentation(true, true) + " use buttons to discard",
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
                            player.getRepresentation(true, true) + " use buttons to discard",
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
                        ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
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
                            player.getRepresentation(true, true) + " use buttons to discard",
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
                        ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
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
                        message = message + ButtonHelper.mechOrInfCheck(planetName, game, player);
                        failed = message.contains("Please try again.");
                    }
                    if (!failed) {
                        message = message + "Gained 1TG (" + player.getTg() + "->" + (player.getTg() + 1) + ").";
                        player.setTg(player.getTg() + 1);
                        ButtonHelperAbilities.pillageCheck(player, game);
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
                    String message = player.getRepresentation() + " Gained 1TG (" + player.getTg() + "->"
                        + (player.getTg() + 1) + ") from Rear Admiral Farran, the Letnev commander.";
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    MessageHelper.sendMessageToChannel(mainGameChannel, message);
                    ButtonHelper.deleteMessage(event);
                }
                case "gain1tgFromMuaatCommander" -> {
                    String message = player.getRepresentation() + " Gained 1TG (" + player.getTg() + "->"
                        + (player.getTg() + 1) + ") from Magmus, the Muaat commander.";
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    MessageHelper.sendMessageToChannel(mainGameChannel, message);
                    ButtonHelper.deleteMessage(event);
                }
                case "gain1tgFromCommander" -> { // should be deprecated
                    String message = player.getRepresentation() + " Gained 1TG (" + player.getTg() + "->"
                        + (player.getTg() + 1) + ") from their commander";
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    MessageHelper.sendMessageToChannel(mainGameChannel, message);
                    ButtonHelper.deleteMessage(event);
                }
                case "mallice_2_tg" -> {
                    String playerRep = player.getFactionEmoji();
                    String message = playerRep + " exhausted Mallice ability and gained 2TGs (" + player.getTg() + "->"
                        + (player.getTg() + 2) + ").";
                    player.setTg(player.getTg() + 2);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 2);
                    ButtonHelper.fullCommanderUnlockCheck(player, game, "hacan", event);
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
                case "forwardSupplyBase" -> ButtonHelperActionCards.resolveForwardSupplyBaseStep1(player, game, event, buttonID);
                case "economicInitiative" -> ButtonHelperActionCards.economicInitiative(player, game, event);
                case "breakthrough" -> ButtonHelperActionCardsWillHomebrew.resolveBreakthrough(player, game, event);
                case "sideProject" -> ButtonHelperActionCardsWillHomebrew.resolveSideProject(player, game, event, buttonID);
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
                case "focusedResearch" -> ButtonHelperActionCards.resolveFocusedResearch(game, player, buttonID, event);
                case "lizhoHeroFighterResolution" -> ButtonHelperHeroes.lizhoHeroFighterDistribution(player, game, event);
                case "resolveReparationsStep1" -> ButtonHelperActionCards.resolveReparationsStep1(player, game, event, buttonID);
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
                case "strandedShipStep1" -> ButtonHelperActionCardsWillHomebrew.resolveStrandedShipStep1(player, game, event, buttonID);
                case "spatialCollapseStep1" -> ButtonHelperActionCardsWillHomebrew.resolveSpatialCollapseStep1(player, game, event, buttonID);
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
                            ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
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
                            ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
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
                            ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(game, player, event);
                        message = "Drew 2 ACs With Scheming. Please Discard 1 AC.";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentation(true, true) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(game, player, false));

                    ButtonHelper.deleteMessage(event);
                    ButtonHelper.checkACLimit(game, event, player);
                }
                case "pass_on_abilities" -> ButtonHelper.addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                case "tacticalAction" -> {
                    ButtonHelperTacticalAction.selectRingThatActiveSystemIsIn(player, game, event);
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
                    List<Button> systemButtons = ButtonHelper.getAllPossibleCompButtons(game, player, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    if (!game.isFowMode()) {
                        ButtonHelper.deleteMessage(event);
                    }

                }
                case "drawRelicFromFrag" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
                    DrawRelic.drawRelicAndNotify(player, event, game);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    ButtonHelper.deleteMessage(event);
                }
                case "drawRelic" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
                    DrawRelic.drawRelicAndNotify(player, event, game);
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
                    String msg = ident + " announces a retreat";
                    if (game.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
                        msg = msg
                            + ". Since they have Jack Hallard, the Nokar commander, this means they may cancel 2 hits in this coming combat round.";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    if (game.getActivePlayer() != null && game.getActivePlayer() != player
                        && game.getActivePlayer().hasAbility("cargo_raiders")) {
                        int round = 0;
                        String combatName = "combatRoundTracker" + game.getActivePlayer().getFaction()
                            + game.getActiveSystem() + "space";
                        if (game.getStoredValue(combatName).isEmpty()) {
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Button.success("pay1tg", "Pay 1TG"));
                            buttons.add(Button.danger("deleteButtons", "I don't have to pay"));
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                player.getRepresentation()
                                    + " reminder that your opponent has the cargo raiders ability, which means you might have to pay 1TG to announce a retreat if they choose.",
                                buttons);
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
                case "searchMyGames" -> SearchMyGames.searchGames(event.getUser(), event, false, false, false, true, false, true, false);
                case "cardsInfo" -> CardsInfo.sendCardsInfo(game, player, event);
                case "showMap" -> ShowGame.showMap(game, event);
                case "showGameAgain" -> ShowGame.simpleShowGame(game, event);
                case "showPlayerAreas" -> ShowGame.showPlayArea(game, event);
                case "mitosisInf" -> ButtonHelperAbilities.resolveMitosisInf(buttonID, event, game, player, ident);
                case "doneLanding" -> ButtonHelperModifyUnits.finishLanding(buttonID, event, game, player);
                case "vote" -> {
                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null) {
                        String voteMessage = "Chose to Vote. Click buttons for which outcome to vote for.";
                        String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
                        List<Button> outcomeActionRow;
                        if (agendaDetails.contains("For") || agendaDetails.contains("for")) {
                            outcomeActionRow = AgendaHelper.getForAgainstOutcomeButtons(null, "outcome");
                        } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(game, null, "outcome", null);
                        } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
                            voteMessage = "Chose to Vote. Too many planets in the game to represent all as buttons. Click buttons for which player owns the planet you wish to elect.";
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(game, null, "planetOutcomes",
                                null);
                        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                            outcomeActionRow = AgendaHelper.getSecretOutcomeButtons(game, null, "outcome");
                        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                            outcomeActionRow = AgendaHelper.getStrategyOutcomeButtons(null, "outcome");
                        } else if (agendaDetails.contains("unit upgrade")) {
                            outcomeActionRow = AgendaHelper.getUnitUpgradeOutcomeButtons(game, null, "outcome");
                        } else if (agendaDetails.contains("Unit") || agendaDetails.contains("unit")) {
                            outcomeActionRow = AgendaHelper.getUnitOutcomeButtons(game, null, "outcome");
                        } else {
                            outcomeActionRow = AgendaHelper.getLawOutcomeButtons(game, null, "outcome");
                        }
                        ButtonHelper.deleteMessage(event);
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,
                            outcomeActionRow);
                    }
                }
                case "sc_no_follow" -> {
                    int scnum2 = 1;
                    boolean setstatus = true;
                    try {
                        scnum2 = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
                    } catch (NumberFormatException e) {
                        try {
                            scnum2 = Integer.parseInt(lastchar);
                        } catch (NumberFormatException e2) {
                            setstatus = false;
                        }
                    }
                    if (setstatus) {
                        player.addFollowedSC(scnum2, event);
                    }
                    ButtonHelper.addReaction(event, false, false, "Not Following", "");
                    Set<Player> players = playerUsedSC.get(messageID);
                    if (players == null) {
                        players = new HashSet<>();
                    }
                    players.remove(player);
                    playerUsedSC.put(messageID, players);
                }
                case "turnEnd" -> {
                    if (game.isFowMode() && !player.equals(game.getActivePlayer())) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "You are not the active player. Force End Turn with /player turn_end.");
                        return;
                    }
                    ButtonHelper.fullCommanderUnlockCheck(player, game, "hacan", event);
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
                case "rematch" -> {
                    ButtonHelper.rematch(game, event);
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
                        player.getRepresentation(true, true)
                            + " All ships have been removed, continue to land troops.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "resolveDataArchive" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveDataArchive(player, game, event);
                }
                case "resolveDefenseInstallation" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveDefenseInstallation(player, game, event);
                }
                case "resolveAncientTradeRoutes" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveAncientTradeRoutes(player, game, event);
                }
                case "resolveSisterShip" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveSisterShip(player, game, event);
                }
                case "resolveBoardingParty" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveBoardingParty(player, game, event);
                }
                case "resolveMercenaryContract" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveMercenaryContract(player, game, event);
                }
                case "resolveRendezvousPoint" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveRendezvousPoint(player, game, event);
                }
                case "resolveFlawlessStrategy" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveFlawlessStrategy(player, game, event);
                }
                case "resolveChainReaction" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveChainReaction(player, game, event);
                }
                case "resolveArmsDeal" -> {
                    ButtonHelperActionCardsWillHomebrew.resolveArmsDeal(player, game, event);
                }
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
                        player.getRepresentation(true, true) + " 2 cruisers and 1 flagship added.");
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
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(true, true)
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
                    poButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
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
                    Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "Click the names of the planets you wish to exhaust to pay the 1 influence", buttons);
                    ButtonHelper.deleteTheOneButton(event);
                    game.setStoredValue("lawsDisabled", "yes");
                }
                case "orbitolDropFollowUp" -> ButtonHelperAbilities.oribtalDropFollowUp(buttonID, event, game, player, ident);
                case "dropAMechToo" -> {
                    String message = "Please select the same planet you dropped the infantry on";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "mech", "place");
                    buttons.add(Button.danger("orbitolDropExhaust", "Pay for mech"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        message, buttons);
                    ButtonHelper.deleteMessage(event);
                }
                case "orbitolDropExhaust" -> ButtonHelperAbilities.oribtalDropExhaust(buttonID, event, game, player, ident);
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
                case "chooseMapView" -> 
                {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.primary("checkWHView", "Find Wormholes"));
                    buttons.add(Button.danger("checkAnomView", "Find Anomalies"));
                    buttons.add(Button.success("checkLegendView", "Find Legendaries"));
                    buttons.add(Button.secondary("checkEmptyView", "Find Empties"));
                    buttons.add(Button.primary("checkAetherView", "Determine Aetherstreamable Systems"));
                    buttons.add(Button.danger("checkCannonView", "Calculate Space Cannon Offense Shots"));
                    buttons.add(Button.success("checkTraitView", "Find Traits"));
                    buttons.add(Button.success("checkTechSkipView", "Find Technology Specialties"));
                    buttons.add(Button.primary("checkAttachmView", "Find Attachments"));
                    buttons.add(Button.secondary("checkShiplessView", "Show Map Without Ships"));
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
                    Button vote = Button.success(finsFactionCheckerPrefix + "vote",
                        player.getFlexibleDisplayName() + " Choose To Vote");
                    Button abstain = Button.danger(finsFactionCheckerPrefix + "resolveAgendaVote_0",
                        player.getFlexibleDisplayName() + " Choose To Abstain");
                    Button forcedAbstain = Button.secondary("forceAbstainForPlayer_" + player.getFaction(),
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
                // case "editUserSettings" ->
                // UserButtonProvider.resolveEditUserSettingsButton(event);
                // case "editUserSettingPreferredColours" ->
                // UserButtonProvider.resolveEditPreferredColoursButton(event);
                // case "editUserSettingFunEmoji" ->
                // UserButtonProvider.resolveEditFunEmojiButton(event);
                default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
            }
        }
    }

    private static void getSoScoreButtons(ButtonInteractionEvent event, Game game, Player player) {
        String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
        List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveButtons(game, player);
        if (soButtons != null && !soButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
        }
    }

    private static void soDiscard(ButtonInteractionEvent event, String buttonID, Game game, Player player,
        MessageChannel privateChannel, MessageChannel mainGameChannel,
        MessageChannel actionsChannel, String ident) {
        String soID = buttonID.replace("SODISCARD_", "");
        boolean drawReplacement = false;
        if (soID.endsWith("redraw")) {
            soID.replace("redraw", "");
            drawReplacement = true;
        }
        MessageChannel channel;
        if (game.isFowMode()) {
            channel = privateChannel;
        } else if (game.isCommunityMode() && game.getMainGameChannel() != null) {
            channel = mainGameChannel;
        } else {
            channel = actionsChannel;
        }

        if (channel != null) {
            try {
                int soIndex = Integer.parseInt(soID);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ident + " discarded an SO");
                DiscardSO.discardSO(event, player, soIndex, game);
                if (drawReplacement) {
                    DrawSO.drawSO(event, game, player);
                }
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please discard manually.").queue();
                return;
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void soScoreFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player,
        MessageChannel privateChannel, MessageChannel mainGameChannel,
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
            if (player.getSecret(soIndex2) != null && "status".equalsIgnoreCase(player.getSecret(soIndex2).getPhase())
                && "true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScoreSOs";
                String key3 = "potentialScoreSOBlockers";
                String key3b = "potentialScorePOBlockers";
                String message = "Drew Secret Objective";
                for (Player player2 : Helper.getInitativeOrder(game)) {
                    if (player2 == player) {
                        int soIndex = Integer.parseInt(soID);
                        ScoreSO.scoreSO(event, game, player, soIndex, channel);
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2)
                                .replace(player.getFaction() + "*", ""));
                        }
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3)
                                .replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                                // Helper.resolveSOScoringQueue(game, event);
                            }
                        }

                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                        message = player.getRepresentation()
                            + " Wants to score an SO but has people ahead of them in initiative order who need to resolve first. They have been queued and will automatically score their SO when everyone ahead of them is clear. ";
                        if (!game.isFowMode()) {
                            message = message + player2.getRepresentation(true, true)
                                + " is the one the game is currently waiting on";
                        }
                        MessageHelper.sendMessageToChannel(channel, message);
                        int soIndex = Integer.parseInt(soID);
                        game.setStoredValue(player.getFaction() + "queuedSOScore", "" + soIndex);
                        game.setStoredValue(key2,
                            game.getStoredValue(key2) + player.getFaction() + "*");
                        break;
                    }
                }
            } else {
                try {
                    int soIndex = Integer.parseInt(soID);
                    ScoreSO.scoreSO(event, game, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.")
                        .queue();
                    return;
                }
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void acDiscardFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player,
        MessageChannel actionsChannel) {
        String acIndex = buttonID.replace("ac_discard_from_hand_", "");
        boolean stalling = false;
        boolean drawReplacement = false;
        if (acIndex.contains("stall")) {
            acIndex = acIndex.replace("stall", "");
            stalling = true;
        }
        if (acIndex.endsWith("redraw")) {
            acIndex.replace("redraw", "");
            drawReplacement = true;
        }

        MessageChannel channel;
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        } else {
            channel = actionsChannel;
        }

        if (channel != null) {
            try {
                String acID = null;
                for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                    if (so.getValue().equals(Integer.parseInt(acIndex))) {
                        acID = so.getKey();
                    }
                }

                boolean removed = game.discardActionCard(player.getUserID(), Integer.parseInt(acIndex));
                if (!removed) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "No such Action Card ID found, please retry");
                    return;
                }
                String sb = "Player: " + player.getUserName() + " - " +
                    "Discarded Action Card:" + "\n" +
                    Mapper.getActionCard(acID).getRepresentation() + "\n";
                MessageChannel channel2 = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel2 = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel2, sb);
                ACInfo.sendActionCardInfo(game, player);
                String message = "Use buttons to end turn or do another action.";
                if (stalling) {
                    String message3 = "Use buttons to drop 1 mech on a planet or decline";
                    List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game,
                        "mech", "placeOneNDone_skipbuild"));
                    buttons.add(Button.danger("deleteButtons", "Decline to drop Mech"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message3, buttons);
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                }
                if (drawReplacement) {
                    DrawAC.drawActionCards(game, player, 1, true);
                }
                ButtonHelper.checkACLimit(game, event, player);
                ButtonHelper.deleteMessage(event);
                if (player.hasUnexhaustedLeader("cymiaeagent")) {
                    List<Button> buttons2 = new ArrayList<>();
                    Button hacanButton = Button
                        .secondary("exhaustAgent_cymiaeagent_" + player.getFaction(),
                            "Use Cymiae Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.cymiae));
                    buttons2.add(hacanButton);
                    MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation(true, true)
                            + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Skhot Unit X-12, the Cymiae" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, to make yourself draw 1AC.",
                        buttons2);
                }

                if ("Action".equalsIgnoreCase(Mapper.getActionCard(acID).getWindow())) {

                    for (Player p2 : game.getRealPlayers()) {
                        if (p2 == player) {
                            continue;
                        }
                        if (p2.getActionCards().containsKey("reverse_engineer")
                            && !ButtonHelper.isPlayerElected(game, player, "censure")
                            && !ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
                            List<Button> reverseButtons = new ArrayList<>();
                            String key = "reverse_engineer";
                            String ac_name = Mapper.getActionCard(key).getName();
                            if (ac_name != null) {
                                reverseButtons.add(Button.success(
                                    Constants.AC_PLAY_FROM_HAND + p2.getActionCards().get(key)
                                        + "_reverse_" + Mapper.getActionCard(acID).getName(),
                                    "Reverse Engineer " + Mapper.getActionCard(acID).getName()));
                            }
                            reverseButtons.add(Button.danger("deleteButtons", "Decline"));
                            String cyberMessage = "" + p2.getRepresentation(true, true)
                                + " reminder that you may use Reverse Engineer on "
                                + Mapper.getActionCard(acID).getName();
                            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                                cyberMessage, reverseButtons);
                        }
                    }
                }

            } catch (Exception e) {
                BotLogger.log(event, "Something went wrong discarding", e);
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
    }

    private static void acPlayFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player,
        MessageChannel actionsChannel, String fowIdentity) {
        String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
        MessageChannel channel;
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        } else {
            channel = actionsChannel;
        }
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                fowIdentity + " please play Sabotage by clicking the Sabo button on the AC you wish to Sabo");
            return;
        }
        if (acID.contains("reverse_")) {
            String actionCardTitle = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Button.success("resolveReverse_" + actionCardTitle,
                "Pick up " + actionCardTitle + " from the discard"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                fowIdentity + " After checking for Sabos, use buttons to resolve Reverse Engineer.", scButtons);
        }
        if (acID.contains("counterstroke_")) {
            String tilePos = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Button.success("resolveCounterStroke_" + tilePos,
                "Counterstroke in " + tilePos));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                fowIdentity + " After checking for Sabos, use buttons to resolve Counterstroke.", scButtons);
        }
        if (channel != null) {
            try {
                String error = PlayAC.playAC(event, game, player, acID, channel);
                if (error != null) {
                    event.getChannel().sendMessage(error).queue();
                }
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse AC ID: " + acID, e);
                event.getChannel().asThreadChannel()
                    .sendMessage("Could not parse AC ID: " + acID + " Please play manually.").queue();
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
    }

    private void deleteButtons(ButtonInteractionEvent event, String buttonID, String buttonLabel, Game game,
        Player player, MessageChannel actionsChannel, String trueIdentity) {
        buttonID = buttonID.replace("deleteButtons_", "");
        String editedMessage = event.getMessage().getContentRaw();
        if (("Done Gaining CCs".equalsIgnoreCase(buttonLabel)
            || "Done Redistributing CCs".equalsIgnoreCase(buttonLabel)
            || "Done Losing CCs".equalsIgnoreCase(buttonLabel)) && editedMessage.contains("CCs have gone from")) {

            String playerRep = player.getRepresentation();
            String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
            shortCCs = shortCCs.replace("CCs have gone from ", "");
            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
            if (event.getMessage().getContentRaw().contains("Net gain")) {
                boolean cyber = false;
                int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                finalCCs = finalCCs + ". Net CC gain was " + netGain;
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                        cyber = true;
                    }
                }
                if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                    if (player.hasAbility("versatile") || player.hasTech("hm") || cyber) {
                        int properGain = 2;
                        String reasons = "";
                        if (player.hasAbility("versatile")) {
                            properGain = properGain + 1;
                            reasons = "versatile ";
                        }
                        if (player.hasTech("hm")) {
                            properGain = properGain + 1;
                            reasons = reasons + "hypermetabolism ";
                        }
                        if (cyber) {
                            properGain = properGain + 1;
                            reasons = reasons + "cybernetics ";
                        }
                        if (netGain < properGain && netGain != 1) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                "# " + player.getRepresentation(true, true)
                                    + " heads up, bot thinks you should have gained " + properGain
                                    + " CC due to: " + reasons);
                        }
                    }
                }
                player.setTotalExpenses(player.getTotalExpenses() + netGain * 3);
            }

            if ("Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs);
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    String message = playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is "
                        + finalCCs;
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        playerRep + " Final CC Allocation Is " + finalCCs);
                }

            }
            ButtonHelper.checkFleetInEveryTile(player, game, event);

        }
        if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
            || "Done Producing Units".equalsIgnoreCase(buttonLabel))
            && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {
            Tile tile = null;
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel) && buttonID.contains("_")) {
                String pos = buttonID.split("_")[1];
                buttonID = buttonID.split("_")[0];
                tile = game.getTileByPosition(pos);
            }
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, buttonID);
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {
                MessageHistory mHistory = event.getChannel().getHistory();
                RestAction<List<Message>> lis = mHistory.retrievePast(3);
                Message previousM = lis.complete().get(1);
                System.out.println(previousM.getContentRaw());
                if (previousM.getContentRaw().contains("You have available to you")) {
                    previousM.delete().queue();
                }
                player.setTotalExpenses(
                    player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, game, true));
                String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
                if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button sar = Button.danger("exhaustTech_sar", "Exhaust Self Assembly Routines")
                        .withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(sar);
                }
                if (player.hasTechReady("htp") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar = Button.danger("exhaustTech_htp", "Exhaust Hegemonic Trade Policy")
                        .withEmoji(Emoji.fromFormatted(Emojis.Winnu));
                    buttons.add(sar);
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    Button sar2 = Button.success("titansCommanderUsage", "Use Ul Commander")
                        .withEmoji(Emoji.fromFormatted(Emojis.Titans));
                    buttons.add(sar2);
                }
                if (player.hasTechReady("dsbenty")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    Button sar2 = Button.success("exhaustTech_dsbenty", "Use Merged Replicators")
                        .withEmoji(Emoji.fromFormatted(Emojis.bentor));
                    buttons.add(sar2);
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0 && player.hasTechReady("aida")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    Button aiDEVButton = Button.danger("exhaustTech_aida",
                        "Exhaust AI Development Algorithm (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)")
                        .withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(aiDEVButton);
                }
                if (player.hasTechReady("st") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button sarweenButton = Button.danger("useTech_st", "Use Sarween Tools")
                        .withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    buttons.add(sarweenButton);
                }
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    Button sarweenButton = Button.danger("useRelic_boon", "Use Boon Of The Cerulean God Relic");
                    buttons.add(sarweenButton);
                }
                if (player.hasTechReady("absol_st")) {
                    Button sarweenButton = Button.danger("useTech_absol_st", "Use Sarween Tools");
                    buttons.add(sarweenButton);
                }
                if (player.hasUnexhaustedLeader("winnuagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button winnuButton = Button.danger("exhaustAgent_winnuagent",
                        "Use Winnu Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.Winnu));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("gledgeagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button winnuButton = Button
                        .danger("exhaustAgent_gledgeagent_" + player.getFaction(),
                            "Use Gledge Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.gledge));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("ghotiagent")) {
                    Button winnuButton = Button
                        .danger("exhaustAgent_ghotiagent_" + player.getFaction(),
                            "Use Ghoti Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.ghoti));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("mortheusagent")) {
                    Button winnuButton = Button
                        .danger("exhaustAgent_mortheusagent_" + player.getFaction(),
                            "Use Mortheus Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.mortheus));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("rohdhnaagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button winnuButton = Button
                        .danger("exhaustAgent_rohdhnaagent_" + player.getFaction(),
                            "Use Roh'Dhna Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.rohdhna));
                    buttons.add(winnuButton);
                }
                if (player.hasLeaderUnlocked("hacanhero") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button hacanButton = Button.danger("purgeHacanHero", "Purge Hacan Hero")
                        .withEmoji(Emoji.fromFormatted(Emojis.Hacan));
                    buttons.add(hacanButton);
                }
                Button doneExhausting;
                if (!buttonID.contains("deleteButtons")) {
                    doneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
                } else {
                    doneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                }
                ButtonHelper.updateMap(game, event,
                    "Result of build on turn " + player.getTurnCount() + " for " + player.getFactionEmoji());
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                if (tile != null && player.hasAbility("rally_to_the_cause")
                    && player.getHomeSystemTile() == tile
                    && ButtonHelperAbilities.getTilesToRallyToTheCause(game, player).size() > 0) {
                    String msg = player.getRepresentation()
                        + " due to your Rally to the Cause ability, if you just produced a ship in your HS, you may produce up to 2 ships in a system that contains a planet with a trait but no legendary planets and no opponent units. Press button to resolve";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Button.success("startRallyToTheCause", "Rally To The Cause"));
                    buttons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg,
                        buttons2);

                }
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if (player.hasTech("asn") && (buttonID.contains("tacticalAction") || buttonID.contains("warfare"))) {
                ButtonHelperFactionSpecific.offerASNButtonsStep1(game, player, buttonID);
            }
            if (buttonID.contains("tacticalAction")) {
                ButtonHelper.exploreDET(player, game, event);
                ButtonHelperFactionSpecific.cleanCavUp(game, event);
                if (player.hasAbility("cunning")) {
                    List<Button> trapButtons = new ArrayList<>();
                    for (UnitHolder uH : game.getTileByPosition(game.getActiveSystem()).getUnitHolders()
                        .values()) {
                        if (uH instanceof Planet) {
                            String planet = uH.getName();
                            trapButtons.add(Button.secondary("setTrapStep3_" + planet,
                                Helper.getPlanetRepresentation(planet, game)));
                        }
                    }
                    trapButtons.add(Button.danger("deleteButtons", "Decline"));
                    String msg = player.getRepresentation(true, true)
                        + " you may use the buttons to place a trap on a planet.";
                    if (trapButtons.size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(), msg, trapButtons);
                    }
                }
                if (player.hasUnexhaustedLeader("celdauriagent")) {
                    List<Button> buttons = new ArrayList<>();
                    Button hacanButton = Button
                        .secondary("exhaustAgent_celdauriagent_" + player.getFaction(), "Use Celdauri Agent")
                        .withEmoji(Emoji.fromFormatted(Emojis.celdauri));
                    buttons.add(hacanButton);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation(true, true)
                            + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "George Nobin, the Celdauri" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to place 1 space dock for 2TGs or 2 commodities.",
                        buttons);
                }
                List<Button> systemButtons2 = new ArrayList<>();
                if (!game.isAbsolMode() && player.getRelics().contains("emphidia")
                    && !player.getExhaustedRelics().contains("emphidia")) {
                    String message = trueIdentity + " You may use the button to explore a planet using " + Emojis.Relic
                        + "Crown of Emphidia.";
                    systemButtons2.add(Button.success("crownofemphidiaexplore", "Use Crown of Emphidia To Explore"));
                    systemButtons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = trueIdentity + " You may use the button to use "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "T'ro, the N'orr" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(game, player));
                    systemButtons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = trueIdentity + " You may use the button to to use "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Field Marshal Mercer, a Nomad" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
                    systemButtons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }

                if (game.isNaaluAgent()) {
                    player = game.getPlayer(game.getActivePlayerID());
                    game.setNaaluAgent(false);
                }
                game.setStoredValue("tnelisCommanderTracker", "");

                String message = player.getRepresentation(true, true)
                    + " Use buttons to end turn or do another action.";
                List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, systemButtons);
                player.resetOlradinPolicyFlags();
            }
        }
        if ("diplomacy".equalsIgnoreCase(buttonID)) {
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, "diplomacy", null);
        }
        if ("spitItOut".equalsIgnoreCase(buttonID) && !"Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), editedMessage);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static boolean addUsedSCPlayer(String messageID, Game game, Player player,
        @NotNull ButtonInteractionEvent event, String defaultText) {
        Set<Player> players = playerUsedSC.get(messageID);
        if (players == null) {
            players = new HashSet<>();
        }
        boolean contains = players.contains(player);
        players.add(player);
        playerUsedSC.put(messageID, players);
        // if (contains) {
        // String alreadyUsedMessage = defaultText.isEmpty() ? "used Secondary of
        // Strategy Card" : defaultText;
        // String message = player.getRepresentation() + " already " +
        // alreadyUsedMessage;
        // if (game.isFoWMode()) {
        // MessageHelper.sendPrivateMessageToPlayer(player, game, message);
        // } else {
        // MessageHelper.sendMessageToChannel(event.getChannel(), message);
        // }
        // }
        return contains;
    }

    @NotNull
    public static String deductCC(Player player, @NotNull ButtonInteractionEvent event) {
        int strategicCC = player.getStrategicCC();
        String message;
        if (strategicCC == 0) {
            message = " have 0 command tokens in strategy pool, can't follow.";
        } else {
            strategicCC--;
            player.setStrategicCC(strategicCC);
            message = " following strategy card, deducted 1 command tokens from strategy pool.";
        }
        return message;
    }

    public void clearAllReactions(@NotNull ButtonInteractionEvent event) {
        Message mainMessage = event.getInteraction().getMessage();
        mainMessage.clearReactions().queue();
        // String messageId = mainMessage.getId();
        // RestAction<Message> messageRestAction =
        // event.getChannel().retrieveMessageById(messageId);
        // messageRestAction.queue(m -> {
        // RestAction<Void> voidRestAction = m.clearReactions();
        // voidRestAction.queue();
        // });
    }

    public static void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();

        String messageId = event.getInteraction().getMessage().getId();
        int matchingFactionReactions = 0;
        for (Player player : game.getRealPlayers()) {
            boolean factionReacted = false;
            if (buttonID.contains("no_after")) {
                if (game.getStoredValue("noAfterThisAgenda").contains(player.getFaction())) {
                    factionReacted = true;
                }
                Message mainMessage = event.getMessage();
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, event.getMessageId()));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                    factionReacted = true;
                }
            }
            if (buttonID.contains("no_when")) {
                if (game.getStoredValue("noWhenThisAgenda").contains(player.getFaction())) {
                    factionReacted = true;
                }
                Message mainMessage = event.getMessage();
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, event.getMessageId()));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                    factionReacted = true;
                }
            }
            if (factionReacted || (game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction()))) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = game.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            respondAllPlayersReacted(event, game);
            game.removeStoredValue(messageId);
        }
    }

    public static void checkForAllReactions(String messageId, Game game) {
        int matchingFactionReactions = 0;
        for (Player player : game.getRealPlayers()) {

            if ((game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction()))) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = game.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            game.getMainGameChannel().retrieveMessageById(messageId).queue(msg -> {
                if (game.getLatestAfterMsg().equalsIgnoreCase(messageId)) {
                    msg.reply("All players have indicated 'No Afters'").queueAfter(1000, TimeUnit.MILLISECONDS);
                    AgendaHelper.startTheVoting(game);
                } else if (game.getLatestWhenMsg().equalsIgnoreCase(messageId)) {
                    msg.reply("All players have indicated 'No Whens'").queueAfter(10, TimeUnit.MILLISECONDS);

                } else {
                    String msg2 = "All players have indicated 'No Sabotage'";
                    // if (game.getMessageIDsForSabo().contains(messageId)) {
                    String faction = "bob_" + game.getStoredValue(messageId) + "_";
                    faction = faction.split("_")[1];
                    Player p2 = game.getPlayerFromColorOrFaction(faction);
                    if (p2 != null && !game.isFowMode()) {
                        msg2 = p2.getRepresentation() + " " + msg2;
                    }
                    // }
                    msg.reply(msg2).queueAfter(1, TimeUnit.SECONDS);
                }
            });

            if (game.getMessageIDsForSabo().contains(messageId)) {
                game.removeMessageIDForSabo(messageId);
            }
        }
    }

    public static boolean checkForASpecificPlayerReact(String messageId, Player player, Game game) {
        boolean foundReact = false;
        try {
            if (game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction())) {
                return true;
            }
            game.getMainGameChannel().retrieveMessageById(messageId).queue(mainMessage -> {
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                }
            });
        } catch (Exception e) {
            game.removeMessageIDForSabo(messageId);
            return true;
        }
        return foundReact;

    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();
        if (game == null || buttonID == null) {
            return;
        }
        if (buttonID.startsWith(Constants.PO_SCORING)) {
            buttonID = Constants.PO_SCORING;
        } else if ((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction().getMessage().reply("All players have reacted to '" + buttonText + "'").queue();
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so", "sc_trade_follow" -> {
                String message = "All players have reacted to this Strategy Card";
                if (game.isFowMode()) {
                    event.getInteraction().getMessage().reply(message).queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage(message).queueAfter(10, TimeUnit.SECONDS);
                }
            }
            case "no_when", "no_when_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1,
                    TimeUnit.SECONDS);
            }
            case "no_after", "no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(game);

            }
            case "no_sabotage" -> {
                String msg = "All players have indicated 'No Sabotage'";
                String faction = "bob_" + game.getStoredValue(event.getMessageId()) + "_";
                faction = faction.split("_")[1];
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 != null && !game.isFowMode()) {
                    msg = p2.getRepresentation() + " " + msg;
                }
                if (game.getMessageIDsForSabo().contains(event.getMessageId())) {
                    game.removeMessageIDForSabo(event.getMessageId());
                }
                event.getInteraction().getMessage().reply(msg).queueAfter(1, TimeUnit.SECONDS);

            }

            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Flip the relevant PO using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button draw2Stage2 = Button.success("reveal_stage_2x2", "Reveal 2 Stage 2");
                Button drawStage2 = Button.success("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Button.success("reveal_stage_1", "Reveal Stage 1");
                // Button runStatusCleanup = Button.primary("run_status_cleanup", "Run Status
                // Cleanup");
                List<Button> buttons = new ArrayList<>();
                if (game.isRedTapeMode()) {
                    message2 = "All players have indicated scoring. This game is red tape mode, which means no objective is revealed at this stage. Please press one of the buttons below anyways though -- don't worry, it won't reveal anything, it will just run cleanup.";
                }
                if (game.getRound() < 4) {
                    buttons.add(drawStage1);
                }
                if (game.getRound() > 2 || game.getPublicObjectives1Peakable().size() == 0) {
                    if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                        buttons.add(draw2Stage2);
                    } else {
                        buttons.add(drawStage2);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                // event.getMessage().delete().queueAfter(20, TimeUnit.SECONDS);
            }
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now",
                        buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
                        + " All players have indicated completion of status phase. Proceed to Strategy Phase.");
                    StartPhase.startPhase(event, game, "strategy");
                }
            }
            case "redistributeCCButtons" -> {
                if (game.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "This message was triggered by the last person pressing redistribute CCs. Please flip agenda after they finish redistributing",
                        buttons);
                } else {
                    Button flipAgenda = Button.primary("startStrategyPhase", "Press this to start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Start Strategy Phase", buttons);
                }
            }
        }
    }
}
