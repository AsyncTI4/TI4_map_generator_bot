package ti4.commands.breakthrough;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.service.breakthrough.DataSkimmerService;
import ti4.service.breakthrough.VoidTetherService;

class BreakthroughActivate extends GameStateSubcommand {

    BreakthroughActivate() {
        super(Constants.BREAKTHROUGH_ACTIVATE, "Activate (or unactivate) breakthrough", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        BreakthroughModel bt = player.getBreakthroughModel();
        if (bt == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Player does not have a breakthrough");
        } else {
            List<String> activatable = List.of("naazbt");
            List<String> usable = List.of("ralnelbt", "empyreanbt");

            if (activatable.contains(bt.getAlias())) {
                boolean active = player.isBreakthroughActive();
                player.setBreakthroughActive(!active);
                String message = player.getRepresentation() + (active ? " de-" : " ") + "activated their breakthrough "
                        + bt.getName();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            } else if (usable.contains(bt.getAlias())) {
                switch (bt.getAlias()) {
                    case "ralnelbt" -> DataSkimmerService.fixDataSkimmer(getGame(), player);
                    case "empyreanbt" -> VoidTetherService.fixVoidTether(getGame(), player);
                }
            } else {
                String msg = player.getRepresentation() + "'s breakthrough, " + bt.getName() + ", cannot be activated.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            }
        }
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
