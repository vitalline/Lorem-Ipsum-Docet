package sys.vitae.lorem;

import org.bukkit.plugin.java.JavaPlugin;

import sys.vitae.lorem.listeners.AnvilListener;
import sys.vitae.lorem.listeners.LoreCraftListener;
import sys.vitae.lorem.listeners.TextFieldListener;

public class LoremIpsumPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getServer().getPluginManager().registerEvents(new LoreCraftListener(this), this);
        getServer().getPluginManager().registerEvents(new TextFieldListener(this), this);
    }
}
