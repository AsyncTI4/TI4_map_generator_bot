package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
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
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class ArcanumUnitHandler {
    private static final String RUNEBOUND = "arcanum_mech";
    private static final String DAMAGE_RUNEBOUND = "arcanumDamageRunebound_";

    public static int getReadyRuneboundCount(Game game, Player player) {
        if (game == null || player == null || !player.ownsUnit(RUNEBOUND)) {
            return 0;
        }
        UnitKey mechKey = Units.getUnitKey(UnitType.Mech, player.getColorID());
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .mapToInt(holder -> Math.max(0, holder.getUnitCount(mechKey) - holder.getDamagedUnitCount(mechKey)))
                .sum();
    }

    public static List<Button> getRuneboundPrerequisiteSkipButtons(
            Game game, Player player, String techID, String payType) {
        if (player == null
                || game == null
                || techID == null
                || !player.ownsUnit(RUNEBOUND)
                || Mapper.getTech(techID) == null
                || Mapper.getTech(techID).getRequirements().orElse("").isEmpty()) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        UnitKey mechKey = Units.getUnitKey(UnitType.Mech, player.getColorID());
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                int readyMechs = holder.getUnitCount(mechKey) - holder.getDamagedUnitCount(mechKey);
                if (readyMechs <= 0) {
                    continue;
                }
                for (int i = 1; i <= readyMechs; i++) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker()
                                    + DAMAGE_RUNEBOUND
                                    + tile.getPosition()
                                    + ";"
                                    + holder.getName()
                                    + ";"
                                    + techID
                                    + ";"
                                    + i
                                    + ";"
                                    + payType,
                            "Damage Rune-Bound Sentinel in "
                                    + (Constants.SPACE.equals(holder.getName())
                                            ? "space area of " + tile.getRepresentationForButtons(game, player)
                                            : Helper.getPlanetRepresentation(holder.getName(), game))
                                    + (readyMechs > 1 ? " (" + i + "/" + readyMechs + ")" : "")));
                }
            }
        }
        return buttons;
    }

    @ButtonHandler(DAMAGE_RUNEBOUND)
    public static void resolveRuneboundPrerequisiteSkip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.ownsUnit(RUNEBOUND)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] parts = buttonID.substring(DAMAGE_RUNEBOUND.length()).split(";", 5);
        if (parts.length < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        UnitKey mechKey = Units.getUnitKey(UnitType.Mech, player.getColorID());
        if (holder == null || holder.getUnitCount(mechKey) <= holder.getDamagedUnitCount(mechKey)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        holder.addDamagedUnit(mechKey, 1);
        player.addSpentThing("arcanumRunebound");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        String payType = parts.length == 5 ? parts[4] : "res";
        event.getMessage()
                .editMessage(Helper.buildSpentThingsMessage(player, game, payType))
                .queue();
    }

    // Flagship
    public static List<NamedCombatModifierModel> getAstralCodexExtraRollModifier(
            Player player, Tile tile, UnitHolder combatOnHolder, CombatRollType rollType) {
        if (player == null
                || tile == null
                || combatOnHolder == null
                || rollType != CombatRollType.combatround
                || !Constants.SPACE.equalsIgnoreCase(combatOnHolder.getName())
                || !ButtonHelper.doesPlayerHaveFSHere("arcanum_flagship", player, tile)) {
            return List.of();
        }

        TechnologyType selectedColor = TechnologyType.NONE;
        int extraDice = 0;
        for (TechnologyType color : TechnologyType.mainFour) {
            int techCount = ButtonHelper.getNumberOfCertainTypeOfTech(player, color);
            if (techCount > extraDice) {
                selectedColor = color;
                extraDice = techCount;
            }
        }

        if (extraDice == 0) {
            return List.of();
        }

        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("arcanum_astral_codex");
        modifier.setType(Constants.COMBAT_EXTRA_ROLLS);
        modifier.setValue(extraDice);
        modifier.setPersistenceType("ALWAYS");
        modifier.setScope("fs");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(CombatRollType.combatround);

        return List.of(new NamedCombatModifierModel(
                modifier,
                "_The Astral Codex_: +" + extraDice + " dice from owning " + extraDice + " "
                        + selectedColor.readableName() + " technology"));
    }
}
