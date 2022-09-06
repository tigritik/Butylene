package com.github.steanky.ethylene.core.mapper.signature;

import com.github.steanky.ethylene.core.ConfigElement;
import com.github.steanky.ethylene.core.collection.ConfigNode;
import com.github.steanky.ethylene.core.collection.Entry;
import com.github.steanky.ethylene.core.mapper.*;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BasicSignatureMatcher implements SignatureMatcher {
    private final Signature[] signatures;
    private final TypeHinter typeHinter;

    public BasicSignatureMatcher(@NotNull Signature @NotNull [] signatures, @NotNull TypeHinter typeHinter) {
        Signature[] copy = new Signature[signatures.length];
        System.arraycopy(signatures, 0, copy, 0, signatures.length);
        Arrays.sort(copy, Comparator.comparing(Signature::priority).reversed());
        this.signatures = copy;
        this.typeHinter = Objects.requireNonNull(typeHinter);
    }

    @Override
    public @NotNull MatchingSignature signature(@NotNull Type desiredType, ConfigElement providedElement,
            Object providedObject) {
        for (Signature signature : signatures) {
            if (!signature.returnType().equals(desiredType)) {
                continue;
            }

            if (providedElement == null) {
                Objects.requireNonNull(providedObject);

                Collection<Entry<String, Signature.TypedObject>> objectData = signature.objectData(providedObject);

                int length = signature.length(null);
                if (length > -1 && length != objectData.size()) {
                    continue;
                }

                boolean matchNames = signature.matchesArgumentNames();
                boolean matchTypeHints = signature.matchesTypeHints();

                if (!(matchNames || matchTypeHints)) {
                    Collection<Signature.TypedObject> objects = new ArrayList<>(objectData.size());
                    for (Entry<String, Signature.TypedObject> objectEntry : objectData) {
                        objects.add(objectEntry.getSecond());
                    }

                    return new MatchingSignature(signature, null, objects, objectData.size());
                }

                outer:
                {
                    Collection<Signature.TypedObject> typeCollection = new ArrayList<>(objectData.size());
                    if (matchNames) {
                        Map<String, Signature.TypedObject> objectDataMap = new HashMap<>(objectData.size());
                        for (Entry<String, Signature.TypedObject> entry : objectData) {
                            objectDataMap.put(entry.getFirst(), entry.getSecond());
                        }

                        Iterable<Entry<String, Type>> signatureTypes = signature.argumentTypes();
                        for (Entry<String, Type> entry : signatureTypes) {
                            Signature.TypedObject typedObject = objectDataMap.get(entry.getFirst());
                            if (typedObject == null) {
                                break outer;
                            }

                            typeCollection.add(typedObject);
                        }
                    }
                    else {
                        for (Entry<String, Signature.TypedObject> entry : objectData) {
                            typeCollection.add(entry.getSecond());
                        }
                    }

                    if (matchTypeHints) {
                        Iterator<Signature.TypedObject> typeCollectionIterator = typeCollection.iterator();
                        Iterator<Entry<String, Type>> signatureIterator = signature.argumentTypes().iterator();

                        while (typeCollectionIterator.hasNext()) {
                            if (typeHinter.getHint(typeCollectionIterator.next().type()) != typeHinter
                                    .getHint(signatureIterator.next().getSecond())) {
                                break outer;
                            }
                        }
                    }

                    return new MatchingSignature(signature, null, typeCollection, length);
                }

                continue;
            }

            Objects.requireNonNull(providedElement);

            boolean matchNames = signature.matchesArgumentNames();
            if (matchNames && !providedElement.isNode()) {
                continue;
            }

            Collection<ConfigElement> elementCollection = providedElement.asContainer().elementCollection();
            int length = signature.length(providedElement);
            if (elementCollection.size() != length) {
                continue;
            }

            boolean matchTypeHints = signature.matchesTypeHints();
            if (!(matchNames || matchTypeHints)) {
                return new MatchingSignature(signature, elementCollection, null, length);
            }

            outer:
            {
                Iterable<Entry<String, Type>> signatureTypes;
                Collection<ConfigElement> targetCollection;
                if (matchNames) {
                    signatureTypes = signature.argumentTypes();

                    ConfigNode providedNode = providedElement.asNode();
                    targetCollection = new ArrayList<>(elementCollection.size());

                    //this ensures that the order is respected when matching names
                    for (Entry<String, Type> entry : signatureTypes) {
                        String name = entry.getFirst();
                        ConfigElement element = providedNode.get(name);
                        if (element == null) {
                            break outer;
                        }

                        targetCollection.add(element);
                    }
                }
                else {
                    targetCollection = elementCollection;
                }

                if (matchTypeHints) {
                    Iterator<ConfigElement> elementIterator = targetCollection.iterator();
                    Iterator<Entry<String, Type>> signatureTypeIterator = signature.argumentTypes().iterator();

                    while (elementIterator.hasNext()) {
                        if (!typeHinter.assignable(elementIterator.next(), signatureTypeIterator.next().getSecond())) {
                            break outer;
                        }
                    }
                }

                return new MatchingSignature(signature, targetCollection, null, length);
            }
        }

        throw new MapperException("unable to find matching signature for element '" + providedElement);
    }
}
