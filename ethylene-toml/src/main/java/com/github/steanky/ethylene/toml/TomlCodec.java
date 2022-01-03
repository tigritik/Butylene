package com.github.steanky.ethylene.toml;

import com.github.steanky.ethylene.ConfigElement;
import com.github.steanky.ethylene.codec.AbstractConfigCodec;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class TomlCodec extends AbstractConfigCodec {
    public static final TomlCodec INSTANCE = new TomlCodec();

    private final TomlWriter tomlWriter;

    private TomlCodec() {
        super(Set.of("toml"));
        tomlWriter = new TomlWriter();
    }

    @Override
    protected @NotNull Map<String, Object> readMap(@NotNull InputStream input) throws IOException {
        try {
            return new Toml().read(input).toMap();
        }
        catch (IllegalStateException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    protected void writeMap(@NotNull Map<String, Object> mappings, @NotNull OutputStream output) throws IOException {
        try {
            tomlWriter.write(mappings, output);
        }
        catch (IllegalArgumentException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    protected @NotNull ConfigElement toElement(@Nullable Object raw) {
        if(raw instanceof Date date) {
            return new ConfigDate(date);
        }

        return super.toElement(raw);
    }

    @Override
    protected @Nullable Object toObject(@NotNull ConfigElement raw) {
        if(raw instanceof ConfigDate configDate) {
            return configDate.getDate();
        }

        return super.toObject(raw);
    }
}