package ti4.commands.map;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.MapStringMapper;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.explore.AddFrontierTokensService;

public class AddTileList extends MapSubcommandData {
    public AddTileList() {
        super(Constants.ADD_TILE_LIST, "Add tile list (map string) to generate map");
        addOption(OptionType.STRING, Constants.TILE_LIST, "Tile list (map string) in TTPG/TTS format", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getInteraction().getMember();
        if (member == null) {
            MessageHelper.replyToMessage(event, "Caller ID not found");
            return;
        }

        String userID = member.getId();
        Game game = GameManager.getUserActiveGame(userID);
        if (!GameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }

        String tileList = event.getOption(Constants.TILE_LIST, "", OptionMapping::getAsString);

        addTileListToMap(game, tileList, event);

        GameSaveLoadManager.saveGame(game, event);
    }

    public static void addTileListToMap(Game game, String tileList, GenericInteractionCreateEvent event) {
        tileList = tileList.replace(",", " ");
        tileList = tileList.replace("  ", " ");

        
        Map<String, String> mappedTilesToPosition = MapStringMapper.getMappedTilesToPosition(tileList, game);
        if (mappedTilesToPosition.isEmpty()) {
            MessageHelper.replyToMessage(event, "Could not map all tiles to map positions");
            return;
        }

        List<String> badTiles = new ArrayList<>();
        game.clearTileMap();
        try {
            badTiles = addTileMapToGame(game, mappedTilesToPosition);
        } catch (Exception e) {
            BotLogger.log(e.getMessage(), e);
            MessageHelper.replyToMessage(event, e.getMessage());
        }

        MessageHelper.sendMessageToEventChannel(event, "Setting Map String to: ```\n" + tileList + "\n```");
        ShowGameService.simpleShowGame(game, event, DisplayType.map);

        if (!badTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There were some bad tiles that were replaced with red tiles: " + badTiles + "\n");
        }

        finishSetup(game, event);
    }

    public static List<String> addTileMapToGame(Game game, Map<String, String> tileMap) throws Exception {
        List<String> badTiles = new ArrayList<>();
        game.clearTileMap();
        for (Map.Entry<String, String> entry : tileMap.entrySet()) {
            String tileID = entry.getValue().toLowerCase();
            if ("-1".equals(tileID)) {
                continue;
            }
            if ("0".equals(tileID)) {
                tileID = "0g";
            }
            if (!TileHelper.isValidTile(tileID)) {
                badTiles.add(tileID);
                tileID = "0gray";
            }
            String tileName = Mapper.getTileID(tileID);
            String position = entry.getKey();
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                throw new Exception("Could not find tile: " + tileID);
            }
            Tile tile = new Tile(tileID, position);
            AddTile.addCustodianToken(tile);
            game.setTile(tile);
        }
        return badTiles;
    }

    public static void finishSetup(Game game, @Nullable GenericInteractionCreateEvent event) {
        try {
            Tile tile;
            tile = new Tile(AliasHandler.resolveTile(Constants.MALLICE), "TL");
            game.setTile(tile);
            if (game.getTileByPosition("000") == null) {
                tile = new Tile(AliasHandler.resolveTile(Constants.MR), "000");
                AddTile.addCustodianToken(tile);
                game.setTile(tile);
            }
        } catch (Exception e) {
            BotLogger.log("Could not add setup and Mallice tiles", e);
        }

        MessageChannel channel = event != null ? event.getMessageChannel() : game.getMainGameChannel();
        if (!game.isBaseGameMode()) {
            AddFrontierTokensService.addFrontierTokens(event, game);
            MessageHelper.sendMessageToChannel(channel, Emojis.Frontier + "Frontier Tokens have been added to empty spaces.");
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("deal2SOToAll", "Deal 2 SO To All"));
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Press this button after every player is setup", buttons);

        if (game.getRealPlayers().size() < game.getPlayers().size()) {
            ButtonHelper.offerPlayerSetupButtons(channel, game);
        }
    }

    @ButtonHandler("addMapString~MDL")
    public static void presentMapStringModal(ButtonInteractionEvent event, Game game) {
        String modalId = "addMapString";
        String fieldID = "mapString";
        TextInput tags = TextInput.create(fieldID, "Enter Map String", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Paste the map string here.")
            .setValue(game.getMapString())
            .setRequired(true)
            .build();
        Modal modal = Modal.create(modalId, "Add Map String for " + game.getName()).addActionRow(tags).build();
        event.replyModal(modal).queue();
    }

    @ModalHandler("addMapString")
    public static void getMapStringFromModal(ModalInteractionEvent event, Game game) {
        ModalMapping mapping = event.getValue("mapString");
        if (mapping == null) return;
        String mapStringRaw = mapping.getAsString();
        AddTileList.addTileListToMap(game, mapStringRaw, event);
    }
}
