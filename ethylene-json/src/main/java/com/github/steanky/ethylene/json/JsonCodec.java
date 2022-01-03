package com.github.steanky.ethylene.json;

import com.github.steanky.ethylene.codec.AbstractConfigCodec;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

public class JsonCodec extends AbstractConfigCodec {
    public static final JsonCodec INSTANCE = new JsonCodec();

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private final Gson gson;

    private JsonCodec() {
        super(Set.of("json"));
        this.gson = new Gson();
    }

    @Override
    protected @NotNull Map<String, Object> readMap(@NotNull InputStream input) throws IOException {
        try(InputStreamReader reader = new InputStreamReader(input)) {
            return gson.fromJson(reader, MAP_TYPE);
        }
        catch (JsonIOException | JsonSyntaxException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    protected void writeMap(@NotNull Map<String, Object> mappings, @NotNull OutputStream output) throws IOException {
        try(OutputStreamWriter writer = new OutputStreamWriter(output)) {
            gson.toJson(mappings, writer);
        }
        catch (JsonIOException exception) {
            throw new IOException(exception);
        }
    }
}