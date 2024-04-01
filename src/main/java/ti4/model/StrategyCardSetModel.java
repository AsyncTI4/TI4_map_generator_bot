package ti4.model;

import lombok.Data;
import ti4.generator.Mapper;
import ti4.model.Source.ComponentSource;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
            && StringUtils.isNotBlank(alias);
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @JsonIgnore
    public Map<Integer, String> getCardValues() {
        return scIDs.stream()
            .map(Mapper::getStrategyCard)
            .collect(Collectors.toMap(StrategyCardModel::getInitiative, StrategyCardModel::getName));
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

    public Optional<StrategyCardModel> getSCModel(int scNumber) {
        return scIDs.stream()
            .map(Mapper::getStrategyCard)
            .filter(sc -> sc.getInitiative() == scNumber)
            .findFirst();
    }
}
