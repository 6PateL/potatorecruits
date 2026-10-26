package com.potatorecruits;

final class PotatoConfigHelper {
    private PotatoConfigHelper() {}

    /** Ammo is required in the inventory whenever shots consume it. */
    static boolean needsAmmo() {
        return true;
    }
}
