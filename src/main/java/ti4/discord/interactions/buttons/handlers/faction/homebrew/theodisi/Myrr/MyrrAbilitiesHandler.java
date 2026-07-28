package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;
import ti4.game.Player;

@UtilityClass
public class MyrrAbilitiesHandler {
    
    public static boolean hasEchoOfTheAnvilDiscount(Player player) {
        if (!player.hasAbility("echo_of_the_anvil")) {
            return false;
        }

        Map<String, Integer> producedByType = new HashMap<>();
        int totalProduced = 0;

        for (Map.Entry<String, Integer> entry : player.getCurrentProducedUnits().entrySet()) {
            String unitType = entry.getKey().split("_", 2)[0];
            int count = entry.getValue();

            totalProduced += count;
            producedByType.merge(unitType, count, Integer::sum);
        }

        return totalProduced >= 2
                && producedByType.values().stream().anyMatch(count -> count >= 2);
    }
}
