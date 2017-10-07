package com.codingchili.Controller;

public class Atomic<E> {
    private E reference;

    public Atomic(E reference) {
        this.reference = reference;
    }

    public E get() {
        return reference;
    }

    public void set(E reference) {
        this.reference = reference;
    }
}
