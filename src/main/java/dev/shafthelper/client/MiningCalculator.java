package dev.shafthelper.client;

import dev.shafthelper.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Calculates and displays mining time for the block the player is looking at.
 * Based on PingOffsetMiner approach - uses Skyblock block hardness values and ping offset timing.
 */
public final class MiningCalculator implements HudElement {

    private static boolean initialized = false;
    
    // Skyblock block hardness values (from PingOffsetMiner)
    private static final java.util.Map<String, Integer> BLOCK_HARDNESSES = new java.util.HashMap<>();
    
    static {
        // Dwarven ores
        BLOCK_HARDNESSES.put("minecraft:obsidian", 500);
        BLOCK_HARDNESSES.put("minecraft:coal_block", 600);
        BLOCK_HARDNESSES.put("minecraft:iron_block", 600);
        BLOCK_HARDNESSES.put("minecraft:gold_block", 600);
        BLOCK_HARDNESSES.put("minecraft:lapis_block", 600);
        BLOCK_HARDNESSES.put("minecraft:redstone_block", 600);
        BLOCK_HARDNESSES.put("minecraft:emerald_block", 600);
        BLOCK_HARDNESSES.put("minecraft:diamond_block", 600);
        BLOCK_HARDNESSES.put("minecraft:quartz_block", 600);
        
        // Dwarven metals
        BLOCK_HARDNESSES.put("skyblock:gray_mithril", 500);
        BLOCK_HARDNESSES.put("skyblock:green_mithril", 800);
        BLOCK_HARDNESSES.put("skyblock:blue_mithril", 1500);
        BLOCK_HARDNESSES.put("skyblock:titanium", 2000);
        
        // Gemstones
        BLOCK_HARDNESSES.put("skyblock:ruby_gemstone", 2300);
        BLOCK_HARDNESSES.put("skyblock:amber_gemstone", 3000);
        BLOCK_HARDNESSES.put("skyblock:sapphire_gemstone", 3000);
        BLOCK_HARDNESSES.put("skyblock:jade_gemstone", 3000);
        BLOCK_HARDNESSES.put("skyblock:amethyst_gemstone", 3000);
        BLOCK_HARDNESSES.put("skyblock:opal_gemstone", 3000);
        BLOCK_HARDNESSES.put("skyblock:topaz_gemstone", 3800);
        BLOCK_HARDNESSES.put("skyblock:jasper_gemstone", 4800);
        BLOCK_HARDNESSES.put("skyblock:onyx_gemstone", 5200);
        BLOCK_HARDNESSES.put("skyblock:aquamarine_gemstone", 5200);
        BLOCK_HARDNESSES.put("skyblock:citrine_gemstone", 5200);
        BLOCK_HARDNESSES.put("skyblock:peridot_gemstone", 5200);
        BLOCK_HARDNESSES.put("skyblock:tungsten", 5600);
        BLOCK_HARDNESSES.put("skyblock:umber", 5600);
        BLOCK_HARDNESSES.put("skyblock:glacite", 6000);
    }
    
    // Track current block and timing state
    private static boolean wasTimeoutExceeded = false;
    private static BlockPos currentBlock;
    private static double ticksNeeded;
    private static boolean timeoutExceeded;
    private static int startServerTick;
    private static int ticksElapsed = 0;
    private static long mineStartWallMs = 0L;  
    private static double estimatedTicksAtStart = 0.0;

    public static void register() {
        if (!initialized) {
            ClientTickEvents.END_CLIENT_TICK.register(MiningCalculator::onTick);
            initialized = true;
        }
    }
    
    private static void onTick(Minecraft client) {  
        if (client.player == null || client.level == null) return;  
        Player player = client.player;  
    
        // Detect the block being looked at (runs regardless of HUD visibility)  
        BlockPos blockPos = null;  
        Integer blockHardness = null;  
        HitResult hit = player.pick(4.5, 0.0f, false);  
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {  
            blockPos = ((BlockHitResult) hit).getBlockPos();  
            Block block = client.level.getBlockState(blockPos).getBlock();  
            blockHardness = BLOCK_HARDNESSES.get(getSkyblockBlockName(block));  
        }  
    
        // Not actively mining a tracked block -> reset  
        if (!client.options.keyAttack.isDown() || blockHardness == null) {  
            ticksElapsed = 0;  
            startServerTick = player.tickCount;  
            timeoutExceeded = false;  
            currentBlock = blockPos;  
            return;  
        }  
    
        // New tracked block -> start timing and count it as expected  
        if (blockPos != null && !blockPos.equals(currentBlock)) {  
            currentBlock = blockPos;  
            startServerTick = player.tickCount;  
            mineStartWallMs = System.currentTimeMillis();  
            estimatedTicksAtStart = ticksNeeded; // may be 0 on first tick; refresh below
            ticksElapsed = 0;  
            timeoutExceeded = false;  
            EfficiencyDisplay.onBlockExpected();  
        }  
    
        ModConfig config = ShaftTracker.config();  
        double miningSpeed = config.miningSpeed > 0 ? config.miningSpeed : 50.0;    
        int professionalLevel = config.proffesionalLevel;  
        professionalLevel = Math.min(professionalLevel, 141); 
        if (config.goblinOmelette) professionalLevel += 1;   
        double addedGemstoneSpeed = 50 + (professionalLevel * 5); 
        double actualMiningSpeed = miningSpeed + addedGemstoneSpeed; 
        ticksNeeded = Math.round(blockHardness * 30 / actualMiningSpeed);
            estimatedTicksAtStart = ticksNeeded; // store the fresh estimate for calibration
    
        ticksElapsed = player.tickCount - startServerTick;  
        double pingOffset = computePingOffset(ticksNeeded);  
        timeoutExceeded = ticksNeeded > 0 && ticksElapsed >= pingOffset;  
    
        if (timeoutExceeded && !wasTimeoutExceeded && ShaftTracker.config().pingSoundAlert) {  
            client.getSoundManager().play(  
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(  
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, 2.0f));  
        }  
        wasTimeoutExceeded = timeoutExceeded;  
    }
    
    // Public getters for TickDisplay
    public static int getTicksElapsed() {
        return ticksElapsed;
    }
    
    public static double getTicksNeeded() {
        return ticksNeeded;
    }
    
    public static boolean isTimeoutExceeded() {
        return timeoutExceeded;
    }
    
    // Reset tick counter when block is mined
    public static void onBlockMined() {
        ticksElapsed = 0;
        startServerTick = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount : 0;
        timeoutExceeded = false;
    }

    public static BlockPos getCurrentBlock()  { return currentBlock; }
    public static long getMineStartWallMs()   { return mineStartWallMs; }  
    public static double getEstimatedTicks()   { return estimatedTicksAtStart; }

    /** Shared ping/TPS-adjusted offset used by both the sound alert and the display. */  
    private static double computePingOffset(double ticksNeeded) {  
        if (ticksNeeded <= 0) return ticksNeeded;  
        double ping = ServerStats.getPing();  
        if (ping <= 0) return ticksNeeded;  
        double pingTicks = ping / 50.0; // 50 ms per client tick; network latency is TPS-independent  
        return ticksNeeded - pingTicks;  
    }

    /** Effective ticks to break including network latency (notes' breakEfficiency formula). */  
    public static double computeEffectiveTicks(double ticks) {  
        double pingTicks = ServerStats.getPing() / ServerStats.getMsPerTick();  
        return ticks + pingTicks;  
    }
    
    /**  
     * Theoretical mining efficiency (%) as limited by ping:  
     * pure break time / (break time + ping). Lower ticksNeeded -> ping hurts more,  
     * matching the notes' "lower ticksToBreak means higher effect of ping on profit".  
     */  
    public static int getPingEfficiency() {  
        double eff = computeEffectiveTicks(ticksNeeded);  
        if (eff <= 0 || ticksNeeded <= 0) return 100;  
        return (int) Math.round(ticksNeeded / eff * 100.0);  
    }

    private static final int EDGE = 4;
    private static final int BOX_HEIGHT = 85;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.getDebugOverlay().showDebugScreen()) return;
        if (!ShaftTracker.config().enableDebugOverlay) return;
        
        Player player = client.player;
        if (player == null || client.level == null) return;

        // Get the block the player is looking at
        HitResult hitResult = player.pick(4.5, 0.0f, false);
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos blockPos = blockHitResult.getBlockPos();
        
        try {
            Block block = client.level.getBlockState(blockPos).getBlock();
            
            String skyblockBlockName = getSkyblockBlockName(block);
            Integer blockHardness = BLOCK_HARDNESSES.get(skyblockBlockName);
            
            if (blockHardness == null) {
                return; // Not a tracked block
            }

            // Reset timing state if block changed
            if (!blockPos.equals(currentBlock) || !client.options.keyAttack.isDown()) {
                timeoutExceeded = false;
                currentBlock = blockPos;
                startServerTick = player.tickCount;
                ticksElapsed = 0;
            }
            
            // Get mining speed from config
            ModConfig config = ShaftTracker.config();
            double MiningSpeed = config.miningSpeed > 0 ? config.miningSpeed : 50.0;
            int professionalLevel = config.proffesionalLevel;
            if(config.goblinOmelette) {
                professionalLevel += 1;
            }
            professionalLevel = Math.min(professionalLevel, 141);
            double addedGemstoneSpeed = 50 + (professionalLevel * 5);
            double actualMiningSpeed = MiningSpeed + addedGemstoneSpeed;
            
            // Calculate ticks needed using PingOffsetMiner formula
            ticksNeeded = Math.round(blockHardness * 30 / (actualMiningSpeed));
            
            // Display the information
            Font font = client.font;

            // Get ping from method
            int ping = (int) ServerStats.getPing();  
            double tps = ServerStats.getTps();  
            double pingOffset = computePingOffset(ticksNeeded);
            
            // Calculate width based on the text we'll display
            String displayName = skyblockBlockName.replace("skyblock:", "").replace("_gemstone", "");  
            int width = Math.max(  
                font.width(Component.literal(displayName)),  
                Math.max(  
                    font.width(Component.literal(String.format("Ticks: %.0f", ticksNeeded))),  
                    Math.max(  
                        font.width(Component.literal(String.format("Offset: %.1f", pingOffset))),  
                        Math.max(  
                            font.width(Component.literal(String.format("Ping: %dms", ping))),  
                            Math.max(  
                                font.width(Component.literal(String.format("TPS: %.1f", tps))),  
                                Math.max(  
                                    font.width(Component.literal(String.format("Speed: %.1f", actualMiningSpeed))),  
                                    font.width(Component.literal(String.format("Eff Ticks: %.1f", computeEffectiveTicks(ticksNeeded))))  
                                )  
                            )  
                        )  
                    )  
                )  
            ) + 10; // Add padding
            
            int height = BOX_HEIGHT; // Use fixed height instead of dynamic calculation
            int bg = config.themeBg;
            int border = config.themeBorder;
            int text = config.themeText;
            int accent = config.themeAccent;
            int scaledWidth = Math.max(1, (int) Math.round(width * config.calcScale));
            int scaledHeight = Math.max(1, (int) Math.round(height * config.calcScale));
            int x = position(config.calcX, graphics.guiWidth(), scaledWidth);
            int top = position(config.calcY, graphics.guiHeight(), scaledHeight);

            int l = x - 2, t = top - 2, r = x + scaledWidth + 2, b = top + scaledHeight + 2;  
            graphics.fill(l, t, r, b, bg);  
            graphics.fill(l, t, r, t + 1, border);  
            graphics.fill(l, b - 1, r, b, border);  
            graphics.fill(l, t, l + 1, b, border);  
            graphics.fill(r - 1, t, r, b, border);  
            int y = top;  
            int inset = Math.max(1, (int) Math.round(5 * config.calcScale));
            int lineGap = Math.max(1, (int) Math.round(font.lineHeight * config.calcScale));
            
            // Draw gemstone/block name
            int textY = y + inset;
            graphics.text(font, Component.literal(displayName), x + inset, textY, accent, true);
            
            // Draw mining ticks needed
            graphics.text(font, Component.literal(String.format("Ticks: %.0f", ticksNeeded)), x + inset, textY + lineGap, text, true);
            
            // Draw ping offset
            graphics.text(font, Component.literal(String.format("Offset: %.1f", pingOffset)), x + inset, textY + lineGap * 2, text, true);
            
            // Draw status
            String statusText = timeoutExceeded ? "MOVE NOW" : "MINING...";
            int statusColor = timeoutExceeded ? accent : 0xFFFFFF00;
            graphics.text(font, Component.literal(statusText), x + inset, textY + lineGap * 3, statusColor, true);
            
            // Draw ping info
            graphics.text(font, Component.literal(String.format("Ping: %dms", ping)), x + inset, textY + lineGap * 4, text, true);
            
            // Draw TPS info
            graphics.text(font, Component.literal(String.format("TPS: %.1f", tps)), x + inset, textY + lineGap * 5, text, true);
            
            // Draw mining speed
            graphics.text(font, Component.literal(String.format("Speed: %.1f", actualMiningSpeed)), x + inset, textY + lineGap * 6, text, true);
        } catch (Exception e) {
            // Silently fail if there's any error
        }
    }

    private static int position(double percent, int screen, int size) {  
        int available = Math.max(0, screen - size - 2 * EDGE);  
        return EDGE + (int) Math.round(available * Math.clamp(percent, 0.0, 100.0) / 100.0);  
    }

    private static String getSkyblockBlockName(Block block) {
        // Map vanilla blocks to Skyblock equivalents (from PingOffsetMiner)
        try {
            if (block.equals(Blocks.GRAY_WOOL) || block.equals(Blocks.CYAN_TERRACOTTA))
                return "skyblock:gray_mithril";
            if (block.equals(Blocks.PRISMARINE) || block.equals(Blocks.PRISMARINE_BRICKS) || block.equals(Blocks.DARK_PRISMARINE))
                return "skyblock:green_mithril";
            if (block.equals(Blocks.LIGHT_BLUE_WOOL))
                return "skyblock:blue_mithril";
            if (block.equals(Blocks.POLISHED_DIORITE))
                return "skyblock:titanium";
            if (block.equals(Blocks.RED_STAINED_GLASS) || block.equals(Blocks.RED_STAINED_GLASS_PANE))
                return "skyblock:ruby_gemstone";
            if (block.equals(Blocks.ORANGE_STAINED_GLASS) || block.equals(Blocks.ORANGE_STAINED_GLASS_PANE))
                return "skyblock:amber_gemstone";
            if (block.equals(Blocks.LIGHT_BLUE_STAINED_GLASS) || block.equals(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE))
                return "skyblock:sapphire_gemstone";
            if (block.equals(Blocks.LIME_STAINED_GLASS) || block.equals(Blocks.LIME_STAINED_GLASS_PANE))
                return "skyblock:jade_gemstone";
            if (block.equals(Blocks.PURPLE_STAINED_GLASS) || block.equals(Blocks.PURPLE_STAINED_GLASS_PANE))
                return "skyblock:amethyst_gemstone";
            if (block.equals(Blocks.WHITE_STAINED_GLASS) || block.equals(Blocks.WHITE_STAINED_GLASS_PANE))
                return "skyblock:opal_gemstone";
            if (block.equals(Blocks.YELLOW_STAINED_GLASS) || block.equals(Blocks.YELLOW_STAINED_GLASS_PANE))
                return "skyblock:topaz_gemstone";
            if (block.equals(Blocks.MAGENTA_STAINED_GLASS) || block.equals(Blocks.MAGENTA_STAINED_GLASS_PANE))
                return "skyblock:jasper_gemstone";
            if (block.equals(Blocks.BLACK_STAINED_GLASS) || block.equals(Blocks.BLACK_STAINED_GLASS_PANE))
                return "skyblock:onyx_gemstone";
            if (block.equals(Blocks.BLUE_STAINED_GLASS) || block.equals(Blocks.BLUE_STAINED_GLASS_PANE))
                return "skyblock:aquamarine_gemstone";
            if (block.equals(Blocks.BROWN_STAINED_GLASS) || block.equals(Blocks.BROWN_STAINED_GLASS_PANE))
                return "skyblock:citrine_gemstone";
            if (block.equals(Blocks.GREEN_STAINED_GLASS) || block.equals(Blocks.GREEN_STAINED_GLASS_PANE))
                return "skyblock:peridot_gemstone";
            if (block.equals(Blocks.CLAY))
                return "skyblock:tungsten";
            if (block.equals(Blocks.BROWN_TERRACOTTA) || block.equals(Blocks.TERRACOTTA) || block.equals(Blocks.SMOOTH_RED_SANDSTONE))
                return "skyblock:umber";
            if (block.equals(Blocks.PACKED_ICE))
                return "skyblock:glacite";
        } catch (Exception e) {
            // Block reference failed, continue to default
        }
        
        // Default to vanilla block name
        return "minecraft:" + block.getDescriptionId().replace("block.", "");
    }
}