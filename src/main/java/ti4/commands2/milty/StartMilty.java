package ti4.commands2.milty;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.milty.MiltyService;

class StartMilty extends GameStateSubcommand {

    public StartMilty() {
        super(Constants.QUICKSTART, "Start Milty Draft with default settings", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count (default = players + 1)"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FACTION_COUNT, "Faction Count (default = players + 1)").setRequiredRange(1, 25));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_DS_FACTIONS, "Include Discordant Stars Factions"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_DS_TILES, "Include Uncharted Space Tiles (ds)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        MiltyService.DraftSpec specs = new MiltyService.DraftSpec(game);

        // Map Template ---------------------------------------------------------------------------
        MapTemplateModel template = getMapTemplateFromOption(event, game);
        if (template == null) return; // we have already sent an error message
        specs.setTemplate(template);

        // Sources (defaults already accounted for) -----------------------------------------------
        OptionMapping includeDsTilesOption = event.getOption(Constants.INCLUDE_DS_TILES);
        if (includeDsTilesOption != null && includeDsTilesOption.getAsBoolean())
            specs.getTileSources().add(ComponentSource.ds);
        OptionMapping includeDsFactionsOption = event.getOption(Constants.INCLUDE_DS_FACTIONS);
        if (includeDsFactionsOption != null && includeDsFactionsOption.getAsBoolean())
            specs.getFactionSources().add(ComponentSource.ds);

        // Faction count & setup ------------------------------------------------------------------
        int factionCount = game.getPlayerCountForMap() + 1;
        OptionMapping factionOption = event.getOption(Constants.FACTION_COUNT);
        if (factionOption != null) {
            factionCount = factionOption.getAsInt();
        }
        if (factionCount > 25) factionCount = 25;
        specs.setNumFactions(factionCount);

        // Slice count ----------------------------------------------------------------------------
        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int presliceCount = game.getPlayerCountForMap() + 1;
        if (sliceOption != null) presliceCount = sliceOption.getAsInt();
        specs.setNumSlices(presliceCount);

        boolean anomaliesCanTouch = false;
        OptionMapping anomaliesCanTouchOption = event.getOption(Constants.ANOMALIES_CAN_TOUCH);
        if (anomaliesCanTouchOption != null) {
            anomaliesCanTouch = anomaliesCanTouchOption.getAsBoolean();
        }
        specs.setAnomaliesCanTouch(anomaliesCanTouch);

        // Players ---
        specs.setPlayerIDs(new ArrayList<>(game.getPlayerIDs()));
        MiltyService.startFromSpecs(event, specs);
    }

    private static MapTemplateModel getMapTemplateFromOption(SlashCommandInteractionEvent event, Game game) {
        int players = game.getPlayers().values().size();
        List<MapTemplateModel> allTemplates = Mapper.getMapTemplates();
        List<MapTemplateModel> validTemplates = Mapper.getMapTemplatesForPlayerCount(players);
        MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);

        if (validTemplates.isEmpty()) {
            String msg = "Milty draft in this bot does not know about any map layouts that support " + players + " player" + (players == 1 ? "" : "s") + " yet.";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            return null;
        }

        MapTemplateModel useTemplate = null;
        String templateName = null;
        OptionMapping templateOption = event.getOption(Constants.USE_MAP_TEMPLATE);
        if (templateOption != null) {
            templateName = templateOption.getAsString();
        }
        if (templateName != null) {
            for (MapTemplateModel model : allTemplates) {
                if (model.getAlias().equals(templateName)) {
                    useTemplate = model;
                }
            }
        } else {
            useTemplate = defaultTemplate;
        }

        if (useTemplate == null) {
            String msg = "There is not a default map layout defined for this player count. Specify map template in options.";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            return null;
        }
        return useTemplate;
    }
}
