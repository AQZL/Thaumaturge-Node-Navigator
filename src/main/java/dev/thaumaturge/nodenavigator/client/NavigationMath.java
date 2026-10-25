package dev.thaumaturge.nodenavigator.client;

public final class NavigationMath {
    private NavigationMath() {}
    public static double bearing(double dx, double dz, double yaw) {
        double degrees = Math.toDegrees(Math.atan2(dx, dz)) + yaw;
        return degrees - Math.floor((degrees + 180.0) / 360.0) * 360.0;
    }
    public static double horizontalDistance(double dx, double dz) { return Math.hypot(dx, dz); }
}
