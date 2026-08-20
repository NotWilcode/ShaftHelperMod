package dev.shafthelper.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Tracks server TPS and hooks into Fabric network telemetry 
 * to capture true Hypixel gameplay round-trip ping.
 */
public class ServerStats {
    private static final ConcurrentLinkedQueue<Long> pingIntervals = new ConcurrentLinkedQueue<>();
    private static final int MAX_LATENCIES = 15; 
    private static final int DEFAULT_PING = 0; 
    private static long lastWorldSwitchTime = 0L;
    private static final java.util.Deque<Double> tpsSamples = new java.util.ArrayDeque<>();  
    private static final int MAX_TPS_SAMPLES = 10;  
    
    private static long prevGameTime = Long.MIN_VALUE;  
    private static long prevNanos = 0L;  
    
    public static void onServerTimeUpdate(long gameTime, long nowNanos) {  
        if (prevGameTime != Long.MIN_VALUE) {  
            long tickDelta = gameTime - prevGameTime;  
            double realSecs = (nowNanos - prevNanos) / 1_000_000_000.0;  
            // ignore day-skips / time-set-backwards / zero deltas  
            if (tickDelta > 0 && tickDelta < 1000 && realSecs > 0) {  
                double tps = tickDelta / realSecs;  
                if (tps > 0 && tps <= 20.5) {  
                    synchronized (tpsSamples) {  
                        tpsSamples.addLast(Math.min(20.0, tps));  
                        while (tpsSamples.size() > MAX_TPS_SAMPLES) tpsSamples.pollFirst();  
                    }  
                }  
            }  
        }  
        prevGameTime = gameTime;  
        prevNanos = nowNanos;  
    }  
    
    public static double getTps() {  
        if (System.currentTimeMillis() - lastWorldSwitchTime < 5000) return 20.0;  
        synchronized (tpsSamples) {  
            if (tpsSamples.isEmpty()) return 20.0; // no data yet  
            double sum = 0;  
            for (Double v : tpsSamples) sum += v;  
            return Math.max(0.0, Math.min(20.0, sum / tpsSamples.size()));  
        }  
    }  
    
    public static void onWorldSwitch() {  
        lastWorldSwitchTime = System.currentTimeMillis();  
        synchronized (tpsSamples) { tpsSamples.clear(); }  
        prevGameTime = Long.MIN_VALUE;  
    }
    
    private static boolean initialized = false;
    public static void init() {
        if (initialized) return;
        initialized = true;
    }

    public static void addPing(long latency) {
        pingIntervals.add(latency);
        while (pingIntervals.size() > MAX_LATENCIES) {
            pingIntervals.poll();
        }
    }

    public static long getPing() {
        int count = 0;
        long sum = 0;
        for (Long latency : pingIntervals) {
            if (latency != null) {
                sum += latency;
                count++;
            }
        }
        return count == 0 ? DEFAULT_PING : (sum / count);
    }

    public static long getOneWayPing() {  
        double tps = getTps();  
        double halfTickMs = tps > 0 ? 500.0 / tps : 25.0; // 500.0 / tps is your half-tick term written correctly — it grows as TPS drops (25ms at 20 TPS, 50ms at 10 TPS), the reciprocal of the TPS/20.0 you originally wrote.
        return Math.round(Math.max(0, getPing() - halfTickMs) / 2.0);  
    }
}