package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.button.ReactionService;

@UtilityClass
public class ListTechService {

    private static final Pattern Y = Pattern.compile("Y");
    private static final Pattern B = Pattern.compile("B");
    private static final Pattern R = Pattern.compile("R");
    private static final Pattern G = Pattern.compile("G");

    @ButtonHandler("acquireATechWithSC")
    public void acquireATechWithSC(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        acquireATechWithResources(event, game, player, true, buttonID.contains("first") || !buttonID.contains("_"));
    }

    @ButtonHandler("acquireATech")
    public void acquireATech(ButtonInteractionEvent event, Game game, Player player) {
        acquireATechWithResources(event, game, player, false, true);
    }

    private void acquireATechWithResources(
            ButtonInteractionEvent event, Game game, Player player, boolean sc, boolean first) {
        acquireATech(event, game, player, sc, TechnologyType.mainFive, first);
    }

    @ButtonHandler("acquireAUnitTechWithInf")
    public void acquireAUnitTechWithInf(ButtonInteractionEvent event, Game game, Player player) {
        boolean sc = false;
        boolean firstTime = true;
        acquireATech(event, game, player, sc, List.of(TechnologyType.UNITUPGRADE), firstTime);
    }

    private void acquireATech(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            boolean sc,
            List<TechnologyType> techTypes,
            boolean first) {
        game.setComponentAction(!sc);

        if (sc) {
            boolean used = ButtonHelperSCs.addUsedSCPlayer(event.getMessageId(), game, player);
            StrategyCardModel scModel =
                    game.getStrategyCardModelByName("technology").orElse(null);
            if (!used
                    && scModel != null
                    && scModel.usesAutomationForSCID("pok7technology")
                    && !player.getFollowedSCs().contains(scModel.getInitiative())) {
                int scNum = scModel.getInitiative();
                player.addFollowedSC(scNum, event);
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed **Technology**");
                }
                String message = ButtonHelperSCs.deductCC(game, player, scNum);
                ReactionService.addReaction(event, game, player, message);
            }

            if (first) {
                ButtonHelperCommanders.yinCommanderSummary(player, game);
                ButtonHelperCommanders.veldyrCommanderSummary(player, game);
                String getAllButtonSpoof = "getAllTechOfType_allTechResearchable";
                getAllTechOfType(event, player, getAllButtonSpoof, game, player.getCardsInfoThread());
                return;
            }
        }

        List<Button> buttons = new ArrayList<>();
        String techPrefix = player.finChecker() + "getAllTechOfType_";
        for (TechnologyType type : techTypes) {
            String id = techPrefix + type.toString();
            if (techTypes.size() == 1 && type == TechnologyType.UNITUPGRADE) {
                id += "_inf";
            }
            String label = "Get a " + type.readableName() + " Technology";
            switch (type) {
                case PROPULSION -> buttons.add(Buttons.blue(id, label, type.emoji()));
                case BIOTIC -> buttons.add(Buttons.green(id, label, type.emoji()));
                case CYBERNETIC -> buttons.add(Buttons.gray(id, label, type.emoji()));
                case WARFARE -> buttons.add(Buttons.red(id, label, type.emoji()));
                case UNITUPGRADE -> buttons.add(Buttons.gray(id, label, type.emoji()));
                default -> {}
            }
        }

        ButtonHelperCommanders.yinCommanderSummary(player, game);
        ButtonHelperCommanders.veldyrCommanderSummary(player, game);
        String message = player.getRepresentation() + ", what type of technology do you wish to get?";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    @ButtonHandler("getAllTechOfType_")
    public static void getAllTechOfType(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        getAllTechOfType(event, player, buttonID, game, event.getMessageChannel());
    }

    private static void getAllTechOfType(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel channel) {
        String techType = buttonID.replace("getAllTechOfType_", "");
        String payType = null;
        if (techType.contains("_")) {
            String[] split = techType.split("_");
            techType = split[0];
            payType = split[1];
        }
        if (payType == null) {
            payType = "normal";
        }

        List<Button> buttons = new ArrayList<>();
        if (techType.contains("allTechResearchable")) {
            for (TechnologyType type : TechnologyType.mainFive) {
                List<TechnologyModel> techs = getAllTechOfAType(game, type.toString(), player, true);
                buttons.addAll(getTechButtons(techs, player, payType));
            }
        } else {
            List<TechnologyModel> techs = getAllTechOfAType(game, techType, player);
            buttons.addAll(getTechButtons(techs, player, payType));
        }

        if (game.isComponentAction()) {
            buttons.add(Buttons.gray("acquireATech", "Get Other Technology"));
        } else {
            buttons.add(Buttons.gray("acquireATechWithSC_second", "Get Other Technology"));
        }

        String message = player.getRepresentation() + ", please choose which technology you wish to get.";

        if (!techType.contains("allTechResearchable")) {
            ButtonHelper.deleteMessage(event);
        } else {
            message +=
                    " The buttons shown correspond to technologies that the bot believes you meet the prerequisites for."
                            + " To get a technology that isn't shown, please use the \"Get Other Technology\" button.";
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, Player player) {
        return getTechButtons(techs, player, "normal");
    }

    private static boolean isTechResearchable(TechnologyModel tech, Player player) {
        Game game = player.getGame();
        String requirements = tech.getRequirements().orElse("");
        int wilds = 0;
        if (ButtonHelperCommanders.getVeldyrCommanderTechs(player, game, false).contains(tech.getAlias())) {
            wilds++;
        }
        if (player.getPurgedTechs().contains(tech.getAlias())) {
            return false;
        }
        if (ButtonHelperCommanders.getVeldyrCommanderTechs(player, game, true).contains(tech.getAlias())) {
            return true;
        }
        if (player.hasAbility("riftmeld") && tech.isUnitUpgrade()) {
            String unit = tech.getBaseUpgrade().orElse("").replace("2", "");
            for (UnitKey uk :
                    player.getNomboxTile().getSpaceUnitHolder().getUnits().keySet()) {
                if (player.getNomboxTile().getSpaceUnitHolder().getUnitCount(uk) <= 0) continue;
                if (unit.startsWith(uk.getUnitType().getValue())) return true;
            }
        }

        for (String planet : player.getPlanets()) {
            if (player.getExhaustedPlanets().contains(planet)
                    && !(player.hasTech("pa") || player.hasTech("absol_pa"))) {
                continue;
            }
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                Planet unitHolder = game.getPlanetsInfo().get(planet);
                Set<String> techTypes = unitHolder.getTechSpecialities();
                for (String type : techTypes) {
                    if (game.playerHasLeaderUnlockedOrAlliance(player, "zealotscommander")) {
                        wilds++;
                    } else {
                        if ("propulsion".equalsIgnoreCase(type)) {
                            requirements = B.matcher(requirements).replaceFirst("");
                            if (player.hasAbility("ancient_knowledge")) {
                                requirements = B.matcher(requirements).replaceFirst("");
                            }
                        }
                        if ("biotic".equalsIgnoreCase(type)) {
                            requirements = G.matcher(requirements).replaceFirst("");
                            if (player.hasAbility("ancient_knowledge")) {
                                requirements = G.matcher(requirements).replaceFirst("");
                            }
                        }
                        if ("warfare".equalsIgnoreCase(type)) {
                            requirements = R.matcher(requirements).replaceFirst("");
                            if (player.hasAbility("ancient_knowledge")) {
                                requirements = R.matcher(requirements).replaceFirst("");
                            }
                        }
                        if ("cybernetic".equalsIgnoreCase(type)) {
                            requirements = Y.matcher(requirements).replaceFirst("");
                            if (player.hasAbility("ancient_knowledge")) {
                                requirements = Y.matcher(requirements).replaceFirst("");
                            }
                        }
                    }
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            requirements = G.matcher(requirements).replaceFirst("");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kollecccommander")) {
            requirements = B.matcher(requirements).replaceFirst("");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "dihmohncommander")) {
            requirements = R.matcher(requirements).replaceFirst("");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "augerscommander")) {
            requirements = Y.matcher(requirements).replaceFirst("");
        }
        if (tech.getFirstType() == TechnologyType.UNITUPGRADE) {
            if (player.hasTechReady("aida") || player.hasTechReady("absol_aida")) {
                wilds++;
            }
        } else if (player.hasAbility("analytical")) {
            wilds++;
        }

        if (player.hasRelicReady("prophetstears") || player.hasRelicReady("absol_prophetstears")) {
            wilds++;
        }

        for (String techID : player.getTechs()) {
            TechnologyModel playerTech = Mapper.getTech(techID);
            if (playerTech == null) continue;
            for (TechnologyType type : playerTech.getTypes()) {
                switch (type) {
                    case BIOTIC -> requirements = G.matcher(requirements).replaceFirst("");
                    case WARFARE -> requirements = R.matcher(requirements).replaceFirst("");
                    case PROPULSION -> requirements = B.matcher(requirements).replaceFirst("");
                    case CYBERNETIC -> requirements = Y.matcher(requirements).replaceFirst("");
                    case UNITUPGRADE -> {
                        if (game.playerHasLeaderUnlockedOrAlliance(player, "kjalengardcommander")) {
                            wilds++;
                        }
                    }
                    default -> {}
                }
            }
        }
        if (ButtonHelper.isLawInPlay(game, "schematics")
                && ("ws".equalsIgnoreCase(tech.getAlias())
                        || "ws".equalsIgnoreCase(tech.getBaseUpgrade().orElse("beh")))) {
            for (Player p2 : game.getRealPlayers()) {
                for (String t2 : p2.getTechs()) {
                    TechnologyModel tech2 = Mapper.getTech(t2);
                    if ("ws".equalsIgnoreCase(tech2.getAlias())
                            || "ws".equalsIgnoreCase(tech2.getBaseUpgrade().orElse("beh"))) {
                        return true;
                    }
                }
            }
        }

        return requirements.length() <= wilds;
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, Player player, String buttonPrefixType) {
        List<Button> techButtons = new ArrayList<>();

        techs.sort(TechnologyModel.sortByTechRequirements);

        String idPrefix = player.finChecker()
                + switch (buttonPrefixType.toLowerCase()) {
                    case "normal", "res", "nekro", "nopay", "free", "inf" -> "getTech_";
                    default -> "swapTechs__" + buttonPrefixType + "__";
                };
        String idSuffix =
                switch (buttonPrefixType.toLowerCase()) {
                    case "nekro", "nopay" -> "__noPay";
                    case "free" -> "__noPay__comp";
                    case "inf" -> "__inf";
                    default -> "";
                };

        for (TechnologyModel tech : techs) {
            String techName = tech.getName();
            String techID = tech.getAlias();
            String buttonID = idPrefix + techID + idSuffix;
            String emoji = tech.getCondensedReqsEmojis(true);

            techButtons.add(
                    switch (tech.getFirstType()) {
                        case PROPULSION -> Buttons.blue(buttonID, techName, emoji);
                        case BIOTIC -> Buttons.green(buttonID, techName, emoji);
                        case WARFARE -> Buttons.red(buttonID, techName, emoji);
                        default -> Buttons.gray(buttonID, techName, emoji);
                    });
        }
        return techButtons;
    }

    public static List<TechnologyModel> getAllTechOfAType(Game game, String techType, Player player) {
        return getAllTechOfAType(game, techType, player, false);
    }

    private static List<TechnologyModel> getAllTechOfAType(
            Game game, String techType, Player player, boolean hasToBeResearchable) {
        List<TechnologyModel> validTechs = Mapper.getTechs().values().stream()
                .filter(tech -> !hasToBeResearchable || isTechResearchable(tech, player))
                .filter(tech -> game.getTechnologyDeck().contains(tech.getAlias()))
                .filter(tech -> tech.isType(techType)
                        || game.getStoredValue("colorChange" + tech.getAlias()).equalsIgnoreCase(techType))
                .filter(tech -> !player.getPurgedTechs().contains(tech.getAlias()))
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .filter(tech -> tech.getFaction().isEmpty()
                        || "".equalsIgnoreCase(tech.getFaction().get())
                        || player.getNotResearchedFactionTechs().contains(tech.getAlias()))
                .toList();

        List<TechnologyModel> techs2 = new ArrayList<>();
        for (TechnologyModel tech : validTechs) {
            boolean addTech = true;
            if (tech.isUnitUpgrade()) {
                List<String> researchedTechs = new ArrayList<>();
                researchedTechs.addAll(player.getTechs());
                researchedTechs.addAll(player.getNotResearchedFactionTechs());
                for (String factionTech : researchedTechs) {
                    TechnologyModel fTech = Mapper.getTech(factionTech);
                    if (fTech != null
                            && !fTech.getAlias().equalsIgnoreCase(tech.getAlias())
                            && fTech.isUnitUpgrade()
                            && fTech.getBaseUpgrade().orElse("bleh").equalsIgnoreCase(tech.getAlias())) {
                        addTech = false;
                    }
                }
            }
            if (addTech) {
                techs2.add(tech);
            }
        }
        return techs2;
    }

    public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Game game, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : getAllNonFactionUnitUpgradeTech(game)) {
            if (player.hasTech(tech.getAlias())) {
                techs.add(tech);
            }
        }
        return techs;
    }

    public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Game game) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            if (tech.isUnitUpgrade()
                    && tech.getFaction().isEmpty()
                    && game.getTechnologyDeck().contains(tech.getAlias())) {
                techs.add(tech);
            }
        }
        return techs;
    }
}
