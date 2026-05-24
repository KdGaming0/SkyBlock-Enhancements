package com.github.kd_gaming1.skyblockenhancements.util.tab;

/**
 * The primitive type of a stat's parsed value.
 * Used by {@link StatDefinition} to indicate how raw tab values
 * should be interpreted.
 */
public enum StatValueType {
    /** Raw string — no conversion applied. */
    STRING,
    /** 32-bit signed integer. */
    INT,
    /** 64-bit signed integer. */
    LONG,
    /** 32-bit floating point. */
    FLOAT,
    /** 64-bit floating point. */
    DOUBLE
}
