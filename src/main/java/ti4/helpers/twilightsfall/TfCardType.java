package ti4.helpers.twilightsfall;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.LeaderModel;
import ti4.model.UnitModel;
import ti4.service.VeiledHeartService;

public enum TfCardType {
    ABILITY {
        @Override
        protected String getDeckId() {
            return "techs_tf";
        }

        @Override
        public boolean isRevealed(String card, Player player) {
            return player.getTechs().contains(card) || player.getPurgedTechs().contains(card);
        }

        public Button toButton(String buttonId, String buttonLabel) {
            return Buttons.green(buttonId, buttonLabel);
        }
    },
    UNIT {
        @Override
        public List<String> allCards() {
            return Mapper.getUnits().values().stream()
                    .filter(UnitModel::isTfCard)
                    .map(UnitModel::getId)
                    .toList();
        }

        public Button toButton(String buttonId, String buttonLabel) {
            return Buttons.gray(buttonId, buttonLabel);
        }
    },
    GENOME {
        @Override
        public boolean isRevealed(String card, Player player) {
            return player.getLeaderIDs().contains(card);
        }
        public Button toButton(String buttonId, String buttonLabel) {
            return Buttons.blue(buttonId, buttonLabel);
        }
    },
    PARADIGM {
        public Button toButton(String buttonId, String buttonLabel) {
            return Buttons.red(buttonId, buttonLabel);
        }
    };

    protected String getDeckId() {
        return "tf_" + this;
    }

    public boolean isRevealed(String card, Player player) {
        return false;
    }

    public boolean isDrawn(String card, Player player) {
        return isRevealed(card, player) || isVeiled(card, player);
    }

    public boolean isVeiled(String card, Player player) {
        return player.getGame().isVeiledHeartMode() && VeiledHeartService.hasVeiledCard(card, player);
    }

    public boolean isRemaining(String card, Player player) {
        return !isDrawn(card, player);
    }

    public boolean isUnknown(String card, Player player) {
        return !isRevealed(card, player);
    }

    public List<String> allCards() {
        return Mapper.getDeck(getDeckId()).getNewDeck();
    }

    public abstract Button toButton(String buttonId, String buttonLabel);

    public boolean matches(String card) {
        return Optional.of(this).equals(fromCard(card));
    }

    public static Optional<TfCardType> fromCard(String card) {
        if (Mapper.getTech(card) != null) {
            return Optional.of(ABILITY);
        }
        if (Mapper.getUnit(card) != null) {
            return Optional.of(UNIT);
        }
        LeaderModel leaderModel = Mapper.getLeader(card);
        if (leaderModel != null) {
            if (Constants.AGENT.equalsIgnoreCase(leaderModel.getType())) {
                return Optional.of(GENOME);
            }
            if (Constants.HERO.equalsIgnoreCase(leaderModel.getType())) {
                return Optional.of(PARADIGM);
            }
        }
        return Optional.empty();
    }

    public static Optional<TfCardType> fromString(String str) {
        str = str.toLowerCase();
        if (str.contains("abil") || str.contains("tech")) {
            return Optional.of(ABILITY);
        }
        if (str.contains("unit") || str.contains("upgr")) {
            return Optional.of(UNIT);
        }
        if (str.contains("genome") || str.contains("agent")) {
            return Optional.of(GENOME);
        }
        if (str.contains("para") || str.contains("hero")) {
            return Optional.of(PARADIGM);
        }
        return Optional.empty();
    }
}
