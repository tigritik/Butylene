package com.github.steanky.ethylene.core.mapper;

import com.github.steanky.ethylene.core.ConfigElement;
import com.github.steanky.ethylene.core.mapper.signature.BasicSignatureMatcher;
import com.github.steanky.ethylene.core.mapper.signature.Signature;
import com.github.steanky.ethylene.core.mapper.signature.SignatureBuilder;
import com.github.steanky.ethylene.core.mapper.signature.SignatureMatcher;
import com.github.steanky.ethylene.core.mapper.signature.container.ArraySignature;
import com.github.steanky.ethylene.core.mapper.signature.container.CollectionSignature;
import com.github.steanky.ethylene.core.mapper.signature.container.MapSignature;
import com.github.steanky.ethylene.core.util.ReflectionUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

public class BasicSignatureMatcherSource implements SignatureMatcher.Source {
    private final TypeHinter typeHinter;
    private final SignatureBuilder.Selector signatureSelector;

    private final Map<Type, SignatureMatcher> signatureCache;
    private final Map<Class<?>, Set<Signature>> customSignatures;

    public BasicSignatureMatcherSource(@NotNull TypeHinter typeHinter, @NotNull SignatureBuilder.Selector signatureSelector) {
        this.typeHinter = Objects.requireNonNull(typeHinter);
        this.signatureSelector = Objects.requireNonNull(signatureSelector);

        this.signatureCache = new WeakHashMap<>();
        this.customSignatures = new WeakHashMap<>();
    }

    @Override
    public SignatureMatcher matcherFor(@NotNull Type resolvedType, @Nullable ConfigElement element) {
        return signatureCache.computeIfAbsent(resolvedType, type -> {
            Class<?> raw = TypeUtils.getRawType(type, null);

            for (Class<?> superclass : ClassUtils.hierarchy(raw, ClassUtils.Interfaces.INCLUDE)) {
                Set<Signature> signatures = customSignatures.get(superclass);
                if (signatures != null) {
                    return new BasicSignatureMatcher(signatures.toArray(new Signature[0]), typeHinter);
                }
            }

            return switch (typeHinter.getHint(type)) {
                case LIST -> {
                    if (TypeUtils.isArrayType(type)) {
                        Signature[] arraySignature =
                                new Signature[] { new ArraySignature(TypeUtils.getArrayComponentType(type)) };
                        yield new BasicSignatureMatcher(arraySignature, typeHinter);
                    }
                    else {
                        if (Collection.class.isAssignableFrom(raw)) {
                            Type[] types = ReflectionUtils.extractGenericTypeParameters(type, Collection.class);
                            Signature[] collectionSignature = new Signature[] { new CollectionSignature(types[0], type) };
                            yield new BasicSignatureMatcher(collectionSignature, typeHinter);
                        }
                        else if (Map.class.isAssignableFrom(raw)) {
                            Type[] types = ReflectionUtils.extractGenericTypeParameters(type, Map.class);
                            Signature[] mapSignature = new Signature[] { new MapSignature(types[0], types[1], type) };
                            yield new BasicSignatureMatcher(mapSignature, typeHinter);
                        }
                    }

                    throw new MapperException("unexpected container-like type '" + type.getTypeName() + "'");
                }
                case NODE -> new BasicSignatureMatcher(signatureSelector.select(type).buildSignatures(type), typeHinter);
                case SCALAR -> null;
            };
        });
    }

    public void registerCustomSignature(@NotNull Signature signature) {
        customSignatures.computeIfAbsent(TypeUtils.getRawType(signature.returnType(), null), o ->
                new HashSet<>(2)).add(signature);
    }
}
