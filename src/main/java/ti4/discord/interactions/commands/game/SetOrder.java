package ti4.discord.interactions.commands.game;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.service.game.SetOrderService;

class SetOrder extends GameStateSubcommand {

    public SetOrder() {
        super(Constants.SET_ORDER, "Set player order in game", true, false);
        addOptions(CommonOptions.playerOptions(false, i -> "Player" + i));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            User member = event.getOption("player" + i, null, OptionMapping::getAsUser);
            if (member == null) {
                break;
            }
            users.add(member);
        }
        if (users.isEmpty()) {
            Helper.setOrder(getGame());
        } else {
            SetOrderService.setPlayerOrder(event, getGame(), users);
        }
    }
}
