package com.github.steanky.ethylene.core.codec;

import com.github.steanky.ethylene.core.ConfigElement;
import com.github.steanky.ethylene.core.collection.ConfigList;
import com.github.steanky.ethylene.core.collection.ConfigNode;
import com.github.steanky.ethylene.core.collection.LinkedConfigNode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AbstractConfigCodecTest {
    private static final String INTEGER_KEY = "integer";
    private static final String FLOAT_KEY = "float";
    private static final String DOUBLE_KEY = "double";
    private static final String BOOLEAN_KEY = "boolean";
    private static final String STRING_KEY = "string";
    private static final String SUB_STRING_KEY = "sub_string";
    private static final String LIST_KEY = "list";
    private static final String SUB_LIST_KEY = "sub_list";
    private static final String SUB_LIST_NODES_KEY = "sub_list_nodes";

    private static final String SUB_ROOT_KEY = "sub_root";

    private static final int SUB_NODE_COUNT = 10;
    private static final String SUB_NODE_KEY_PREFIX = "sub_node_";

    private static final int INTEGER_VALUE = 666;
    private static final float FLOAT_VALUE = 0.69F;
    private static final double DOUBLE_VALUE = 0.420D;
    private static final boolean BOOLEAN_VALUE = true;

    private static final String STRING_VALUE = "this is a string";
    private static final String SUB_STRING_VALUE = "this is another string";

    private static final List<String> LIST_VALUE = List.of("first", "second", "third");
    private static final List<String> SUB_LIST_VALUE = List.of("first_sub", "second_sub", "third_sub");

    private final ConfigNode resultingElement;

    AbstractConfigCodecTest() throws IOException {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> subRoot = new HashMap<>();

        AbstractConfigCodec testCodec = new AbstractConfigCodec(List.of("test")) {
            @Override
            protected @NotNull Map<String, Object> readMap(@NotNull InputStream input) {
                return root;
            }

            @Override
            protected void writeMap(@NotNull Map<String, Object> mappings, @NotNull OutputStream output) {
                throw new IllegalStateException();
            }
        };

        root.put(INTEGER_KEY, INTEGER_VALUE);
        root.put(FLOAT_KEY, FLOAT_VALUE);
        root.put(DOUBLE_KEY, DOUBLE_VALUE);
        root.put(BOOLEAN_KEY, BOOLEAN_VALUE);
        root.put(STRING_KEY, STRING_VALUE);
        root.put(LIST_KEY, LIST_VALUE);
        root.put(SUB_ROOT_KEY, subRoot);

        List<Object> subListNodes = new ArrayList<>();
        subRoot.put(SUB_STRING_KEY, SUB_STRING_VALUE);
        subRoot.put(SUB_LIST_KEY, SUB_LIST_VALUE);
        subRoot.put(SUB_LIST_NODES_KEY, subListNodes);

        for(int i = 0; i < SUB_NODE_COUNT; i++) {
            Map<String, Object> subNode = new HashMap<>();
            subNode.put(SUB_NODE_KEY_PREFIX + i, i);
            subListNodes.add(subNode);
        }

        resultingElement = testCodec.decodeNode(InputStream.nullInputStream(), LinkedConfigNode::new);
    }

    @Test
    void validTopLevelPrimitives() {
        assertEquals(INTEGER_VALUE, resultingElement.getElement(INTEGER_KEY).asNumber().intValue());
        assertEquals(FLOAT_VALUE, resultingElement.getElement(FLOAT_KEY).asNumber().floatValue());
        assertEquals(DOUBLE_VALUE, resultingElement.getElement(DOUBLE_KEY).asNumber().doubleValue());
        assertEquals(BOOLEAN_VALUE, resultingElement.getElement(BOOLEAN_KEY).asBoolean());
        assertEquals(STRING_VALUE, resultingElement.getElement(STRING_KEY).asString());
    }

    @Test
    void validTopLevelFlatStringList() {
        ConfigList array = resultingElement.getElement(LIST_KEY).asList();
        List<String> equivalent = new ArrayList<>();
        for(ConfigElement element : array) {
            equivalent.add(element.asString());
        }

        assertEquals(LIST_VALUE, equivalent);
    }

    @Test
    void validNestedFlatStringList() {
        ConfigList array = resultingElement.getElement(SUB_ROOT_KEY).asNode().getElement(SUB_LIST_KEY).asList();
        List<String> equivalent = new ArrayList<>();
        for(ConfigElement element : array) {
            equivalent.add(element.asString());
        }

        assertEquals(SUB_LIST_VALUE, equivalent);
    }

    @Test
    void validNestedPrimitives() {
        assertEquals(SUB_STRING_VALUE, resultingElement.getElement(SUB_ROOT_KEY).asNode()
                .getElement(SUB_STRING_KEY).asString());
    }

    @Test
    void validNestedArrayNodes() {
        ConfigList subNodes = resultingElement.getElement(SUB_ROOT_KEY).asNode()
                .getElement(SUB_LIST_NODES_KEY).asList();

        for(int i = 0; i < SUB_NODE_COUNT; i++) {
            ConfigNode element = subNodes.get(i).asNode();
            assertEquals(i, element.getElement(SUB_NODE_KEY_PREFIX + i).asNumber().intValue());
        }
    }
}