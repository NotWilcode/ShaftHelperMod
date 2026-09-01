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

    // Learned per-tick server compute time (t), the "50ms" that scales with TPS.  
    private static final double DEFAULT_MS_PER_TICK = 50.0;  
    private static final double MS_PER_TICK_ALPHA   = 0.2;   // EMA smoothing  
    private static volatile double msPerTick = DEFAULT_MS_PER_TICK;  
  
    /** Learned per-tick server compute time in ms (converges toward the real value). */  
    public static double getMsPerTick() {  
        return msPerTick;  
    }
  
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
     * Fine-tunes the per-tick server compute time (t) from a real block break.  
     *  
     * Model of the break round trip:  
     *   observedElapsedMs = ackPing (c + s, network both ways) + t * ticks * tpsFactor  
     * Solve for t:  
     *   t = (observedElapsedMs - ackPing) / (ticks * tpsFactor)  
     *  
     * @param estimatedTicks    client-side estimated ticks-to-break  
     * @param observedElapsedMs wall-clock ms from mine-start to the block-update packet  
     */  
    public static void calibrateFromBreak(double estimatedTicks, long observedElapsedMs) {  
        if (estimatedTicks <= 0 || observedElapsedMs <= 0) return;  
  
        long ackPing = getPing();            // ground-truth network RTT (c + s) from action acks  
        if (ackPing <= 0) return;            // no reliable network baseline yet  
  
        double tps = getTps();  
        double tpsFactor = tps > 0 ? 20.0 / tps : 1.0;  
  
        // Strip the network legs; what's left is the server-side compute for the break.  
        double serverComputeMs = observedElapsedMs - ackPing;  
        if (serverComputeMs <= 0) return;  
  
        double observedMsPerTick = serverComputeMs / (estimatedTicks * tpsFactor);  
  
        // Reject outliers (packet jitter, missed acks, world switches, etc.)  
        if (observedMsPerTick < 20.0 || observedMsPerTick > 150.0) return;  
  
        // Exponential moving average toward the observed value.  
        msPerTick += MS_PER_TICK_ALPHA * (observedMsPerTick - msPerTick);  
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