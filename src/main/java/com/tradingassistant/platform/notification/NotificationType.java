package com.tradingassistant.platform.notification;

/**
 * Supported notification categories.
 *
 * <p>Notifications describe platform activity only. They never recommend a
 * trade and never contain a prediction.</p>
 */
public enum NotificationType {

    ORDER_UPDATE,
    POSITION_UPDATE,
    LOGIN_ALERT,
    SYSTEM_MESSAGE
}
