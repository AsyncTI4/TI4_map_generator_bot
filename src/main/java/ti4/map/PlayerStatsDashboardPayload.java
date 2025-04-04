package ti4.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import ti4.image.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.RelicModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class PlayerStatsDashboardPayload {

    private final Player player;
    private final Game game;

    public PlayerStatsDashboardPayload(Player player) {
        this.player = player;
        this.game = player.getGame();
    }

    @JsonIgnore
    public String getJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.error("Could not get PlayerStatsDashboardPayload JSON for Game: " + player.getGame().getID() + " Player: " + player.getUserName(), e);
            return null;
        }
    }

    @JsonIgnore
    public boolean isActive() {
        return player.getUserID().equals(game.getActivePlayerID()); // UNUSED, IGNORE
    }

    public String getDiscordUserID() {
        return player.getUserID();
    }

    public String getDiscordUsername() {
        return player.getUserName();
    }

    public List<String> getAlliances() {
        return player.getPromissoryNotesInPlayArea().stream()
            .map(Mapper::getPromissoryNote)
            .filter(pn -> "Alliance".equalsIgnoreCase(pn.getName()))
            .filter(pn -> game.getPNOwner(pn.getAlias()) != null)
            .map(pn -> game.getPNOwner(pn.getAlias()).getColor())
            .toList();
    }

    public String getColor() {
        return player.getColor();
    }

    public String getColorActual() {
        return getColor(); // I think TTPG has an underlying TTPG based colour ID, and a "TI4" colour which can be controlled via colour picker
    }

    public Map<String, Integer> getCommandTokens() {
        return Map.of(
            "fleet", player.getFleetCC(),
            "strategy", player.getStrategicCC(),
            "tactics", player.getTacticalCC());
    }

    public int getCommodities() {
        return player.getCommodities();
    }

    public int getMaxCommodities() {
        return player.getCommoditiesTotal();
    }

    public int getCustodianPoints() {
        return (int) game.getScoredPublicObjectives().entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase().contains("custodian") && entry.getValue().contains(player.getUserID()))
            .count();
    }

    public String getFactionName() {
        var factionModel = player.getFactionModel();
        return factionModel == null ? null : factionModel.getFactionName();
    }

    public Map<String, Integer> getHandSummary() {
        return Map.of(
            "Secret Objectives", player.getSecrets().size(),
            "Actions", player.getActionCards().size(),
            "Promissory", player.getPromissoryNotes().size());
    }

    public List<String> getLaws() {
        return game.getLaws().keySet().stream()
            .filter(lawId -> ButtonHelper.isPlayerElected(game, player, lawId))
            .map(Mapper::getAgenda)
            .map(AgendaModel::getName)
            .toList();
    }

    public LeaderPayload getLeaders() {
        var leaderPayload = new LeaderPayload();
        player.getLeaders().stream()
            .filter(leader -> leader.getId().contains("commander"))
            .findAny()
            .ifPresent(commander -> leaderPayload.setCommander(commander.isLocked() ? "locked" : "unlocked"));
        player.getLeaders().stream()
            .filter(leader -> leader.getId().contains("hero"))
            .findAny()
            .ifPresentOrElse(hero -> leaderPayload.setHero(hero.isLocked() ? "locked" : "unlocked"), () -> leaderPayload.setHero("purged"));
        return leaderPayload;
    }

    public List<String> getObjectives() {
        List<String> objectives = new ArrayList<>();
        // Publics & Custom (Custodians, Relic, Agenda)
        game.getScoredPublicObjectives().entrySet().stream()
            .filter(e -> e.getValue().contains(player.getUserID())) // player has scored this
            .map(Map.Entry::getKey)
            .filter(objId -> !objId.contains("custodian")) // this is counted elsewhere
            .map(objId -> {
                if (Mapper.isValidPublicObjective(objId)) {
                    return Mapper.getPublicObjective(objId).getName();
                }
                if (objId.toLowerCase().contains("censure")) {
                    return "Political Censure";
                }
                if (objId.toLowerCase().contains("mutiny")) {
                    return "Mutiny";
                }
                if (objId.toLowerCase().contains("seed")) {
                    return "Seed of an Empire";
                }
                if (objId.toLowerCase().contains("rider")) {
                    return "Imperial Rider";
                }
                return objId;
            })
            .forEach(objectives::add);

        // Secrets
        player.getSecretsScored().keySet().stream()
            .map(Mapper::getSecretObjective)
            .map(SecretObjectiveModel::getName)
            .forEach(objectives::add);

        // Supports
        player.getPromissoryNotesInPlayArea().stream()
            .map(Mapper::getPromissoryNote)
            .filter(pn -> "Support for the Throne".equalsIgnoreCase(pn.getName()))
            .map(pn -> "Support for the Throne (" + pn.getColor() + ")")
            .forEach(objectives::add);

        return objectives;
    }

    public Map<String, Object> getPlanetTotals() {
        Map<String, Object> planetTotals = new HashMap<>();

        // Influence
        planetTotals.put("influence", Map.of(
            "avail", Helper.getPlayerInfluenceAvailable(player, player.getGame()),
            "total", Helper.getPlayerInfluenceTotal(player, player.getGame())));

        // Resources
        planetTotals.put("resources", Map.of(
            "avail", Helper.getPlayerResourcesAvailable(player, player.getGame()),
            "total", Helper.getPlayerResourcesTotal(player, player.getGame())));

        // Legendary Count
        long legendaryCount = player.getPlanets().stream()
            .map(pID -> game.getPlanetsInfo().get(pID))
            .filter(Objects::nonNull)
            .filter(Planet::isLegendary)
            .count();
        planetTotals.put("legendary", legendaryCount);

        // Traits
        long culturalCount = player.getPlanets().stream()
            .map(pID -> game.getPlanetsInfo().get(pID))
            .filter(Objects::nonNull)
            .filter(p -> p.getPlanetTypes().contains("cultural"))
            .count();
        long hazardousCount = player.getPlanets().stream()
            .map(pID -> game.getPlanetsInfo().get(pID))
            .filter(Objects::nonNull)
            .filter(p -> p.getPlanetTypes().contains("hazardous"))
            .count();
        long industrialCount = player.getPlanets().stream()
            .map(pID -> game.getPlanetsInfo().get(pID))
            .filter(Objects::nonNull)
            .filter(p -> p.getPlanetTypes().contains("industrial"))
            .count();
        planetTotals.put("traits", Map.of(
            "cultural", culturalCount,
            "hazardous", hazardousCount,
            "industrial", industrialCount));

        // Techs
        AtomicInteger blueCount = new AtomicInteger();
        AtomicInteger yellowCount = new AtomicInteger();
        AtomicInteger greenCount = new AtomicInteger();
        AtomicInteger redCount = new AtomicInteger();
        player.getPlanets().stream()
            .map(pID -> game.getPlanetsInfo().get(pID))
            .filter(Objects::nonNull)
            .map(Planet::getTechSpecialities)
            .flatMap(Collection::stream)
            .map(String::toLowerCase)
            .forEach(speciality -> {
                if (speciality.equalsIgnoreCase("propulsion")) {
                    blueCount.getAndIncrement();
                } else if (speciality.equalsIgnoreCase("cybernetic")) {
                    yellowCount.getAndIncrement();
                } else if (speciality.equalsIgnoreCase("biotic")) {
                    greenCount.getAndIncrement();
                } else if (speciality.equalsIgnoreCase("warfare")) {
                    redCount.getAndIncrement();
                }
            });
        planetTotals.put("techs", Map.of(
            "blue", blueCount.get(),
            "green", redCount.get(),
            "red", greenCount.get(),
            "yellow", yellowCount.get()));

        return planetTotals;
    }

    public List<String> getRelicCards() {
        return player.getRelics().stream()
            .map(Mapper::getRelic)
            .filter(Objects::nonNull)
            .map(RelicModel::getName)
            .toList();
    }

    public int getScore() {
        return player.getTotalVictoryPoints();
    }

    public List<String> getStrategyCards() {
        return player.getSCs().stream()
            .map(sc -> player.getGame().getStrategyCardModelByInitiative(sc))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(StrategyCardModel::getName)
            .toList();
    }

    public List<String> getTechnologies() {
        return player.getTechs().stream()
            .map(Mapper::getTech)
            .map(TechnologyModel::getName)
            .toList();
    }

    public int getTradeGoods() {
        return player.getTg();
    }

    public int getTurnOrder() {
        return game.getActionPhaseTurnOrder(player.getUserID());
    }

    @JsonIgnore // Dashboard doesn't use this yet
    public List<Object> getUnitModifiers() {
        return Collections.emptyList();
    }

    @JsonIgnore // Dashboard doesn't use this yet
    public List<String> getUnitUpgrades() {
        return player.getUnitModels().stream()
            .filter(u -> u.getRequiredTechId().isPresent()) // is a unit that requires a tech upgrade
            .map(UnitModel::getBaseType)
            .toList();
    }

    public int getTotalNumberOfTurns() {
        return player.getNumberOfTurns();
    }

    public long getTotalTurnTime() {
        return player.getTotalTurnTime();
    }

    public double getExpectedHits() {
        return player.getExpectedHits();
    }

    public int getActualHits() {
        return player.getActualHits();
    }

    public boolean isEliminated() {
        return player.isEliminated();
    }

    public List<String> getTeammateIDs() {
        return player.getTeamMateIDs();
    }

    @Data
    public static class LeaderPayload {
        private String hero;
        private String commander;
    }

}
