package ti4.service.fow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

/*
 * Flow of Blind Selection:
 * 1) filterForBlindPositionSelection() / PlanetTargetService.targetButtons()
 *   - reduce the candidate buttons to those the player could legitimately know about
 *   - Blind Selection button carries the original button prefix
 * 2) offerBlindSelection()
 *   - spawn a Modal to enter the target
 * 3) doBlindSelection()
 *   - parse the modal input; an unparseable typo leaves the original message intact so it can be retried
 *  4) doBlindValidation()
 *   - build the real action button with the original prefix
 *
 * There is deliberately no "is this a legal target" check here. Rejecting a typed target would tell the
 * player whether it was legal, which is a yes/no oracle over hidden state — exactly what blind targeting is
 * meant to avoid. Legality is enforced when the action resolves, and an illegal target simply comes to
 * nothing (see PlanetTargetService#fizzleMessage).
 */
@UtilityClass
public class BlindSelectionService {
    public static final String TBD_FACTION = "TBDF";
    private static final String TARGET = "target";

    private static final String PLANET = "P";
    private static final String POSITION = "T";
    /**
     * A specific unit holder inside a system, written {@code <position>_<space|planetId>}. A system can hold
     * a space dock in space <em>and</em> one on each of its planets, so naming only the system is ambiguous.
     */
    private static final String UNIT_HOLDER = "U";

    public void filterForBlindPositionSelection(Game game, Player player, List<Button> buttons, String buttonPrefix) {
        if (!game.isFowMode()) return;
        filterForBlindSelection(game, player, buttons, buttonPrefix, POSITION);
    }

    public void filterForBlindPlanetSelection(Game game, Player player, List<Button> buttons, String buttonPrefix) {
        if (!game.isFowMode()) return;
        filterForBlindSelection(game, player, buttons, buttonPrefix, PLANET);
    }

    private static void filterForBlindSelection(
            Game game, Player player, List<Button> buttons, String buttonPrefix, String type) {

        Set<String> visibleTilePositions = FoWHelper.getTilePositionsToShow(game, player);

        for (Button button : new ArrayList<>(buttons)) {
            String target = StringUtils.substringAfterLast(button.getCustomId(), "_");

            boolean keep;
            if (POSITION.equals(type)) {
                // Position selection stays on *current* visibility, deliberately narrower than the planet
                // rule. Every position candidate list is built from hidden predicates ("no rival ships",
                // "has fighters", "empty tile"), so showing a remembered tile as a live button would assert
                // a hidden fact about it. Memory is still reachable through Blind Target.
                keep = visibleTilePositions.contains(target);
            } else {
                keep = FoWHelper.knowsPlanetExists(game, player, target);
            }

            if (!keep) {
                buttons.remove(button);
            }
        }

        appendBlindTargetButton(buttons, buttonPrefix, PLANET.equals(type));
    }

    /** Adds the red "Blind Target" button that opens the type-a-name modal. */
    public static void appendBlindTargetButton(List<Button> buttons, String buttonPrefix, boolean planetType) {
        appendBlindTargetButton(buttons, buttonPrefix, planetType ? PLANET : POSITION);
    }

    /**
     * Blind target for a specific unit holder inside a system. Typing a system position selects the holder in
     * space; typing a planet name selects the holder on that planet.
     */
    public static void appendBlindUnitHolderTargetButton(List<Button> buttons, String buttonPrefix) {
        appendBlindTargetButton(buttons, buttonPrefix, UNIT_HOLDER);
    }

    private static void appendBlindTargetButton(List<Button> buttons, String buttonPrefix, String type) {
        buttons.add(Buttons.red("blindSelection~MDL_" + encodePrefix(buttonPrefix) + "_" + type, "Blind Target"));
    }

    /**
     * Resolves the faction segment of a target button id into a player.
     *
     * @return the controlling player, or {@code null} when the planet is uncontrolled or the segment names
     *         no known player. Callers must treat null as "this came to nothing" rather than dereferencing it.
     */
    public static Player ownerOf(Game game, String factionSegment, String planetId) {
        if (TBD_FACTION.equals(factionSegment)) {
            return game.getPlayerThatControlsPlanet(planetId, true);
        }
        return game.getPlayerFromColorOrFaction(factionSegment);
    }

    // Base64url can emit '_', which would break the split("_") parsing of every id below. '.' is never in the
    // base64url alphabet, is legal in Discord custom ids, and appears in no tile position or resolved planet
    // id, so it is a safe stand-in. Keep encode/decode symmetric.
    private static String encodePrefix(String buttonPrefix) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buttonPrefix.getBytes(StandardCharsets.UTF_8))
                .replace('_', '.');
    }

    private static String decodePrefix(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded.replace('.', '_')), StandardCharsets.UTF_8);
    }

    @ButtonHandler(value = "blindSelection~MDL", save = false)
    public static void offerBlindPositionSelection(ButtonInteractionEvent event, String buttonID) {
        String[] splitButton = buttonID.replace("blindSelection~MDL_", "").split("_");
        String encodedButtonPrefix = splitButton[0];
        String type = splitButton[1];

        // Say what the field wants. The old prompt was just "Target", so on a position-type selection sitting
        // under a list of planet names the natural thing to type was a planet name, and it simply failed.
        String fieldLabel;
        String placeholder;
        if (PLANET.equals(type)) {
            fieldLabel = "Planet name";
            placeholder = "e.g. Mellon";
        } else if (UNIT_HOLDER.equals(type)) {
            fieldLabel = "Planet name, or a system position for the one in space";
            placeholder = "e.g. Mellon, or 305 for space";
        } else {
            fieldLabel = "System position, or a planet in it";
            placeholder = "e.g. 305, or Mellon";
        }

        TextInput target = TextInput.create(TARGET, TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder(placeholder)
                .build();

        Modal blindSelectionModal = Modal.create(
                        "blindSelection_" + event.getMessageId() + "_" + encodedButtonPrefix + "_" + type,
                        "Blind Target")
                .addComponents(Label.of(fieldLabel, target))
                .build();

        event.replyModal(blindSelectionModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    /**
     * Turns what the player typed into a target the action's button id can carry, or {@code null} when it
     * cannot be read at all.
     *
     * <p>A position prompt accepts a planet name as well as a position, because a planet names its system
     * unambiguously and these prompts often sit under a list of planet labels — typing the planet you can see
     * on the card is the natural move.
     *
     * <p>Parsing is done against the <b>static</b> planet list, never against the map. Rejecting a planet for
     * not being in this game would answer "does that planet exist here?", which is precisely the yes/no
     * oracle blind targeting exists to avoid. A real planet that is not on this map is passed through as a
     * position that will not resolve, so the action fizzles like any other unreachable target.
     *
     * <p>Input is matched against each planet's alias list, which is generous - short forms, common
     * misspellings and the {@code <tile>_<index>} form all resolve. Spaces are stripped first, which is what
     * makes the multi-word planet names work.
     *
     * <p>A target button is therefore only ever produced for a planet that exists in the static database. It
     * is <b>not</b> checked against this game's map, deliberately: "that planet is not in play" is a fact
     * about the board, and answering it here would turn the modal into a lookup for exactly the information
     * blind targeting exists to withhold.
     *
     * @return the resolved target, or null if the input is not a position or a known planet name.
     */
    static String parseBlindTarget(Game game, String type, String raw) {
        if (UNIT_HOLDER.equals(type)) {
            // A system can hold a dock in space and one on each planet, so the target has to say which.
            // A position means "the one in space"; a planet name means "the one on that planet".
            if (PositionMapper.isTilePositionValid(raw)) return raw + "_" + Constants.SPACE;
            String planetID = AliasHandler.resolvePlanet(raw);
            if (!Mapper.isValidPlanet(planetID)) return null;
            Tile tile = game.getTileFromPlanet(planetID);
            // Off this map: the position segment cannot resolve, so the action fizzles at resolution rather
            // than being rejected here, which would reveal that the planet is not in play.
            return (tile == null ? planetID : tile.getPosition()) + "_" + planetID;
        }
        if (POSITION.equals(type)) {
            if (PositionMapper.isTilePositionValid(raw)) return raw;
            String planetID = AliasHandler.resolvePlanet(raw);
            if (!Mapper.isValidPlanet(planetID)) return null;
            Tile tile = game.getTileFromPlanet(planetID);
            return tile == null ? planetID : tile.getPosition();
        }
        String planetID = AliasHandler.resolvePlanet(raw);
        return Mapper.isValidPlanet(planetID) ? planetID : null;
    }

    /** Readable text for a parsed target, including the {@code <position>_<holder>} composite form. */
    private static String describeTarget(String target) {
        String holder = StringUtils.substringAfter(target, "_");
        if (!holder.isEmpty()) {
            String position = StringUtils.substringBefore(target, "_");
            return Constants.SPACE.equals(holder) ? "Space in " + position : planetName(holder) + " (" + position + ")";
        }
        return Mapper.isValidPlanet(target) ? planetName(target) : target;
    }

    private static String planetName(String planetId) {
        String name = Mapper.getPlanetRepresentations().get(planetId);
        return name == null ? planetId : name;
    }

    @ModalHandler(value = "blindSelection_", save = false)
    public static void doBlindSelection(ModalInteractionEvent event, Player player, Game game) {
        String[] buttonData = event.getModalId().split("_");
        String origMessageId = buttonData[1];
        String encodedButtonPrefix = buttonData[2];
        String type = buttonData[3];
        String target = event.getValue(TARGET).getAsString().replace(" ", "").trim();

        String parsed = parseBlindTarget(game, type, target);
        boolean invalidTarget = parsed == null;
        if (!invalidTarget) {
            target = parsed;
        }

        if (invalidTarget) {
            // Return WITHOUT deleting the original message, so the Blind Target button survives and an
            // unparseable typo can simply be retried. This is a parse failure, not a targeting outcome, so
            // it tells the player nothing about the game state.
            String hint;
            if (PLANET.equals(type)) {
                hint = " Enter a planet name, such as `Mellon`.";
            } else if (UNIT_HOLDER.equals(type)) {
                hint = " Enter a planet name for the one on that planet, or a system position such as `305`"
                        + " for the one in space.";
            } else {
                hint = " Enter a system position such as `305`, or the name of a planet in that system.";
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Could not read `" + target + "`." + hint
                            + " Short forms and common misspellings work (`ap` and `alio` both find Alio Prima)."
                            + " To look a name up, start any slash command with a `planet` option and type into"
                            + " it — the autocomplete lists every planet in the database."
                            + " Press Blind Target again to retry.");
            return;
        }

        String buttonLabel = describeTarget(target);
        List<Button> chooseTargetButtons = new ArrayList<>();
        chooseTargetButtons.add(
                Buttons.blue("blindValidation_" + encodedButtonPrefix + "_" + type + "_" + target, buttonLabel));
        chooseTargetButtons.add(
                Buttons.red("blindSelection~MDL_" + encodedButtonPrefix + "_" + type, "Change Selection"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the target.",
                chooseTargetButtons);

        event.getMessageChannel().deleteMessageById(origMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = "blindValidation_", save = false)
    public static void doBlindValidation(ButtonInteractionEvent event, String buttonID, Game game) {
        // Limit of 4: a UNIT_HOLDER target is itself "<position>_<holder>", so the target is everything
        // after the third underscore, not just the next segment.
        String[] parts = buttonID.split("_", 4);
        String originalButtonPrefix = decodePrefix(parts[1]);
        String type = parts[2];
        String target = parts[3];

        originalButtonPrefix = insertFactionToButtonId(target, type, originalButtonPrefix, game);
        Button actionButton = Buttons.green(
                originalButtonPrefix + "_" + target, event.getButton().getLabel());

        MessageHelper.sendMessageToChannelWithButton(event.getChannel(), "Targeting **" + target + "**.", actionButton);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    // If the original button id contains TBDF, replace it with the faction of the owner of the target.
    // Positions are deliberately not resolved here: the only position caller is Micrometeoroid Storm, and
    // "first real player with ships in the system" is not its victim - it can even be the acting player.
    // That handler resolves its own victim, so TBDF is left in place for it.
    private static String insertFactionToButtonId(String target, String type, String originalButtonPrefix, Game game) {
        // Only a plain planet target names an owner. Position and unit-holder targets are resolved by their
        // own handlers, which know what "owner" means for them.
        if (!originalButtonPrefix.contains(TBD_FACTION) || !PLANET.equals(type)) return originalButtonPrefix;

        Player owner = game.getPlayerThatControlsPlanet(target, true);
        if (owner != null) {
            return originalButtonPrefix.replace(TBD_FACTION, owner.getFaction());
        }
        return originalButtonPrefix;
    }
}
