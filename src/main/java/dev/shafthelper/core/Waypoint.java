package dev.shafthelper.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a waypoint with location, grouping, and display information.
 */
public final class Waypoint {
    private String id;
    public String name;
    public int x;
    public int y;
    public int z;
    public String group;
    public String island;
    public boolean enabled;
    public int color;

    public Waypoint() {
        // Default constructor for Gson
        this.id = UUID.randomUUID().toString();
        this.enabled = false;
        this.color = 0xFF0000; // Default red
    }

    public Waypoint(String name, int x, int y, int z, String group, String island) {
        this();
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.group = group;
        this.island = island;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return name != null && !name.isEmpty() ? name : "Waypoint";
    }

    public String getCoordinates() {
        return "(" + x + ", " + y + ", " + z + ")";
    }

    public double distanceTo(int px, int py, int pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Waypoint waypoint = (Waypoint) o;
        return Objects.equals(id, waypoint.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Waypoint{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", group='" + group + '\'' +
                ", island='" + island + '\'' +
                ", enabled=" + enabled +
                ", color=" + color +
                '}';
    }
}
