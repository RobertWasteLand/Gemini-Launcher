package se.krka.kahlua.vm;

public interface KahluaTableIterator {
    boolean advance();

    Object getKey();
}
