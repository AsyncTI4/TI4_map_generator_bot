package ti4.service.leader.agent;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class AgentTargets {

    public static Optional<Player> player(Game game, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.empty();
        }
        Player wholePayloadMatch = game.getPlayerFromColorOrFaction(payload);
        if (wholePayloadMatch != null) {
            return Optional.of(wholePayloadMatch);
        }
        return Optional.ofNullable(game.getPlayerFromColorOrFaction(StringUtils.substringBefore(payload, "_")));
    }

    public static Optional<Player> playerOrSelf(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(user);
        }
        return player(game, payload);
    }
}
