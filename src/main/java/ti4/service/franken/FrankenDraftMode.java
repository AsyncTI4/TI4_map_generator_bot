package ti4.service.franken;

public enum FrankenDraftMode {
      POWERED("powered", "Adds 1 extra faction technology/ability to pick from."),
      ONEPICK("onepick", "Draft 1 item a time."),
      POWEREDONEPICK("poweredonepick", "Combines powered and onepick modes.");

      private final String name;
      private final String description;

      FrankenDraftMode(String name, String description) {
          this.name = name;
          this.description = description;
      }

      @Override
      public String toString() {
          return super.toString().toLowerCase();
      }

      public static FrankenDraftMode fromString(String id) {
          for (FrankenDraftMode mode : values()) {
              if (id.equals(mode.toString())) {
                  return mode;
              }
          }
          return null;
      }

      public String getAutoCompleteName() {
          return name + ": " + description;
      }

      public boolean search(String searchString) {
          return name.toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString) || toString().contains(searchString);
      }
}