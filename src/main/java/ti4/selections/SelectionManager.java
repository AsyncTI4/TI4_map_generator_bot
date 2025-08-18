package ti4.selections;

import java.util.ArrayList;
import java.util.List;
import ti4.selections.selectmenus.BigSelectDemo;
import ti4.selections.selectmenus.SelectFaction;

public class SelectionManager {

    private final List<Selection> selectionMenuList = new ArrayList<>();
    private static SelectionManager manager;

    private SelectionManager() {}

    public static SelectionManager getInstance() {
        if (manager == null) {
            manager = new SelectionManager();
        }
        return manager;
    }

    public List<Selection> getSelectionMenuList() {
        return selectionMenuList;
    }

    private void addSelectionMenu(Selection selectionMenu) {
        selectionMenuList.add(selectionMenu);
    }

    public static void init() {
        SelectionManager selectionManager = getInstance();
        selectionManager.addSelectionMenu(new SelectFaction());
        selectionManager.addSelectionMenu(new BigSelectDemo());
    }
}
