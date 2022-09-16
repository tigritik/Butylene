package com.github.steanky.ethylene.mapper.signature;

import com.github.steanky.ethylene.core.collection.Entry;
import com.github.steanky.ethylene.mapper.type.Token;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

@ApiStatus.Internal
public class TypeMappingCollection extends AbstractCollection<Entry<String, Type>> {
    private final Collection<Entry<String, Token<?>>> tokenCollection;

    public TypeMappingCollection(@NotNull Collection<Entry<String, Token<?>>> tokenCollection) {
        this.tokenCollection = Objects.requireNonNull(tokenCollection);
    }

    @Override
    public Iterator<Entry<String, Type>> iterator() {
        return new Iterator<>() {
            private final Iterator<Entry<String, Token<?>>> iterator = tokenCollection.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Entry<String, Type> next() {
                Entry<String, Token<?>> entry = iterator.next();
                return Entry.of(entry.getFirst(), entry.getSecond().get());
            }
        };
    }

    @Override
    public int size() {
        return tokenCollection.size();
    }
}
