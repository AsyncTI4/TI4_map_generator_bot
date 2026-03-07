package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;

public class TfSpliceResolver implements EdictResolver {

    @Getter
    String edict = "tf-splice";

    private static List<Button> buttons(Player player) {
        String id = player.finChecker() + "startSplice_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(id + "7_all", "Initiate Ability Splice", MiscEmojis.tf_ability));
        buttons.add(Buttons.gray(id + "2_all", "Initiate Genome Splice", MiscEmojis.tf_genome));
        buttons.add(Buttons.blue(id + "6_all", "Initiate Unit Upgrade Splice", TechEmojis.UnitUpgradeTech));
        return buttons;
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), playerPing(player), buttons(player));
    }
}
