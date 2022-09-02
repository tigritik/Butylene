package com.github.steanky.ethylene.core.mapper;

import com.github.steanky.ethylene.core.collection.ConfigList;
import com.github.steanky.ethylene.core.collection.ConfigNode;
import com.github.steanky.ethylene.core.mapper.signature.constructor.ObjectSignatureBuilder;
import com.github.steanky.ethylene.core.mapper.signature.SignatureBuilder;
import com.github.steanky.ethylene.core.mapper.signature.TypeSignatureMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class MappingConfigProcessorIntegrationTest {
    private final MappingConfigProcessor<List<String>> stringListProcessor;
    private final MappingConfigProcessor<List<Object>> objectListProcessor;
    private final MappingConfigProcessor<List<List<String>>> listListStringProcessor;
    private final MappingConfigProcessor<List<List<String>[]>> reallyStupidProcessor;
    private final MappingConfigProcessor<CustomClass> customClassProcessor;

    public static class CustomClass {
        private final List<String> strings;
        private final int value;
        private final Set<Integer> intSet;

        public CustomClass(@NotNull List<String> strings, int value, @NotNull Set<Integer> intSet) {
            this.strings = strings;
            this.value = value;
            this.intSet = intSet;
        }
    }

    public MappingConfigProcessorIntegrationTest() {
        TypeHinter typeHinter = new BasicTypeHinter();
        BasicTypeResolver typeResolver = new BasicTypeResolver();
        SignatureBuilder signatureBuilder = new ObjectSignatureBuilder();
        typeResolver.registerTypeImplementation(Collection.class, ArrayList.class);
        typeResolver.registerTypeImplementation(Set.class, HashSet.class);

        TypeSignatureMatcher.Source source = new BasicTypeMatcherSource(typeHinter, typeResolver, signatureBuilder,
                false, false);

        this.stringListProcessor = new MappingConfigProcessor<>(new Token<>() {}, source);
        this.objectListProcessor = new MappingConfigProcessor<>(new Token<>() {}, source);
        this.listListStringProcessor = new MappingConfigProcessor<>(new Token<>() {}, source);
        this.reallyStupidProcessor = new MappingConfigProcessor<>(new Token<>() {}, source);
        this.customClassProcessor = new MappingConfigProcessor<>(new Token<>() {}, source);
    }

    @Nested
    class Lists {
        @Test
        void basicStringList() {
            List<String> stringList = assertDoesNotThrow(
                    () -> stringListProcessor.dataFromElement(ConfigList.of("a", "b", "c")));

            assertLinesMatch(List.of("a", "b", "c"), stringList);
        }

        @Test
        void basicObjectList() {
            List<Object> stringList = assertDoesNotThrow(
                    () -> objectListProcessor.dataFromElement(ConfigList.of("a", "b", "c", 1, 2, 3)));

            assertEquals(List.of("a", "b", "c", 1, 2, 3), stringList);
        }

        @Test
        void listListProcessor() {
            List<List<String>> listListString = assertDoesNotThrow(() -> listListStringProcessor.dataFromElement(
                    ConfigList.of(ConfigList.of("a", "b"), ConfigList.of("c", "d"))));

            assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), listListString);
        }

        @Test
        void reallyStupidProcessor() {
            List<List<String>[]> stupidString = assertDoesNotThrow(() -> reallyStupidProcessor.dataFromElement(
                    ConfigList.of(ConfigList.of(ConfigList.of("a", "b", "c"), ConfigList.of("d", "e", "f")),
                            ConfigList.of(ConfigList.of("g", "h", "i"), ConfigList.of("j", "k", "l")))));

            List<String>[] stupidStringArray = new List[] {List.of("a", "b", "c"), List.of("d", "e", "f")};
            List<String>[] stupidStringArray2 = new List[] {List.of("g", "h", "i"), List.of("j", "k", "l")};

            assertEquals(2, stupidString.size());

            assertArrayEquals(stupidStringArray, stupidString.get(0));
            assertArrayEquals(stupidStringArray2, stupidString.get(1));
        }
    }

    @Nested
    class Objects {
        @Test
        void customObject() {
            ConfigNode node = ConfigNode.of("strings", ConfigList.of("a", "b", "c"), "value", 69,
                    "intSet", ConfigList.of(1, 2, 3));
            CustomClass custom = assertDoesNotThrow(() -> customClassProcessor.dataFromElement(node));

            assertEquals(List.of("a", "b", "c"), custom.strings);
            assertEquals(69, custom.value);
            assertEquals(Set.of(1, 2, 3), custom.intSet);
        }
    }
}