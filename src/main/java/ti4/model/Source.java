package ti4.model;

import ti4.helpers.Emojis;

public class Source {

    public enum ComponentSource {

        // official
        base, pok, codex1, codex2, codex3,

        //big homebrew
        ds, absol, franken,

        // lil homebrew
        lazax, action_deck_2, action_deck_2_old, keleresplus, little_omega,

        // async homebrew
        admins, pbd100, testsource,

        // personal projs
        ignis_aurora, asteroid, cryypter, oath_of_kings;

        public String toString() {
            return super.toString().toLowerCase();
        }

        public boolean isPok() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3 -> true;
                default -> false;
            };
        }

        public boolean isDs() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3, ds -> true;
                default -> false;
            };
        }

        public String emoji() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3 -> "";
                case absol -> Emojis.Absol;
                case ds -> Emojis.DiscordantStars;
                case admins -> Emojis.AdminsFaction;
                case ignis_aurora -> Emojis.IgnisAurora;
                case lazax -> Emojis.Lazax;
                default -> "";
            };
        }
    }

}
