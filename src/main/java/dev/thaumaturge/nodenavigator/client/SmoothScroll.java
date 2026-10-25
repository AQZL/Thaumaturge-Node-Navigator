package dev.thaumaturge.nodenavigator.client;

public final class SmoothScroll {
    private static final double RESPONSE = 18.0;
    private static final double SETTLED_DISTANCE = 0.01;
    private static final double SETTLED_SPEED = 0.05;

    private double maximum;
    private double position;
    private double target;
    private double velocity;

    public double position() {
        return position;
    }

    public double maximum() {
        return maximum;
    }

    public void setMaximum(double maximum) {
        this.maximum = Double.isFinite(maximum) ? Math.max(0.0, maximum) : 0.0;
        double clamped = clamp(position);
        target = clamp(target);
        if (clamped != position || this.maximum == 0.0) velocity = 0.0;
        position = clamped;
    }

    public void scroll(double pixelDelta) {
        if (!Double.isFinite(pixelDelta)) return;
        target = clamp(target + pixelDelta);
    }

    public void advance(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) return;
        if (deltaSeconds >= 60.0) {
            jumpTo(target);
            return;
        }
        double displacement = position - target;
        double change = velocity + RESPONSE * displacement;
        double decay = Math.exp(-RESPONSE * deltaSeconds);
        double nextPosition = target + (displacement + change * deltaSeconds) * decay;
        double nextVelocity = (velocity - RESPONSE * change * deltaSeconds) * decay;
        if (!Double.isFinite(nextPosition) || !Double.isFinite(nextVelocity)) {
            jumpTo(target);
            return;
        }
        position = clamp(nextPosition);
        velocity = position != nextPosition ? 0.0 : nextVelocity;
        if (Math.abs(position - target) < SETTLED_DISTANCE && Math.abs(velocity) < SETTLED_SPEED) {
            jumpTo(target);
        }
    }

    public void jumpTo(double position) {
        this.position = clamp(position);
        target = this.position;
        velocity = 0.0;
    }

    public void reset() {
        jumpTo(0.0);
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(maximum, value));
    }
}
