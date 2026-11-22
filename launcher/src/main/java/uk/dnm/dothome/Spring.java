package uk.dnm.dothome;

import android.view.Choreographer;

/**
 * A damped spring driven by real physics each frame, so it keeps the speed of a flick and can be
 * redirected at any moment without a jump. stiffness ~ k, damping = ratio (1 = no bounce).
 */
final class Spring implements Choreographer.FrameCallback {

    interface Listener {
        void onUpdate(float value);
        default void onSettled(float value) { }
    }

    float value, velocity, target;
    float stiffness, damping;
    private final Listener listener;
    private boolean running;
    private long lastFrameNs;

    Spring(float stiffness, float damping, float start, Listener l) {
        this.stiffness = stiffness;
        this.damping = damping;
        this.value = start;
        this.target = start;
        this.listener = l;
    }

    boolean isRunning() { return running; }

    /** Jump straight to a value (e.g. while a finger is dragging). */
    void set(float v) {
        cancel();
        value = v;
        velocity = 0;
        target = v;
        listener.onUpdate(v);
    }

    void animateTo(float t) { animateTo(t, velocity); }

    void animateTo(float t, float startVelocity) {
        target = t;
        velocity = startVelocity;
        if (!running) {
            running = true;
            lastFrameNs = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    void cancel() {
        if (running) {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float dt = lastFrameNs == 0 ? 1f / 120f : Math.min(0.05f, (frameTimeNanos - lastFrameNs) / 1e9f);
        lastFrameNs = frameTimeNanos;
        // Sub-steps keep stiff springs stable on slow frames
        int steps = Math.max(1, (int) Math.ceil(dt / (1f / 480f)));
        float h = dt / steps;
        float w0 = (float) Math.sqrt(stiffness);
        for (int i = 0; i < steps; i++) {
            float a = -stiffness * (value - target) - 2f * damping * w0 * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        if (Math.abs(velocity) < 0.0025f && Math.abs(value - target) < 0.0008f) {
            value = target;
            velocity = 0;
            running = false;
            listener.onUpdate(value);
            listener.onSettled(value);
            return;
        }
        listener.onUpdate(value);
        Choreographer.getInstance().postFrameCallback(this);
    }
}
