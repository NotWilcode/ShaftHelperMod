package dev.shafthelper.client;  
  
import java.util.function.Function;  
  
import dev.shafthelper.core.CorpseLootParser;  
  
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;  
import net.minecraft.client.Minecraft;  
import net.minecraft.network.chat.Component;  
  
/**  
 * Pops the CS:GO-style reveal screen when a Vanguard corpse is opened.  
 * Drop stats are recorded by ShaftTracker.onGameMessage, so this class  
 * only handles presentation (no recordCorpseLoot call = no double count).  
 */  
public final class CorpseLootAnimationHandler {  
  
    private final Function<String, Double> priceLookup;  
  
    public CorpseLootAnimationHandler(Function<String, Double> priceLookup) {  
        this.priceLookup = priceLookup;  
    }  
  
    public void register() {  
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {  
            if (overlay) return; // ignore action bar messages  
            onChatMessage(message);  
        });  
    }  
  
    private void onChatMessage(Component message) {  
        if (!ShaftTracker.config().corpseOpeningAnimationEnabled) return;  
  
        CorpseLootParser.Loot loot = CorpseLootParser.parse(message);  
        if (loot == null) return;  
        if (loot.type() != CorpseLootParser.CorpseType.VANGUARD) return;  
        if (loot.rewards().isEmpty()) return;  
  
        Minecraft client = Minecraft.getInstance();  
        client.execute(() -> {  
            if (client.screen == null) {  
                client.setScreen(new CorpseOpeningScreen(loot.rewards(), priceLookup));  
            }  
        });  
    }  
}