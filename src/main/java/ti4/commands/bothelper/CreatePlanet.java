package ti4.commands.bothelper;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.TileHelper;
import ti4.generator.UnitTokenPosition;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel;
import ti4.model.Source;
import ti4.model.TechSpecialtyModel;

public class CreatePlanet extends BothelperSubcommandData {
    public CreatePlanet() {
        super(Constants.CREATE_PLANET, "Permanently creates a new planet that can be used in future games.");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_ID, "The reference ID for the planet, generally the name in all lowercase without spaces").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_TILE_ID, "The tile this planet belongs to").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_NAME, "The planet's display name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_ALIASES, "A comma-separated list of any aliases you want to set for the planet.").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLANET_POSITION_X, "The x-coordinate of the planet's position in the tile image").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLANET_POSITION_Y, "The y-coordinate of the planet's position in the tile image").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLANET_RESOURCES, "The planet's resource value").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLANET_INFLUENCE, "The planet's influence value").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_TYPE, "Planet type - valid values are Hazardous, Industrial, Cultural, None.").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_TECH_SKIPS, "Comma-separated list of skips (Biotic, Cybernetic, Propulsion, Warfare, Unitskip, Nonunitskip)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_LEGENDARY_NAME, "If the planet has a legendary ability, this is its name. An ability must have both a name and text."));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_LEGENDARY_TEXT, "If the planet has a legendary ability, this is its text. An ability must have both a name and text."));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_FACTION_HOMEWORLD, "If this planet is in a faction's home system, put that faction's ID here"));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_SHORT_NAME, "A shortened name to display on the planet \"card\". MAX 10 CHARACTERS, INCLUDING SPACES"));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET_FLAVOUR_TEXT, "Flavour text for the planet - must include Discord Markdown"));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "The source of the planet - generally the ID of the expansion or module it comes from").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, "Creating planet " + event.getOption(Constants.PLANET_NAME).getAsString());
        String planetID = event.getOption(Constants.PLANET_ID).getAsString().toLowerCase();
        PlanetModel planet = null;
        boolean fail = false;
        try {
            planet = createPlanetModel(planetID,
                event.getOption(Constants.PLANET_TILE_ID).getAsString().toLowerCase(),
                event.getOption(Constants.PLANET_NAME).getAsString(),
                event.getOption(Constants.PLANET_ALIASES).getAsString(),
                event.getOption(Constants.PLANET_POSITION_X).getAsInt(),
                event.getOption(Constants.PLANET_POSITION_Y).getAsInt(),
                event.getOption(Constants.PLANET_RESOURCES).getAsInt(),
                event.getOption(Constants.PLANET_INFLUENCE).getAsInt(),
                event.getOption(Constants.PLANET_TYPE).getAsString(),
                event.getOption(Constants.PLANET_TECH_SKIPS, null, OptionMapping::getAsString),
                event.getOption(Constants.PLANET_LEGENDARY_NAME, null, OptionMapping::getAsString),
                event.getOption(Constants.PLANET_LEGENDARY_TEXT, null, OptionMapping::getAsString),
                event.getOption(Constants.PLANET_FACTION_HOMEWORLD, null, OptionMapping::getAsString),
                event.getOption(Constants.PLANET_SHORT_NAME, null, OptionMapping::getAsString),
                event.getOption(Constants.PLANET_FLAVOUR_TEXT, null, OptionMapping::getAsString),
                event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        } catch (Exception e) {
            fail = true;
            BotLogger.log("Something went wrong creating the planet: " + planetID, e);
        }
        if (fail || planet == null) {
            MessageHelper.sendMessageToEventChannel(event, "Something went wrong creating the planet: " + planetID);
            return;
        }
        try {
            exportPlanetModelToJson(planet);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something went wrong creating the planet: " + planetID);
            BotLogger.log("Something went wrong exporting the planet to json: " + planetID, e);
        }
        try {
            TileHelper.addNewPlanetToList(planet);
            AliasHandler.addNewPlanetAliases(planet);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something went wrong adding the planet to the active planets list: " + planetID);
            BotLogger.log("Something went wrong adding the planet to the active planets list: " + planetID, e);
        }

        String message = "Created new planet! Please check and make sure everything generated properly. This is the model:\n" +
            "```json\n" + TileHelper.getPlanetIdsToPlanetModels().get(event.getOption(Constants.PLANET_ID).getAsString()) + "\n```";
        MessageHelper.sendMessageToChannelWithEmbed(event.getChannel(), message, planet.getRepresentationEmbed(true));
    }

    private static PlanetModel createPlanetModel(String planetId, String planetTileId, String planetName, String planetAliases, int planetPosX, int planetPosY,
        int resources, int influence, String planetType, String skips, String legendaryName, String legendaryText, String factionHomeworld, String shortName,
        String flavourText, String source) {

        PlanetTypeModel typeModel = new PlanetTypeModel();
        PlanetModel planet = new PlanetModel();
        planet.setId(planetId.toLowerCase());
        planet.setTileId(planetTileId);
        planet.setName(planetName);
        planet.setAliases(getAliasListFromString(planetAliases));
        planet.setPositionInTile(new Point(planetPosX, planetPosY));
        planet.setResources(resources);
        planet.setInfluence(influence);
        planet.setPlanetType(typeModel.getPlanetTypeFromString(planetType));
        if (Optional.ofNullable(skips).isPresent())
            planet.setTechSpecialties(getTechSpecialtiesFromString(skips));
        if (Optional.ofNullable(factionHomeworld).isPresent())
            planet.setFactionHomeworld(factionHomeworld);
        if (Optional.ofNullable(shortName).isPresent())
            planet.setShortName(shortName);
        if (Optional.ofNullable(legendaryName).isPresent()) {
            planet.setLegendaryAbilityName(legendaryName);
            planet.setLegendaryAbilityText(legendaryText);
        }
        if (flavourText != null) planet.setFlavourText(flavourText);
        planet.setUnitPositions(createDefaultUnitTokenPosition(planet));
        if (source != null) planet.setSource(Source.ComponentSource.valueOf(source));

        return planet;
    }

    private static List<String> getAliasListFromString(String aliases) {
        return Stream.of(aliases.replace(" ", "").toLowerCase().split(",")).toList();
    }

    private static List<TechSpecialtyModel.TechSpecialty> getTechSpecialtiesFromString(String techString) {
        TechSpecialtyModel techModel = new TechSpecialtyModel();
        return Stream.of(techString.replace(" ", "").toLowerCase().split(",")).map(techModel::getTechSpecialtyFromString).toList();
    }

    private static UnitTokenPosition createDefaultUnitTokenPosition(PlanetModel planet) {
        int planetX = planet.getPositionInTile().x;
        int planetY = planet.getPositionInTile().y;
        UnitTokenPosition unitTokenPosition = new UnitTokenPosition(planet.getId());
        unitTokenPosition.addPosition("control", new Point(planetX - 39, planetY - 15));
        unitTokenPosition.addPosition("sd", new Point(planetX - 70, planetY + 20));

        unitTokenPosition.addPosition("pd", new Point(planetX - 33, planetY + 12));
        unitTokenPosition.addPosition("pd", new Point(planetX - 18, planetY + 12));
        unitTokenPosition.addPosition("pd", new Point(planetX, planetY + 12));
        unitTokenPosition.addPosition("pd", new Point(planetX - 22, planetY + 25));
        unitTokenPosition.addPosition("pd", new Point(planetX - 5, planetY + 25));

        unitTokenPosition.addPosition("mf", new Point(planetX - 32, planetY - 58));
        unitTokenPosition.addPosition("mf", new Point(planetX - 14, planetY - 58));
        unitTokenPosition.addPosition("mf", new Point(planetX - 37, planetY - 45));
        unitTokenPosition.addPosition("mf", new Point(planetX - 20, planetY - 45));
        unitTokenPosition.addPosition("mf", new Point(planetX, planetY - 45));

        unitTokenPosition.addPosition("tkn_gf", new Point(planetX - 19, planetY - 17));
        unitTokenPosition.addPosition("tkn_gf", new Point(planetX - 50, planetY - 17));

        unitTokenPosition.addPosition("att", new Point(planetX - 99, planetY - 11));
        unitTokenPosition.addPosition("att", new Point(planetX + 23, planetY - 55));
        unitTokenPosition.addPosition("att", new Point(planetX + 30, planetY - 25));

        return unitTokenPosition;
    }

    private static void exportPlanetModelToJson(PlanetModel planetModel) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(Storage.getResourcePath() + File.separator + "planets" + File.separator + planetModel.getId() + ".json"), planetModel);
            mapper.writeValue(new File(Storage.getStoragePath() + File.separator + "planets" + File.separator + planetModel.getId() + ".json"), planetModel);
        } catch (IOException e) {
            BotLogger.log("Something went wrong creating new planet JSON!", e);
        }
    }
}
