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

import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import static com.mastfrog.http.test.microframework.processor.HttpTestAnnotationProcessor.ANNOTATIONS_PACKAGE;
import static com.mastfrog.http.test.microframework.processor.HttpTestAnnotationProcessor.ARGUMENTS_TYPE;
import static com.mastfrog.http.test.microframework.processor.HttpTestAnnotationProcessor.ON_ERROR_TYPE;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.NestingKind.ANONYMOUS;
import static javax.lang.model.element.NestingKind.LOCAL;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class Model {

    private final AnnotationUtils utils;
    final Set<TestFixture> producers = new HashSet<>();
    final Set<Testable> tests = new HashSet<>();
    final Set<PostRun> postRuns = new HashSet<>();
    private final AtomicInteger ids = new AtomicInteger();

    public Model(AnnotationUtils utils) {
        this.utils = utils;
    }

    private boolean ifNestingKindOk(AnnotationMirror mir, TypeElement te, BooleanSupplier run) {
        switch (te.getNestingKind()) {
            case ANONYMOUS:
            case LOCAL:
                utils.fail("A test fixture cannot be a "
                        + te.getNestingKind() + " class.", te, mir);
                return false;
            default:
                return run.getAsBoolean();
        }
    }

    private boolean ifAccessible(AnnotationMirror mir, TypeElement te, BooleanSupplier supp) {
        Set<Modifier> mods = te.getModifiers();
        if (!mods.contains(Modifier.PUBLIC)) {
            utils.fail(te.getSimpleName() + " needs to be public", te, mir);
            return false;
        }
        return supp.getAsBoolean();
    }

    private boolean ifAccessible(AnnotationMirror mir, ExecutableElement method, BooleanSupplier supp) {
        Set<Modifier> mods = method.getModifiers();
        if (!mods.contains(Modifier.PUBLIC)) {
            utils.fail(method.getSimpleName() + " needs to be public", method, mir);
            return false;
        }
        return supp.getAsBoolean();
    }

    private boolean ifNoOrSingleConstructor(AnnotationMirror mir, TypeElement el, BooleanSupplier supp) {
        List<ExecutableElement> constructors = findPublicConstructors(el);
        if (constructors.size() > 1) {
            utils.fail(el.getSimpleName() + " has more than one public constructor", el, mir);
            return false;
        }
        return supp.getAsBoolean();
    }

    private boolean ifValidReturnType(AnnotationMirror mir, ExecutableElement el, BooleanSupplier supp) {
        if (el.getReturnType() == null || "void".equals(el.getReturnType().toString())) {
            utils.fail("void is not a valid test fixture return type", el, mir);
            return false;
        }
        return supp.getAsBoolean();
    }

    Set<TypeMirror> allProducedTypes() {
        Set<TypeMirror> result = typeSet();
        for (TestFixture f : producers) {
            result.addAll(f.produces());
        }
        return result;
    }

    Set<TypeMirror> allRequiredTypes() {
        Set<TypeMirror> result = typeSet();
        producers.forEach(p -> {
            result.addAll(p.requires());
        });
        tests.forEach(t -> {
            result.addAll(t.requires());
        });
        return result;
    }

    Set<TypeMirror> fixturesImplementingOnError() {
        Set<TypeMirror> result = typeSet();
        TypeMirror onError = utils.type(ON_ERROR_TYPE);
        for (TestFixture fix : producers) {
            fix.origin().ifPresent(type -> {
                boolean match = utils.isAssignable(type.asType(), onError);
                utils.warn("Test " + type + " as " + onError
                    + " " + match);
                if (match) {
                    utils.warn("  GOT ONE " + type.asType());
                    result.add(type.asType());
                }
            });
        }
        for (PostRun pr : postRuns) {
            pr.origin().ifPresent(type -> {
                boolean match = utils.isAssignable(type.asType(), onError);
                utils.warn("Test " + type + " as " + onError
                    + " " + match);
                if (match) {
                    utils.warn("  GOT ONE " + type.asType());
                    result.add(type.asType());
                }
            });
        }
        return result;
    }

    String leastPackage() {
        Set<Element> els = new HashSet<>();
        producers.forEach(p -> p.collectElements(els));
        tests.forEach(p -> p.collectElements(els));
        Set<String> packages = new TreeSet<>();
        for (Element e : els) {
            String pkn = utils.packageName(e);
            if (!ANNOTATIONS_PACKAGE.equals(pkn) && !pkn.isEmpty()) {
                packages.add(pkn);
            }
        }
        if (packages.isEmpty()) {
            return "com.foo";
        }
        packages.remove("com.mastfrog.http.test.microframework");
        List<String[]> parts = new ArrayList<>();
        for (String s : packages) {
            parts.add(s.split("\\."));
        }
        Collections.sort(parts, (a, b) -> {
            return Integer.compare(a.length, b.length);
        });
        return Strings.join('.', parts.get(0));
    }

    public static Set<TypeMirror> typeSet(TypeMirror... types) {
        // TypeMirror does not properly implement equality
        TreeSet<TypeMirror> result = new TreeSet<>((a, b) -> {
            return a.toString().compareTo(b.toString());
        });
        if (types.length > 0) {
            for (TypeMirror m : types) {
                result.add(m);
            }
        }
        return result;
    }

    public static Set<TypeMirror> copyOf(Set<TypeMirror> set) {
        Set<TypeMirror> nue = typeSet();
        nue.addAll(set);
        return nue;
    }

    boolean validateContents() {
        Set<TypeMirror> produced = allProducedTypes();
        Set<TypeMirror> required = allRequiredTypes();
        Set<TypeMirror> absent = copyOf(required);
        absent.removeAll(produced);
        for (Iterator<TypeMirror> it = absent.iterator(); it.hasNext();) {
            TypeMirror tm = it.next();
            for (TypeMirror prod : produced) {
                if (utils.isAssignable(tm, prod)) {
                    it.remove();
                }
            }
        }
        if (!absent.isEmpty()) {
            for (TypeMirror ab : absent) {
                requirersOf(ab).forEach(item -> {
                    item.attachError("No test fixture produces " + ab
                            + ".\nProduced: " + produced + "\nRequired: " + required);
                });
            }
        }
        Set<TypeMirror> extra = copyOf(produced);
        extra.removeAll(required);
        extra.forEach(type -> {
            TestElement producer = producerOf(type);
            if (producer != null) { // should not be
                producer.attachWarning("No test fixture or test requires " + type
                        + " but it is configured as a test fixture.");
            }
        });
        return absent.isEmpty();
    }

    private boolean addTestFixture(AnnotationMirror mir, Element el, TestFixture fix) {
        Set<TypeMirror> currentProduction = allProducedTypes();
        Set<TypeMirror> addingTypes = fix.produces();
        int expectedSize = currentProduction.size() + addingTypes.size();
        currentProduction.addAll(addingTypes);
        if (currentProduction.size() != expectedSize) {
            Set<TypeMirror> intersection = allProducedTypes();
            intersection.retainAll(addingTypes);
            utils.fail("More than once test fixture produces " + intersection, el, mir);
            return false;
        }
        producers.add(fix);
        return true;
    }

    boolean addTestFixtureMethod(AnnotationMirror anno,
            TypeElement te, ExecutableElement exe) {
        return ifNestingKindOk(anno, te, () -> {
            return ifAccessible(anno, te, () -> {
                return ifAccessible(anno, exe, () -> {
                    return ifNoOrSingleConstructor(anno, te, () -> {
                        return ifValidReturnType(anno, exe, () -> {
                            return addTestFixture(anno, exe, new MethodTestFixture(te, anno, exe));
                        });
                    });
                });
            });
        });
    }

    boolean addTestFixtureClass(AnnotationMirror anno,
            TypeElement te) {
        return ifNestingKindOk(anno, te, () -> {
            return ifAccessible(anno, te, () -> {
                return ifNoOrSingleConstructor(anno, te, () -> {
                    return addTestFixture(anno, te, new ClassTestFixture(te, anno));
                });
            });
        });
    }

    boolean addTestMethod(AnnotationMirror anno,
            TypeElement te, ExecutableElement exe) {
        return ifNestingKindOk(anno, te, () -> {
            return ifAccessible(anno, te, () -> {
                return ifAccessible(anno, exe, () -> {
                    return ifNoOrSingleConstructor(anno, te, () -> {
                        tests.add(new MethodTestable(te, anno, exe));
                        return true;
                    });
                });
            });
        });
    }

    boolean addPostRun(AnnotationMirror anno, TypeElement te, ExecutableElement exe) {
        return ifNestingKindOk(anno, te, () -> {
            return ifAccessible(anno, te, () -> {
                return ifAccessible(anno, exe, () -> {
                    return ifNoOrSingleConstructor(anno, te, () -> {
                        postRuns.add(new PostRun(anno, te, exe));
                        return true;
                    });
                });
            });
        });
    }

    boolean addTestClass(AnnotationMirror anno,
            TypeElement te) {
        return ifNestingKindOk(anno, te, () -> {
            return ifAccessible(anno, te, () -> {
                return ifNoOrSingleConstructor(anno, te, () -> {
                    tests.add(new ClassTestable(te, anno));
                    return true;
                });
            });
        });
    }

    String fieldNameForType(TypeMirror mir) {
        TestElement producer = this.producerOf(mir);
        if (producer == null) {
            throw new IllegalArgumentException("No producer of " + mir
                    + " (" + mir.getKind() + " - "
                    + mir.getClass().getName() + ")"
                    + " in " + allProducedTypes());
        }
        return toFieldName(mir, producer.id());
    }

    private String toFieldName(TypeMirror type, int ix) {
        String result = simpleName(utils.erasureOf(type).toString()) + "_" + ix;
        char[] c = result.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private static List<ExecutableElement> findConstructors(TypeElement on) {
        List<ExecutableElement> els = new ArrayList<>();
        on.getEnclosedElements().forEach(item -> {
            if (item.getKind() == ElementKind.CONSTRUCTOR) {
                els.add((ExecutableElement) item);
            }
        });
        return els;
    }

    private static List<ExecutableElement> findPublicConstructors(TypeElement on) {
        List<ExecutableElement> els = new ArrayList<>();
        on.getEnclosedElements().forEach(item -> {
            if (item.getKind() == ElementKind.CONSTRUCTOR && item.getModifiers().contains(Modifier.PUBLIC)) {
                els.add((ExecutableElement) item);
            }
        });
        return els;
    }

    Set<String> originatingTypes() {
        Set<TypeMirror> types = typeSet();
        for (TestFixture f : producers) {
            f.origin().ifPresent(te -> {
                types.add(te.asType());
            });
        }
        for (Testable f : tests) {
            f.origin().ifPresent(te -> {
                types.add(te.asType());
            });
        }
        for (PostRun f : postRuns) {
            f.origin().ifPresent(te -> {
                types.add(te.asType());
            });
        }
        Set<String> result = new TreeSet<>();
        for (TypeMirror tm : types) {
            result.add(tm.toString());
        }
        result.remove(ARGUMENTS_TYPE);
        return result;
    }

    TestElement producerOf(TypeMirror type) {
        for (TestFixture f : producers) {
            if (f.produces().contains(type)) {
                return f;
            }
        }
        for (Testable t : tests) {
            if (t.produces().contains(type)) {
                return t;
            }
        }
        for (PostRun pr : postRuns) {
            if (pr.produces().contains(type)) {
                return pr;
            }
        }

        // Allows un-generified and similar coercions:
        for (TestFixture f : producers) {
            for (TypeMirror prod : f.produces()) {
                if (isCompatible(type, prod)) {
                    return f;
                }
            }
        }
        for (Testable t : tests) {
            for (TypeMirror prod : t.produces()) {
                if (isCompatible(type, prod)) {
                    return t;
                }
            }
        }
        for (PostRun pr : postRuns) {
            for (TypeMirror prod : pr.produces()) {
                if (isCompatible(type, prod)) {
                    return pr;
                }
            }
        }
        return null;
    }

    private boolean isCompatible(TypeMirror type, TypeMirror prod) {
        return utils.isAssignable(type, prod)
                || utils.erasureOf(type).toString().equals(utils.erasureOf(prod));
    }

    Set<TestElement> requirersOf(TypeMirror type) {
        Set<TestElement> result = new HashSet<>();
        producers.forEach(p -> {
            if (p.requires().contains(type)) {
                result.add(p);
            }
        });
        tests.forEach(t -> {
            if (t.requires().contains(type)) {
                result.add(t);
            }
        });
        return result;
    }

    class PostRun implements TestElement {

        final AnnotationMirror anno;
        final TypeElement type;
        final ExecutableElement exe;
        private final int id = ids.getAndIncrement();

        public PostRun(AnnotationMirror anno, TypeElement te, ExecutableElement exe) {
            this.anno = anno;
            this.type = te;
            this.exe = exe;
        }

        @Override
        public Optional<TypeElement> origin() {
            return Optional.of(type);
        }

        @Override
        public String toString() {
            return type.getQualifiedName() + "." + exe.getSimpleName();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public ItemKind kind() {
            return ItemKind.POST_RUN;
        }

        @Override
        public Set<TypeMirror> requires() {
            Set<TypeMirror> result = copyOf(constructorArgTypes(type));
            result.addAll(argumentTypes(exe));
            return result;
        }

        @Override
        public void attachWarning(String what) {
            utils.warn(what, exe, anno);
        }

        @Override
        public void attachError(String what) {
            utils.fail(what, exe, anno);
        }

        public Set<TypeMirror> produces() {
            Set<TypeMirror> result = typeSet();
            result.add(type.asType());
            return result;
        }

        @Override
        public void collectElements(Set<? super TypeElement> into) {
            into.add(type);
        }

        public List<TypeMirror> orderedConstructorArguments() {
            return Model.this.orderedConstructorArguments(type);
        }

        List<TypeMirror> orderedMethodArguments(ExecutableElement exe) {
            List<TypeMirror> result = new ArrayList<>();
            for (VariableElement ve : exe.getParameters()) {
                result.add(ve.asType());
            }
            return result;
        }
    }

    class ClassTestFixture implements TestFixture {

        final TypeElement type;
        final AnnotationMirror anno;
        private final int id = ids.getAndIncrement();

        public ClassTestFixture(TypeElement type, AnnotationMirror anno) {
            this.type = type;
            this.anno = anno;
        }

        @Override
        public Optional<TypeElement> origin() {
            return Optional.of(type);
        }

        public String toString() {
            return type.getQualifiedName().toString();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public void attachWarning(String what) {
            utils.warn(what, type, anno);
        }

        @Override
        public void attachError(String what) {
            utils.fail(what, type, anno);
        }

        @Override
        public ItemKind kind() {
            return ItemKind.CLASS;
        }

        @Override
        public Set<TypeMirror> produces() {
            return Collections.singleton(type.asType());
        }

        @Override
        public void visitGeneratableFields(BiConsumer<String, TypeMirror> c) {
            c.accept(toFieldName(type.asType(), id), type.asType());
            for (TypeMirror m : requires()) {
                c.accept(toFieldName(m, id), m);
            }
        }

        @Override
        public Set<TypeMirror> requires() {
            return constructorArgTypes(type);
        }

        public List<TypeMirror> orderedConstructionArguments() {
            return orderedConstructorArguments(type);
        }

        @Override
        public void collectElements(Set<? super TypeElement> into) {
            into.add(type);
        }
    }

    List<TypeMirror> orderedConstructorArguments(TypeElement type) {
        List<ExecutableElement> con = findPublicConstructors(type);
        if (con.isEmpty()) {
            return Collections.emptyList();
        }
        return orderedMethodArguments(con.iterator().next());
    }

    List<TypeMirror> orderedMethodArguments(ExecutableElement exe) {
        List<TypeMirror> result = new ArrayList<>();
        for (VariableElement ve : exe.getParameters()) {
            result.add(ve.asType());
        }
        return result;
    }

    private Set<TypeMirror> constructorArgTypes(TypeElement type) {
        List<ExecutableElement> con = findPublicConstructors(type);
        if (con.isEmpty()) {
            return Collections.emptySet();
        }
        ExecutableElement constructor = con.iterator().next();
        return argumentTypes(constructor);
    }

    private Set<TypeMirror> argumentTypes(ExecutableElement exe) {
        List<? extends VariableElement> args = exe.getParameters();
        if (args.isEmpty()) {
            return Collections.emptySet();
        }
        Set<TypeMirror> types = typeSet();
        for (VariableElement var : args) {
            types.add(var.asType());
        }
        return types;
    }

    private Set<TypeMirror> requiredTypes(TypeElement type,
            ExecutableElement element) {
        Set<TypeMirror> result = constructorArgTypes(type);
        if (result.isEmpty()) {
            result = typeSet();
        }
        if (element != null) {
            result.addAll(argumentTypes(element));
        }
        return result;
    }

    private Set<ExecutableElement> publicMethods(TypeElement type) {
        Set<ExecutableElement> exes = new HashSet<>();
        for (Element el : type.getEnclosedElements()) {
            switch (el.getKind()) {
                case METHOD:
                    exes.add((ExecutableElement) el);
                    break;
            }
        }
        return exes;
    }

    private Set<TypeMirror> publicMethodArgumentTypes(TypeElement type) {
        Set<TypeMirror> result = typeSet();
        publicMethods(type).forEach(method -> result.addAll(argumentTypes(method)));
        return result;
    }

    class MethodTestFixture implements TestFixture {

        final TypeElement type;
        final AnnotationMirror anno;
        final ExecutableElement element;
        private final int id = ids.getAndIncrement();

        public MethodTestFixture(TypeElement type, AnnotationMirror anno,
                ExecutableElement element) {
            this.type = type;
            this.anno = anno;
            this.element = element;
        }

        @Override
        public Optional<TypeElement> origin() {
            return Optional.of(type);
        }

        @Override
        public String toString() {
            return type.getQualifiedName() + "." + element.getSimpleName();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public void attachWarning(String what) {
            utils.warn(what, element, anno);
        }

        @Override
        public void attachError(String what) {
            utils.fail(what, element, anno);
        }

        @Override
        public void visitGeneratableFields(BiConsumer<String, TypeMirror> c) {
            TypeMirror t = type.asType();
            c.accept(toFieldName(t, id), t);
            for (TypeMirror m : requires()) {
                c.accept(toFieldName(m, id), m);
            }
        }

        @Override
        public ItemKind kind() {
            return ItemKind.METHOD;
        }

        @Override
        public Set<TypeMirror> produces() {
            Set<TypeMirror> types = typeSet();
            types.add(type.asType());
            TypeMirror result = element.getReturnType();
            if (result != null && !"void".equals(result)) {
                types.add(result);
            }
            return types;
        }

        @Override
        public Set<TypeMirror> requires() {
            return requiredTypes(type, element);
        }

        public List<TypeMirror> orderedConstructionArguments() {
            return orderedConstructorArguments(type);
        }

        public List<TypeMirror> orderedMethodArguments() {
            List<TypeMirror> result = Model.this.orderedMethodArguments(element);
            utils.warn("METHOD ARGS " + element.getSimpleName() + ": " + result, element, anno);

            return result;
        }

        @Override
        public void collectElements(Set<? super TypeElement> into) {
            into.add(type);
        }
    }

    class ClassTestable implements Testable {

        final TypeElement type;
        final AnnotationMirror anno;
        private final int id = ids.getAndIncrement();

        public ClassTestable(TypeElement type, AnnotationMirror anno) {
            this.type = type;
            this.anno = anno;
        }

        @Override
        public Optional<TypeElement> origin() {
            return Optional.of(type);
        }

        @Override
        public String toString() {
            return type.getQualifiedName().toString();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public void attachError(String what) {
            utils.fail(what, type, anno);
        }

        @Override
        public void attachWarning(String what) {
            utils.warn(what, type, anno);
        }

        @Override
        public ItemKind kind() {
            return ItemKind.CLASS;
        }

        @Override
        public Set<TypeMirror> requires() {
            Set<TypeMirror> result = copyOf(requiredTypes(type, null));
            result.addAll(publicMethodArgumentTypes(type));
            return result;
        }

        public List<TypeMirror> orderedConstructionArguments() {
            return orderedConstructorArguments(type);
        }

        @Override
        public Set<TypeMirror> produces() {
            return typeSet(type.asType());
        }

        @Override
        public void visitGeneratableFields(BiConsumer<String, TypeMirror> c) {
            String nm = toFieldName(type.asType(), id);
            c.accept(nm, type.asType());
        }

        @Override
        public void collectElements(Set<? super TypeElement> into) {
            into.add(type);
        }
    }

    class MethodTestable implements Testable {

        final TypeElement type;
        final AnnotationMirror anno;
        final ExecutableElement element;
        private final int id = ids.getAndIncrement();

        public MethodTestable(TypeElement type, AnnotationMirror anno,
                ExecutableElement element) {
            this.type = type;
            this.anno = anno;
            this.element = element;
        }

        @Override
        public Optional<TypeElement> origin() {
            return Optional.of(type);
        }

        @Override
        public String toString() {
            return type.getQualifiedName() + "." + element.getSimpleName();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public void attachWarning(String what) {
            utils.warn(what, element, anno);
        }

        @Override
        public void attachError(String what) {
            utils.fail(what, element, anno);
        }

        @Override
        public ItemKind kind() {
            return ItemKind.METHOD;
        }

        @Override
        public Set<TypeMirror> produces() {
            return typeSet(type.asType());
        }

        @Override
        public void visitGeneratableFields(BiConsumer<String, TypeMirror> c) {
            String nm = toFieldName(type.asType(), id);
            c.accept(nm, type.asType());
        }

        @Override
        public Set<TypeMirror> requires() {
            return requiredTypes(type, element);
        }

        public List<TypeMirror> orderedConstructionArguments() {
            return orderedConstructorArguments(type);
        }

        public List<TypeMirror> orderedMethodArguments() {
            return Model.this.orderedMethodArguments(element);
        }

        @Override
        public void collectElements(Set<? super TypeElement> into) {
            into.add(type);
        }
    }

    boolean generate(RoundEnvironment roundEnv) {
        try {
            for (TypeMirror mir : allRequiredTypes()) {
                if (ARGUMENTS_TYPE.equals(mir.toString())) {
                    TypeElement argsType = utils.processingEnv().getElementUtils().getTypeElement(ARGUMENTS_TYPE);
                    TestFixture fix = new ClassTestFixture(argsType, null);
                    this.producers.add(fix);
                }
            }
            if (!validateContents()) {
                return false;
            }
            new Generators(utils, this).generate();
        } catch (Exception | Error ex) {
            ex.printStackTrace();
            utils.fail(ex.toString());
        } finally {
            clear();
        }
        return true;
    }

    void clear() {
        ids.set(0);
        tests.clear();
        producers.clear();
    }
}
