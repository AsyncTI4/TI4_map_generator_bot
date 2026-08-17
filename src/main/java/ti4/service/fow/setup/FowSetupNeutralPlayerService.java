package ti4.service.fow.setup;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.special.SetupNeutralPlayer;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.GameColorsService;

/** Step "Neutral Player" of the FoW setup wizard: give the neutral (Dicecord) player a color. */
final class FowSetupNeutralPlayerService {

    private FowSetupNeutralPlayerService() {}

    static void render(Game game, FowSetupWizardState state, StringBuilder sb, List<Button> buttons) {
        Player neutral = game.getPlayer(Constants.dicecordId);
        sb.append("Give the neutral player a color, then add their units (see the info thread below).\n")
                .append("> Currently: ")
                .append(
                        neutral != null && StringUtils.isNotBlank(neutral.getColor())
                                ? neutral.getColor()
                                : "_no color yet_")
                .append('\n');
        buttons.add(Buttons.green("fowSetupNeutralPlayerPick", "Pick Neutral Player Color"));
        buttons.add(Buttons.gray("fowSetupNeutralPlayerRandom", "Randomize Neutral Player Color"));
    }

    @ButtonHandler("fowSetupNeutralPlayerRandom")
    static void randomize(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String color = SetupNeutralPlayer.pickNeutralColor(game);
        game.setupNeutralPlayer(color);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Neutral player set to **" + color + "**.");
        FowSetupWizardService.openOrRefresh(game);
    }

    @ButtonHandler("fowSetupNeutralPlayerPick")
    static void openColorSelect(ButtonInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        List<ColorModel> options = GameColorsService.getUnusedColors(game).stream()
                .filter(color -> ColorChangeHelper.isColorAllowedForPlayer(color.getName(), Constants.dicecordId, game))
                .sorted(Comparator.comparing(ColorModel::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (options.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No colors are available to pick.");
            return;
        }
        // The colour palette is well over Discord's per-menu option cap, so this spans several menus.
        MessageHelper.sendPagedSelectMenus(
                event.getMessageChannel(),
                "fowSetupNeutralColorSelect",
                colorSelectOptions(options),
                "Pick a color for the neutral player:");
    }

    /** Shared with the per-player colour picker on the Factions step - same palette, same option shape. */
    static List<SelectOption> colorSelectOptions(List<ColorModel> colors) {
        return colors.stream()
                .map(color -> SelectOption.of(StringUtils.capitalize(color.getName()), color.getName())
                        .withEmoji(ColorEmojis.getColorEmoji(color.getName()).asEmoji()))
                .toList();
    }

    @SelectionHandler("fowSetupNeutralColorSelect")
    static void resolveColorSelect(StringSelectInteractionEvent event, Game game) {
        if (!FowSetupWizardService.requireGM(event, game)) return;
        String color = event.getValues().getFirst();
        game.setupNeutralPlayer(color);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Neutral player set to **" + color + "**.");
        FowSetupWizardService.openOrRefresh(game);
    }
}
