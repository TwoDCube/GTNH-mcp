package com.twodcube.gtnhmcp.client;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.client.Minecraft;

import com.twodcube.gtnhmcp.mcp.protocol.McpToolException;

/**
 * Runs a unit of read-only work on the Minecraft client thread and blocks the calling (HTTP worker) thread for its
 * result.
 *
 * <p>
 * <b>Why this exists:</b> the embedded HTTP server handles requests on its own worker threads, but reading world/player
 * state from those threads is a data race — Minecraft mutates that state from the single client thread every tick.
 * Touching it off-thread can corrupt iteration, observe half-updated objects, or crash the game. Every tool therefore
 * routes its game-state access through here.
 *
 * <p>
 * <b>How it works:</b> we wrap the work in a {@link FutureTask} and hand it to {@code Minecraft.func_152344_a} (the
 * obfuscated name for {@code addScheduledTask} in the 1.7.10 mappings used by this workspace). The client drains its
 * scheduled-task queue once per game loop on the client thread, which runs our task; the worker thread then collects
 * the
 * result via {@link FutureTask#get(long, TimeUnit)} with a timeout. Passing a {@link FutureTask} (rather than relying
 * on
 * the return value of {@code func_152344_a}) keeps this robust regardless of that method's return type.
 */
public final class ClientThreadExecutor {

    private final long timeoutMs;

    /**
     * @param timeoutMs how long a worker thread will wait for the client thread to run the task before giving up. Keep
     *                  this well under the MCP client's per-call timeout so callers get a clean error instead of a
     *                  hang.
     */
    public ClientThreadExecutor(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        this.timeoutMs = timeoutMs;
    }

    /**
     * Schedule {@code task} on the client thread and wait for its result.
     *
     * @param task the read-only work to run on the client thread; may throw {@link McpToolException} to report a
     *             user-facing problem.
     * @param <T>  the result type.
     * @return the task's result.
     * @throws McpToolException if the client is unavailable, the wait times out (game paused/closed), the thread is
     *                          interrupted, or the task itself fails.
     */
    public <T> T call(Callable<T> task) throws McpToolException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            throw new McpToolException("The Minecraft client is not available yet.");
        }
        FutureTask<T> futureTask = new FutureTask<T>(task);
        // Enqueue onto the client thread's scheduled-task queue, drained each game loop on the main thread.
        mc.func_152344_a(futureTask);
        try {
            return futureTask.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futureTask.cancel(false);
            throw new McpToolException(
                "Timed out after " + timeoutMs
                    + " ms waiting for the Minecraft client thread. Is the game running and not frozen?");
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            throw new McpToolException("Interrupted while waiting for the Minecraft client thread.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpToolException) {
                throw (McpToolException) cause;
            }
            String detail = cause == null ? e.toString()
                : cause.getClass()
                    .getSimpleName() + ": "
                    + cause.getMessage();
            throw new McpToolException("Error reading game state: " + detail, cause);
        }
    }
}
