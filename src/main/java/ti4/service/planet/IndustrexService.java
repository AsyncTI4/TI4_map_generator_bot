package ti4.service.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

@UtilityClass
public class IndustrexService {

    public static List<Button> getIndustrexButtonsPart1(Game game, Player player) {
        SortedSet<UnitType> typesAvailable = new TreeSet<>();
        for (String tech : player.getTechs()) {
            TechnologyModel model = Mapper.getTech(tech);
            if (!model.isUnitUpgrade()) continue;
            UnitModel unitModel = Mapper.getUnitModelByTechUpgrade(tech);
            if (unitModel != null && unitModel.getIsShip()) {
                typesAvailable.add(unitModel.getUnitType());
            }
        }
        if (game.isTwilightsFallMode()) {
            for (String unit : player.getUnitsOwned()) {
                if (unit.contains("tf-")) {
                    UnitModel unitModel = Mapper.getUnit(unit);
                    if (unitModel != null && unitModel.getIsShip()) {
                        typesAvailable.add(unitModel.getUnitType());
                    }
                }
            }
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitType type : typesAvailable) {
            String id = player.finChecker() + "industrexPickType_" + type.getValue();
            String label = "Place " + type.humanReadableName();
            buttons.add(Buttons.green(id, label, type.getUnitTypeEmoji()));
        }
        return buttons;
    }

    public static List<Button> getIndustrexButtonsPart2(Game game, Player player, UnitType typeChosen) {
        return Helper.getTileWithShipsPlaceUnitButtons(player, game, typeChosen.plainName(), "placeOneNDone_skipbuild");
    }
}
