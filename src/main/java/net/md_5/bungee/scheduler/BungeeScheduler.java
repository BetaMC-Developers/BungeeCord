package net.md_5.bungee.scheduler;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BungeeScheduler implements TaskScheduler {

    private final AtomicInteger taskCounter = new AtomicInteger();
    private final Int2ObjectMap<BungeeTask> tasks = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>()); // BMC - fastutil

    @Override
    public void cancel(int id) {
        cancel(tasks.remove(id));
    }

    @Override
    public void cancel(ScheduledTask task) {
        Preconditions.checkArgument(task instanceof BungeeTask, "Don't know how to handle task %s", task);
        tasks.remove(task.getId()).getFuture().cancel(false);
    }

    @Override
    public int cancel(Plugin plugin) {
        // BMC start - fastutil
        AtomicInteger cancelled = new AtomicInteger();
        tasks.int2ObjectEntrySet().removeIf(entry -> {
            BungeeTask task = entry.getValue();
            if (task.getOwner() == plugin) {
                task.getFuture().cancel(false);
                cancelled.getAndIncrement();
                return true;
            }
            return false;
        });
        // BMC end
        return cancelled.get();
    }

    @Override
    public ScheduledTask runAsync(Plugin owner, Runnable task) {
        return schedule(owner, task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledTask schedule(Plugin owner, Runnable task, long delay, TimeUnit unit) {
        BungeeTask bungeeTask = prepare(owner, task); // BMC
        return bungeeTask.setFuture(BungeeCord.getInstance().executors.schedule(bungeeTask.getTask(), delay, unit));
    }

    @Override
    public ScheduledTask schedule(Plugin owner, Runnable task, long delay, long period, TimeUnit unit) {
        BungeeTask bungeeTask = prepare(owner, task); // BMC
        return bungeeTask.setFuture(BungeeCord.getInstance().executors.scheduleWithFixedDelay(bungeeTask.getTask(), delay, period, unit));
    }

    private BungeeTask prepare(Plugin owner, Runnable task) {
        Preconditions.checkNotNull(owner, "owner");
        Preconditions.checkNotNull(task, "task");
        // BMC start - fix repeating tasks being cancelled when an error occurs
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        // BMC end

        BungeeTask prepared = new BungeeTask(taskCounter.getAndIncrement(), owner, wrappedTask);
        tasks.put(prepared.getId(), prepared);
        return prepared;
    }
}
