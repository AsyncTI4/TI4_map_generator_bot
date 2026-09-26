package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class KryxosUnitHandler {
    private static final String FLIP_UNIT = "kryxosFlipUnit_";
    private static final String KRYXOS_FLAGSHIP_TECH = "thkryxosfs";
    private static final String KRYXOS_FLAGSHIP_III_TECH = "thkryxosfs2";
    private static final String KRYXOS_MECH_TECH = "thkryxosmf";
    private static final String KRYXOS_MECH_III_TECH = "thkryxosmf2";
    private static final String DAMAGE_WARSPAWN_JUGGERNAUT = "kryxosDamageWarspawnJuggernaut_";
    private static final String DECLINE_WARSPAWN_JUGGERNAUT = "kryxosDeclineWarspawnJuggernaut";

    public static void offerEvolutionButtons(Player player, Game game, String techID) {
        if (player == null
                || game == null
                || techID == null
                || !player.getTechs().contains(techID)
                || "thkryxosfs".equalsIgnoreCase(techID)
                || "thkryxosmf".equalsIgnoreCase(techID)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (player.ownsUnit("kryxos_flagship2") && !player.ownsUnit("kryxos_flagship3")) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FLIP_UNIT + techID + "|flagship", "Flip Ultimate Evolution II"));
        }
        if (player.ownsUnit("kryxos_mech2") && !player.ownsUnit("kryxos_mech3")) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FLIP_UNIT + techID + "|mech", "Flip Warspawn Juggernaut II"));
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", after gaining a technology, you may flip one of your Kryxos units.",
                buttons);
    }

    public static boolean isKryxosEvolutionResultTech(String techID) {
        return KRYXOS_FLAGSHIP_III_TECH.equals(techID) || KRYXOS_MECH_III_TECH.equals(techID);
    }

    public static void offerWarspawnJuggernautHitButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder unitHolder,
            List<Integer> hitsByMech) {
        if (event == null
                || game == null
                || player == null
                || tile == null
                || unitHolder == null
                || hitsByMech == null
                || hitsByMech.isEmpty()
                || !player.ownsUnit("kryxos_mech3")) {
            return;
        }

        UnitKey mechKey = Units.getUnitKey(UnitType.Mech, player.getColorID());
        int undamagedMechs = unitHolder.getUnitCount(mechKey) - unitHolder.getDamagedUnitCount(mechKey);
        if (undamagedMechs <= 0) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (int hits : hitsByMech) {
            if (hits <= 0 || buttons.size() >= undamagedMechs) {
                continue;
            }
            buttons.add(Buttons.red(
                    player.factionButtonChecker()
                            + DAMAGE_WARSPAWN_JUGGERNAUT
                            + tile.getPosition()
                            + "|"
                            + unitHolder.getName()
                            + "|"
                            + hits
                            + "|"
                            + buttons.size(),
                    "Damage Warspawn Juggernaut III: Place " + hits + " Infantry"));
        }
        if (buttons.isEmpty()) {
            return;
        }

        buttons.add(Buttons.red(
                player.factionButtonChecker() + DECLINE_WARSPAWN_JUGGERNAUT, "Decline Warspawn Juggernaut III"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", each Warspawn Juggernaut III (Kryxos mech) that rolled at least one hit may SUSTAIN DAMAGE to place infantry "
                        + "with it equal to the hits it rolled.",
                buttons);
    }

    @ButtonHandler(DAMAGE_WARSPAWN_JUGGERNAUT)
    public static void resolveWarspawnJuggernautDamage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null) {
            return;
        }
        if (game == null || player == null || !player.ownsUnit("kryxos_mech3")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] parts = buttonID.substring(DAMAGE_WARSPAWN_JUGGERNAUT.length()).split("\\|", 4);
        if (parts.length != 4) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        int hits;
        try {
            hits = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        UnitKey mechKey = Units.getUnitKey(UnitType.Mech, player.getColorID());
        if (hits <= 0
                || unitHolder == null
                || unitHolder.getUnitCount(mechKey) <= unitHolder.getDamagedUnitCount(mechKey)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        unitHolder.addDamagedUnit(mechKey, 1);
        String location = unitHolder.getName();
        String infantry = hits + " inf" + (Constants.SPACE.equals(location) ? "" : " " + location);
        AddUnitService.addUnits(event, tile, game, player.getColor(), infantry);
        boolean hasAnotherWarspawnButton = event.getMessage().getComponentTree().findAll(Button.class).stream()
                .anyMatch(button -> button.getCustomId() != null
                        && button.getCustomId().contains(DAMAGE_WARSPAWN_JUGGERNAUT)
                        && button.getUniqueId() != event.getButton().getUniqueId());
        if (hasAnotherWarspawnButton) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        } else {
            ButtonHelper.deleteMessage(event);
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " damaged a Warspawn Juggernaut III (Kryxos mech) and placed "
                        + hits
                        + " infantry with it on " + Helper.getUnitHolderRepresentation(tile, location, game, player)
                        + ".");
    }

    @ButtonHandler(DECLINE_WARSPAWN_JUGGERNAUT)
    public static void declineWarspawnJuggernautDamage(ButtonInteractionEvent event, Player player) {
        if (event == null) {
            return;
        }
        if (player == null || !player.ownsUnit("kryxos_mech3")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FLIP_UNIT)
    @SuppressFBWarnings(
            value = "LSC_LITERAL_STRING_COMPARISON",
            justification =
                    "The detector incorrectly reports the compiler-generated comparisons for this string switch.")
    public static void resolveFlipUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }

        String payload = buttonID.substring(FLIP_UNIT.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String triggeringTech = parts[0];
        String currentTech;
        String flippedTech;
        String currentUnit;
        String flippedUnit;
        switch (parts[1]) {
            case "flagship" -> {
                currentTech = KRYXOS_FLAGSHIP_TECH;
                flippedTech = KRYXOS_FLAGSHIP_III_TECH;
                currentUnit = "kryxos_flagship2";
                flippedUnit = "kryxos_flagship3";
            }
            case "mech" -> {
                currentTech = KRYXOS_MECH_TECH;
                flippedTech = KRYXOS_MECH_III_TECH;
                currentUnit = "kryxos_mech2";
                flippedUnit = "kryxos_mech3";
            }
            default -> {
                ButtonHelper.deleteMessage(event);
                return;
            }
        }
        if (!player.hasTech(triggeringTech)
                || !player.hasTech(currentTech)
                || player.hasTech(flippedTech)
                || !player.ownsUnit(currentUnit)
                || player.ownsUnit(flippedUnit)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        TechnologyModel triggeringModel = Mapper.getTech(triggeringTech);
        if (!triggeringTech.equals(currentTech)) {
            player.removeTech(triggeringTech);
        }
        player.removeTech(currentTech);
        player.addTech(flippedTech);
        ButtonHelper.deleteMessage(event);

        TechnologyModel currentModel = Mapper.getTech(currentTech);
        TechnologyModel flippedModel = Mapper.getTech(flippedTech);
        String triggeringName = triggeringModel == null ? triggeringTech : triggeringModel.getName();
        String currentName = currentModel == null ? currentTech : currentModel.getName();
        String flippedName = flippedModel == null ? flippedTech : flippedModel.getName();

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " returned _" + triggeringName + "_ and replaced _" + currentName
                        + "_ with _" + flippedName + "_.");
    }
}
