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
package com.mastfrog.http.test.microframework.processor;

import com.mastfrog.java.vogon.ClassBuilder;
import java.util.Set;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
interface CodeGenerator {

    default void generateShutdownCode(Consumer<InvocationTarget> c) {
    }

    default <T, B extends ClassBuilder.BlockBuilderBase<T, B, T>> void generateMainMethodCode(B bb) {
    }

    void generateCreationCode(ClassBuilder.BlockBuilder<?> bb, Set<TypeMirror> generated);

    void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated);

    void collectElements(Set<Element> elements);

    default boolean isInvokable() {
        return kind() == ItemKind.METHOD;
    }

    default void generateInvocationCode(Consumer<InvocationTarget> c) {
        // do nothing
    }

    default boolean isFixture() {
        return this instanceof Generators.ClassTestFixtureGenerator || this instanceof Generators.MethodTestFixtureGenerator;
    }


    ItemKind kind();
}
