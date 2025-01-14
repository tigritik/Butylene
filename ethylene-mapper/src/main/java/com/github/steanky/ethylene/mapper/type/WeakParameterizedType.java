package com.github.steanky.ethylene.mapper.type;

import com.github.steanky.ethylene.mapper.internal.ReflectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Implementation of {@link ParameterizedType} that retains no strong references to any {@link Type} or {@link Class}
 * objects used in constructing it. Not part of the public API.
 */
final class WeakParameterizedType extends WeakTypeBase implements ParameterizedType, WeakType {
    private final Reference<Class<?>> rawClassReference;
    private final String rawClassName;

    private final Reference<Type> ownerTypeReference;
    private final String ownerTypeName;

    private final Reference<Type>[] typeArgumentReferences;
    private final String[] typeArgumentNames;

    /**
     * Creates a new instance of this class.
     *
     * @param rawClass      the raw class of this generic type
     * @param owner         the owner, or enclosing, type
     * @param typeArguments the type arguments, which are not checked for compatibility with the number or bounds of the
     *                      raw class's type variables
     */
    @SuppressWarnings("unchecked")
    WeakParameterizedType(@NotNull Class<?> rawClass, @Nullable Type owner, Type @NotNull [] typeArguments) {
        super(generateIdentifier(rawClass, owner, typeArguments));

        this.rawClassReference = new WeakReference<>(rawClass);
        this.rawClassName = rawClass.getTypeName();

        ClassLoader classLoader = rawClass.getClassLoader();
        this.ownerTypeReference = owner == null ? null : GenericInfo.ref(owner, this, classLoader);
        this.ownerTypeName = owner == null ? StringUtils.EMPTY : owner.getTypeName();

        this.typeArgumentReferences = new Reference[typeArguments.length];
        this.typeArgumentNames = new String[typeArguments.length];
        GenericInfo.populate(typeArguments, typeArgumentReferences, typeArgumentNames, this, classLoader);
    }

    private static byte @NotNull [] generateIdentifier(@NotNull Class<?> rawClass, @Nullable Type owner,
        Type @NotNull [] typeArguments) {
        Type[] mergedArray = new Type[3 + typeArguments.length];
        mergedArray[0] = owner;
        mergedArray[1] = rawClass;
        mergedArray[2] = null;
        System.arraycopy(typeArguments, 0, mergedArray, 3, typeArguments.length);

        return GenericInfo.identifier(GenericInfo.PARAMETERIZED, mergedArray);
    }

    @Override
    public Type[] getActualTypeArguments() {
        return ReflectionUtils.resolve(typeArgumentReferences, typeArgumentNames, Type.class);
    }

    @Override
    public Type getRawType() {
        return ReflectionUtils.resolve(rawClassReference, rawClassName);
    }

    @Override
    public Type getOwnerType() {
        return ownerTypeReference == null ? null : ReflectionUtils.resolve(ownerTypeReference, ownerTypeName);
    }
}
