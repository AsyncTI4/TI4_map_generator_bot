package ti4.map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.message.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.RelicModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PlayerStatsDashboardPayload {

    private final Player player;
    private final Game game;

    public PlayerStatsDashboardPayload(Player player, Game game) {
        this.player = player;
        this.game = game;
    }

    @JsonIgnore
    public String getJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.log("Could not get PlayerStatsDashboardPayload JSON for Game: " + player.getGame().getID() + " Player: " + player.getUserName(), e);
            return null;
        }
    }

    public boolean isActive() {
        return player.getUserID().equals(game.getActivePlayerID()); // UNUSED, IGNORE
    }

    public List<String> getAlliances() {
        return Collections.emptyList(); // TODO: list of colours
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
        return player.getFactionModel().getFactionName();
    }

    public Map<String, Integer> getHandSummary() {
        return Map.of(
            "Secret Objectives", player.getSecrets().size(),
            "Actions", player.getActionCards().size(), //TODO: add more card types? 
            "Promissory", player.getPromissoryNotes().size());
    }

    public List<String> getLaws() {
        return game.getLaws().keySet().stream()
                .filter(lawId -> ButtonHelper.isPlayerElected(game, player, lawId))
                .map(Mapper::getAgenda)
                .map(AgendaModel::getName)
                .toList();
    }

    public List<Leader> getLeaders() {
        return player.getLeaders();
    }

    public List<String> getObjectives() {
        //var scoredPublics = game.getScoredPublicObjectives();
        //var scoredSecrets = player.getSecretsScored();
        //"Support for the Throne (Green)"
        //misc points like Shard?
        return Collections.emptyList(); //TODO
    }

    public Map<String, Object> getPlanetTotals() {
        return Collections.emptyMap(); //TODO
        /*
         * "planetTotals": {
         * "influence": {
         * "avail": 2,
         * "total": 15
         * },
         * "legendary": 1,
         * "resources": {
         * "avail": 0,
         * "total": 16
         * },
         * "techs": {
         * "blue": 0,
         * "green": 2,
         * "red": 0,
         * "yellow": 0
         * },
         * "traits": {
         * "cultural": 3,
         * "hazardous": 0,
         * "industrial": 3
         * }
         * }
         */
    }

    public List<String> getRelicCards() {
        return player.getRelics().stream()
            .map(Mapper::getRelic)
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

    public List<Object> getUnitModifiers() {
        return Collections.emptyList(); // UNUSED, IGNORE
    }

    public List<String> getUnitUpgrades() {
        return player.getUnitModels().stream()
            .filter(u -> u.getRequiredTechId().isPresent()) // is a unit that requires a tech upgrade
            .map(UnitModel::getBaseType)
            .toList();
    }

}
