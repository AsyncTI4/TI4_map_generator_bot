package ti4.buttons.handlers.edict.resolver;

import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelperTwilightsFallActionCards;
import ti4.helpers.DiceHelper.Die;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.agenda.IxthianArtifactService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;

public class TfLegacyOfIxthResolver implements EdictResolver {

    @Getter
    public String edict = "tf-legacy_of_ixth";

    private static final List<Button> buttons = List.of(
            Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability", MiscEmojis.tf_ability),
            Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade", TechEmojis.UnitUpgradeTech),
            Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome", MiscEmojis.tf_genome));

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        Die d10 = new Die(6);

        IxthianArtifactService.informGameAndWatchPartyAndExhaustHeart(game, d10, false, true);
        if (d10.isSuccess()) {
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), gamePing(game), buttons);
        } else {
            Tile tile = game.getMecatolTile();
            ButtonHelperTwilightsFallActionCards.sendDestroyButtonsForSpecificTileAndSurrounding(game, tile);
        }
    }
}
