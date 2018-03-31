package com.codingchili.Controller;

/**
 * Holds an atomic reference to the given reference.
 * @param <E> the type of reference that is held.
 */
public class Atomic<E> {
    private E reference;

    /**
     * @param reference the inital reference to hold.
     */
    public Atomic(E reference) {
        this.reference = reference;
    }

    /**
     * @return retrieves the reference. may return null.
     */
    public synchronized E get() {
        return reference;
    }

    /**
     * @param reference the reference to set.
     */
    public synchronized void set(E reference) {
        this.reference = reference;
    }
}
