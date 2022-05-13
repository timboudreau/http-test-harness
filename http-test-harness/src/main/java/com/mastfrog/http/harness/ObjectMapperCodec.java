package com.mastfrog.http.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.util.codec.Codec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Codec wrapper for Jackson ObjectMapper.
 *
 * @author Tim Boudreau
 */
final class ObjectMapperCodec implements Codec {

    private final ObjectMapper mapper;

    ObjectMapperCodec() {
        this(new ObjectMapper());
    }

    ObjectMapperCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> String writeValueAsString(T t) throws IOException {
        return mapper.writeValueAsString(t);
    }

    @Override
    public <T> void writeValue(T t, OutputStream out) throws IOException {
        out.write(writeValueAsBytes(out));
    }

    @Override
    public <T> byte[] writeValueAsBytes(T t) throws IOException {
        return mapper.writeValueAsBytes(t);
    }

    @Override
    public <T> T readValue(InputStream in, Class<T> type) throws IOException {
        return mapper.readValue(in, type);
    }
}
