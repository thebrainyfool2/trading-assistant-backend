package com.tradingassistant.platform.user;

/**
 * Lifecycle state of an account.
 */
public enum UserStatus {

    /** The account can authenticate. */
    ACTIVE,

    /** The account is retained for history but cannot authenticate. */
    DISABLED
}
