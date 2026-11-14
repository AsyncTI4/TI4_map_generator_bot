package ti4.service.tournament.setup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.json.JsonMapperManager;
import ti4.model.DeckModel;
import ti4.model.DeckModel.DeckType;
import ti4.model.Mapper;
import ti4.service.game.MonumentsService;
import ti4.service.megagame.MegagameGroupService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class TournamentSetupCopier {

    private record Flag(String label, Predicate<Game> get, BiConsumer<Game, Boolean> set) {}

    private record Mode(String label, Predicate<Game> isOn) {}

    private static final List<Flag> FLAGS = List.of(
            new Flag("Thunder's Edge", Game::isThundersEdge, Game::setThundersEdge),
            new Flag("Prophecy of Kings", Game::isProphecyOfKings, Game::setProphecyOfKings),
            new Flag("Twilight's Fall", Game::isTwilightsFallMode, Game::setTwilightsFallMode),
            new Flag("TK Destroyer Cup", Game::isTkDestroyerCup, Game::setTkDestroyerCup),
            new Flag("TK Nova Cup", Game::isTkNovaCup, Game::setTkNovaCup),
            new Flag("Twilight's Fall with Discordant Stars", Game::isTwilightDS, Game::setTwilightDS),
            new Flag("Twilight's Fall with Blue Reverie", Game::isTfBr, Game::setTfBr),
            new Flag("Homebrew", Game::isHomebrew, Game::setHomebrew),
            new Flag("Absol", Game::isAbsolMode, Game::setAbsolMode),
            new Flag("Discordant Stars", Game::isDiscordantStarsMode, Game::setDiscordantStarsMode),
            new Flag("Uncharted Space", Game::isUnchartedSpaceStuff, Game::setUnchartedSpaceStuff),
            new Flag("Blue Reverie", Game::isBlueReverieMode, Game::setBlueReverieMode),
            new Flag("Lost Legacies tiles", Game::isLostLegaciesTiles, Game::setLostLegaciesTiles),
            new Flag("Refracture", Game::isRefractureMode, Game::setRefractureMode),
            new Flag("Monuments", Game::isMonumentsMode, Game::setMonumentsMode),
            new Flag("Red Tape", Game::isRedTapeMode, Game::setRedTapeMode),
            new Flag("Alliance", Game::isAllianceMode, Game::setAllianceMode),
            new Flag("Promises Promises", Game::isPromisesPromisesMode, Game::setPromisesPromisesMode),
            new Flag("Flagshipping", Game::isFlagshippingMode, Game::setFlagshippingMode),
            new Flag("Extra secret", Game::isExtraSecretMode, Game::setExtraSecretMode),
            new Flag("No swap", Game::isNoSwapMode, Game::setNoSwapMode),
            new Flag("Veiled Heart", Game::isVeiledHeartMode, Game::setVeiledHeartMode),
            new Flag("Limited whispers", Game::isLimitedWhispersMode, Game::setLimitedWhispersMode),
            new Flag("Whispers disabled", Game::isWhispersDisabled, Game::setWhispersDisabled),
            new Flag("Reverse speaker order", Game::isReverseSpeakerOrder, Game::setReverseSpeakerOrder),
            new Flag("CPTI explores", Game::isCptiExploreMode, Game::setCptiExploreMode),
            new Flag("Ordinian C1", Game::isOrdinianC1Mode, Game::setOrdinianC1Mode),
            new Flag("Liberation C4", Game::isLiberationC4Mode, Game::setLiberationC4Mode),
            new Flag("Erwan's Gambit", Game::isErwansGambitMode, Game::setErwansGambitMode),
            new Flag("Age of Exploration", Game::isAgeOfExplorationMode, Game::setAgeOfExplorationMode),
            new Flag("Facilities", Game::isFacilitiesMode, Game::setFacilitiesMode),
            new Flag("Minor Factions", Game::isMinorFactionsMode, Game::setMinorFactionsMode),
            new Flag("Total War", Game::isTotalWarMode, Game::setTotalWarMode),
            new Flag("Dangerous Wilds", Game::isDangerousWildsMode, Game::setDangerousWildsMode),
            new Flag("Civilized Society", Game::isCivilizedSocietyMode, Game::setCivilizedSocietyMode),
            new Flag("Age of Fighters", Game::isAgeOfFightersMode, Game::setAgeOfFightersMode),
            new Flag("Mercenaries for Hire", Game::isMercenariesForHireMode, Game::setMercenariesForHireMode),
            new Flag("Advent of the Warsun", Game::isAdventOfTheWarsunMode, Game::setAdventOfTheWarsunMode),
            new Flag(
                    "Cultural Exchange Program",
                    Game::isCulturalExchangeProgramMode,
                    Game::setCulturalExchangeProgramMode),
            new Flag(
                    "Conventions of War Abandoned",
                    Game::isConventionsOfWarAbandonedMode,
                    Game::setConventionsOfWarAbandonedMode),
            new Flag("Rapid Mobilization", Game::isRapidMobilizationMode, Game::setRapidMobilizationMode),
            new Flag("Cosmic Convergence", Game::isCosmicConvergenceMode, Game::setCosmicConvergenceMode),
            new Flag("Muaat Mania", Game::isMuaatManiaMode, Game::setMuaatManiaMode),
            new Flag("Weird Wormholes", Game::isWeirdWormholesMode, Game::setWeirdWormholesMode),
            new Flag("No Fracture", Game::isNoFractureMode, Game::setNoFractureMode),
            new Flag("Call of the Void", Game::isCallOfTheVoidMode, Game::setCallOfTheVoidMode),
            new Flag("Cosmic Phenomenae", Game::isCosmicPhenomenaeMode, Game::setCosmicPhenomenaeMode),
            new Flag("Monument to the Ages", Game::isMonumentToTheAgesMode, Game::setMonumentToTheAgesMode),
            new Flag("Wild Wild Galaxy", Game::isWildWildGalaxyMode, Game::setWildWildGalaxyMode),
            new Flag("Feast or Famine", Game::isFeastOrFamineMode, Game::setFeastOrFamineMode),
            new Flag("Zealous Orthodoxy", Game::isZealousOrthodoxyMode, Game::setZealousOrthodoxyMode),
            new Flag("Stellar Atomics", Game::isStellarAtomicsMode, Game::setStellarAtomicsMode),
            new Flag("Age of Commerce", Game::isAgeOfCommerceMode, Game::setAgeOfCommerceMode),
            new Flag("Hidden Agenda", Game::isHiddenAgendaMode, Game::setHiddenAgendaMode));

    private static final List<Mode> UNSUPPORTED = List.of(
            new Mode("Fog of War", Game::isFowMode),
            new Mode("Base game", Game::isBaseGameMode),
            new Mode("MiltyMod", Game::isMiltyModMode),
            new Mode("Voice of the Council", Game::isVotcMode),
            new Mode("Omega Phase", Game::isOmegaPhaseMode),
            new Mode("O++", game -> game.isOplusplusRegularMode() || game.isOplusplusCouncilMode()),
            new Mode("Secrets Only", Game::isSecretsOnlyMode),
            new Mode("Megagame", MegagameGroupService::hasTag));

    private static final List<String> STORED_VALUES = List.of(
            "useOldPok",
            "homebrewMode",
            "removeSupports",
            "removeOverrule",
            Constants.INCLUDE_ECHOES_OF_YGGDRASIL_TILES,
            "ccLimit",
            "IslandMode",
            "bannedTechs",
            "bannedUnits",
            Constants.TK_NOVA_CUP + "_setup_option");

    public static List<String> unsupportedModes(Game source) {
        return UNSUPPORTED.stream()
                .filter(mode -> mode.isOn().test(source))
                .map(Mode::label)
                .toList();
    }

    public static List<String> enabledModes(Game source) {
        return FLAGS.stream()
                .filter(flag -> flag.get().test(source))
                .map(Flag::label)
                .toList();
    }

    public static Map<String, Set<String>> removedCards(Game source) {
        Map<String, Set<String>> removed = new LinkedHashMap<>();
        for (String alias : deckAliases(source)) {
            DeckModel deck = Mapper.getDeck(alias);
            if (deck == null || deck.getType() == null) continue;
            Set<String> present = presentCards(source, deck.getType());
            if (present == null) continue;
            Set<String> missing = new LinkedHashSet<>(deck.getCardIDs());
            missing.removeAll(present);
            if (!missing.isEmpty()) removed.put(alias, missing);
        }
        return removed;
    }

    private static List<String> deckAliases(Game game) {
        List<String> aliases = new ArrayList<>(List.of(
                game.getAcDeckID(),
                game.getSoDeckID(),
                game.getStage1PublicDeckID(),
                game.getStage2PublicDeckID(),
                game.getRelicDeckID(),
                game.getAgendaDeckID(),
                game.getExplorationDeckID()));
        if (StringUtils.isNotBlank(game.getEventDeckID())) aliases.add(game.getEventDeckID());
        aliases.removeIf(StringUtils::isBlank);
        return aliases;
    }

    static Set<String> presentCards(Game game, DeckType type) {
        Set<String> present = new LinkedHashSet<>();
        Collection<Player> players = game.getPlayers().values();
        switch (type) {
            case ACTION_CARD -> {
                present.addAll(game.getActionCards());
                present.addAll(game.getDiscardActionCards().keySet());
                present.addAll(game.getPlayedActionCards());
                players.forEach(player -> present.addAll(player.getActionCards().keySet()));
            }
            case SECRET_OBJECTIVE -> {
                present.addAll(game.getSecretObjectives());
                present.addAll(game.getSoToPoList());
                players.forEach(player -> {
                    present.addAll(player.getSecrets().keySet());
                    present.addAll(player.getSecretsScored().keySet());
                });
            }
            case PUBLIC_STAGE_1_OBJECTIVE -> {
                present.addAll(game.getPublicObjectives1());
                present.addAll(game.getPublicObjectives1Peekable());
                present.addAll(game.getRevealedPublicObjectives().keySet());
            }
            case PUBLIC_STAGE_2_OBJECTIVE -> {
                present.addAll(game.getPublicObjectives2());
                present.addAll(game.getPublicObjectives2Peekable());
                present.addAll(game.getRevealedPublicObjectives().keySet());
            }
            case AGENDA -> {
                present.addAll(game.getAgendas());
                present.addAll(game.getDiscardAgendas().keySet());
                present.addAll(game.getSentAgendas().keySet());
                present.addAll(game.getLaws().keySet());
            }
            case RELIC -> {
                present.addAll(game.getAllRelics());
                players.forEach(player -> {
                    present.addAll(player.getRelics());
                    present.addAll(player.getExhaustedRelics());
                });
            }
            case EXPLORE -> {
                present.addAll(game.getAllExplores());
                present.addAll(game.getAllExploreDiscard());
                players.forEach(player -> present.addAll(player.getFragments()));
            }
            case EVENT -> {
                present.addAll(game.getEvents());
                present.addAll(game.getDiscardedEvents().keySet());
                present.addAll(game.getEventsInEffect().keySet());
                players.forEach(player -> present.addAll(player.getEvents().keySet()));
            }
            default -> {
                return null;
            }
        }
        return present;
    }

    public static List<String> apply(Game source, Game target) {
        List<String> problems = new ArrayList<>();
        List<String> unsupported = unsupportedModes(source);
        if (!unsupported.isEmpty()) {
            problems.add("Not copied, unsupported mode(s) in the setup game: " + String.join(", ", unsupported) + ".");
            return problems;
        }
        FLAGS.forEach(flag -> flag.set().accept(target, flag.get().test(source)));
        for (String key : STORED_VALUES) {
            String value = source.getStoredValue(key);
            if (StringUtils.isBlank(value)) target.removeStoredValue(key);
            else target.setStoredValue(key, value);
        }
        target.setVp(source.getVp());
        target.setMaxSOCountPerPlayer(source.getMaxSOCountPerPlayer());
        copyDecks(source, target, problems);
        removeCards(target, removedCards(source));
        if (StringUtils.isNotBlank(source.getStoredValue("removeOverrule"))) target.removeOverruleIfPurged();
        if (source.isMonumentsMode()) {
            if (source.isTwilightsFallMode()) MonumentsService.applyTwilightsFallMonuments(target);
            else MonumentsService.applyMonuments(target);
        }
        copyDraftSettings(source, target);
        return problems;
    }

    private static void copyDecks(Game source, Game target, List<String> problems) {
        resetDeck(source.getAcDeckID(), target::resetActionCardDeck, problems);
        resetDeck(source.getSoDeckID(), target::resetSecretObjectiveDeck, problems);
        resetDeck(source.getRelicDeckID(), target::resetRelicDeck, problems);
        resetDeck(source.getAgendaDeckID(), target::resetAgendaDeck, problems);
        resetDeck(source.getExplorationDeckID(), target::resetExplorationDeck, problems);
        if (StringUtils.isBlank(source.getEventDeckID())) {
            target.setEventDeckID("");
            target.setEvents(new ArrayList<>());
        } else {
            resetDeck(source.getEventDeckID(), target::resetEventDeck, problems);
        }
        copyObjectiveDeck(source, target, 1, problems);
        copyObjectiveDeck(source, target, 2, problems);
        target.swapOutVariantTechs();
        target.setTechnologyDeckID(source.getTechnologyDeckID());
        target.swapInVariantTechs();
        target.setAbilitySpliceDeckID(source.getAbilitySpliceDeckID());
        target.setGenomeSpliceDeckID(source.getGenomeSpliceDeckID());
        target.setParadigmSpliceDeckID(source.getParadigmSpliceDeckID());
        target.setUnitSpliceDeckID(source.getUnitSpliceDeckID());
        try {
            target.setStrategyCardSet(source.getScSetID());
            int players = Math.max(1, target.getPlayers().size());
            target.setStrategyCardsPerPlayer(Math.max(1, target.getSCList().size() / players));
        } catch (IllegalArgumentException e) {
            problems.add("Unknown strategy card set " + source.getScSetID() + ".");
        }
    }

    private static void resetDeck(String alias, Consumer<DeckModel> reset, List<String> problems) {
        DeckModel deck = Mapper.getDeck(alias);
        if (deck == null) {
            problems.add("Unknown deck " + alias + ".");
            return;
        }
        reset.accept(deck);
    }

    private static void copyObjectiveDeck(Game source, Game target, int stage, List<String> problems) {
        String alias = stage == 1 ? source.getStage1PublicDeckID() : source.getStage2PublicDeckID();
        DeckModel deck = Mapper.getDeck(alias);
        if (deck == null) {
            problems.add("Unknown deck " + alias + ".");
            return;
        }
        int peekable = stage == 1
                ? source.getPublicObjectives1Peekable().size()
                : source.getPublicObjectives2Peekable().size();
        if (stage == 1) {
            target.setStage1PublicDeckID(alias);
            target.setPublicObjectives1(new ArrayList<>(deck.getNewShuffledDeck()));
            target.getPublicObjectives1Peekable().clear();
            target.getPublicObjectives1Peeked().clear();
        } else {
            target.setStage2PublicDeckID(alias);
            target.setPublicObjectives2(new ArrayList<>(deck.getNewShuffledDeck()));
            target.getPublicObjectives2Peekable().clear();
            target.getPublicObjectives2Peeked().clear();
        }
        target.setUpPeekableObjectives(peekable, stage);
    }

    static void removeCards(Game target, Map<String, Set<String>> removed) {
        removed.forEach((alias, cards) -> {
            DeckModel deck = Mapper.getDeck(alias);
            if (deck == null || deck.getType() == null) return;
            for (String card : cards) {
                removeCard(target, deck.getType(), card);
            }
        });
    }

    private static void removeCard(Game target, DeckType type, String card) {
        switch (type) {
            case ACTION_CARD -> target.getActionCards().remove(card);
            case SECRET_OBJECTIVE -> target.removeSOFromGame(card);
            case AGENDA -> target.removeAgendaFromGame(card);
            case RELIC -> target.removeRelicFromGame(card);
            case EXPLORE -> target.purgeExplore(card);
            case EVENT -> target.getEvents().remove(card);
            case PUBLIC_STAGE_1_OBJECTIVE ->
                removeObjective(target, card, target.getPublicObjectives1(), target.getPublicObjectives1Peekable(), 1);
            case PUBLIC_STAGE_2_OBJECTIVE ->
                removeObjective(target, card, target.getPublicObjectives2(), target.getPublicObjectives2Peekable(), 2);
            default -> {}
        }
    }

    private static void removeObjective(Game target, String card, List<String> deck, List<String> peekable, int stage) {
        deck.remove(card);
        if (peekable.remove(card)) {
            target.setUpPeekableObjectives(peekable.size() + 1, stage);
        }
    }

    public static boolean hasDraftSettings(Game source) {
        return miltyJson(source) != null || draftSystemJson(source) != null;
    }

    public static boolean usesDraftSystem(Game source) {
        return draftSystemJson(source) != null;
    }

    private static String miltyJson(Game source) {
        if (source.getMiltySettingsUnsafe() != null)
            return source.getMiltySettingsUnsafe().json();
        return StringUtils.isBlank(source.getMiltyJson()) ? null : source.getMiltyJson();
    }

    private static String draftSystemJson(Game source) {
        if (source.getDraftSystemSettingsUnsafe() != null)
            return source.getDraftSystemSettingsUnsafe().json();
        return StringUtils.isBlank(source.getDraftSystemSettingsJson()) ? null : source.getDraftSystemSettingsJson();
    }

    private static void copyDraftSettings(Game source, Game target) {
        String milty = miltyJson(source);
        String draftSystem = draftSystemJson(source);
        target.setMiltySettings(null);
        target.setDraftSystemSettings(null);
        target.setMiltyJson(milty == null ? null : stripMiltyJson(milty));
        target.setDraftSystemSettingsJson(draftSystem == null ? null : stripDraftSystemJson(draftSystem));
    }

    static String stripMiltyJson(String json) {
        JsonNode root = JsonMapperManager.basic().readTree(json);
        JsonNode menu = root.has("miltySettings") ? root.get("miltySettings") : root;
        if (menu instanceof ObjectNode milty) {
            milty.remove("messageId");
            milty.remove("randomSetup");
            if (milty.get("playerSettings") instanceof ObjectNode players) {
                players.remove("gamePlayers");
                turnOff(players, "presetDraftOrder");
            }
        }
        return JsonMapperManager.basic().writeValueAsString(root);
    }

    static String stripDraftSystemJson(String json) {
        JsonNode root = JsonMapperManager.basic().readTree(json);
        if (root instanceof ObjectNode draft) {
            draft.remove("messageId");
            if (draft.get("gameSetupSettings") instanceof ObjectNode setup) {
                setup.remove("gamePlayers");
                setup.remove("messageId");
            }
            if (draft.get("publicSnakeDraftSettings") instanceof ObjectNode snake) {
                snake.remove("orderedPlayerIds");
                turnOff(snake, "presetDraftOrder");
            }
        }
        return JsonMapperManager.basic().writeValueAsString(root);
    }

    private static void turnOff(ObjectNode parent, String settingName) {
        if (parent.get(settingName) instanceof ObjectNode setting) setting.put("val", false);
    }

    public static String describeDecks(Game game) {
        return "AC " + game.getAcDeckID() + " · SO " + game.getSoDeckID() + " · agendas "
                + game.getAgendaDeckID() + " · relics " + game.getRelicDeckID() + " · techs "
                + game.getTechnologyDeckID() + " · strategy cards " + game.getScSetID();
    }
}
