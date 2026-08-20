package dev.shafthelper.mod; // Adjust based on your actual entrypoint location

import dev.shafthelper.client.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // This passes the active Mod Menu screen into your screen constructor
        return parent -> new ConfigScreen(); 
    }
}
