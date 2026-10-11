package ti4.discord.interactions.listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;
import ti4.cron.CronManager;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.search.FindService;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.discord.interactions.commands.uncategorized.ServerPromoteCommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.AbilityModel;
import ti4.model.AttachmentModel;
import ti4.model.BorderAnomalyModel;
import ti4.model.BreakthroughModel;
import ti4.model.ColorableModelInterface;
import ti4.model.DeckModel;
import ti4.model.DeckModel.DeckType;
import ti4.model.EmbeddableModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetTypeModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.ShipPositionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechSpecialtyModel;
import ti4.model.TechnologyModel;
import ti4.model.WormholeModel;
import ti4.service.UnitDecalService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftComponentFactory;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.fow.MapSegmentService;
import ti4.service.franken.FrankenDraftMode;
import ti4.service.game.GameNameService;
import ti4.service.game.GameUndoNameService;
import ti4.service.map.AddTileService.RandomOption;
import ti4.service.map.MapPresetService;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.statistics.PlayerStatTypes;
import ti4.service.statistics.game.GameStatTypes;
import ti4.service.testbed.TestBedAutoComplete;
import ti4.service.testbed.TestBedService;
import ti4.settings.GlobalSettings;

@UtilityClass
class AutoCompleteProvider {

    private static final List<String> GAME_TYPES = List.of(
            "base",
            "pok",
            "absol",
            "ds",
            "tigl",
            "action_deck_2",
            "franken",
            "milty_mod",
            "red_tape",
            "age_of_exploration",
            "minor_factions",
            "alliance",
            "age_of_commerce",
            "facilities",
            "total_war",
            "liberation",
            "ordinian",
            "te",
            "tf",
            "twilightds",
            "tedemo",
            "noswap",
            Constants.TWILIGHT_KART,
            Constants.TK_DESTROYER_CUP,
            Constants.TK_NOVA_CUP);

    private static final Map<String, List<String>> FIXED_CHOICES = Map.ofEntries(
            Map.entry(
                    Constants.VOLTRON_STYLE,
                    List.of(
                            "eyes",
                            "arms",
                            "link",
                            "saiyan",
                            "at_field",
                            "nyan",
                            "fancy",
                            "royal",
                            "baba",
                            "minis",
                            "lightning",
                            "panther")),
            Map.entry(Constants.CC_USE, List.of("t/tactic", "r/retreat/reinforcements", "no")),
            Map.entry(
                    Constants.DISPLAY_TYPE,
                    List.of(
                            "all",
                            "map",
                            "stats",
                            "split",
                            "landscape",
                            "wormholes",
                            "anomalies",
                            "legendaries",
                            "empties",
                            "aetherstream",
                            "space_cannon_offense",
                            "traits",
                            "technology_specialties",
                            "attachments",
                            "unlocked_units",
                            "shipless",
                            "googly",
                            "none")),
            Map.entry(
                    Constants.TRAIT,
                    List.of(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER)),
            Map.entry(Constants.FOG_FILTER, List.of("Dark Grey (default)", "Sepia", "White", "Pink", "Purple")),
            Map.entry(
                    Constants.PRIMARY_TILE_DIRECTION,
                    List.of("north", "northeast", "southeast", "south", "southwest", "northwest")),
            Map.entry(Constants.ANON, List.of("y", "n")),
            Map.entry(Constants.TEXT_SIZE, List.of("tiny", "small", "medium", "large")),
            Map.entry(Constants.SPLICE_TYPE, List.of("ability", "units", "genome")),
            Map.entry(
                    Constants.EXPEDITION,
                    List.of("secret", "actionCards", "fiveInf", "fiveRes", "tradeGoods", "techSkip")),
            Map.entry(Constants.TECH_TYPE, List.of("cybernetic", "biotic", "warfare", "propulsion")),
            Map.entry(
                    Constants.SPECIFIC_PHASE,
                    List.of(
                            "strategy",
                            "voting",
                            "statusScoring",
                            "statusHomework",
                            "action",
                            "agendaResolve",
                            "playerSetup",
                            "setupHomebrew",
                            "ixthian",
                            "agenda")),
            Map.entry(Constants.CREUSS_TOKEN_NAME, List.of("alpha", "beta", "gamma")),
            Map.entry(
                    Constants.SCENARIO,
                    List.of(
                            "ordinian (codex 1)",
                            "liberation (codex 4)",
                            "erwan's gambit (homebrew)",
                            "muaat mania (homebrew)")),
            Map.entry(GameStatisticsFilterer.GAME_TYPES_FILTER, GAME_TYPES),
            Map.entry(GameStatisticsFilterer.EXCLUDED_GAME_TYPES_FILTER, GAME_TYPES),
            Map.entry(Constants.ADD_REMOVE, List.of("add", "remove")));

    private static final Map<String, DeckType> DECK_TYPE_OPTIONS = Map.of(
            Constants.SO_DECK, DeckType.SECRET_OBJECTIVE,
            Constants.STAGE_1_PUBLIC_DECK, DeckType.PUBLIC_STAGE_1_OBJECTIVE,
            Constants.STAGE_2_PUBLIC_DECK, DeckType.PUBLIC_STAGE_2_OBJECTIVE,
            Constants.RELIC_DECK, DeckType.RELIC,
            Constants.AGENDA_DECK, DeckType.AGENDA,
            Constants.EVENT_DECK, DeckType.EVENT,
            Constants.EXPLORATION_DECKS, DeckType.EXPLORE,
            Constants.TECHNOLOGY_DECK, DeckType.TECHNOLOGY);

    private static final Map<String, String> HUES = hashMapOf(new String[][] {
        {"RED", "Reds"},
        {"ORANGE", "Oranges"},
        {"BROWN", "Browns"},
        {"YELLOW", "Yellows"},
        {"GREEN", "Greens"},
        {"BLUE", "Blues"},
        {"PURPLE", "Purples"},
        {"PINK", "Pinks"},
        {"WHITE", "Whites"},
        {"BLACK", "Blacks"},
        {"MULTI", "Multi-Colours"},
        {Constants.ALL, "ALL COLOURS"}
    });

    private static final Map<String, String> DECAL_HUES = hashMapOf(new String[][] {
        {"Pattern", "Pattern"},
        {"Icon", "Icon"},
        {"Symbol", "Symbol"},
        {"Texture", "Texture"},
        {"Line", "Line"},
        {"Other", "Other"},
        {Constants.ALL, "ALL DECALS"}
    });

    static void handleAutoCompleteEvent(CommandAutoCompleteInteractionEvent event) {
        try {
            resolveAutoCompleteEvent(event);
        } catch (IllegalArgumentException ignored) {
            // We don't care about these.
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event), "Error in handleAutoCompleteEvent", e);
        }
    }

    private static void resolveAutoCompleteEvent(CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        String subCommandName = event.getSubcommandName();
        String optionName = event.getFocusedOption().getName();

        if (Constants.FIND.equals(commandName)) {
            resolveFindAutoComplete(event, optionName);
            if (event.isAcknowledged()) return;
        }

        if (subCommandName != null) {
            switch (commandName) {
                case Constants.DEVELOPER -> resolveDeveloperCommandAutoComplete(event, subCommandName, optionName);
                case Constants.SEARCH, "search2" -> resolveSearchCommandAutoComplete(event, subCommandName, optionName);
                case Constants.FRANKEN -> resolveFrankenAutoComplete(event, subCommandName, optionName);
                case Constants.FRANKEN2 -> resolvePlotAutoComplete(event, optionName);
                case "testbed" -> resolveTestBedAutoComplete(event, optionName);
            }
            if (event.isAcknowledged()) return;
        }

        String gameName = GameNameService.getGameNameFromChannel(event);
        if (GameManager.isValid(gameName) && subCommandName != null) {
            switch (commandName) {
                case Constants.MAP -> resolveMapAutoComplete(event, subCommandName, optionName, gameName);
                case Constants.CARDS_AC ->
                    resolveActionCardDiscardAutoComplete(event, subCommandName, optionName, gameName);
                case Constants.EXPLORE -> resolveExploreAutoComplete(event, subCommandName, optionName, gameName);
                case Constants.STATUS -> resolveStatusAutoComplete(event, subCommandName, optionName, gameName);
                case Constants.AGENDA -> resolveAgendaAutoComplete(event, subCommandName, optionName, gameName);
                case Constants.EVENT -> resolveEventAutoComplete(event, subCommandName, optionName, gameName);
                case Constants.CARDS_SO ->
                    resolveSecretObjectiveAutoComplete(event, subCommandName, optionName, gameName);
            }
            if (event.isAcknowledged()) return;
        }

        handleOptions(event, optionName, subCommandName, gameName);
        if (!event.isAcknowledged()) {
            reply(event, Collections.emptyList());
        }
    }

    private static void handleOptions(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String optionName,
            String subcommandName,
            String gameName) {
        List<String> fixedChoices = FIXED_CHOICES.get(optionName);
        if (fixedChoices != null) {
            replyContaining(event, fixedChoices);
            return;
        }
        DeckType deckType = DECK_TYPE_OPTIONS.get(optionName);
        if (deckType != null) {
            List<DeckModel> decks = Mapper.getDecks().values().stream()
                    .filter(deckModel -> deckModel.getType() == deckType)
                    .toList();
            reply(event, searchModels(event, decks, null));
            return;
        }
        switch (optionName) {
            case Constants.COLOR -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Mapper.getColors().stream()
                                        .filter(color -> color.getName().startsWith(enteredValue)
                                                || color.getAliases().contains(enteredValue)),
                                color -> color.getName(),
                                color -> color.getName()));
            }
            case Constants.FACTION,
                    Constants.FACTION2,
                    Constants.FACTION3,
                    Constants.FACTION4,
                    Constants.FACTION5,
                    Constants.FACTION6,
                    Constants.BAN_FLEET,
                    Constants.BAN_HS,
                    Constants.BAN_STARTING_TECH,
                    Constants.BAN_COMMODITIES,
                    GameStatisticsFilterer.WINNING_FACTION_FILTER,
                    Constants.HOMESYSTEM_FACTION_OPTION,
                    Constants.STARTING_FLEET_FACTION_OPTION,
                    Constants.PRIORITY_NUMBER_FACTION_OPTION ->
                reply(event, searchModels(event, Mapper.getFactionsValues(), null));
            case Constants.HUE -> reply(event, labelledChoices(event, HUES));
            case Constants.DECAL_HUE -> reply(event, labelledChoices(event, DECAL_HUES));
            case Constants.TOKEN ->
                replyContaining(
                        event,
                        Mapper.getTokensFromProperties().stream()
                                .map(str -> {
                                    AttachmentModel attachment = Mapper.getAttachmentInfo(str);
                                    return attachment != null ? attachment.getAutoCompleteName() : str;
                                })
                                .toList());
            case Constants.MAP_SEGMENT -> {
                Game game = getGame(gameName);
                List<String> names = List.of();
                if (game != null) {
                    boolean foggedView = MapSegmentService.isFoggedView(game, event.getChannel());
                    names = MapSegmentService.viewableNames(
                            game, event.getUser().getId(), foggedView);
                }
                replyContaining(event, names);
            }
            case Constants.RELIC_ALL ->
                reply(
                        event,
                        containing(
                                Mapper.getRelics().entrySet().stream(),
                                enteredLower(event),
                                entry -> entry.getValue().getName(),
                                Map.Entry::getKey));
            case Constants.PO_ID ->
                reply(
                        event,
                        containing(
                                Mapper.getPublicObjectives().entrySet().stream(),
                                enteredLower(event),
                                entry -> entry.getValue().getName(),
                                Map.Entry::getKey));
            case Constants.SO_ID ->
                reply(event, containingByValue(event, Mapper.getSecretObjectivesJustNamesAndSource()));
            case Constants.AC_ID -> reply(event, containingByValue(event, Mapper.getACJustNames()));
            case Constants.PROMISSORY_NOTE_ID ->
                reply(
                        event,
                        containing(
                                Mapper.getPromissoryNotes().values().stream(),
                                enteredLower(event),
                                pn -> pn.getAlias() + " " + pn.getName() + " " + pn.getOwner(),
                                PromissoryNoteModel::getAlias));
            case Constants.UNIT_ID,
                    Constants.UNIT_ID_1,
                    Constants.UNIT_ID_2,
                    Constants.UNIT_ID_3,
                    Constants.UNIT_ID_4,
                    Constants.UNIT_ID_5 -> {
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                Mapper.getUnits().values().stream()
                                        .filter(unit -> (unit.getId() + " " + unit.getName())
                                                .toLowerCase()
                                                .contains(enteredValue))
                                        .filter(model -> !model.getSource().isHiddenFromSearch()),
                                unit -> unit.getId() + " (" + unit.getName() + ")",
                                unit -> unit.getId()));
            }
            case Constants.DECAL_SET -> {
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                Mapper.getDecals().stream()
                                        .filter(value -> value.contains(enteredValue)
                                                || Mapper.getDecalName(value)
                                                        .toLowerCase()
                                                        .contains(enteredValue))
                                        .filter(decalID -> UnitDecalService.userMayUseDecal(
                                                event.getUser().getId(), decalID)),
                                value -> Mapper.getDecalName(value),
                                value -> value));
            }
            case Constants.CATEGORY -> {
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                JdaService.guilds.stream()
                                        .flatMap(guild -> guild.getCategories().stream())
                                        .filter(c -> c.getName().toLowerCase().contains(enteredValue)),
                                c -> c.getGuild().getName() + ": #" + c.getName(),
                                c -> c.getName()));
            }
            case Constants.DECK_NAME -> reply(event, decksByAlias(event, deck -> true));
            case Constants.AC_DECK -> reply(event, decksByAlias(event, deck -> deck.getType() == DeckType.ACTION_CARD));
            case Constants.STRATEGY_CARD_SET -> {
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                Mapper.getStrategyCardSets().values().stream()
                                        .filter(scSet -> !"template".equals(scSet.getAlias()))
                                        .filter(scSet -> scSet.getAlias().contains(enteredValue)),
                                StrategyCardSetModel::getName,
                                StrategyCardSetModel::getAlias));
            }
            case Constants.BORDER_TYPE ->
                reply(
                        event,
                        toChoices(
                                Mapper.getBorderAnomalies().stream()
                                        .filter(anomaly ->
                                                anomaly.getName().toLowerCase().contains(enteredLower(event))),
                                BorderAnomalyModel::getAutoCompleteName,
                                BorderAnomalyModel::getId));
            case Constants.AUTO_ARCHIVE_DURATION ->
                queue(event.replyChoiceStrings("1_HOUR", "24_HOURS", "3_DAYS", "1_WEEK"));
            case Constants.PLANET_TYPE ->
                replyStrings(
                        event,
                        Arrays.stream(PlanetTypeModel.PlanetType.values()).map(Object::toString));
            case Constants.PLANET_TECH_SKIPS ->
                replyStrings(
                        event,
                        Arrays.stream(TechSpecialtyModel.TechSpecialty.values()).map(Object::toString));
            case Constants.TILE_TYPE ->
                replyStrings(
                        event,
                        Arrays.stream(ShipPositionModel.ShipPosition.values())
                                .map(ShipPositionModel.ShipPosition::getTypeString));
            case Constants.TILE_WORMHOLES ->
                replyStrings(
                        event,
                        Arrays.stream(WormholeModel.Wormhole.values()).limit(25).map(Object::toString));
            case Constants.UNIT_SOURCE -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Mapper.getUnitSourcesDistinct().stream().filter(token -> token.contains(enteredValue)),
                                token -> token,
                                token -> token));
            }
            case Constants.SOURCE -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Stream.of(ComponentSource.values())
                                        .map(ComponentSource::toString)
                                        .filter(token -> token.contains(enteredValue)),
                                token -> token,
                                token -> token));
            }
            case Constants.GAME_NAME -> replyContaining(event, GameManager.getGameNames());
            case Constants.GAME_STATISTIC -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Arrays.stream(GameStatTypes.values())
                                        .filter(stat -> stat.search(enteredValue))
                                        .limit(25)
                                        .sorted(Comparator.comparing(GameStatTypes::getAutoCompleteName)),
                                GameStatTypes::getAutoCompleteName,
                                GameStatTypes::toString));
            }
            case Constants.PLAYER_STATISTIC -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Arrays.stream(PlayerStatTypes.values())
                                        .filter(stat -> stat.search(enteredValue))
                                        .limit(25)
                                        .sorted(Comparator.comparing(PlayerStatTypes::getAutoCompleteName)),
                                PlayerStatTypes::getAutoCompleteName,
                                PlayerStatTypes::toString));
            }
            case Constants.USE_MAP_TEMPLATE, Constants.MAP_TEMPLATE ->
                reply(
                        event,
                        containing(
                                Mapper.getMapTemplates().stream(),
                                enteredLower(event),
                                MapTemplateModel::autoCompleteString,
                                MapTemplateModel::getAlias));
            case Constants.DRAFT_MODE -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Arrays.stream(FrankenDraftMode.values())
                                        .filter(mode -> mode.search(enteredValue))
                                        .limit(25)
                                        .sorted(Comparator.comparing(FrankenDraftMode::getAutoCompleteName)),
                                FrankenDraftMode::getAutoCompleteName,
                                FrankenDraftMode::toString));
            }
            case Constants.PROMOTE_TARGET -> reply(event, promoteChoices(event, ServerPromoteCommand.servers));
            case Constants.PROMOTE_RANK -> reply(event, promoteChoices(event, ServerPromoteCommand.ranks));
            case "draft_pick" -> {
                Game game = getGame(gameName);
                if (game == null) return;
                replyContaining(event, game.getMiltyDraftManager().allRemainingOptionsForActive());
            }
            case Constants.RELIC -> {
                Game game = getGame(gameName);
                if (game == null) return;
                List<String> tableRelics = new ArrayList<>(
                        Mapper.getDecks().get(game.getRelicDeckID()).getNewShuffledDeck());
                if (!isFogRestricted(event, game)) {
                    for (Player player : game.getRealPlayers()) {
                        for (String relic : player.getRelics()) {
                            if (Mapper.getRelic(relic) != null
                                    && Mapper.getRelic(relic).isFakeRelic()
                                    && !tableRelics.contains(relic)) {
                                tableRelics.add(relic);
                            }
                        }
                    }
                }
                Collections.shuffle(tableRelics);
                replyContaining(event, tableRelics);
            }
            case Constants.LATEST_COMMAND -> {
                Game game = getGame(gameName);
                if (game == null) return;
                String latestCommand = isFogRestricted(event, game)
                        ? "Game is Fog of War mode - last command is hidden."
                        : StringUtils.left(game.getLatestCommand(), 100);
                queue(event.replyChoice(latestCommand, Constants.LATEST_COMMAND));
            }
            case Constants.UNDO_TO_COMMAND -> {
                Game game = getGame(gameName);
                if (game == null) return;
                if (isFogRestricted(event, game)) {
                    queue(event.replyChoiceStrings("Game is Fog of War mode - you can't see what you are undoing."));
                    return;
                }
                reply(
                        event,
                        toChoices(
                                GameUndoNameService.getUndoNamesToCommandText(game, 25).entrySet().stream()
                                        .sorted(Map.Entry.comparingByKey(
                                                Comparator.comparing(GameUndoNameService::getUndoNumberFromFileName)
                                                        .reversed())),
                                entry -> StringUtils.left(entry.getValue(), 100),
                                Map.Entry::getKey));
            }
            case Constants.TILE_NAME, Constants.TILE_NAME_FROM, Constants.TILE_NAME_TO, Constants.HS_TILE_POSITION -> {
                Game game = getGame(gameName);
                if (game == null) return;
                if (Constants.ADD_FOG_TILE.equals(subcommandName) && Constants.TILE_NAME.equals(optionName)) {
                    reply(event, searchModels(event, TileHelper.getAllTileModels(), null));
                } else if (game.isFowMode()) {
                    replyContaining(event, game.getTileMap().keySet());
                } else {
                    reply(
                            event,
                            containing(
                                    game.getTileNameAutocompleteOptionsCache().stream(),
                                    enteredLower(event),
                                    Map.Entry::getKey,
                                    Map.Entry::getValue));
                }
            }
            case Constants.PLANET,
                    Constants.PLANET2,
                    Constants.PLANET3,
                    Constants.PLANET4,
                    Constants.PLANET5,
                    Constants.PLANET6 -> {
                String enteredValue = enteredLower(event);
                Stream<Map.Entry<String, String>> planets = Mapper.getPlanetRepresentations().entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue));
                ManagedGame managedGame = GameManager.getManagedGame(gameName);
                if (managedGame != null && !managedGame.isFowMode()) {
                    Game game = managedGame.getGame();
                    Set<String> planetIDs = game.getPlanets();
                    reply(
                            event,
                            toChoices(
                                    planets.filter(value -> planetIDs.isEmpty() || planetIDs.contains(value.getKey())),
                                    value -> value.getValue() + " ("
                                            + Helper.getPlanetResources(value.getKey(), game) + "/"
                                            + Helper.getPlanetInfluence(value.getKey(), game) + ")",
                                    Map.Entry::getKey));
                } else {
                    reply(event, toChoices(planets, Map.Entry::getValue, Map.Entry::getKey));
                }
            }
            case Constants.LEADER, Constants.LEADER_1, Constants.LEADER_2, Constants.LEADER_3, Constants.LEADER_4 -> {
                Game game = getGame(gameName);
                if (game == null) return;
                List<String> leaderIDs = new ArrayList<>();
                if (game.isFowMode() || Constants.LEADER_ADD.equals(event.getSubcommandName())) {
                    leaderIDs.addAll(Mapper.getLeaders().keySet());
                } else {
                    leaderIDs.addAll(List.of("agent", "commander", "hero"));
                    for (Player player : game.getPlayers().values()) {
                        leaderIDs.addAll(player.getLeaderIDs());
                    }
                }
                replyContaining(event, leaderIDs);
            }
            case Constants.TF_PARADIGM -> {
                Game game = getGame(gameName);
                String deckID = game == null ? "tf_paradigm" : game.getParadigmSpliceDeckID();
                replyContaining(event, Mapper.getDeck(deckID).getNewDeck());
            }
            case Constants.TECH, Constants.TECH2, Constants.TECH3, Constants.TECH4 -> {
                Game game = getGame(gameName);
                if (game == null) return;
                Map<String, TechnologyModel> techs = isFogRestricted(event, game)
                        ? Mapper.getTechs()
                        : Mapper.getTechs().entrySet().stream()
                                .filter(entry -> game.getTechnologyDeck().contains(entry.getKey()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                techs.entrySet().stream()
                                        .filter(value -> value.getValue()
                                                .getName()
                                                .toLowerCase()
                                                .contains(enteredValue)),
                                value -> value.getValue().getName() + " ("
                                        + value.getValue().getSource() + ")",
                                Map.Entry::getKey));
            }
            case Constants.BREAKTHROUGH -> {
                Game game = getGame(gameName);
                if (game == null) return;
                String enteredValue = enteredLower(event);
                boolean allBreakthroughs = Constants.FRANKEN.equals(event.getName()) || isFogRestricted(event, game);
                Set<BreakthroughModel> btSet = allBreakthroughs
                        ? new HashSet<>(Mapper.getBreakthroughs().values())
                        : game.getPlayers().values().stream()
                                .flatMap(p -> p.getBreakthroughIDs().stream())
                                .map(Mapper::getBreakthrough)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
                boolean addAllOpt = !allBreakthroughs
                        && !Constants.BREAKTHROUGH_SET_TG.equalsIgnoreCase(subcommandName)
                        && game.getPlayers().values().stream()
                                .anyMatch(p -> p.getBreakthroughIDs().size() > 1);

                List<Command.Choice> options = btSet.stream()
                        .filter(bt -> bt.getAutoCompleteName(game).toLowerCase().contains(enteredValue))
                        .limit(addAllOpt ? 24 : 25)
                        .map(bt -> new Command.Choice(bt.getAutoCompleteName(game), bt.getAlias()))
                        .collect(Collectors.toList());
                if (addAllOpt) {
                    options.addFirst(new Command.Choice("all", "all"));
                }
                reply(event, options);
            }
            case Constants.PLAYER_FACTION -> {
                Game game = getGame(gameName);
                if (game == null) return;
                Function<Player, String> getDisp =
                        p -> game.isFowMode() ? p.getUserName() : p.getAutoCompleteRepresentation();
                reply(
                        event,
                        containing(
                                game.getPlayers().values().stream(),
                                enteredLower(event).trim(),
                                getDisp,
                                Player::getUserID));
            }
            case Constants.FACTION_COLOR, Constants.TARGET_FACTION_OR_COLOR -> {
                Game game = getGame(gameName);
                if (game == null) return;
                if (game.isFowMode()) {
                    List<String> factionColors = new ArrayList<>(Mapper.getFactionIDs());
                    factionColors.addAll(Mapper.getColorNames());

                    List<String> factionColorsRetain = new ArrayList<>();
                    boolean privateGame = FoWHelper.isPrivateGame(game, null, event.getChannel());
                    for (Player player : game.getPlayers().values()) {
                        if (!privateGame) {
                            factionColorsRetain.add(player.getFaction());
                        }
                        factionColorsRetain.add(player.getColor());
                    }
                    factionColors.retainAll(factionColorsRetain);
                    replyContaining(event, factionColors);
                } else {
                    reply(
                            event,
                            containing(
                                    game.getPlayers().values().stream(),
                                    enteredLower(event),
                                    Player::getAutoCompleteRepresentation,
                                    Player::getColor));
                }
            }
            case Constants.ABILITY,
                    Constants.ABILITY_1,
                    Constants.ABILITY_2,
                    Constants.ABILITY_3,
                    Constants.ABILITY_4,
                    Constants.ABILITY_5 -> {
                Game game = getGame(gameName);
                if (game == null) return;
                String enteredValue = enteredLower(event);
                Map<String, AbilityModel> abilities = abilityOptions(event, game, subcommandName);
                reply(
                        event,
                        toChoices(
                                abilities.entrySet().stream()
                                        .filter(value -> value.getValue().search(enteredValue)),
                                value -> value.getValue().getAutoCompleteName(),
                                Map.Entry::getKey));
            }
            case Constants.RANDOM_TYPE -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Arrays.stream(RandomOption.values()).filter(value -> value.search(enteredValue)),
                                RandomOption::getAutoCompleteName,
                                RandomOption::toString));
            }
            case Constants.PRIORITY_TRACK -> {
                String enteredValue = entered(event);
                reply(
                        event,
                        toChoices(
                                Arrays.stream(PriorityTrackMode.values()).filter(value -> value.search(enteredValue)),
                                PriorityTrackMode::getAutoCompleteName,
                                PriorityTrackMode::toString));
            }
            case Constants.ADD_DRAFTABLE_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                List<Draftable> alreadyInDraft = draftManager.getDraftables();
                reply(
                        event,
                        containingSelf(
                                DraftComponentFactory.getKnownDraftableClasses().stream()
                                        .filter(option -> alreadyInDraft.stream()
                                                .noneMatch(draftable ->
                                                        draftable.getType().equals(DraftableType.of(option)))),
                                enteredLower(event)));
            }
            case Constants.SET_ORCHESTRATOR_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                List<String> orchestratorOptions = DraftComponentFactory.getKnownOrchestratorClasses();
                DraftOrchestrator alreadyInDraft = draftManager.getOrchestrator();
                if (alreadyInDraft != null) {
                    orchestratorOptions.remove(alreadyInDraft.getClass().getSimpleName());
                }
                reply(event, containingSelf(orchestratorOptions.stream(), enteredLower(event)));
            }
            case Constants.UNKNOWN_DRAFT_USER_ID_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                Game game = draftManager.getGame();
                Predicate<String> isUnknownUserId =
                        id -> game.getPlayer(id) == null || event.getGuild().getMemberById(id) == null;
                List<String> userIDs = draftManager.getPlayerUserIds().stream()
                        .filter(isUnknownUserId)
                        .toList();
                reply(event, containingSelf(userIDs.stream(), enteredLower(event)));
            }
            case Constants.DRAFTABLE_TYPE_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                reply(
                        event,
                        containingSelf(
                                draftManager.getDraftables().stream()
                                        .map(Draftable::getType)
                                        .map(DraftableType::toString)
                                        .distinct(),
                                enteredLower(event)));
            }
            case Constants.DRAFTABLE_CHOICE_KEY_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                String enteredValue = enteredLower(event);
                DraftableType draftableType = focusedDraftableType(event, draftManager);
                if (draftableType == null) return;

                List<DraftChoice> alreadyPicked = draftManager.getAllPicksOfType(draftableType);
                Predicate<DraftChoice> notPicked = choice -> alreadyPicked.stream()
                        .noneMatch(picked -> picked.choiceKey().equals(choice.choiceKey()));
                Predicate<DraftChoice> matchesEnteredText =
                        choice -> choice.choiceKey().toLowerCase().contains(enteredValue)
                                || choice.unformattedName().toLowerCase().contains(enteredValue);
                reply(
                        event,
                        toChoices(
                                draftManager.getDraftable(draftableType).getAllDraftChoices().stream()
                                        .filter(notPicked)
                                        .filter(matchesEnteredText),
                                DraftChoice::unformattedName,
                                DraftChoice::choiceKey));
            }
            case Constants.DRAFT_FACTION_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                FactionDraftable draftable = (FactionDraftable) draftManager.getDraftable(FactionDraftable.TYPE);
                if (draftable == null) return;
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                draftable.getAllDraftChoices().stream()
                                        .map(choice -> Mapper.getFactions().get(choice.choiceKey()))
                                        .filter(Objects::nonNull)
                                        .filter(faction -> faction.getAutoCompleteName()
                                                .toLowerCase()
                                                .contains(enteredValue)),
                                FactionModel::getFactionName,
                                FactionModel::getAlias));
            }
            case Constants.KELERES_FLAVOR_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                FactionDraftable draftable = (FactionDraftable) draftManager.getDraftable(FactionDraftable.TYPE);
                if (draftable == null) return;
                reply(event, keleresFlavorChoices(event, draftManager, draftable));
            }
            case Constants.DEBT_POOL -> {
                Game game = getGame(gameName);
                if (game == null) return;
                Set<String> pools = new HashSet<>(game.getAllDebtPoolIcons().keySet());
                pools.add(Constants.DEBT_DEFAULT_POOL.toLowerCase());
                replyContaining(event, pools);
            }
            case Constants.SEAT_COUNT_OPTION -> {
                if (!entered(event).isBlank()) return;
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                if (draftManager.getDraftable(SeatDraftable.TYPE) == null) return;
                String mapTemplateId = draftManager.getGame().getMapTemplateID();
                if (mapTemplateId == null) return;
                MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
                if (mapTemplate == null) return;
                int maxSeats = mapTemplate.getPlayerCount();
                queue(event.replyChoice(maxSeats + " seats", maxSeats));
            }
            case Constants.DRAFT_SLICE_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                SliceDraftable draftable = (SliceDraftable) draftManager.getDraftable(SliceDraftable.TYPE);
                if (draftable == null) return;
                reply(
                        event,
                        containing(
                                draftable.getDraftSlices().stream(),
                                enteredLower(event),
                                slice -> slice.getName(),
                                slice -> slice.getName()));
            }
            case Constants.DRAFT_TILE_1_OPTION,
                    Constants.DRAFT_TILE_2_OPTION,
                    Constants.DRAFT_TILE_3_OPTION,
                    Constants.DRAFT_TILE_4_OPTION,
                    Constants.DRAFT_TILE_5_OPTION,
                    Constants.DRAFT_TILE_6_OPTION,
                    Constants.DRAFT_TILE_7_OPTION,
                    Constants.DRAFT_TILE_8_OPTION,
                    Constants.DRAFT_TILE_9_OPTION -> {
                Game game = getGame(gameName);
                if (game == null) return;
                DraftTileManager draftTileManager = game.getDraftTileManager();
                if (draftTileManager.getAll().isEmpty()) {
                    DraftTileManager.resetForGame(game);
                }
                String enteredValue = enteredLower(event);
                Function<MiltyDraftTile, String> representation = tile ->
                        Mapper.getTileRepresentations().get(tile.getTile().getTileID());
                reply(
                        event,
                        toChoices(
                                draftTileManager.getAll().stream().filter(tile -> {
                                    String text = representation.apply(tile);
                                    return text != null && text.toLowerCase().contains(enteredValue);
                                }),
                                tile -> representation.apply(tile) + " ("
                                        + tile.getTile().getTileID() + ")",
                                tile -> tile.getTile().getTileID()));
            }
            case Constants.PICK_COUNT_OPTION -> {
                if (!entered(event).isBlank()) return;
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                if (draftManager.getDraftable(SpeakerOrderDraftable.TYPE) == null) return;
                int maxSeats = Math.min(draftManager.getPlayerStates().size(), 8);
                queue(event.replyChoice(maxSeats + " speaker order positions", maxSeats));
            }
            case Constants.PLAYER_PICKS_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                DraftableType draftableType = focusedDraftableType(event, draftManager);
                if (draftableType == null) return;
                reply(
                        event,
                        containing(
                                draftManager.getAllPicksOfType(draftableType).stream(),
                                enteredLower(event),
                                DraftChoice::unformattedName,
                                DraftChoice::choiceKey));
            }
            case Constants.PACKAGE_KEY_OPTION -> {
                DraftManager draftManager = getDraftManager(gameName);
                if (draftManager == null) return;
                AndcatReferenceCardsDraftable draftable =
                        (AndcatReferenceCardsDraftable) draftManager.getDraftable(AndcatReferenceCardsDraftable.TYPE);
                if (draftable == null) return;
                reply(
                        event,
                        draftable.getReferenceCardPackages().keySet().stream()
                                .map(key -> new Command.Choice(key.toString(), key))
                                .collect(Collectors.toList()));
            }
        }
    }

    private static Map<String, AbilityModel> abilityOptions(
            CommandAutoCompleteInteractionEvent event, Game game, String subcommandName) {
        try {
            Player player = CommandHelper.getPlayerFromGame(
                    game, event.getMember(), event.getUser().getId());
            if (player != null && Constants.ABILITY_REMOVE.equals(subcommandName)) {
                Map<String, AbilityModel> abilities = new HashMap<>();
                for (String abilityID : player.getAbilities()) {
                    abilities.put(abilityID, Mapper.getAbilities().get(abilityID));
                }
                return abilities;
            }
            Map<String, AbilityModel> abilities = Mapper.getAbilities();
            if (player != null && Constants.ABILITY_ADD.equals(subcommandName)) {
                for (String abilityID : player.getAbilities()) {
                    abilities.remove(abilityID);
                }
            }
            return abilities;
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(event), "Ability Autocomplete Setup Error", e);
            return Mapper.getAbilities();
        }
    }

    private static List<Command.Choice> keleresFlavorChoices(
            CommandAutoCompleteInteractionEvent event, DraftManager draftManager, FactionDraftable draftable) {
        String enteredValue = enteredLower(event);
        List<DraftChoice> choices = draftable.getAllDraftChoices();
        List<DraftChoice> pickedFactions = draftManager.getAllPicksOfType(FactionDraftable.TYPE);

        List<String> keleresFlavors = FactionDraftable.getKeleresFlavors();
        List<String> alreadyPickedFlavors = keleresFlavors.stream()
                .filter(faction -> pickedFactions.stream()
                        .anyMatch(picked -> picked.choiceKey().equals(faction)))
                .toList();
        List<String> possiblyPickedFlavors = keleresFlavors.stream()
                .filter(faction -> alreadyPickedFlavors.stream().noneMatch(picked -> picked.equals(faction)))
                .filter(faction ->
                        choices.stream().anyMatch(choice -> choice.choiceKey().equals(faction)))
                .toList();
        List<String> safeFlavors = keleresFlavors.stream()
                .filter(faction -> possiblyPickedFlavors.stream().noneMatch(picked -> picked.equals(faction)))
                .toList();
        List<Command.Choice> options = new ArrayList<>(keleresChoices(safeFlavors, enteredValue, "", 25));
        options.addAll(keleresChoices(possiblyPickedFlavors, enteredValue, " (may be taken!!)", 25 - options.size()));
        if (options.isEmpty()) {
            options.addAll(keleresChoices(keleresFlavors, enteredValue, " (all playable)", 25));
        }
        return options;
    }

    private static List<Command.Choice> keleresChoices(
            List<String> flavors, String enteredValue, String suffix, int limit) {
        return flavors.stream()
                .filter(faction -> faction.toLowerCase().contains(enteredValue))
                .limit(limit)
                .map(faction ->
                        new Command.Choice(Mapper.getFactions().get(faction).getFactionName() + suffix, faction))
                .toList();
    }

    private static DraftableType focusedDraftableType(
            CommandAutoCompleteInteractionEvent event, DraftManager draftManager) {
        String draftableTypeStr = event.getOption(Constants.DRAFTABLE_TYPE_OPTION, null, OptionMapping::getAsString);
        if (draftableTypeStr == null) return null;
        DraftableType draftableType = DraftableType.of(draftableTypeStr);
        return draftManager.getDraftable(draftableType) == null ? null : draftableType;
    }

    private static void resolveActionCardDiscardAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName,
            @NotNull String gameName) {
        if (!Constants.ACTION_CARD_ID.equals(optionName)) return;
        if (!Constants.PICK_AC_FROM_DISCARD.equals(subCommandName)
                && !Constants.SHUFFLE_AC_BACK_INTO_DECK.equals(subCommandName)) return;
        String enteredValue = enteredLower(event);
        Game game = getGame(gameName);
        Player viewer = TestBedService.resolveActingPlayer(
                game, event, game.getPlayer(event.getUser().getId()));
        boolean hideUnplayed = ActionCardHelper.hidesUnplayedDiscards(game, viewer);
        List<Command.Choice> options = game.getDiscardActionCards().entrySet().stream()
                .filter(entry -> ActionCardHelper.isDiscardVisible(game, hideUnplayed, entry.getKey()))
                .map(entry -> Map.entry(Mapper.getActionCard(entry.getKey()), entry.getValue()))
                .filter(entry -> entry.getKey().getName().toLowerCase().contains(enteredValue))
                .limit(25)
                .map(entry ->
                        new Command.Choice(entry.getKey().getName() + " (" + entry.getValue() + ")", entry.getValue()))
                .toList();
        reply(event, options);
    }

    private static void resolveDeveloperCommandAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName) {
        switch (subCommandName) {
            case Constants.SET_SETTING -> {
                switch (optionName) {
                    case Constants.SETTING_TYPE -> queue(event.replyChoiceStrings("string", "number", "bool"));
                    case Constants.SETTING_NAME ->
                        replyContaining(
                                event,
                                Arrays.stream(GlobalSettings.ImplementedSettings.values())
                                        .map(GlobalSettings.ImplementedSettings::toString)
                                        .toList());
                }
            }
            case Constants.RUN_CRON -> {
                if (!Constants.CRON_NAME.equals(optionName)) return;
                replyContaining(event, CronManager.getCronNames());
            }
            default -> {}
        }
    }

    private static void resolveSearchCommandAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName) {
        if (!Constants.SEARCH.equals(optionName)) return;
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        List<Command.Choice> options =
                switch (subCommandName) {
                    case Constants.SEARCH_ABILITIES ->
                        searchModels(event, Mapper.getAbilities().values(), source, true);
                    case Constants.SEARCH_ACTION_CARDS ->
                        searchModels(event, Mapper.getActionCards().values(), source, true);
                    case Constants.SEARCH_AGENDAS ->
                        searchModels(event, Mapper.getAgendas().values(), source, true);
                    case Constants.SEARCH_GALACTIC_EVENTS ->
                        searchModels(event, Mapper.getGalacticEvents().values(), source, true);
                    case Constants.SEARCH_RULES ->
                        searchModels(event, Mapper.getRules().values(), source);
                    case Constants.SEARCH_BREAKTHROUGHS ->
                        searchModels(event, Mapper.getBreakthroughs().values(), source, true);
                    case Constants.SEARCH_ATTACHMENTS ->
                        searchModels(event, Mapper.getAttachments().values(), source);
                    case Constants.SEARCH_DECKS ->
                        searchModels(event, Mapper.getDecks().values(), source);
                    case Constants.SEARCH_EVENTS ->
                        searchModels(event, Mapper.getEvents().values(), source);
                    case Constants.SEARCH_EXPLORES ->
                        searchModels(event, Mapper.getExplores().values(), source, true);
                    case Constants.SEARCH_FACTIONS ->
                        searchModels(event, Mapper.getFactions().values(), source);
                    case Constants.SEARCH_LEADERS, Constants.SEARCH_GENOMES, Constants.SEARCH_PARADIGMS ->
                        searchModels(event, Mapper.getLeaders().values(), source, true);
                    case Constants.SEARCH_PROMISSORY_NOTES ->
                        searchModels(event, Mapper.getPromissoryNotes().values(), source, true);
                    case Constants.SEARCH_PUBLIC_OBJECTIVES ->
                        searchModels(event, Mapper.getPublicObjectives().values(), source, true);
                    case Constants.SEARCH_RELICS ->
                        searchModels(event, Mapper.getRelics().values(), source, true);
                    case Constants.SEARCH_SECRET_OBJECTIVES ->
                        searchModels(event, Mapper.getSecretObjectives().values(), source, true);
                    case Constants.SEARCH_TECHS ->
                        searchModels(event, Mapper.getTechs().values(), source, true);
                    case Constants.SEARCH_TOKENS ->
                        searchModels(event, Mapper.getTokens().values(), source);
                    case Constants.SEARCH_UNITS ->
                        searchModels(event, Mapper.getUnits().values(), source, true);
                    case Constants.SEARCH_TILES -> searchModels(event, TileHelper.getAllTileModels(), source);
                    case Constants.SEARCH_PLANETS -> searchModels(event, TileHelper.getAllPlanetModels(), source);
                    default -> Collections.emptyList();
                };
        reply(event, options);
    }

    private static void resolveFindAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event, @NotNull String optionName) {
        List<Command.Choice> options =
                switch (optionName) {
                    case Constants.SEARCH_TYPE -> FindService.autoCompleteType(event);
                    case Constants.SOURCE -> FindService.autoCompleteSource(event);
                    default -> Collections.emptyList();
                };
        reply(event, options);
    }

    private static void resolveTestBedAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event, @NotNull String optionName) {
        if (!TestBedService.isEnabled() || !TestBedService.isDeveloper(event.getMember())) {
            reply(event, List.of());
            return;
        }
        Game game = getGame(GameNameService.getGameNameFromChannel(event));
        List<Command.Choice> choices = TestBedAutoComplete.choices(optionName, game, entered(event));
        if (choices == null) return;
        reply(event, choices);
    }

    private static void resolveFrankenAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName) {
        boolean leaderOption =
                switch (optionName) {
                    case Constants.LEADER,
                            Constants.LEADER_1,
                            Constants.LEADER_2,
                            Constants.LEADER_3,
                            Constants.LEADER_4 -> true;
                    default -> false;
                };
        boolean techOption =
                switch (optionName) {
                    case Constants.TECH, Constants.TECH2, Constants.TECH3, Constants.TECH4 -> true;
                    default -> false;
                };
        switch (subCommandName) {
            case Constants.FACTION_TECH_ADD, Constants.FACTION_TECH_REMOVE -> {
                if (techOption) reply(event, factionTechChoices(event, true));
            }
            case Constants.LEADER_ADD, Constants.LEADER_REMOVE -> {
                if (leaderOption)
                    reply(event, searchModels(event, Mapper.getLeaders().values(), null));
            }
            case Constants.BAN -> {
                if (leaderOption) {
                    reply(event, searchModels(event, Mapper.getLeaders().values(), null));
                } else if (techOption) {
                    reply(event, factionTechChoices(event, false));
                } else if (Constants.TILE_NAME.equals(optionName)) {
                    reply(event, searchModels(event, TileHelper.getAllTileModels(), null));
                }
            }
        }
    }

    private static List<Command.Choice> factionTechChoices(
            CommandAutoCompleteInteractionEvent event, boolean excludeHidden) {
        String enteredValue = enteredLower(event);
        return toChoices(
                Mapper.getTechs().values().stream()
                        .filter(entry -> entry.getFaction().isPresent())
                        .filter(entry -> entry.search(enteredValue))
                        .filter(model -> !excludeHidden || !model.getSource().isHiddenFromSearch()),
                TechnologyModel::getAutoCompleteName,
                TechnologyModel::getAlias);
    }

    private static void resolvePlotAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event, @NotNull String optionName) {
        if (!Constants.PLOT_CARDS.equals(optionName)) return;
        String enteredValue = enteredLower(event);
        reply(
                event,
                toChoices(
                        Mapper.getPlots().values().stream().filter(plot -> plot.search(enteredValue)),
                        plot -> plot.getAutoCompleteName(),
                        plot -> plot.getAlias()));
    }

    private static void resolveMapAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName,
            @NotNull String gameName) {
        switch (subCommandName) {
            case Constants.ADD_TILE -> {
                if (!Constants.TILE_NAME.equals(optionName)) return;
                reply(event, searchModels(event, TileHelper.getAllTileModels(), null));
            }
            case Constants.REMOVE_TILE -> {
                if (!Constants.POSITION.equals(optionName)) return;
                Game game = getGame(gameName);
                String enteredValue = enteredLower(event);
                reply(
                        event,
                        toChoices(
                                game.getTileMap().entrySet().stream()
                                        .filter(entry -> entry.getValue().search(enteredValue)),
                                entry -> entry.getValue().getAutoCompleteName(),
                                Map.Entry::getKey));
            }
            case Constants.PRESET -> {
                if (!Constants.MAP_TEMPLATE.equals(optionName)) return;
                replyContaining(event, MapPresetService.templates);
            }
        }
    }

    private static void resolveEventAutoComplete(
            CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName, String gameName) {
        if (!Constants.EVENT_PLAY.equals(subCommandName)) return;
        if (!Constants.EVENT_ID.equals(optionName)) return;
        Game game = getGame(gameName);
        Player player = CommandHelper.getPlayerFromGame(
                game, event.getMember(), event.getUser().getId());
        String enteredValue = enteredLower(event);
        List<Command.Choice> options = new HashMap<>(player.getEvents())
                .entrySet().stream()
                        .filter(entry -> entry.getKey().contains(enteredValue))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue() + " " + entry.getKey(), entry.getValue()))
                        .toList();
        reply(event, options);
    }

    private static void resolveExploreAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName,
            @NotNull String gameName) {
        if (!Constants.USE.equals(subCommandName)) return;
        if (!Constants.EXPLORE_CARD_ID.equals(optionName)) return;
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame.isFowMode()) {
            queue(event.replyChoice("You cannot see the autocomplete in Fog of War", "[error]"));
            return;
        }

        String enteredValue = enteredLower(event);
        reply(
                event,
                toChoices(
                        managedGame.getGame().getAllExplores().stream()
                                .map(Mapper::getExplore)
                                .filter(Objects::nonNull)
                                .filter(e -> e.search(enteredValue))
                                .limit(25)
                                .sorted(Comparator.comparing(ExploreModel::getAutoCompleteName)),
                        ExploreModel::getAutoCompleteName,
                        ExploreModel::getId));
    }

    private static void resolveStatusAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName,
            @NotNull String gameName) {
        if (!Constants.SHUFFLE_OBJECTIVE_BACK.equals(subCommandName)
                && !Constants.SCORE_OBJECTIVE.equals(subCommandName)
                && !Constants.UNSCORE_OBJECTIVE.equals(subCommandName)) {
            return;
        }
        if (!Constants.PO_ID.equals(optionName)) {
            return;
        }
        reply(
                event,
                numberedChoices(
                        event,
                        getGame(gameName).getRevealedPublicObjectives(),
                        Map.Entry.comparingByValue(),
                        e -> e.getValue() + " - " + e.getKey()));
    }

    private static void resolveAgendaAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName,
            @NotNull String gameName) {
        if (!Constants.AGENDA_ID.equals(optionName)) {
            return;
        }
        Game game = getGame(gameName);
        Map<String, Integer> agendas =
                switch (subCommandName) {
                    case Constants.ADD_LAW, Constants.PUT_DISCARD_BACK_INTO_DECK -> game.getDiscardAgendas();
                    case Constants.REVISE_LAW, Constants.ADD_CONTROL_TOKEN, Constants.REMOVE_LAW -> game.getLaws();
                    default -> null;
                };
        if (agendas == null) return;
        reply(
                event,
                numberedChoices(
                        event,
                        agendas,
                        Map.Entry.comparingByKey(),
                        e -> e.getValue() + " - " + Mapper.getAgenda(e.getKey()).getAutoCompleteName()));
    }

    private static void resolveSecretObjectiveAutoComplete(
            @NotNull CommandAutoCompleteInteractionEvent event,
            @NotNull String subCommandName,
            @NotNull String optionName,
            @NotNull String gameName) {
        if (!Constants.SECRET_OBJECTIVE_ID.equals(optionName) && !Constants.SO_ID.equals(optionName)) return;
        Game game = getGame(gameName);
        Player player = CommandHelper.getPlayerFromGame(
                game, event.getMember(), event.getUser().getId());
        if (player == null) return;
        switch (subCommandName) {
            case Constants.UNSCORE_SO ->
                reply(
                        event,
                        numberedChoices(
                                event,
                                player.getSecretsScored(),
                                Map.Entry.comparingByKey(),
                                e -> e.getValue() + " - "
                                        + Mapper.getSecretObjective(e.getKey()).getAutoCompleteName()));
            case Constants.SCORE_SO, Constants.DISCARD_SO, Constants.SHOW_SO, Constants.SHOW_TO_ALL ->
                reply(
                        event,
                        numberedChoices(
                                event,
                                player.getSecretsUnscored(),
                                Map.Entry.comparingByValue(),
                                e -> e.getValue() + ""));
            case Constants.DRAW_SPECIFIC_SO -> {
                String enteredValue = enteredLower(event);
                DeckModel soDeck = Mapper.getDeck(player.getGame().getSoDeckID());
                reply(
                        event,
                        toChoices(
                                soDeck.getNewDeck().stream()
                                        .map(Mapper::getSecretObjective)
                                        .filter(soModel -> soModel.search(enteredValue))
                                        .sorted(Comparator.comparing(SecretObjectiveModel::getName)),
                                SecretObjectiveModel::getAutoCompleteName,
                                SecretObjectiveModel::getAlias));
            }
        }
    }

    private static Game getGame(String gameName) {
        return GameManager.isValid(gameName)
                ? GameManager.getManagedGame(gameName).getGame()
                : null;
    }

    private static DraftManager getDraftManager(String gameName) {
        Game game = getGame(gameName);
        return game != null && DraftManager.hasDraftManager(game) ? game.getDraftManager() : null;
    }

    private static boolean isFogRestricted(CommandAutoCompleteInteractionEvent event, Game game) {
        return game.isFowMode() && !FoWHelper.isGameMaster(event.getUser().getId(), game);
    }

    private static String entered(CommandAutoCompleteInteractionEvent event) {
        return event.getFocusedOption().getValue();
    }

    private static String enteredLower(CommandAutoCompleteInteractionEvent event) {
        return entered(event).toLowerCase();
    }

    private static void queue(AutoCompleteCallbackAction action) {
        action.queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void reply(CommandAutoCompleteInteractionEvent event, List<Command.Choice> choices) {
        queue(event.replyChoices(choices));
    }

    private static void replyStrings(CommandAutoCompleteInteractionEvent event, Stream<String> values) {
        queue(event.replyChoiceStrings(values.toList()));
    }

    private static void replyContaining(CommandAutoCompleteInteractionEvent event, Collection<String> toFilter) {
        reply(event, mapTo25ChoicesThatContain(toFilter, entered(event)));
    }

    private static <T> List<Command.Choice> toChoices(
            Stream<T> items, Function<T, String> label, Function<T, String> value) {
        return items.limit(25)
                .map(item -> new Command.Choice(label.apply(item), value.apply(item)))
                .toList();
    }

    private static <T> List<Command.Choice> containing(
            Stream<T> items, String enteredLower, Function<T, String> label, Function<T, String> value) {
        return toChoices(items.filter(item -> label.apply(item).toLowerCase().contains(enteredLower)), label, value);
    }

    private static List<Command.Choice> containingSelf(Stream<String> items, String enteredLower) {
        return containing(items, enteredLower, option -> option, option -> option);
    }

    private static List<Command.Choice> containingByValue(
            CommandAutoCompleteInteractionEvent event, Map<String, String> values) {
        return containing(values.entrySet().stream(), enteredLower(event), Map.Entry::getValue, Map.Entry::getKey);
    }

    private static List<Command.Choice> labelledChoices(
            CommandAutoCompleteInteractionEvent event, Map<String, String> values) {
        String enteredValue = Objects.toString(entered(event), "").toLowerCase();
        return containing(values.entrySet().stream(), enteredValue, Map.Entry::getValue, Map.Entry::getKey);
    }

    private static List<Command.Choice> decksByAlias(
            CommandAutoCompleteInteractionEvent event, Predicate<DeckModel> filter) {
        String enteredValue = enteredLower(event);
        return toChoices(
                Mapper.getDecks().values().stream()
                        .filter(filter)
                        .filter(deck -> deck.getAlias().contains(enteredValue)),
                DeckModel::getName,
                DeckModel::getAlias);
    }

    private static List<Command.Choice> promoteChoices(
            CommandAutoCompleteInteractionEvent event, Map<String, String> values) {
        String enteredValue = entered(event);
        return toChoices(
                ServerPromoteCommand.servers.keySet().stream()
                        .filter(key -> values.get(key).toLowerCase().contains(enteredValue)),
                key -> key,
                values::get);
    }

    private static List<Command.Choice> numberedChoices(
            CommandAutoCompleteInteractionEvent event,
            Map<String, Integer> entries,
            Comparator<Map.Entry<String, Integer>> order,
            Function<Map.Entry<String, Integer>, String> label) {
        String enteredValue = enteredLower(event);
        return entries.entrySet().stream()
                .filter(entry ->
                        (entry.getValue() + entry.getKey()).toLowerCase().contains(enteredValue))
                .sorted(order)
                .limit(25)
                .map(entry -> new Command.Choice(label.apply(entry), entry.getValue()))
                .toList();
    }

    private static <T extends ModelInterface & EmbeddableModel> List<Command.Choice> searchModels(
            CommandAutoCompleteInteractionEvent event, Collection<T> models, ComponentSource source) {
        return searchModels(event, models, source, false);
    }

    private static <T extends ModelInterface & EmbeddableModel> List<Command.Choice> searchModels(
            CommandAutoCompleteInteractionEvent event,
            Collection<T> models,
            ComponentSource source,
            boolean hideBySource) {
        String enteredValue = enteredLower(event);
        return models.stream()
                .filter(model -> model.getSource() != null)
                .filter(model -> model.search(enteredValue, source))
                .filter(model -> hideBySource
                        ? !model.getSource().isHiddenFromSearch(source)
                        : !model.getSource().isHiddenFromSearch())
                .filter(model -> !(model instanceof ColorableModelInterface cm) || !cm.isDupe())
                .limit(25)
                .map(model -> new Command.Choice(model.getAutoCompleteName(), model.getAlias()))
                .toList();
    }

    private static Map<String, String> hashMapOf(String[][] entries) {
        Map<String, String> map = new HashMap<>();
        for (String[] entry : entries) {
            map.put(entry[0], entry[1]);
        }
        return map;
    }

    private static List<Command.Choice> mapTo25ChoicesThatContain(Collection<String> toFilter, String toContain) {
        return toFilter.stream()
                .filter(string -> string.toLowerCase().contains(toContain.toLowerCase()))
                .sorted()
                .limit(25)
                .map(string -> new Command.Choice(string, string))
                .toList();
    }
}
