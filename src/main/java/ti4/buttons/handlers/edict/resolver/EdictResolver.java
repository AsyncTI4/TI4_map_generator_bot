package ti4.buttons.handlers.edict.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.AgendaModel;

public interface EdictResolver {

    String getEdict();

    void handle(ButtonInteractionEvent event, Game game, Player player);

    default String gamePing(Game game) {
        return gamePing(game, null);
    }

    default String gamePing(Game game, String addlMsg) {
        String msg = String.format(msgFormat, game.getPing(), edictModel().getName());
        if (addlMsg != null && !addlMsg.isBlank()) return msg + "\n" + addlMsg.trim();
        return msg;
    }

    default String playerPing(Player player) {
        return playerPing(player, null);
    }

    default String playerPing(Player player, String addlMsg) {
        String msg = String.format(
                msgFormat, player.getRepresentationUnfogged(), edictModel().getName());
        if (addlMsg != null && !addlMsg.isBlank()) return msg + "\n" + addlMsg.trim();
        return msg;
    }

    String msgFormat = "%s, use the buttons to resolve _%s_:";

    default AgendaModel edictModel() {
        return Mapper.getAgenda(getEdict());
    }
}
