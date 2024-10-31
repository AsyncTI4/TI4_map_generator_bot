package ti4.map;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.utils.StringUtils;
import ti4.generator.Mapper;
import ti4.message.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;

public class GameStatsDashboardPayload {

    private final Game game;

    public GameStatsDashboardPayload(Game game) {
        this.game = game;
    }

    @JsonIgnore
    public String getJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.log("Could not get GameStatsDashboardPayload JSON for Game ", e);
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
            "config", Map.of(
                "baseMagen", baseMagen,
                "codex1", true, //TODO: don't fake this
                "codex2", true, //TODO: don't fake this
                "codex3", true, //TODO: don't fake this
                "codex4", true, //TODO: don't fake this
                "note_that_this_map_is_probably_not_accurate", true));
    }

    public String getHexSummary() {
        // 18+0+0*b;Bio,71+0+2Rct;Ro;Ri,36+1+1Kcf;Km*I;Ki,76+1-1;;;,72+0-2; ......
        // CSV of {tileID}{+x+yCoords}??{list;of;tokens} ?? 
        // See ConvertTTPGtoAsync.ConvertTTPGHexToAsyncTile() and reverse it!
        return null;
    }

    public Map<Timestamp, GameStatsDashboardPayload> getHistory() {
        return game.getHistoricalGameStatsDashboardPayloads();
    }

    @JsonProperty("isPoK")
    public boolean isPoK() {
        return !game.isBaseGameMode();
    }

    public List<String> getLaws() {
        var lawsInPlay = game.getLaws().keySet().stream()
            .map(Mapper::getAgenda)
            .map(AgendaModel::getName)
            .toList();
        var agendasInDiscard = game.getDiscardAgendas().keySet().stream()
            .map(Mapper::getAgenda)
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
                .filter(relic -> relic.toLowerCase().contains("shard") || relic.toLowerCase().contains("emphidia"))
                .forEach(relics::add);
        // some older games may have added these custom
        game.getCustomPublicVP().keySet()
                .forEach(customPublicVp -> {
                    if (customPublicVp.toLowerCase().contains("shard")) {
                        relics.add("Shard of the Throne");
                    } else if (customPublicVp.toLowerCase().contains("emphidia")) {
                        relics.add("The Crown of Emphidia");
                    }});
        objectives.put("Relics", relics);

        // Agenda
        var agendas = new ArrayList<String>();
        game.getCustomPublicVP().keySet()
                .forEach(customPublicVp -> {
                    if (customPublicVp.toLowerCase().contains("political censure")) {
                        agendas.add("Political Censure");
                    } else if (customPublicVp.toLowerCase().contains("mutiny")) {
                        agendas.add("Mutiny");
                    } else if (customPublicVp.toLowerCase().contains("seed")) {
                        agendas.add("Seed of an Empire");
                    }});
        objectives.put("Agenda", agendas);

        // Custom (Unknown)
        objectives.put("Custom", new ArrayList<>(game.getCustomPublicVP().keySet()));

        // Other (Supports)
        for (Player player : game.getRealAndEliminatedPlayers()) {
            objectives.put("Other", player.getPromissoryNotesOwned().stream()
                .map(Mapper::getPromissoryNote)
                .filter(pn -> "Support for the Throne".equalsIgnoreCase(pn.getName()))
                .map(pn -> "Support for the Throne (" + player.getColor() + ")")
                .toList());
        }

        var revealedPublics = game.getRevealedPublicObjectives().keySet().stream()
                .map(Mapper::getPublicObjective)
                .filter(Objects::nonNull)
                .toList();

        //Public I
        objectives.put("Public Objectives I",
                revealedPublics.stream()
                        .filter(publicObjective -> publicObjective.getPoints() == 1)
                        .map(PublicObjectiveModel::getName)
                        .toList());

        //Public II
        objectives.put("Public Objectives II",
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

    public String getSetupTimestamp() {
        try {
            var localDate = LocalDate.parse(game.getCreationDate(), DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            var epochSeconds = localDate.atStartOfDay(ZoneId.of("UTC")).toInstant().getEpochSecond();
            return Long.toString(epochSeconds);
        } catch (DateTimeParseException e) {
            return Long.toString(Instant.now().getEpochSecond());
        }
    }

    public String getSpeaker() {
        if (game.getSpeaker() == null) return null;
        return game.getSpeaker().getColor();
    }

    public Timestamp getTimestamp() {
        return Timestamp.from(Instant.now());
    }

    public String getTurn() {
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) return null;
        return activePlayer.getColor();
    }

    public Map<String, Integer> getUnpickedStrategyCards() {
        return game.getScTradeGoods().entrySet().stream()
            .filter(e -> e.getValue() > 0) // TGs > 0
            .map(e -> Map.entry(game.getStrategyCardModelByInitiative(e.getKey()), e.getValue())) // Optional(SCModel), TGs
            .filter(e -> e.getKey().isPresent())
            .map(e -> Map.entry(e.getKey().get().getName(), e.getValue())) // SCName, TGs
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1));
    }

}
