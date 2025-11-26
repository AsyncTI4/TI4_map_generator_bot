package ti4.service;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.*;
import ti4.model.LeaderModel;

@UtilityClass
public class VeiledHeartService {
    public enum VeiledCardType {
        ABILITY,
        UNIT,
        GENOME,
        PARADIGM;

        static Optional<VeiledCardType> fromCard(String card) {
            if (Mapper.getTech(card) != null) {
                return Optional.of(VeiledCardType.ABILITY);
            }
            if (Mapper.getUnit(card) != null) {
                return Optional.of(VeiledCardType.UNIT);
            }
            LeaderModel leaderModel = Mapper.getLeader(card);
            if (leaderModel != null) {
                if (Constants.AGENT.equalsIgnoreCase(leaderModel.getType())) {
                    return Optional.of(VeiledCardType.GENOME);
                }
                if (Constants.HERO.equalsIgnoreCase(leaderModel.getType())) {
                    return Optional.of(VeiledCardType.PARADIGM);
                }
            }
            return Optional.empty();
        }

        boolean matches(String card) {
            return Optional.of(this).equals(fromCard(card));
        }
    }

    private static Stream<String> getVeiledCards(Player player) {
        return Arrays.stream(player.getGame()
                .getStoredValue("veiledCards" + player.getFaction())
                .split("_"));
    }

    private static Stream<String> getVeiledCards(VeiledCardType type, Player player) {
        return getVeiledCards(player).filter(type::matches);
    }

    private static Map<VeiledCardType, List<String>> getVeiledCardsByType(Game game, Player player) {
        Map<VeiledCardType, List<String>> veiledCardsByType = new HashMap<>();
        for (VeiledCardType cardType : VeiledCardType.values()) {
            veiledCardsByType.put(cardType, new ArrayList<>());
        }

        getVeiledCards(player).forEach(card -> {
            VeiledCardType.fromCard(card).ifPresent(type -> {
                veiledCardsByType.get(type).add(card);
            });
        });
        return veiledCardsByType;
    }
}
