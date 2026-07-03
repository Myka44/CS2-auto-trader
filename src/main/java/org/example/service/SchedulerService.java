package org.example.service;

import org.example.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the two recurring background jobs: target auto-adjustment and alert
 * checking. Designed to keep running as long as the app's JVM process is
 * alive, matching the "always-on home PC server" use case described in the
 * project brief -- you can minimize the window and it keeps polling.
 */
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "cs-auto-targets-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final TargetService targetService;
    private final AlertService alertService;

    private volatile int targetIntervalMinutes = AppConfig.DEFAULT_TARGET_POLL_INTERVAL_MINUTES;
    private volatile int alertIntervalMinutes = AppConfig.DEFAULT_ALERT_POLL_INTERVAL_MINUTES;

    public SchedulerService(TargetService targetService, AlertService alertService) {
        this.targetService = targetService;
        this.alertService = alertService;
    }

    public void start() {
        log.info("Starting scheduler: targets every {} min, alerts every {} min", targetIntervalMinutes, alertIntervalMinutes);
        executor.scheduleWithFixedDelay(this::safeRunTargets, 10, targetIntervalMinutes * 60L, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(this::safeRunAlerts, 15, alertIntervalMinutes * 60L, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void setTargetIntervalMinutes(int minutes) {
        this.targetIntervalMinutes = minutes;
    }

    public void setAlertIntervalMinutes(int minutes) {
        this.alertIntervalMinutes = minutes;
    }

    /** Triggers an immediate one-off run outside the regular schedule (e.g. a "Check Now" button). */
    public void runTargetsNow() {
        executor.submit(this::safeRunTargets);
    }

    public void runAlertsNow() {
        executor.submit(this::safeRunAlerts);
    }

    private void safeRunTargets() {
        try {
            targetService.runAdjustCycle();
        } catch (Exception e) {
            log.error("Unhandled error in target adjust cycle", e);
        }
    }

    private void safeRunAlerts() {
        try {
            alertService.runCheckCycle();
        } catch (Exception e) {
            log.error("Unhandled error in alert check cycle", e);
        }
    }
}
