package sys.vitae.lorem;

import org.bukkit.plugin.java.JavaPlugin;

import sys.vitae.lorem.listeners.AnvilListener;
import sys.vitae.lorem.listeners.NameTagListener;
import sys.vitae.lorem.listeners.TemplateCraftListener;
import sys.vitae.lorem.listeners.TextFieldListener;
import sys.vitae.lorem.utils.Templates;

public class LoremIpsumPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Templates templates = new Templates(this);
        getServer().getPluginManager().registerEvents(new AnvilListener(this, templates), this);
        getServer().getPluginManager().registerEvents(new TemplateCraftListener(this, templates), this);
        getServer().getPluginManager().registerEvents(new NameTagListener(templates), this);
        getServer().getPluginManager().registerEvents(new TextFieldListener(this), this);
    }
}
