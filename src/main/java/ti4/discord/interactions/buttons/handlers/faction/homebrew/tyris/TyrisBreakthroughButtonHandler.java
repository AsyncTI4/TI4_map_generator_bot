package ti4.discord.interactions.buttons.handlers.faction.homebrew.tyris;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

@UtilityClass
public class TyrisBreakthroughButtonHandler {

    private static final String STORED_KEY_SUFFIX = "tyrisBTTechs";

    public static List<String> getTyrisBTTechs(Player player, Game game) {
        List<String> result = new ArrayList<>();
        for (String tech :
                game.getStoredValue(player.getFaction() + STORED_KEY_SUFFIX).split(",")) {
            if (!tech.isEmpty() && Mapper.getTech(tech) != null) {
                result.add(tech);
            }
        }
        return result;
    }

    public static void addTyrisBTTech(Player player, Game game, String techID) {
        if (getTyrisBTTechs(player, game).contains(techID)) return;
        String prev = game.getStoredValue(player.getFaction() + STORED_KEY_SUFFIX);
        game.setStoredValue(player.getFaction() + STORED_KEY_SUFFIX, prev.isEmpty() ? techID : prev + "," + techID);
        player.removeTech(techID);
    }

    public static String removeTyrisBTTech(Player player, Game game) {
        List<String> techs = getTyrisBTTechs(player, game);
        if (techs.isEmpty()) return null;
        String removed = techs.getFirst();
        removeTechByID(player, game, removed);
        return removed;
    }

    private static void removeTechByID(Player player, Game game, String techID) {
        String stored = game.getStoredValue(player.getFaction() + STORED_KEY_SUFFIX);
        stored = stored.replace("," + techID, "").replace(techID + ",", "").replace(techID, "");
        game.setStoredValue(player.getFaction() + STORED_KEY_SUFFIX, stored);
        player.addTech(techID);
    }

    public static Optional<Button> getPlaceButton(Player player, Game game) {
        List<String> onCard = getTyrisBTTechs(player, game);
        boolean hasEligible = player.getTechs().stream()
                .filter(t -> !onCard.contains(t))
                .map(Mapper::getTech)
                .anyMatch(m -> m != null && !m.isUnitUpgrade());
        if (!hasEligible) return Optional.empty();
        int current = onCard.size();
        String label =
                "Place Tech on Non-Linear Time Progression" + (current > 0 ? " (currently -" + current + ")" : "");
        return Optional.of(Buttons.green(player.factionButtonChecker() + "tyrisBTSelectTech", label));
    }

    @ButtonHandler("tyrisBTSelectTech")
    public static void selectTech(ButtonInteractionEvent event, Game game, Player player) {
        List<String> onCard = getTyrisBTTechs(player, game);
        List<Button> buttons = new ArrayList<>();
        for (String techID : player.getTechs()) {
            if (onCard.contains(techID)) continue;
            TechnologyModel m = Mapper.getTech(techID);
            if (m == null || m.isUnitUpgrade()) continue;
            buttons.add(Buttons.green(player.factionButtonChecker() + "tyrisBTPlaceTech_" + techID, m.getName()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has no eligible technologies to place on _Non-Linear Time Progression_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a non-unit-upgrade technology to place on _Non-Linear Time Progression_:",
                buttons);
    }

    @ButtonHandler("tyrisBTPlaceTech_")
    public static void placeTech(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String techID = buttonID.replace("tyrisBTPlaceTech_", "");
        TechnologyModel m = Mapper.getTech(techID);
        if (m == null) return;
        addTyrisBTTech(player, game, techID);
        player.addSpentThing("tyrisbt");
        int count = getTyrisBTTechs(player, game).size();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed _" + m.getName()
                        + "_ on _Non-Linear Time Progression_ (cost reduced by "
                        + StringHelper.pluralize(count, "resource") + " total).");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tyrisBTRemoveTech_")
    public static void removeTech(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String techID = buttonID.replace("tyrisBTRemoveTech_", "");
        if (!getTyrisBTTechs(player, game).contains(techID)) return;
        removeTechByID(player, game, techID);
        TechnologyModel m = Mapper.getTech(techID);
        String name = m != null ? "_" + m.getName() + "_" : techID;
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " removed " + name + " from _Non-Linear Time Progression_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void sendNonLinearTimeProgressionInfo(Game game, Player tyris, Player caller) {
        List<String> techs = getTyrisBTTechs(tyris, game);
        StringBuilder msg = new StringBuilder("Here are the current techs on _Non-Linear Time Progression_:");
        if (techs.isEmpty()) {
            msg.append(" _(none)_");
        } else {
            for (String id : techs) {
                TechnologyModel m = Mapper.getTech(id);
                msg.append("\n- ").append(m != null ? "_" + m.getName() + "_" : id);
            }
        }
        MessageHelper.sendMessageToChannel(caller.getCardsInfoThread(), msg.toString());
    }

    public static void handlePlayerPassed(Game game, Player p) {
        List<String> techs = getTyrisBTTechs(p, game);
        if (techs.isEmpty()) return;
        if (techs.size() == 1) {
            String removed = removeTyrisBTTech(p, game);
            TechnologyModel m = Mapper.getTech(removed);
            String name = m != null ? "_" + m.getName() + "_" : removed;
            MessageHelper.sendMessageToChannel(
                    p.getCardsInfoThread(),
                    p.getRepresentation() + "'s " + name
                            + " was removed from _Non-Linear Time Progression_ because a player passed.");
        } else {
            List<Button> buttons = new ArrayList<>();
            for (String techID : techs) {
                TechnologyModel m = Mapper.getTech(techID);
                String label = m != null ? m.getName() : techID;
                buttons.add(Buttons.red(p.factionButtonChecker() + "tyrisBTRemoveTech_" + techID, label));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    p.getCardsInfoThread(),
                    p.getRepresentation()
                            + ", a player passed — choose 1 technology to remove from _Non-Linear Time Progression_:",
                    buttons);
        }
    }
}
