package net.sanaechan.storage.manager.crypt;

import java.util.function.Supplier;

public class Holder<T> implements Supplier<T> {

    private Supplier<T> supplier;
    private T value;

    public Holder(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        if (this.value == null) {
            this.value = this.supplier.get();
            this.supplier = null;
        }
        return this.value;
    }

    public boolean isEmpty() {
        return this.value == null;
    }
}
