package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class Ban extends GameStateSubcommand {

    public Ban() {
        super(Constants.BAN, "Ban Something From The Draft", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY, "Ability Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY_1, "Ability Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER_1, "Leader Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID, "Unit Name, only mechs or FS")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_1, "Unit Name, only mechs or FS")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Technology Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "Technology Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name").setAutoComplete(true));

        addOptions(new OptionData(OptionType.STRING, Constants.BAN_FLEET, "Starting Fleet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.BAN_COMMODITIES, "Commodities").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.BAN_HS, "Home System").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.BAN_STARTING_TECH, "Starting Technology")
                .setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> abilityIDs = new ArrayList<>();

        // GET ALL ABILITY OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.ABILITY))
                .toList()) {
            abilityIDs.add(option.getAsString());
        }

        abilityIDs.removeIf(StringUtils::isEmpty);
        abilityIDs.removeIf(a -> !Mapper.getAbilities().containsKey(a));

        Game game = getGame();
        for (String ability : abilityIDs) {
            game.setStoredValue("bannedAbilities", game.getStoredValue("bannedAbilities") + "finSep" + ability);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Successfully banned " + Mapper.getAbility(ability).getName());
        }

        List<String> leaderIDs = new ArrayList<>();

        // GET ALL Leader OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.LEADER))
                .toList()) {
            leaderIDs.add(option.getAsString());
        }

        leaderIDs.removeIf(StringUtils::isEmpty);
        leaderIDs.removeIf(leaderID -> !Mapper.isValidLeader(leaderID));

        for (String leader : leaderIDs) {
            if (Mapper.getLeader(leader) != null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Successfully banned " + Mapper.getLeader(leader).getName() + ".");
                game.setStoredValue("bannedLeaders", game.getStoredValue("bannedLeaders") + "finSep" + leader);
            }
        }

        List<String> pnIDs = new ArrayList<>();

        // GET ALL PN OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.PROMISSORY_NOTE_ID))
                .toList()) {
            pnIDs.add(option.getAsString());
        }

        pnIDs.removeIf(StringUtils::isEmpty);
        pnIDs.removeIf(pn -> !Mapper.getAllPromissoryNoteIDs().contains(pn));

        for (String pn : pnIDs) {
            if (Mapper.getPromissoryNote(pn) != null) {
                game.setStoredValue("bannedPNs", game.getStoredValue("bannedPNs") + "finSep" + pn);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Successfully banned " + Mapper.getPromissoryNote(pn).getName() + ".");
            }
        }

        List<String> unitIDs = new ArrayList<>();

        // GET ALL UNIT OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.UNIT_ID))
                .toList()) {
            unitIDs.add(option.getAsString());
        }

        unitIDs.removeIf(StringUtils::isEmpty);
        unitIDs.removeIf(unitID -> !Mapper.getUnits().containsKey(unitID));

        for (String unit : unitIDs) {
            String faction = unit.split("_")[0];
            String type = unit.split("_")[1];
            if ("mech".equalsIgnoreCase(type)) {
                game.setStoredValue("bannedMechs", game.getStoredValue("bannedMechs") + "finSep" + faction);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Successfully banned " + faction + " " + type + ".");
            }
            if ("flagship".equalsIgnoreCase(type)) {
                game.setStoredValue("bannedFSs", game.getStoredValue("bannedFSs") + "finSep" + faction);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Successfully banned " + faction + " " + type + ".");
            }
        }

        List<String> techIDs = new ArrayList<>();

        // GET ALL TECH OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.TECH))
                .toList()) {
            techIDs.add(option.getAsString());
        }

        techIDs.removeIf(StringUtils::isEmpty);
        techIDs.removeIf(id -> !Mapper.isValidTech(id));

        for (String tech : techIDs) {
            game.setStoredValue("bannedTechs", game.getStoredValue("bannedTechs") + "finSep" + tech);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Successfully banned " + Mapper.getTech(tech).getName() + ".");
        }

        List<String> tileIDs = new ArrayList<>();

        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.TILE_NAME))
                .toList()) {
            tileIDs.add(option.getAsString());
        }

        tileIDs.removeIf(StringUtils::isEmpty);
        for (String tileID : tileIDs) {
            Tile tile = new Tile(tileID, "000");
            if (tile.getTileModel() != null) {
                game.setStoredValue("bannedTiles", game.getStoredValue("bannedTiles") + "finSep" + tile.getTileID());
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Successfully banned " + tile.getTileModel().getName() + ".");
            }
        }

        List<String> commodityIDs = new ArrayList<>();
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.BAN_COMMODITIES))
                .toList()) {
            commodityIDs.add(option.getAsString());
        }

        commodityIDs.removeIf(StringUtils::isEmpty);
        commodityIDs.removeIf(a -> !Mapper.getFactionIDs().contains(a));

        for (String commodity : commodityIDs) {
            game.setStoredValue("bannedComms", game.getStoredValue("bannedComms") + "finSep" + commodity);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Successfully banned " + Mapper.getFaction(commodity).getFactionName() + " commodities.");
        }

        List<String> fleetIDs = new ArrayList<>();
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.BAN_FLEET))
                .toList()) {
            fleetIDs.add(option.getAsString());
        }

        fleetIDs.removeIf(StringUtils::isEmpty);
        fleetIDs.removeIf(a -> !Mapper.getFactionIDs().contains(a));

        for (String fleet : fleetIDs) {
            game.setStoredValue("bannedFleets", game.getStoredValue("bannedFleets") + "finSep" + fleet);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Successfully banned " + Mapper.getFaction(fleet).getFactionName() + " starting fleet.");
        }

        List<String> hsIDs = new ArrayList<>();
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.BAN_HS))
                .toList()) {
            hsIDs.add(option.getAsString());
        }

        hsIDs.removeIf(StringUtils::isEmpty);
        hsIDs.removeIf(a -> !Mapper.getFactionIDs().contains(a));

        for (String hs : hsIDs) {
            game.setStoredValue("bannedHSs", game.getStoredValue("bannedHSs") + "finSep" + hs);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Successfully banned " + Mapper.getFaction(hs).getFactionName() + " home system.");
        }

        List<String> startingTech = new ArrayList<>();
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.BAN_STARTING_TECH))
                .toList()) {
            startingTech.add(option.getAsString());
        }

        startingTech.removeIf(StringUtils::isEmpty);
        startingTech.removeIf(a -> !Mapper.getFactionIDs().contains(a));

        for (String fleet : startingTech) {
            game.setStoredValue("bannedStartingTechs", game.getStoredValue("bannedStartingTechs") + "finSep" + fleet);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Successfully banned " + Mapper.getFaction(fleet).getFactionName() + " starting technology.");
        }
    }
}
