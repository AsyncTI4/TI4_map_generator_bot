package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;

@UtilityClass
public class ThirtyKTechSupport {

    private static final int MAX_BUTTONS = 24;

    public static List<Button> noPrerequisiteTechButtons(Game game, Player player) {
        List<TechnologyModel> techs = new ArrayList<>(game.getTechnologyDeck().stream()
                .distinct()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> tech.getRequirements().orElse("").isBlank())
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .filter(tech ->
                        tech.getFaction().isEmpty() || tech.getFaction().get().equalsIgnoreCase(player.getFaction()))
                .toList());
        List<Button> buttons = ListTechService.getTechButtons(techs, player, "nopay");
        return buttons.size() > MAX_BUTTONS ? buttons.subList(0, MAX_BUTTONS) : buttons;
    }
}
