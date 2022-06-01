/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.http.harness.difference;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class JacksonDifferencer implements Differencer<Object> {

    @Override
    public <P> void difference(String name, Object a, Object b, DifferencesBuilder<P> bldr) {
        ReflectionDifferencer refl = new ReflectionDifferencer();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> mapa = mapper.readValue(mapper.writeValueAsBytes(a), Map.class);
            Map<?, ?> mapb = mapper.readValue(mapper.writeValueAsBytes(a), Map.class);
            refl.difference(name, mapa, mapb, bldr);
        } catch (IOException ex) {
            refl.difference(name, a, b, bldr);
        }
    }
}
