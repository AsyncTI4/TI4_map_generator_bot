package ti4.helpers.twilightsfall;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.LeaderModel;

public enum TfCardType {
    ABILITY,
    UNIT,
    GENOME,
    PARADIGM;

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

    public boolean matches(String card) {
        return Optional.of(this).equals(fromCard(card));
    }

    public Button toButton(String buttonId, String buttonLabel) {
        return switch (this) {
            case ABILITY -> Buttons.green(buttonId, buttonLabel);
            case UNIT -> Buttons.gray(buttonId, buttonLabel);
            case GENOME -> Buttons.blue(buttonId, buttonLabel);
            case PARADIGM -> Buttons.red(buttonId, buttonLabel);
        };
    }
}
