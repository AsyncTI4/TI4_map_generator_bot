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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class ArcanumPromissoryHandler {
    private static final String SCROLL = "thpnarcanum";
    private static final String USE_SCROLL = "useScrollOfAscension";
    private static final String SCROLL_TECH = "scrollOfAscension";
    private static final String GAIN_SCROLL_TECH = "gainScrollOfAscensionTech_";

    public static Button getScrollOfAscensionButton(Player player, Game game) {
        return Buttons.green(
                player.factionButtonChecker() + USE_SCROLL, "Use Scroll of Ascension", FactionEmojis.arcanum);
    }

    public static void offerScrollOfAscension(Game game, Player player) {
        if (game == null
                || player == null
                || !"strategy".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.hasPlayablePromissoryInHand(SCROLL)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", you may play _Scroll of Ascension_ to research a technology.",
                getScrollOfAscensionButton(player, game));
    }

    @ButtonHandler(USE_SCROLL)
    public static void startScrollOfAscension(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null
                || game == null
                || player.ownsPromissoryNote(SCROLL)
                || !"strategy".equalsIgnoreCase(game.getPhaseOfGame())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player pnOwner = game.getPNOwner(SCROLL);
        if (pnOwner == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyType type : TechnologyType.mainFour) {
            techs.addAll(ListTechService.getAllTechOfAType(game, type.toString(), player, false, true).stream()
                    .filter(tech -> !tech.isUnitUpgrade()
                            && tech.getFaction().isEmpty()
                            && !player.hasTech(tech.getAlias())
                            && !pnOwner.hasTech(tech.getAlias()))
                    .toList());
        }
        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", there are no eligible technologies for _Scroll of Ascension_. It remains in your hand.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removePromissoryNote(SCROLL);
        pnOwner.setPromissoryNote(SCROLL);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentationNoPing() + " returned _Scroll of Ascension_ to "
                        + pnOwner.getRepresentationNoPing() + ".");

        List<Button> buttons = getScrollTechButtons(techs, player);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please select the tech you would like to research:",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerScrollOwnerTechGain(Game game, Player researcher, String techID) {
        if (game == null || researcher == null || !Mapper.isValidTech(techID)) {
            return;
        }
        Player pnOwner = game.getPNOwner(SCROLL);
        TechnologyModel tech = Mapper.getTech(techID);
        if (pnOwner == null
                || pnOwner == researcher
                || pnOwner.getStrategicCC() < 1
                || pnOwner.hasTech(techID)
                || tech.isUnitUpgrade()
                || tech.getFaction().isPresent()) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        pnOwner.factionButtonChecker() + GAIN_SCROLL_TECH + techID,
                        "Spend 1 Strategy Token to gain " + tech.getName()),
                Buttons.red(pnOwner.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                pnOwner.getCardsInfoThread(),
                pnOwner.getRepresentationUnfogged()
                        + ", "
                        + researcher.getRepresentationNoPing()
                        + " researched "
                        + tech.getRepresentation(false)
                        + " using _Scroll of Ascension_. Spend 1 command token from your strategy pool to gain it?",
                buttons);
    }

    @ButtonHandler(GAIN_SCROLL_TECH)
    public static void gainScrollTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techID = buttonID.substring(GAIN_SCROLL_TECH.length());
        Player pnOwner = game.getPNOwner(SCROLL);
        if (pnOwner != player || player.getStrategicCC() < 1 || !Mapper.isValidTech(techID) || player.hasTech(techID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "used _Scroll of Ascension_");
        PlayerTechService.addTech(event, game, player, techID);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentationUnfogged() + " spent 1 command token from their strategy pool to gain "
                        + Mapper.getTech(techID).getRepresentation(false) + " with _Scroll of Ascension_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getScrollTechButtons(List<TechnologyModel> techs, Player player) {
        List<Button> buttons = new ArrayList<>();
        techs.sort(TechnologyModel.sortByTechRequirements);
        for (TechnologyModel tech : techs) {
            String buttonID =
                    player.factionButtonChecker() + "getTech_" + tech.getAlias() + "__noPay__comp__" + SCROLL_TECH;
            String emoji = tech.getCondensedReqsEmojis(true);
            buttons.add(
                    switch (tech.getFirstType()) {
                        case PROPULSION -> Buttons.blue(buttonID, tech.getName(), emoji);
                        case BIOTIC -> Buttons.green(buttonID, tech.getName(), emoji);
                        case WARFARE -> Buttons.red(buttonID, tech.getName(), emoji);
                        default -> Buttons.gray(buttonID, tech.getName(), emoji);
                    });
        }
        return buttons;
    }
}
