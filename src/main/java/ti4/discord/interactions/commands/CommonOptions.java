package ti4.discord.interactions.commands;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;

@UtilityClass
public class CommonOptions {

    public static OptionData factionColor() {
        return factionColor("Faction or Color");
    }

    public static OptionData factionColorForStats() {
        return factionColor("Faction or Color for which you set stats");
    }

    public static OptionData factionColor(String description) {
        return new OptionData(OptionType.STRING, Constants.FACTION_COLOR, description).setAutoComplete(true);
    }

    public static OptionData targetFactionOrColor() {
        return targetFactionOrColor("Faction or Color");
    }

    public static OptionData targetFactionOrColor(String description) {
        return new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, description)
                .setRequired(true)
                .setAutoComplete(true);
    }

    public static OptionData tileName() {
        return tileName("System/Tile name");
    }

    public static OptionData tileName(String description) {
        return new OptionData(OptionType.STRING, Constants.TILE_NAME, description)
                .setRequired(true)
                .setAutoComplete(true);
    }

    public static OptionData planet() {
        return planet("Planet");
    }

    public static OptionData planet(String description) {
        return new OptionData(OptionType.STRING, Constants.PLANET, description)
                .setRequired(true)
                .setAutoComplete(true);
    }

    public static OptionData noMapGen() {
        return new OptionData(
                OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command");
    }

    public static OptionData leader() {
        return new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action")
                .setRequired(true)
                .setAutoComplete(true);
    }

    public static List<OptionData> playerOptions(boolean firstRequired, IntFunction<String> description) {
        List<String> names = List.of(
                Constants.PLAYER1,
                Constants.PLAYER2,
                Constants.PLAYER3,
                Constants.PLAYER4,
                Constants.PLAYER5,
                Constants.PLAYER6,
                Constants.PLAYER7,
                Constants.PLAYER8);
        return IntStream.range(0, names.size())
                .mapToObj(i -> new OptionData(OptionType.USER, names.get(i), description.apply(i + 1))
                        .setRequired(firstRequired && i == 0))
                .toList();
    }
}
