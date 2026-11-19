package com.tradingassistant.platform.security;

/**
 * Application role granted to a user.
 *
 * <p>Roles are stored with the user document and copied into the access token so
 * an authorization decision never needs a database round trip. A role change
 * takes effect when the next access token is issued.</p>
 */
public enum Role {

    /** A normal client account: own watchlists, orders, history and profile. */
    USER,

    /** Platform administration: all users, application settings and all orders. */
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
