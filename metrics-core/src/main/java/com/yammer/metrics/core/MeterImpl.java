package com.yammer.metrics.core;

import com.yammer.metrics.stats.EWMA;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The default implementation of {@link Meter}.
 */
public class MeterImpl implements Meter {
    private static final long TICK_INTERVAL = TimeUnit.SECONDS.toNanos(5);

    private final EWMA m1Rate = EWMA.oneMinuteEWMA();
    private final EWMA m5Rate = EWMA.fiveMinuteEWMA();
    private final EWMA m15Rate = EWMA.fifteenMinuteEWMA();

    private final AtomicLong count = new AtomicLong();
    private final long startTime;
    private final AtomicLong lastTick;
    private final Clock clock;

    /**
     * Creates a new {@link MeterImpl}.
     *
     * @param clock      the clock to use for the meter ticks
     */
    public MeterImpl(Clock clock) {
        this.clock = clock;
        this.startTime = this.clock.getTick();
        this.lastTick = new AtomicLong(startTime);
    }

    @Override
    public void mark() {
        mark(1);
    }

    @Override
    public void mark(long n) {
        tickIfNecessary();
        count.addAndGet(n);
        m1Rate.update(n);
        m5Rate.update(n);
        m15Rate.update(n);
    }

    private void tickIfNecessary() {
        final long oldTick = lastTick.get();
        final long newTick = clock.getTick();
        final long age = newTick - oldTick;
        if (age > TICK_INTERVAL && lastTick.compareAndSet(oldTick, newTick)) {
            final long requiredTicks = age / TICK_INTERVAL;
            for (long i = 0; i < requiredTicks; i++) {
                m1Rate.tick();
                m5Rate.tick();
                m15Rate.tick();
            }
        }
    }

    @Override
    public long getCount() {
        return count.get();
    }

    @Override
    public double getFifteenMinuteRate() {
        tickIfNecessary();
        return m15Rate.getRate(TimeUnit.SECONDS);
    }

    @Override
    public double getFiveMinuteRate() {
        tickIfNecessary();
        return m5Rate.getRate(TimeUnit.SECONDS);
    }

    @Override
    public double getMeanRate() {
        if (getCount() == 0) {
            return 0.0;
        } else {
            final long elapsed = (clock.getTick() - startTime);
            return convertNsRate(getCount() / (double) elapsed);
        }
    }

    @Override
    public double getOneMinuteRate() {
        tickIfNecessary();
        return m1Rate.getRate(TimeUnit.SECONDS);
    }

    private double convertNsRate(double ratePerNs) {
        return ratePerNs * (double) TimeUnit.SECONDS.toNanos(1);
    }
}
