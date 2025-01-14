package com.github.steanky.ethylene.mapper.signature.constructor;

import com.github.steanky.ethylene.core.ConfigElement;
import com.github.steanky.ethylene.core.collection.ConfigContainer;
import com.github.steanky.ethylene.core.collection.Entry;
import com.github.steanky.ethylene.core.collection.LinkedConfigNode;
import com.github.steanky.ethylene.mapper.MapperException;
import com.github.steanky.ethylene.mapper.PrioritizedBase;
import com.github.steanky.ethylene.mapper.annotation.Name;
import com.github.steanky.ethylene.mapper.annotation.Order;
import com.github.steanky.ethylene.mapper.annotation.Widen;
import com.github.steanky.ethylene.mapper.internal.ReflectionUtils;
import com.github.steanky.ethylene.mapper.signature.Signature;
import com.github.steanky.ethylene.mapper.type.Token;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * {@link Signature} that uses a constructor to create its objects. This does not support building objects.
 */
public class ConstructorSignature<T> extends PrioritizedBase implements Signature<T> {
    private static final Comparator<? super Field> COMPARATOR = Comparator.comparing(field -> {
        Order order = field.getAnnotation(Order.class);
        if (order != null) {
            return order.value();
        }

        return 0;
    });

    private final Token<T> genericReturnType;
    private final Reference<Class<?>> rawClassReference;
    private final String rawClassName;
    private final Reference<Class<?>>[] parameterTypes;
    private final String[] parameterTypeNames;

    //constructor objects are not retained by the classloader, so they might be garbage collected early
    //use soft reference to reduce the frequency of this occurrence, and resolve the actual constructor at runtime if it
    //is necessary to do so
    private Reference<Constructor<?>> constructorReference;
    private boolean matchesNames;

    private Collection<Map.Entry<String, Token<?>>> types;

    //similarly to constructors, fields are not tied to the classloader, keep a soft reference and be prepared to
    //re-create as necessary
    private Reference<Map<String, Field>> namedFieldsReference = new SoftReference<>(null);
    private Reference<Field[]> fieldsReference = new SoftReference<>(null);

    /**
     * Creates a new instance of this class.
     *
     * @param constructor the {@link Constructor} used to create new objects and from which to obtain signature
     *                    information
     * @param genericReturnType the full generic type of the object created by the constructor
     */
    @SuppressWarnings("unchecked")
    public ConstructorSignature(@NotNull Constructor<?> constructor, @NotNull Token<T> genericReturnType) {
        super(0);
        this.genericReturnType = Objects.requireNonNull(genericReturnType);
        this.constructorReference = new SoftReference<>(Objects.requireNonNull(constructor));

        Class<?> declaringClass = constructor.getDeclaringClass();
        this.rawClassReference = new WeakReference<>(declaringClass);
        this.rawClassName = declaringClass.getTypeName();

        Class<?>[] params = constructor.getParameterTypes();
        this.parameterTypes = new Reference[params.length];
        this.parameterTypeNames = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> referent = params[i];
            parameterTypes[i] = new WeakReference<>(referent);
            parameterTypeNames[i] = referent.getTypeName();
        }
    }

    private static Map.Entry<String, Token<?>> makeEntry(Parameter parameter, boolean parameterHasName) {
        Name parameterName = parameter.getAnnotation(Name.class);
        Token<?> parameterType = Token.ofType(parameter.getParameterizedType());
        if (parameterName != null) {
            return Entry.of(parameterName.value(), parameterType);
        }

        return Entry.of(parameterHasName ? parameter.getName() : null, parameterType);
    }

    @Override
    public @NotNull Iterable<Map.Entry<String, Token<?>>> argumentTypes() {
        return resolveTypeCollection();
    }

    @Override
    public @NotNull Collection<TypedObject> objectData(@NotNull T object) {
        Collection<Map.Entry<String, Token<?>>> types = resolveTypeCollection();

        Class<?> declaringClass = ReflectionUtils.resolve(rawClassReference, rawClassName);
        boolean widenAccess = declaringClass.isAnnotationPresent(Widen.class);

        Field[] fields = initFields(declaringClass, widenAccess);

        int i = 0;
        Collection<TypedObject> typedObjects = new ArrayList<>(types.size());
        Map<String, Field> fieldMap = null;
        for (Map.Entry<String, Token<?>> typeEntry : types) {
            Field field;
            String name;
            if (matchesNames) {
                if (fieldMap == null) {
                    fieldMap = resolveNamedFields(fields);
                }

                field = fieldMap.get(name = typeEntry.getKey());
                if (field == null) {
                    break;
                }
            } else {
                if (i == fields.length) {
                    break;
                }

                field = fields[i++];
                name = ReflectionUtils.getFieldName(field);
            }

            if (widenAccess && !field.trySetAccessible()) {
                break;
            }

            try {
                typedObjects.add(
                    new TypedObject(name, Token.ofType(field.getGenericType()), FieldUtils.readField(field, object)));
            } catch (IllegalAccessException ignored) {
                break;
            }
        }

        return typedObjects;
    }

    @Override
    public @NotNull ConfigContainer initContainer(int sizeHint) {
        return new LinkedConfigNode(sizeHint);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull T buildObject(@Nullable T buildingObject, Object @NotNull [] args) {
        if (buildingObject != null) {
            throw new MapperException("ConstructorSignature does not support pre-initialized building objects");
        }

        try {
            //it is the caller's responsibility to check argument length!
            return (T) resolveConstructor().newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new MapperException(e);
        }
    }

    @Override
    public boolean matchesArgumentNames() {
        //make sure the type collection is generated
        resolveTypeCollection();
        return matchesNames;
    }

    @Override
    public boolean matchesTypeHints() {
        return true;
    }

    @Override
    public int length(@Nullable ConfigElement element) {
        return resolveConstructor().getParameterCount();
    }

    @Override
    public @NotNull Token<T> returnType() {
        return genericReturnType;
    }

    private Field[] initFields(Class<?> declaringClass, boolean widenAccess) {
        Field[] fields = fieldsReference.get();
        if (fields != null) {
            return fields;
        }

        fields = widenAccess ? declaringClass.getDeclaredFields() : declaringClass.getFields();

        if (matchesNames) {
            resolveNamedFields(fields);
        } else {
            Arrays.sort(fields, COMPARATOR);
        }

        fieldsReference = new SoftReference<>(fields);
        return fields;
    }

    private Map<String, Field> resolveNamedFields(Field[] fields) {
        Map<String, Field> fieldMap = namedFieldsReference.get();
        if (fieldMap != null) {
            return fieldMap;
        }

        fieldMap = new HashMap<>(fields.length);
        for (Field field : fields) {
            fieldMap.put(ReflectionUtils.getFieldName(field), field);
        }

        namedFieldsReference = new SoftReference<>(fieldMap);
        return fieldMap;
    }

    private Collection<Map.Entry<String, Token<?>>> resolveTypeCollection() {
        if (types != null) {
            return types;
        }

        Constructor<?> constructor = resolveConstructor();
        if (constructor.getParameterCount() == 0) {
            //use empty list if we can
            return types = List.of();
        }

        Parameter[] parameters = constructor.getParameters();
        if (parameters.length == 1) {
            //alternatively use singleton list
            Parameter first = parameters[0];
            Map.Entry<String, Token<?>> entry = makeEntry(first, first.isNamePresent());
            matchesNames = entry.getKey() != null;
            return types = List.of(entry);
        }

        //use a backing ArrayList for n > 1 length
        List<Map.Entry<String, Token<?>>> entryList = new ArrayList<>(parameters.length);

        Parameter first = parameters[0];

        boolean parameterHasName = first.isNamePresent();
        Map.Entry<String, Token<?>> firstEntry = makeEntry(first, parameterHasName);
        matchesNames = firstEntry.getKey() != null;

        entryList.add(firstEntry);

        boolean firstNonNullName = firstEntry.getKey() != null;
        for (int i = 1; i < parameters.length; i++) {
            Map.Entry<String, Token<?>> entry = makeEntry(parameters[i], parameterHasName);
            if (firstNonNullName == (entry.getKey() == null)) {
                throw new MapperException("Inconsistent parameter naming");
            }

            entryList.add(entry);
        }

        return types = Collections.unmodifiableList(entryList);
    }

    private Constructor<?> resolveConstructor() {
        Constructor<?> constructor = constructorReference.get();
        if (constructor != null) {
            return constructor;
        }

        Class<?> declaringClass = ReflectionUtils.resolve(rawClassReference, rawClassName);
        try {
            constructor = declaringClass.getConstructor(resolveParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No valid constructor found for type '" + rawClassName + "'", e);
        }

        constructorReference = new SoftReference<>(constructor);
        return constructor;
    }

    private Class<?>[] resolveParameterTypes() {
        Class<?>[] parameterClasses = new Class[parameterTypes.length];
        for (int i = 0; i < parameterClasses.length; i++) {
            Class<?> referent = parameterTypes[i].get();
            if (referent == null) {
                throw new IllegalStateException("Class named '" + parameterTypeNames[i] + "' no longer exists");
            }

            parameterClasses[i] = referent;
        }

        return parameterClasses;
    }
}
