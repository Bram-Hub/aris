package edu.rpi.aris.assign;

public class Triple<F, S, T> {

    private final F first;
    private final S second;
    private final T third;

    public Triple(F first, S second, T third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    private Triple() {
        this(null, null, null);
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    public T getThird() {
        return third;
    }

    public F getLeft() {
        return first;
    }

    public S getMiddle() {
        return second;
    }

    public T getRight() {
        return third;
    }

}
