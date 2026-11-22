package org.hmscommunity.patients.domain;

/** US-06: hastanın durum seviyesi (Mild, Moderate, Advanced). */
public enum ConditionLevel {
    MILD,
    MODERATE,
    ADVANCED;

    /** Yalnızca "Advanced" seviyesinde yakın (relative) bilgisi zorunludur. */
    public boolean requiresRelativeInformation() {
        return this == ADVANCED;
    }
}
