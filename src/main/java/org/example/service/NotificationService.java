package org.example.service;

import javafx.application.Platform;
import org.example.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Fires desktop notifications for triggered alerts. Uses java.awt.SystemTray
 * for OS-level toasts (works alongside JavaFX without conflicting, as long
 * as Toolkit is initialized lazily and not on the JavaFX thread at startup).
 * Also exposes a hook for the UI to show an in-app toast/badge.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private TrayIcon trayIcon;
    private boolean trayInitialized = false;
    private Consumer<String> uiToastHandler;

    public synchronized void initTrayIfSupported() {
        if (trayInitialized || !SystemTray.isSupported()) {
            return;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            trayIcon = new TrayIcon(image, "CS Auto Targets");
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
            trayInitialized = true;
        } catch (AWTException e) {
            log.warn("Could not initialize system tray icon: {}", e.getMessage());
        }
    }

    /** Lets the UI register a callback to display an in-window toast/badge whenever a notification fires. */
    public void setUiToastHandler(Consumer<String> handler) {
        this.uiToastHandler = handler;
    }

    public void notifyAlert(Alert alert, int currentPriceCents, org.example.model.Platform platformOfOffer) {
        String title = "CS2 Price Alert";
        String body = alert.getSkinMarketHashName()
                + " is " + formatUsd(currentPriceCents)
                + " on " + (platformOfOffer != null ? platformOfOffer.displayName() : "a platform");

        if (trayIcon != null) {
            trayIcon.displayMessage(title, body, TrayIcon.MessageType.INFO);
        } else {
            log.info("[Notification - no tray available] {}: {}", title, body);
        }

        if (uiToastHandler != null) {
            Platform.runLater(() -> uiToastHandler.accept(body));
        }
    }

    private static String formatUsd(int cents) {
        return String.format("$%.2f", cents / 100.0);
    }
}
