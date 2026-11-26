package se.krka.kahlua.vm;

public interface KahluaTable {
    Object rawget(Object key);

    Object rawget(int index);

    int len();

    KahluaTableIterator iterator();
}
