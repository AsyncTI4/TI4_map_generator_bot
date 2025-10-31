package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class ValefarZService {

    @ButtonHandler("valefarZ_")
    public void resolveValefarZ(ButtonInteractionEvent event, Game game, Player nekro, String buttonID) {
        String faction = buttonID.replace("valefarZ_", "");
        Player victim = game.getPlayerFromColorOrFaction(faction);
        if (victim == null) return;
        UnitModel fs = victim.getUnitByBaseType("flagship");
        if (fs == null) {
            MessageHelper.sendMessageToChannel(nekro.getCorrectChannel(), "Target has no flagship...?");
            return;
        }
        String ability = fs.getAbility().orElse(fs.getName() + " has no text ability, womp womp");
        String msg = "Placed a Valefar Z token on " + faction + "'s flagship, " + fs.getNameRepresentation()
                + ", gaining the ability:\n> " + ability;

        game.setStoredValue("valefarZ", game.getStoredValue("valefarZ") + faction + "|");
        MessageHelper.sendMessageToChannel(nekro.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public boolean hasFlagshipAbility(Game game, Player player, String flagship) {
        if (player == null) return false;
        if (player.getUnitsOwned().contains(flagship)) return true;

        // Check for Valefar Z
        if (player.hasUnlockedBreakthrough("nekrobt")) {
            String valefarZ = game.getStoredValue("valefarZ");
            for (Player p : game.getPlayers().values()) {
                if (p.getUnitsOwned().contains(flagship) && valefarZ.contains(p.getFaction())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasAnyFlagshipAbility(Game game, Player player, String... flagships) {
        if (player == null) return false;
        for (String fs : flagships) {
            if (hasFlagshipAbility(game, player, fs)) {
                return true;
            }
        }
        return false;
    }

    public List<Player> getAllPlayersWithFlagships(Game game, String... flagships) {
        List<Player> players = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (ValefarZService.hasAnyFlagshipAbility(game, player, flagships)) {
                players.add(player);
            }
        }
        return players;
    }
}
