package ti4.buttons;

import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class Buttons {
    public static final Button GET_A_TECH = Button.success("acquireATech", "Get a Tech");
    public static final Button GET_A_FREE_TECH = Button.success("acquireAFreeTech", "Get a Tech");
    public static final Button REDISTRIBUTE_CCs = Button.success("redistributeCCButtons", "Redistribute CCs");


    public static Button blue(String buttonID, String buttonLabel) {
        return Button.primary(buttonID, buttonLabel);
    }

    public static Button gray(String buttonID, String buttonLabel) {
        return Button.secondary(buttonID, buttonLabel);
    }

    public static Button green(String buttonID, String buttonLabel) {
        return Button.success(buttonID, buttonLabel);
    }

    public static Button red(String buttonID, String buttonLabel) {
        return Button.danger(buttonID, buttonLabel);
    }
}
