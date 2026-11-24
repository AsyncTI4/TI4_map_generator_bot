package ti4.service.info;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ExtraCardText {

    public static List<TextDisplay> relics(String faction, String... aliases) {
        return Arrays.stream(aliases)
                .map(Mapper::getRelic)
                .map(relic -> card(faction, relic.getSource(), relic.getName(), relic.getText(), relic.getNotes()))
                .toList();
    }

    public static List<TextDisplay> traps(String faction, String... aliases) {
        return Arrays.stream(aliases)
                .map(Mapper::getTrap)
                .map(trap -> card(faction, trap.getSource(), trap.getName(), trap.getText(), trap.getNotes()))
                .toList();
    }

    public static TextDisplay card(String faction, ComponentSource source, String name, String text, String notes) {
        String card = FactionEmojis.getFactionIcon(faction) + " **" + name + "** " + source.emoji() + "\n> "
                + text.trim().replace("\n", "\n> ");
        if (notes != null && !notes.isBlank()) {
            card += "\n-# [" + notes.trim().replace("\n", "\n-# ") + "]";
        }
        return TextDisplay.of(card);
    }
}
