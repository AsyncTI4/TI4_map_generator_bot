package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

@UtilityClass
public class FactionExtraSetupHelper {
    public static void offerKeleresSetupButtons(Player player, Predicate<String> isTaken, Predicate<String> isInDraft) {
        List<String> flavors = List.of("mentak", "xxcha", "argent");
        List<Button> keleresPresets = new ArrayList<>();
        boolean warn = false;
        for (String f : flavors) {
            if (isTaken.test(f)) continue;

            FactionModel model = Mapper.getFaction(f);
            String id = "draftPresetKeleres_" + f;
            String label = StringUtils.capitalize(f);
            if (isInDraft.test(f)) {
                keleresPresets.add(Buttons.gray(id, label + " 🛑", model.getFactionEmoji()));
                warn = true;
            } else {
                keleresPresets.add(Buttons.green(id, label, model.getFactionEmoji()));
            }
        }

        String message = player.getPing()
                + " Pre-select which flavor of Keleres to play in this game by clicking one of these buttons!";
        message += " You can change your decision later by clicking a different button.";
        if (warn)
            message +=
                    "\n- 🛑 Some of these factions are in the draft! 🛑 If you preset them and they get chosen, then the preset will be cancelled.";
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, keleresPresets);
    }
}
