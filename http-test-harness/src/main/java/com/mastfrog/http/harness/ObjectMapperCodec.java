/*
 * The MIT License
 *
 * Copyright 2022 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
