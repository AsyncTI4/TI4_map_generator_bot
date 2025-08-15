package ti4.service.fow;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.SortHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class LoreService {

    private static final List<Button> LORE_BUTTONS = Arrays.asList(
            Buttons.blue("gmLoreEdit_System~MDL", "Add to System"),
            Buttons.blue("gmLoreEdit_Planet~MDL", "Add to Planet"),
            Buttons.gray("gmLoreRefresh", "Refresh"),
            Buttons.DONE_DELETE_BUTTONS);

    private static final String SYSTEM_LORE_KEY = "fowSystemLore";

    @ButtonHandler("gmLoreRefresh")
    private static void refreshLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        showLoreButtons(event, buttonID, game);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("gmLore")
    private static void showLoreButtons(ButtonInteractionEvent event, String buttonID, Game game) {
        String page = StringUtils.substringAfter(buttonID, "page");
        int pageNum = StringUtils.isBlank(page) ? 1 : Integer.parseInt(page);
        List<ActionRow> buttons = Buttons.paginateButtons(getLoreButtons(game), LORE_BUTTONS, pageNum, "gmLore");

        if (StringUtils.isBlank(page)) {
            String msg = """
                ### Lore Management\
                
                -# System Lore is shown to the first player to conclude an action with units in the system.\
                
                -# Planet Lore is shown to the first player to gain control of the planet.""";
            event.getChannel().sendMessage(msg).setComponents(buttons).queue();
        } else {
            event.getHook().editOriginalComponents(buttons).queue();
        }
    }

    private static List<Button> getLoreButtons(Game game) {
        List<Button> loreButtons = new ArrayList<>();
        for (String target : getSavedLore(game).keySet()) {
            String buttonLabel = "";
            String emoji = null;
            boolean isValidLore = true;

            if (PositionMapper.isTilePositionValid(target)) {
                // System Lore
                Tile tile = game.getTileByPosition(target);
                if (tile == null) isValidLore = false;

                buttonLabel = target;
                emoji = tile != null ? tile.getTileModel().getEmoji().toString() : null;
            } else {
                // Planet Lore
                PlanetModel planet = Mapper.getPlanet(target);
                if (!game.getPlanets().contains(target)) isValidLore = false;

                buttonLabel = planet.getName();
                emoji = planet.getEmoji().toString();
            }

            if (isValidLore) {
                loreButtons.add(Buttons.green("gmLoreEdit_" + target + "~MDL", buttonLabel, emoji));
            } else {
                loreButtons.add(Buttons.red("gmLoreEdit_" + target + "~MDL", buttonLabel, emoji));
            }
        }
        SortHelper.sortButtonsByTitle(loreButtons);
        return loreButtons;
    }

    private static Map<String, String[]> getSavedLore(Game game) {
        Map<String, String[]> savedLoreMap = new HashMap<>();
        String savedLoreString = game.getStoredValue(SYSTEM_LORE_KEY);
        if (StringUtils.isNotBlank(savedLoreString)) {
            for (String savedLore : savedLoreString.split("\\|")) {
                String[] splitLore = savedLore.split(";");
                if (splitLore.length == 2 || splitLore.length == 3) {
                    savedLoreMap.put(
                            splitLore[0], new String[] {splitLore[1], splitLore.length == 3 ? splitLore[2] : ""});
                } else {
                    BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Invalid lore string: " + savedLore);
                }
            }
        }
        return savedLoreMap;
    }

    @ButtonHandler("gmLoreEdit")
    public static void editLore(ButtonInteractionEvent event, String buttonID, Game game) {
        String target = StringUtils.substringBetween(buttonID, "gmLoreEdit_", "~MDL");
        boolean systemLore = "System".equals(target) || PositionMapper.isTilePositionValid(target);
        String addingTo = systemLore ? "System" : "Planet";

        TextInput.Builder position = TextInput.create(Constants.POSITION, addingTo, TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder(systemLore ? "000" : "Sem-Lore");
        TextInput.Builder lore = TextInput.create(Constants.MESSAGE, "Lore (clear to delete)", TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setPlaceholder("Once upon a time...")
                .setMaxLength(1000);
        TextInput.Builder footer = TextInput.create("footer", "Other info", TextInputStyle.SHORT)
                .setRequired(false)
                .setPlaceholder("Please use `/add_token token:gravityrift` on this system.");

        if (!"System".equals(target) && !"Planet".equals(target)) {
            position.setValue(target);
            String[] savedLore = getSavedLore(game).get(target);
            lore.setValue(savedLore[0]);
            if (StringUtils.isNotBlank(savedLore[1])) {
                footer.setValue(savedLore[1]);
            }
        }

        Modal editLoreModal = Modal.create("gmLoreSave" + addingTo, "Add Lore to " + addingTo)
                .addActionRow(position.build())
                .addActionRow(lore.build())
                .addActionRow(footer.build())
                .build();

        event.replyModal(editLoreModal).queue();
    }

    @ModalHandler("gmLoreSave")
    public static void saveLore(ModalInteractionEvent event, Player player, Game game) {
        String target = event.getValue(Constants.POSITION).getAsString();
        String loreText = event.getValue(Constants.MESSAGE).getAsString();
        String footerText = event.getValue("footer").getAsString();
        boolean systemLore = "System".equals(event.getModalId().replace("gmLoreSave", ""));
        PlanetModel planet = null;

        if (systemLore) {
            if (!PositionMapper.isTilePositionValid(target) || game.getTileByPosition(target) == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Position " + target + " is invalid to save lore `" + loreText + "`");
                return;
            }
        } else {
            planet = Mapper.getPlanet(AliasHandler.resolvePlanet(target));
            if (planet == null || !game.getPlanets().contains(planet.getID())) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Planet " + target + " is invalid to save lore `" + loreText + "`");
                return;
            }
            target = planet.getID();
        }

        Map<String, String[]> savedLoreMap = getSavedLore(game);
        if (StringUtils.isBlank(loreText)) {
            savedLoreMap.remove(target);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Removed Lore from " + (planet != null ? planet.getName() : target));
        } else {
            savedLoreMap.put(target, new String[] {
                loreText.replace(";", "").replace("|", ""),
                footerText.replace(";", "").replace("|", "")
            });
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Saved Lore to " + (planet != null ? planet.getName() : target));
        }

        setLore(game, savedLoreMap);
    }

    private static void setLore(Game game, Map<String, String[]> lore) {
        String loreString = lore.entrySet().stream()
                .map(entry -> entry.getKey() + ";" + entry.getValue()[0] + ";" + entry.getValue()[1])
                .collect(Collectors.joining("|"));
        game.setStoredValue(SYSTEM_LORE_KEY, loreString);
    }

    private static MessageEmbed buildLoreEmbed(Game game, String target, String[] lore, boolean isSystemLore) {
        Tile tile = isSystemLore ? game.getTileByPosition(target) : game.getTileFromPlanet(target);
        PlanetModel planet = isSystemLore ? null : Mapper.getPlanet(target);
        String titleTile = "";
        if (isSystemLore && tile != null && tile.getTileModel() != null) {
            titleTile = target + " - " + tile.getTileModel().getNameNullSafe() + " "
                    + tile.getTileModel().getEmoji();
        } else if (planet != null) {
            titleTile = planet.getName() + " " + planet.getEmoji();
        }

        Color embedColor = Color.black;
        if (tile != null && tile.getTileModel() != null) {
            switch (tile.getTileModel().getTileBack()) {
                case RED -> embedColor = Color.red;
                case BLUE -> embedColor = Color.blue;
                case GREEN -> embedColor = Color.green;
                default -> embedColor = Color.black;
            }
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("⭐ Lore of " + titleTile);
        eb.setDescription(lore[0]);
        eb.setFooter(lore[1]);
        eb.setColor(embedColor);
        return eb.build();
    }

    private static boolean hasLoreToShow(Game game, String target) {
        return game.isFowMode() && getSavedLore(game).containsKey(target);
    }

    public static void showSystemLore(Player player, Game game, String position) {
        if (!hasLoreToShow(game, position)) return;

        if (!FoWHelper.playerHasUnitsInSystem(player, game.getTileByPosition(position))) {
            return;
        }

        showLore(player, game, position, true);
    }

    public static void showPlanetLore(Player player, Game game, String planet) {
        if (!hasLoreToShow(game, planet)) return;

        showLore(player, game, planet, false);
    }

    private static void showLore(Player player, Game game, String target, boolean isSystemLore) {
        if (!player.isRealPlayer()) return;

        Map<String, String[]> lore = getSavedLore(game);
        if (lore.isEmpty() || !lore.containsKey(target)) {
            return;
        }

        MessageEmbed embed = buildLoreEmbed(game, target, lore.get(target), isSystemLore);
        MessageHelper.sendMessageToChannelWithEmbed(player.getPrivateChannel(), "You found a Lore Fragment", embed);

        GMService.logPlayerActivity(
                game, player, player.getRepresentationUnfoggedNoPing() + " was shown the lore of " + target);

        lore.remove(target);
        setLore(game, lore);
    }
}
