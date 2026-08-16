package ti4.service.fow.setup;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.special.SetupNeutralPlayer;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
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
        // Discord select menus cap out at 25 options - this codebase's color palette is well over that.
        for (List<ColorModel> page : ListUtils.partition(options, 25)) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("fowSetupNeutralColorSelect");
            for (ColorModel color : page) {
                menuBuilder.addOptions(SelectOption.of(StringUtils.capitalize(color.getName()), color.getName())
                        .withEmoji(ColorEmojis.getColorEmoji(color.getName()).asEmoji()));
            }
            menuBuilder.setRequiredRange(1, 1);
            String range = FowSetupFactionService.pageRangeLabel(page.stream()
                    .map(color -> StringUtils.capitalize(color.getName()))
                    .toList());
            event.getMessageChannel()
                    .sendMessage("Pick a color for the neutral player" + range + ":")
                    .addComponents(ActionRow.of(menuBuilder.build()))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
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
