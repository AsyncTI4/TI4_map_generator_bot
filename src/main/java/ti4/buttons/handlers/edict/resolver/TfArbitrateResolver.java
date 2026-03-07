package ti4.buttons.handlers.edict.resolver;

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

public class TfArbitrateResolver implements EdictResolver {

    @Getter
    String edict = "tf-arbitrate";

    private static final List<Button> buttons = List.of(
            Buttons.red("discardSpliceCard_ability", "Discard 1 Ability", MiscEmojis.tf_ability),
            Buttons.red("discardSpliceCard_units", "Discard 1 Unit Upgrade", TechEmojis.UnitUpgradeTech),
            Buttons.red("discardSpliceCard_genome", "Discard 1 Genome", MiscEmojis.tf_genome),
            Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability", MiscEmojis.tf_ability),
            Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade", TechEmojis.UnitUpgradeTech),
            Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome", MiscEmojis.tf_genome));

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        String msg = gamePing(
                game,
                "-# You must get permission from " + player.getRepresentation() + " in order to resolve this edict.");
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, buttons);
    }
}
