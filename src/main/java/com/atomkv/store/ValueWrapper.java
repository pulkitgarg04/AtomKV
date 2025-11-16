package com.atomkv.store;

import java.util.Objects;

public class ValueWrapper {
    private final String value;
    private volatile long expireAtMillis; // -1 means no expiration

    public ValueWrapper(String value, long expireAtMillis) {
        this.value = value;
        this.expireAtMillis = expireAtMillis;
    }

    public String getValue() {
        return value;
    }

    public long getExpireAtMillis() {
        return expireAtMillis;
    }

    public void setExpireAtMillis(long expireAtMillis) {
        this.expireAtMillis = expireAtMillis;
    }

    public boolean isExpired() {
        return expireAtMillis > 0 && System.currentTimeMillis() > expireAtMillis;
    }

    public long ttlMillis() {
        if (expireAtMillis <= 0)
            return -1;
        return Math.max(-1, expireAtMillis - System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return "ValueWrapper{" + "value='" + value + '\'' + ", expireAtMillis=" + expireAtMillis + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ValueWrapper that = (ValueWrapper) o;
        return expireAtMillis == that.expireAtMillis && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, expireAtMillis);
    }
}