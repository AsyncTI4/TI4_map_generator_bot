package ti4.buttons.handlers.unitPickers;

import java.util.regex.Matcher;
import lombok.experimental.UtilityClass;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class UnitPickerHandlerHelper {

    public String singleUnitRegex(Game game, String action) {
        String regexSingleUnit = action;
        regexSingleUnit += "_" + RegexHelper.posRegex(game);
        regexSingleUnit += "_" + RegexHelper.intRegex("amt");
        regexSingleUnit += "_" + RegexHelper.unitTypeRegex();
        regexSingleUnit += RegexHelper.optional("_" + RegexHelper.unitStateRegex());
        regexSingleUnit += RegexHelper.optional("_" + RegexHelper.planetNameRegex(game, "planet"));
        regexSingleUnit += RegexHelper.optional("_" + RegexHelper.colorRegex(game));
        return regexSingleUnit;
    }

    // TODO: Jazz make this useful
    public ParsedUnit parsedUnitFromMatcher(Player player, Matcher matcher) {
        int amt = Integer.parseInt(matcher.group("amt"));
        UnitType type = Units.findUnitType(matcher.group("unittype"));
        // TODO: Jazz add this functionality by default to parsed unit
        // boolean prefersState = matcher.group("state") != null && StringUtils.isNotBlank(matcher.group("state"));
        // UnitState state = prefersState ? Units.findUnitState(matcher.group("state")) : UnitState.none;
        String location = matcher.group("planet");
        if (location == null || location.isBlank()) location = "space";

        UnitKey key = Units.getUnitKey(type, player.getColorID());
        return new ParsedUnit(key, amt, location);
    }
}
