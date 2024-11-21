package ti4.service.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.PlanetService;

@UtilityClass
public class ParseUnitService {

    public List<ParsedUnit> getParsedUnits(GenericInteractionCreateEvent event, String color, Tile tile, String unitList) {
        validateColor(color);

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

    private void validateColor(String color) {
        if (!Mapper.isValidColor(color)) {
            throw new IllegalStateException("Invalid color: " + color);
        }
    }

    private String preprocessUnitList(String unitList) {
        return unitList.replace(", ", ",").replace("-", "").replace("'", "").toLowerCase();
    }

    private ParsedUnit parseUnit(String unitListToken, String color, Tile tile, GenericInteractionCreateEvent event) {
        StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");

        int count = parseCount(unitInfoTokenizer);
        count = Math.max(count, 1);

        String originalUnit = parseUnitName(unitInfoTokenizer);
        String resolvedUnit = AliasHandler.resolveUnit(originalUnit);

        Units.UnitKey unitKey = Mapper.getUnitKey(resolvedUnit, color);

        String planetName = parsePlanetName(unitInfoTokenizer, tile);

        var parsedUnit =  new ParsedUnit(unitKey, count, planetName);
        if (!validateParsedUnit(parsedUnit, tile, unitListToken, event)) {
            return null;
        }
        return parsedUnit;
    }

    private int parseCount(StringTokenizer tokenizer) {
        if (!tokenizer.hasMoreTokens()) return 1;
        try {
            return Integer.parseInt(tokenizer.nextToken());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String parseUnitName(StringTokenizer tokenizer) {
        return tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "";
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

    private boolean validateParsedUnit(ParsedUnit parsedUnit, Tile tile, String unitListToken, GenericInteractionCreateEvent event) {
        boolean isValidUnit = parsedUnit.getUnitKey() != null;
        boolean isValidUnitHolder = parsedUnit.getLocation().equals(Constants.SPACE) || tile.isSpaceHolderValid(parsedUnit.getLocation());

        if (!(event instanceof SlashCommandInteractionEvent) || (isValidUnit && isValidUnitHolder)) {
            return true;
        }

        sendValidationError(event, unitListToken, parsedUnit, isValidUnit, isValidUnitHolder, tile);
        return false;
    }

    private void sendValidationError(GenericInteractionCreateEvent event, String token, ParsedUnit parsedUnit, boolean isValidUnit,
                                        boolean isValidUnitHolder, Tile tile) {
        String message = "Could not parse this section of the command: `" + token + "`\n"
            + formatValidationMessage("Unit", isValidUnit, parsedUnit.getUnitKey(), "UnitID or Alias not found. Try: `inf, mech, dn, etc.`")
            + formatValidationMessage("Planet", isValidUnitHolder, parsedUnit.getLocation(), "Planets in this system are: " + String.join(", ", tile.getUnitHolders().keySet()));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }

    private String formatValidationMessage(String type, boolean isValid, Object value, String errorMessage) {
        return (isValid ? "✅" : "❌") + " " + type + " = `" + value + "`" + (isValid ? "" : " -> " + errorMessage) + "\n";
    }
}
