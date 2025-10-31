package ti4.website.model.stats;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.utils.StringUtils;
import ti4.helpers.Constants;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.helper.GameHelper;
import ti4.map.pojo.PlayerProperties;
import ti4.message.logging.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.EventModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.website.EgressClientManager;

public class GameStatsDashboardPayload {

    private final Game game;

    public GameStatsDashboardPayload(Game game) {
        this.game = game;
    }

    @JsonIgnore
    public String getJson() {
        try {
            return EgressClientManager.getObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.error("Could not get GameStatsDashboardPayload JSON for Game ", e);
            return null;
        }
    }

    public String getAsyncGameID() {
        return game.getID();
    }

    public String getAsyncFunGameName() {
        return game.getCustomName();
    }

    @JsonIgnore // not currently used for Dashboard
    public String getActiveSystem() {
        String activeSystemPosition = game.getActiveSystem();
        if (StringUtils.isEmpty(activeSystemPosition)) return null;

        Tile tile = game.getTileByPosition(activeSystemPosition);
        if (tile == null) return null;

        return tile.getTileID();
    }

    public Map<String, Map<String, Boolean>> getConfig() {
        boolean baseMagen = game.getRealAndEliminatedPlayers().stream().anyMatch(p -> p.hasTech("md_base"));
        return Map.of(
                "config",
                Map.of(
                        "baseMagen", baseMagen,
                        "codex1", true, // TODO: don't fake this
                        "codex2", true, // TODO: don't fake this
                        "codex3", true, // TODO: don't fake this
                        "codex4", true, // TODO: don't fake this
                        "note_that_this_map_is_probably_not_accurate", true));
    }

    public String getHexSummary() {
        return game.getHexSummary();
    }

    @JsonProperty("isPoK")
    public boolean isPoK() {
        return !game.isBaseGameMode();
    }

    public List<String> getLaws() {
        var lawsInPlay = game.getLaws().keySet().stream()
                .map(Mapper::getAgenda)
                .filter(Objects::nonNull)
                .map(AgendaModel::getName)
                .toList();
        var agendasInDiscard = game.getDiscardAgendas().keySet().stream()
                .map(Mapper::getAgenda)
                .filter(Objects::nonNull)
                .map(AgendaModel::getName)
                .toList();
        return Stream.concat(lawsInPlay.stream(), agendasInDiscard.stream()).toList();
    }

    public String getMapString() {
        return game.getMapString();
    }

    public Map<String, List<String>> getObjectives() {
        Map<String, List<String>> objectives = new HashMap<>();

        // Relics
        var relics = new ArrayList<String>();
        game.getRealAndEliminatedPlayers().stream()
                .map(Player::getRelics)
                .flatMap(Collection::stream)
                .forEach(customPublicVp -> {
                    if (customPublicVp.startsWith("absol_shardofthethrone")) {
                        var shardNumber = customPublicVp.charAt(customPublicVp.length() - 1);
                        relics.add("Shard of the Throne " + shardNumber + " (Absol)");
                    } else if (customPublicVp.toLowerCase().contains("shard")) {
                        relics.add("Shard of the Throne");
                    }
                });
        // some older games may have added these custom
        game.getCustomPublicVP().keySet().forEach(customPublicVp -> {
            if (customPublicVp.toLowerCase().contains("shard") && !relics.contains("Shard of the Throne")) {
                relics.add("Shard of the Throne");
            } else if (customPublicVp.toLowerCase().contains("emphidia")) {
                relics.add("The Crown of Emphidia");
            }
        });
        objectives.put("Relics", relics);

        // Agenda
        var agendas = new ArrayList<String>();
        game.getCustomPublicVP().keySet().forEach(customPublicVp -> {
            if (customPublicVp.toLowerCase().contains("censure")) {
                agendas.add("Political Censure");
            } else if (customPublicVp.equalsIgnoreCase(Constants.VOICE_OF_THE_COUNCIL_PO)) {
                agendas.add(Constants.VOICE_OF_THE_COUNCIL_ID);
            } else if (customPublicVp.toLowerCase().contains("mutiny")) {
                agendas.add("Mutiny");
            } else if (customPublicVp.toLowerCase().contains("seed")) {
                agendas.add("Seed of an Empire");
            }
        });
        objectives.put("Agenda", agendas);

        // Custom
        objectives.put("Custom", new ArrayList<>(game.getCustomPublicVP().keySet()));

        // Other (Supports + Imperial Rider)
        var otherObjectives = new ArrayList<String>();
        game.getPlayers().values().stream()
                .map(Player::getPromissoryNotesOwned)
                .flatMap(Collection::stream)
                .map(Mapper::getPromissoryNote)
                .filter(pn -> "Support for the Throne".equalsIgnoreCase(pn.getName()))
                .map(pn -> "Support for the Throne (" + pn.getColor().get() + ")")
                .forEach(otherObjectives::add);
        game.getCustomPublicVP().keySet().forEach(customPublicVp -> {
            if (customPublicVp.toLowerCase().contains("rider")) {
                otherObjectives.add("Imperial Rider");
            }
        });
        objectives.put("Other", otherObjectives);

        var revealedPublics = game.getRevealedPublicObjectives().keySet().stream()
                .map(Mapper::getPublicObjective)
                .filter(Objects::nonNull)
                .toList();

        // Public I
        objectives.put(
                "Public Objectives I",
                revealedPublics.stream()
                        .filter(publicObjective -> publicObjective.getPoints() == 1)
                        .map(PublicObjectiveModel::getName)
                        .toList());

        // Public II
        objectives.put(
                "Public Objectives II",
                revealedPublics.stream()
                        .filter(publicObjective -> publicObjective.getPoints() == 2)
                        .map(PublicObjectiveModel::getName)
                        .toList());

        // Secrets
        List<String> secrets = new ArrayList<>();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            secrets.addAll(player.getSecretsScored().keySet().stream()
                    .map(Mapper::getSecretObjective)
                    .map(SecretObjectiveModel::getName)
                    .toList());
        }
        objectives.put("Secret Objectives", secrets);

        return objectives;
    }

    public List<String> getEventsInEffect() {
        return game.getEventsInEffect().keySet().stream()
                .map(eventId -> {
                    EventModel eventModel = Mapper.getEvent(eventId);
                    return eventModel != null ? eventModel.getName() : eventId;
                })
                .toList();
    }

    public List<String> getModes() {
        List<String> enabledModes = Stream.of(
                        Map.entry("Alliance", (Supplier<Boolean>) game::isAllianceMode),
                        Map.entry("Community", (Supplier<Boolean>) game::isCommunityMode),
                        Map.entry("Competitive TIGL", (Supplier<Boolean>) game::isCompetitiveTIGLGame),
                        Map.entry("Fog of War", (Supplier<Boolean>) game::isFowMode),
                        Map.entry("Light Fog", (Supplier<Boolean>) game::isLightFogMode),
                        Map.entry("CPTI Explore", (Supplier<Boolean>) game::isCptiExploreMode),
                        Map.entry("Absol", (Supplier<Boolean>) game::isAbsolMode),
                        Map.entry("Discordant Stars", (Supplier<Boolean>) game::isDiscordantStarsMode),
                        Map.entry("Uncharted Space", (Supplier<Boolean>) game::isUnchartedSpaceStuff),
                        Map.entry("Milty Mod", (Supplier<Boolean>) game::isMiltyModMode),
                        Map.entry("Promises Promises", (Supplier<Boolean>) game::isPromisesPromisesMode),
                        Map.entry("Flagshipping", (Supplier<Boolean>) game::isFlagshippingMode),
                        Map.entry("Red Tape", (Supplier<Boolean>) game::isRedTapeMode),
                        Map.entry("Omega Phase", (Supplier<Boolean>) game::isOmegaPhaseMode),
                        Map.entry("Homebrew", (Supplier<Boolean>) game::hasHomebrew),
                        Map.entry("Homebrew Strategy Cards", (Supplier<Boolean>) game::isHomebrewSCMode),
                        Map.entry("Fast Strategy Card Follow", (Supplier<Boolean>) game::isFastSCFollowMode),
                        Map.entry("Extra Secret", (Supplier<Boolean>) game::isExtraSecretMode),
                        Map.entry("Voice of the Council", (Supplier<Boolean>) game::isVotcMode),
                        Map.entry("Reverse Speaker Order", (Supplier<Boolean>) game::isReverseSpeakerOrder),
                        Map.entry("Thunders Edge", (Supplier<Boolean>) game::isThundersEdge),
                        Map.entry("Age of Exploration", (Supplier<Boolean>) game::isAgeOfExplorationMode),
                        Map.entry("Facilities", (Supplier<Boolean>) game::isFacilitiesMode),
                        Map.entry("Minor Factions", (Supplier<Boolean>) game::isMinorFactionsMode),
                        Map.entry("Total War", (Supplier<Boolean>) game::isTotalWarMode),
                        Map.entry("Dangerous Wilds", (Supplier<Boolean>) game::isDangerousWildsMode),
                        Map.entry("Civilized Society", (Supplier<Boolean>) game::isCivilizedSocietyMode),
                        Map.entry("Age of Fighters", (Supplier<Boolean>) game::isAgeOfFightersMode),
                        Map.entry("Mercenaries for Hire", (Supplier<Boolean>) game::isMercenariesForHireMode),
                        Map.entry("Advent of the Warsun", (Supplier<Boolean>) game::isAdventOfTheWarsunMode),
                        Map.entry("Cultural Exchange Program", (Supplier<Boolean>) game::isCulturalExchangeProgramMode),
                        Map.entry("Conventions of War Abandoned", (Supplier<Boolean>) game::isConventionsOfWarAbandonedMode),
                        Map.entry("Rapid Mobilization", (Supplier<Boolean>) game::isRapidMobilizationMode),
                        Map.entry("Weird Wormholes", (Supplier<Boolean>) game::isWeirdWormholesMode),
                        Map.entry("Cosmic Phenomenae", (Supplier<Boolean>) game::isCosmicPhenomenaeMode),
                        Map.entry("Monument to the Ages", (Supplier<Boolean>) game::isMonumentToTheAgesMode),
                        Map.entry("Wild Wild Galaxy", (Supplier<Boolean>) game::isWildWildGalaxyMode),
                        Map.entry("Zealous Orthodoxy", (Supplier<Boolean>) game::isZealousOrthodoxyMode),
                        Map.entry("Stellar Atomics", (Supplier<Boolean>) game::isStellarAtomicsMode),
                        Map.entry("No Swap", (Supplier<Boolean>) game::isNoSwapMode),
                        Map.entry("Limited Whispers", (Supplier<Boolean>) game::isLimitedWhispersMode),
                        Map.entry("Age of Commerce", (Supplier<Boolean>) game::isAgeOfCommerceMode),
                        Map.entry("Hidden Agenda", (Supplier<Boolean>) game::isHiddenAgendaMode),
                        Map.entry("Ordinian C1", (Supplier<Boolean>) game::isOrdinianC1Mode),
                        Map.entry("Liberation C4", (Supplier<Boolean>) game::isLiberationC4Mode))
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!"OFF".equalsIgnoreCase(game.getSpinMode())) {
            enabledModes.add("Spin: " + game.getSpinMode());
        }

        PriorityTrackMode priorityTrackMode = game.getPriorityTrackMode();
        if (priorityTrackMode != null && priorityTrackMode != PriorityTrackMode.NONE) {
            enabledModes.add("Priority Track: " + priorityTrackMode.name());
        }

        return enabledModes;
    }

    public String getPlatform() {
        return "asyncti4";
    }

    public List<PlayerStatsDashboardPayload> getPlayers() {
        return game.getRealAndEliminatedPlayers().stream()
                .map(PlayerStatsDashboardPayload::new)
                .toList();
    }

    public int getRound() {
        return game.getRound();
    }

    public int getScoreboard() {
        return game.getVp();
    }

    public long getSetupTimestamp() {
        LocalDate localDate;
        try {
            localDate = GameHelper.getCreationDateAsLocalDate(game);
        } catch (DateTimeParseException e) {
            localDate = LocalDate.now();
        }
        int gameNameHash = Math.abs(game.getName().hashCode());
        int hours = gameNameHash % 24;
        int minutes = gameNameHash % 60;
        int seconds = Math.abs(game.getCustomName().hashCode()) % 60;
        var localDateTime = localDate.atTime(hours, minutes, seconds);
        return localDateTime.atZone(ZoneId.of("UTC")).toInstant().getEpochSecond();
    }

    public String getSpeaker() {
        if (game.getSpeaker() == null) return null;
        return game.getSpeaker().getColor();
    }

    public long getTimestamp() {
        try {
            return Instant.ofEpochMilli(game.getLastModifiedDate()).getEpochSecond();
        } catch (DateTimeException e) {
            return Instant.now().getEpochSecond();
        }
    }

    public Long getEndedTimestamp() {
        if (!game.isHasEnded()) {
            return null;
        }
        return Instant.ofEpochMilli(game.getEndedDate()).getEpochSecond();
    }

    public String getTurn() {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) return null;
        return activePlayer.getColor();
    }

    public Map<String, Integer> getUnpickedStrategyCards() {
        return game.getScTradeGoods().entrySet().stream()
                .filter(e -> e.getValue() > 0) // TGs > 0
                .map(e -> Map.entry(
                        game.getStrategyCardModelByInitiative(e.getKey()), e.getValue())) // Optional(SCModel), TGs
                .filter(e -> e.getKey().isPresent())
                .map(e -> Map.entry(e.getKey().get().getName(), e.getValue())) // SCName, TGs
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1));
    }

    public List<String> getWinners() {
        return game.getWinners().stream().map(PlayerProperties::getUserID).toList();
    }

    public boolean hasCompleted() {
        return game.getWinner().isPresent() && game.isHasEnded();
    }

    public boolean isHomebrew() {
        return game.hasHomebrew();
    }

    public boolean isDiscordantStarsMode() {
        return game.isDiscordantStarsMode();
    }

    public boolean isAbsolMode() {
        return game.isAbsolMode();
    }

    public boolean isFrankenGame() {
        return game.isFrankenGame();
    }

    public boolean isAllianceMode() {
        return game.isAllianceMode();
    }

    public boolean isTIGLGame() {
        return game.isCompetitiveTIGLGame();
    }
}
