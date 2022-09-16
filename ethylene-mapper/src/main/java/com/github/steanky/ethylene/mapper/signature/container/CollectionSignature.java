package com.github.steanky.ethylene.mapper.signature.container;

import com.github.steanky.ethylene.core.ConfigElement;
import com.github.steanky.ethylene.core.collection.ConfigContainer;
import com.github.steanky.ethylene.mapper.MapperException;
import com.github.steanky.ethylene.mapper.type.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class CollectionSignature extends ContainerSignatureBase {
    public CollectionSignature(@NotNull Token<?> componentType, @NotNull Token<?> collectionClass) {
        super(componentType, collectionClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Collection<TypedObject> objectData(@NotNull Object object) {
        Collection<Object> objectCollection = (Collection<Object>) object;

        return new AbstractCollection<>() {
            @Override
            public Iterator<TypedObject> iterator() {
                return new Iterator<>() {
                    private final Iterator<Object> collectionIterator = objectCollection.iterator();

                    @Override
                    public boolean hasNext() {
                        return collectionIterator.hasNext();
                    }

                    @Override
                    public TypedObject next() {
                        return new TypedObject(null, CollectionSignature.this.entry.getSecond(),
                                collectionIterator.next());
                    }
                };
            }

            @Override
            public int size() {
                return objectCollection.size();
            }
        };
    }

    @Override
    protected @NotNull Object makeBuildingObject(@NotNull ConfigContainer container) {
        return makeNewCollection(container.entryCollection().size());
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Object buildObject(@Nullable Object buildingObject, Object @NotNull [] args) {
        if (buildingObject != null) {
            Collection<Object> buildingCollection = (Collection<Object>) buildingObject;
            buildingCollection.addAll(Arrays.asList(args));
            return buildingCollection;
        }

        Collection<Object> collection = makeNewCollection(args.length);
        collection.addAll(Arrays.asList(args));
        return collection;
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> makeNewCollection(int size) {
        try {
            ConstructorInfo constructorInfo = resolveConstructor();
            boolean parameterless = constructorInfo.parameterless();
            Constructor<?> constructor = constructorInfo.constructor();

            if (parameterless) {
                return (Collection<Object>) constructor.newInstance();
            }

            return (Collection<Object>) constructor.newInstance(size);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new MapperException(e);
        }
    }
}
