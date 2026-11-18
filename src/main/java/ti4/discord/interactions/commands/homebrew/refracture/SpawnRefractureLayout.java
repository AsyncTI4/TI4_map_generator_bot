package ti4.discord.interactions.commands.homebrew.refracture;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.map.FractureService;
import ti4.service.map.RefractureLayoutRegistry;

class SpawnRefractureLayout extends GameStateSubcommand {

    SpawnRefractureLayout() {
        super("spawn_layout", "Place a specific Refracture layout", true, true);
        addOptions(new OptionData(OptionType.INTEGER, "layout", "Refracture layout number")
                .setRequired(true)
                .setMinValue(1)
                .setMaxValue(10));
        addOption(OptionType.BOOLEAN, "with_demeter", "Also place Demeter II in an empty corner");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isRefractureMode()) {
            MessageHelper.sendMessageToEventChannel(event, "Refracture is not enabled for this game.");
            return;
        }
        if (!FractureService.canFractureEnterPlay(game)) {
            MessageHelper.sendMessageToEventChannel(event, FractureService.whyFractureCannotEnterPlay(game));
            return;
        }
        int layout = event.getOption("layout", 0, OptionMapping::getAsInt);
        boolean withDemeter = event.getOption("with_demeter", Boolean.FALSE, OptionMapping::getAsBoolean);
        if (!RefractureLayoutRegistry.isConfigured(layout)) {
            MessageHelper.sendMessageToEventChannel(event, "Refracture layout " + layout + " is not configured.");
            return;
        }
        if (!FractureService.spawnRefractureLayout(event, game, layout, withDemeter)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    withDemeter
                            ? "Refracture layout " + layout + " could not be placed with Demeter II."
                            : "Refracture layout " + layout + " could not be placed.");
            return;
        }
        Player player = getPlayer();
        FractureService.spawnIngressTokens(event, game, player, null);
        MessageHelper.sendMessageToEventChannel(
                event, "Refracture layout " + layout + (withDemeter ? " and Demeter II were placed." : " was placed."));
    }
}
