package ti4.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import ti4.generator.Mapper;
import ti4.model.Source.ComponentSource;

@Data
public class StrategyCardSetModel implements ModelInterface {
    private String name;
    private String alias;
    private List<String> scIDs; // List of strategy card IDs
    private String description;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return scIDs != null
            && !scIDs.isEmpty()
            && StringUtils.isNotBlank(name)
            && StringUtils.isNotBlank(alias)
            && source != null;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    /**
     * @deprecated This method is deprecated and only here to support legacy code.
     */
    @JsonIgnore
    @Deprecated
    public Map<Integer, String> getCardValues() {
        Map<Integer, String> cardValues = new LinkedHashMap<>();
        for (String scID : scIDs) {
            cardValues.put(Mapper.getStrategyCard(scID).getInitiative(), Mapper.getStrategyCard(scID).getName());
        }
        return cardValues;
    }

    @JsonIgnore
    public List<StrategyCardModel> getStrategyCardModels() {
        return scIDs.stream()
            .map(Mapper::getStrategyCard)
            .collect(Collectors.toList());
    }

    public String getSCName(int scNumber) {
        return scIDs.stream()
            .map(Mapper::getStrategyCard)
            .filter(sc -> sc.getInitiative() == scNumber)
            .map(StrategyCardModel::getName)
            .findFirst()
            .orElse("Name Unknown - Invalid SC Number: " + scNumber);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<StrategyCardModel> getStrategyCardModelByInitiative(int initiative) {
        return scIDs.stream()
            .map(Mapper::getStrategyCard)
            .filter(sc -> sc.getInitiative() == initiative)
            .findFirst();
    }

    public Optional<StrategyCardModel> getStrategyCardModelByName(String name) {
        return scIDs.stream()
            .map(Mapper::getStrategyCard)
            .filter(sc -> name.equalsIgnoreCase(sc.getName()))
            .findFirst();
    }

    @JsonIgnore
    public boolean isGroupedSet() {
        return getStrategyCardModels().stream().anyMatch(sc -> sc.getGroup().isPresent());
    }
}
