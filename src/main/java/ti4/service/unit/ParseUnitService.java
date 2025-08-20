package ti4.service.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.PlanetService;

@UtilityClass
public class ParseUnitService {

    public ParsedUnit simpleParsedUnit(Player player, UnitType type, String holder, Integer amt) {
        return new ParsedUnit(Units.getUnitKey(type, player.getColorID()), amt, holder);
    }

    public ParsedUnit simpleParsedUnit(Player player, UnitType type, UnitHolder holder, Integer amt) {
        return new ParsedUnit(Units.getUnitKey(type, player.getColorID()), amt, holder.getName());
    }

    public List<ParsedUnit> getParsedUnits(
            GenericInteractionCreateEvent event, String color, Tile tile, String unitList) {
        if (!Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "The unit color is invalid: " + color);
            return Collections.emptyList();
        }

        unitList = preprocessUnitList(unitList);
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");

        List<ParsedUnit> parsedUnits = new ArrayList<>();
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            ParsedUnit parsedUnit = parseUnit(unitListToken, color, tile, event);
            if (parsedUnit != null) {
                parsedUnits.add(parsedUnit);
            }
        }

        return parsedUnits;
    }

    private String preprocessUnitList(String unitList) {
        return unitList.replace(", ", ",").replace("-", "").replace("'", "").toLowerCase();
    }

    private ParsedUnit parseUnit(String unitListToken, String color, Tile tile, GenericInteractionCreateEvent event) {
        StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");

        String firstToken = unitInfoTokenizer.nextToken();
        int count = 1;
        String originalUnit;
        if (NumberUtils.isDigits(firstToken)) {
            count = Math.max(Integer.parseInt(firstToken), 1);
            if (!unitInfoTokenizer.hasMoreTokens()) return null;
            originalUnit = unitInfoTokenizer.nextToken();
        } else {
            originalUnit = firstToken;
        }

        String resolvedUnit = AliasHandler.resolveUnit(originalUnit);

        Units.UnitKey unitKey = Mapper.getUnitKey(resolvedUnit, color);

        String planetName = parsePlanetName(unitInfoTokenizer, tile);

        var parsedUnit = new ParsedUnit(unitKey, count, planetName);
        if (event instanceof SlashCommandInteractionEvent
                && !validateParsedUnit(parsedUnit, tile, unitListToken, event)) {
            return null;
        }
        return parsedUnit;
    }

    private String parsePlanetName(StringTokenizer tokenizer, Tile tile) {
        if (!tokenizer.hasMoreTokens()) return Constants.SPACE;

        String planetToken = tokenizer.nextToken();
        if (tokenizer.hasMoreTokens()) {
            planetToken += tokenizer.nextToken();
        }
        String resolvedPlanet = AliasHandler.resolvePlanet(planetToken);
        return PlanetService.getPlanet(tile, resolvedPlanet);
    }

    private boolean validateParsedUnit(
            ParsedUnit parsedUnit, Tile tile, String unitListToken, GenericInteractionCreateEvent event) {
        boolean isValidUnit = parsedUnit.getUnitKey() != null;
        boolean isValidUnitHolder =
                parsedUnit.getLocation().equals(Constants.SPACE) || tile.isSpaceHolderValid(parsedUnit.getLocation());

        if (isValidUnit && isValidUnitHolder) {
            return true;
        }

        sendValidationError(event, unitListToken, parsedUnit, isValidUnit, isValidUnitHolder, tile);
        return false;
    }

    private void sendValidationError(
            GenericInteractionCreateEvent event,
            String token,
            ParsedUnit parsedUnit,
            boolean isValidUnit,
            boolean isValidUnitHolder,
            Tile tile) {
        String message = "Could not parse this section of the command: `" + token + "`\n"
                + formatValidationMessage(
                        "Unit",
                        isValidUnit,
                        parsedUnit.getUnitKey(),
                        "UnitID or Alias not found. Try: `inf, mech, dn, etc.`")
                + formatValidationMessage(
                        "Planet",
                        isValidUnitHolder,
                        parsedUnit.getLocation(),
                        "Planets in this system are: "
                                + String.join(", ", tile.getUnitHolders().keySet()));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }

    private String formatValidationMessage(String type, boolean isValid, Object value, String errorMessage) {
        return (isValid ? "✅" : "❌") + " " + type + " = `" + value + "`" + (isValid ? "" : " -> " + errorMessage)
                + "\n";
    }
}
