package dev.shafthelper.client;  
  
import java.util.concurrent.ConcurrentLinkedQueue;  
  
/**  
 * Tracks server TPS (from server time packets) and hooks into Fabric  
 * network telemetry to capture true Hypixel gameplay round-trip ping.  
 */  
public class ServerStats {  
    private static final ConcurrentLinkedQueue<Long> pingIntervals = new ConcurrentLinkedQueue<>();  
    private static final int MAX_LATENCIES = 15;  
    private static final int DEFAULT_PING = 0;  
   
    private static volatile long latestPing = DEFAULT_PING;  
  
    private static long lastServerGameTime = -1L;  
    private static long lastServerTimeWallClock = -1L;  
    private static volatile double currentServerTps = 20.0;  
    private static long lastWorldSwitchTime = 0L;  
  
    private static boolean initialized = false;  
  
    public static void init() {  
        initialized = true;  
        // No client-tick registration needed anymore; TPS comes from server time packets.  
    }  
  
    /** Call this when the player switches worlds/servers to suppress bogus readings briefly. */  
    public static void onWorldSwitch() {  
        lastWorldSwitchTime = System.currentTimeMillis();  
        lastServerGameTime = -1L;  
        lastServerTimeWallClock = -1L;  
        currentServerTps = 20.0;  
    }  
  
    public static void addPing(long latency) {  
        latestPing = latency;  
        // Queue kept only if you later want an average display; not used by getPing().  
        pingIntervals.add(latency);  
        while (pingIntervals.size() > MAX_LATENCIES) {  
            pingIntervals.poll();  
        }  
    }  
  
    /** Exact latest measured round-trip ping (ms), not an average. */  
    public static long getPing() {  
        return latestPing;  
    }  
  
    /**  
     * Called from ConnectionMixin when a ClientboundSetTimePacket arrives.  
     * Computes instantaneous server TPS from THIS interval only (no averaging).  
     */  
    public static void onServerTimeUpdate(long gameTime) {  
        long now = System.currentTimeMillis();  
        if (lastServerGameTime != -1L && lastServerTimeWallClock != -1L) {  
            long ticksElapsed = gameTime - lastServerGameTime;  
            double secondsElapsed = (now - lastServerTimeWallClock) / 1000.0;  
            if (ticksElapsed > 0 && secondsElapsed > 0) {  
                double tps = ticksElapsed / secondsElapsed;
                currentServerTps = Math.max(0.0, Math.min(20.0, tps));  
            }  
        }  
        lastServerGameTime = gameTime;  
        lastServerTimeWallClock = now;  
    }  

    /**  
     * Refines ping using the estimated vs. observed block-break time (image notes 2).  
     * @param estimatedTicks the client-side estimated ticks-to-break (t)  
     * @param observedElapsedMs wall-clock ms from mine-start to the block-update packet  
     */  
    public static void calibrateFromBreak(double estimatedTicks, long observedElapsedMs) {  
        if (estimatedTicks <= 0 || observedElapsedMs <= 0) return;  
        double tps = getTps();  
        double tpsFactor = tps > 0 ? 20.0 / tps : 1.0;  
        // Expected server-side break duration in ms, adjusted for current TPS.  
        double expectedBreakMs = estimatedTicks * 50.0 * tpsFactor;  
        // Latency component observed on top of the actual break = round trip for the break event.  
        long measuredRtt = observedElapsedMs - Math.round(expectedBreakMs);  
        if (measuredRtt > 0 && measuredRtt < 2000) {  
            addPing(measuredRtt); // feed the specific, break-derived RTT  
        }  
    }
  
    /** Current server TPS. Returns 20.0 during the warm-up window after a world switch. */  
    public static double getTps() {  
        long now = System.currentTimeMillis();
        if (now - lastWorldSwitchTime < 5000) {  
            return 20.0;  
        }  
        return currentServerTps;  
    }  
}