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

import com.mastfrog.abstractions.list.IndexedResolvable;
import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.graph.ObjectPath;
import static com.mastfrog.http.test.microframework.processor.HttpTestAnnotationProcessor.ARGUMENTS_TYPE;
import com.mastfrog.http.test.microframework.processor.Model.ClassTestFixture;
import com.mastfrog.http.test.microframework.processor.Model.ClassTestable;
import com.mastfrog.http.test.microframework.processor.Model.MethodTestFixture;
import com.mastfrog.http.test.microframework.processor.Model.MethodTestable;
import com.mastfrog.http.test.microframework.processor.Model.PostRun;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.TryBuilder;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

/**
 *
 * @author Tim Boudreau
 */
final class Generators {

    private static final String EXE_SVC_NAME = "__SVC";
    private final AnnotationUtils utils;
    private final Model model;
    private final String pkg;

    public Generators(AnnotationUtils utils, Model model) {
        this.utils = utils;
        this.model = model;
        this.pkg = model.leastPackage();
    }

    private String typeNameFor(TypeMirror mir) {
        String s = mir.toString();
        if (s.startsWith(pkg) /*&& s.lastIndexOf('.') == pkg.length() + 1*/) {
            return simpleName(s);
        }
        return s;
//        return simpleName(utils.erasureOf(mir).toString());
    }

    private ClassBuilder<String> classDocComment(ClassBuilder<String> cb) {
        List<Object> all = new ArrayList<>();
        all.add("Generated from annotations on:\n<ul>");
        for (String type : model.originatingTypes()) {
            all.add("<li>" + type + "</li>\n");
        }
        all.add("</ul>");
        return cb.docComment(all.toArray());
    }

    public void generate() throws IOException {
        ClassBuilder<String> cb = classDocComment(ClassBuilder.forPackage(pkg)
                .named("TestMain").withModifier(Modifier.PUBLIC, Modifier.FINAL));
        cb.importing("java.util.concurrent.ExecutorService",
                "java.util.concurrent.Executors",
                "java.util.concurrent.CountDownLatch");
        cb.field(EXE_SVC_NAME, fb -> {
            fb.withModifier(PRIVATE, STATIC, FINAL)
                    .initializedFromInvocationOf("newCachedThreadPool")
                    .on("Executors").ofType("ExecutorService");
        });
//        cb.generateDebugLogCode();
        Set<TypeMirror> fields = Model.typeSet();
        List<CodeGenerator> generators = generators();
        utils.warn("GENS: ");
        for (CodeGenerator cg : generators) {
            utils.warn("  * " + cg);
        }
        generators.forEach(generator -> {
            generator.generateFields(cb, fields);
        });
        Set<TypeMirror> constructed = Model.typeSet();
        cb.constructor(con -> {
            con.setModifier(Modifier.PUBLIC)
                    .throwing("Exception")
                    .body(bb -> {
                        generators.forEach(generator
                                -> generator.generateCreationCode(bb, constructed)
                        );
                    });
        });
        cb.method("run", mth -> {
            mth.withModifier(PUBLIC).throwing("Exception")
                    .body(bb -> {
                        // need names for methods
                        List<InvocationTarget> targets = new ArrayList<>();
                        generators.stream().filter(CodeGenerator::isInvokable)
                                .forEachOrdered(cg -> {
                                    if (!(cg instanceof PostRunCodeGenerator)) {
                                        cg.generateInvocationCode(targets::add);
                                    }
                                });
                        bb.lineComment("We ensure that we do not call done() until all");
                        bb.lineComment("test methods have returned.  If they perform background");
                        bb.lineComment("work, you need some test fixture's close() or shutdown()");
                        bb.lineComment("method to block until all of the work is done.");
                        bb.declare("latch").initializedWithNew(nb -> {
                            nb.withArgument(targets.size())
                                    .ofType("CountDownLatch");
                        }).as("CountDownLatch");
                        Collections.sort(targets);
                        for (InvocationTarget target : targets) {
                            bb.invoke("submit").withLambdaArgument(lb -> {
                                lb.body(lbb -> {
                                    lbb.trying(tri -> {
                                        target.generate(tri);
//                                        tri.invoke(target.name()).withArgument("latch").inScope();
                                        tri.catching("Exception", "Error").as("thrown")
                                                .invoke("onError")
                                                .withStringLiteral("run")
                                                .withStringLiteral(target.name())
                                                .withArgument("thrown")
                                                .onThis()
                                                .fynalli().invoke("countDown").on("latch")
                                                .endBlock();
                                    });
                                });
                            }).on(EXE_SVC_NAME);
                        }
                        bb.lineComment("Wait until all jobs are at least started");
                        bb.invoke("await").on("latch");
                    });
        });

        cb.method("onError", mb -> {
            mb.withModifier(PRIVATE)
                    .addArgument("String", "task")
                    .addArgument("String", "executing")
                    .addArgument("Throwable", "thrown")
                    .body(bb -> {
                        Set<TypeMirror> onErrors = model.fixturesImplementingOnError();
                        if (onErrors.isEmpty()) {
                            bb.lineComment("Default error handling.");
                            bb.lineComment("Have a fixture implement OnError to replace.");
                            bb.invoke("println").withStringConcatentationArgument("Thrown ")
                                    .appendExpression("thrown").append(" in ").appendExpression("task")
                                    .append(" executing ").appendExpression("executing").append(".")
                                    .endConcatenation().onField("err").of("System");
                            bb.invoke("printStackTrace")
                                    .withArgumentFromField("err").of("System")
                                    .on("thrown");
                        } else {
                            onErrors.forEach(oe -> {
                                bb.lineComment(oe + " implements OnError, so pass errors to it.");
                                String fld = model.fieldNameForType(oe);
                                bb.invoke("onError")
                                        .withArgument("task")
                                        .withArgument("executing")
                                        .withArgument("thrown")
                                        .on(fld);
                            });
                        }
                    });
        });

        cb.method("main").withModifier(PUBLIC, STATIC)
                .throwing("Exception")
                .addVarArgArgument("String", "args", bb -> {
                    generators.forEach(gen -> {
                        gen.generateMainMethodCode(bb);
                    });
                    bb.declare("runner").initializedWithNew(nb -> {
                        nb.ofType(cb.className());
                    }).as(cb.className());
                    bb.trying(tri -> {
                        tri.invoke("run").on("runner");
                        tri.fynalli().invoke("done").on("runner").endBlock();
                    });

                });

        cb.method("done").withModifier(PRIVATE)
                .throwing("Exception")
                .body(bb -> {
                    bb.trying((TryBuilder<?> tri) -> {
                        for (int i = generators.size() - 1; i >= 0; i--) {
                            CodeGenerator gen = generators.get(i);
                            if (gen.kind() != ItemKind.POST_RUN) {
                                gen.generateShutdownCode(it -> {
                                    tri.trying(subtri -> {
                                        it.generate(subtri);
                                        subtri.catching("Exception", "Error").as("thrown")
                                                .invoke("onError")
                                                .withStringLiteral("done")
                                                .withStringLiteral(gen.toString())
                                                .withArgument("thrown")
                                                .inScope()
                                                .endTryCatch();
                                    });

                                });
                            }
                        }
                        tri.fynalli(fin -> {
                            for (CodeGenerator cg : generators) {
                                if (cg instanceof PostRunCodeGenerator) {
                                    cg.generateShutdownCode(it -> {
                                        it.generate(fin);
                                    });
                                }
                            }
                        });
                    });
                });
        cb.sortMembers();

        Filer filer = utils.processingEnv().getFiler();
        Set<Element> allElements = new HashSet<>();
        generators.forEach(gen -> gen.collectElements(allElements));
        JavaFileObject file = filer.createSourceFile(
                cb.fqn(),
                allElements.toArray(Element[]::new));
        try ( OutputStream out = file.openOutputStream()) {
            out.write(cb.build().getBytes(UTF_8));
        }
    }

    private static BitSet[] bitsetArray(int size) {
        BitSet[] result = new BitSet[size];
        for (int i = 0; i < size; i++) {
            result[i] = new BitSet(size);
        }
        return result;
    }

    private List<CodeGenerator> generators() {
        List<TestElement> els = topoOrder();
        List<CodeGenerator> fixtureGenerators = new ArrayList<>();
        List<CodeGenerator> testGenerators = new ArrayList<>();
        for (TestElement te : els) {
            CodeGenerator gen;
            if (te instanceof ClassTestFixture) {
                ClassTestFixture ctf = (ClassTestFixture) te;
                if (ARGUMENTS_TYPE.equals(ctf.type.asType().toString())) {
                    gen = new ArgumentsFixtureGenerator();
                } else {
                    gen = new ClassTestFixtureGenerator(ctf);
                }
            } else if (te instanceof MethodTestFixture) {
                gen = new MethodTestFixtureGenerator((MethodTestFixture) te);
            } else if (te instanceof ClassTestable) {
                gen = new ClassTestGenerator((ClassTestable) te);
            } else if (te instanceof MethodTestable) {
                gen = new MethodTestGenerator((MethodTestable) te);
            } else if (te instanceof PostRun) {
                gen = new PostRunCodeGenerator((PostRun) te);
            } else {
                throw new AssertionError("Huh? " + te);
            }
            if (gen.isFixture()) {
                fixtureGenerators.add(gen);
            } else {
                testGenerators.add(gen);
            }
        }
        List<CodeGenerator> gens = new ArrayList<>(fixtureGenerators.size()
                + testGenerators.size());
        gens.addAll(fixtureGenerators);
        gens.addAll(testGenerators);
        return gens;
    }

    private List<TestElement> topoOrder() {
        int total = model.producers.size() + model.tests.size()
                + model.postRuns.size();

        BitSet[] parents = bitsetArray(total);
        BitSet[] kids = bitsetArray(total);
        Map<Integer, TestElement> elementById = new HashMap<>();

        model.producers.forEach(testFixture -> {
            elementById.put(testFixture.id(), testFixture);
            for (TypeMirror mir : testFixture.requires()) {
                TestElement origin = model.producerOf(mir);
                if (origin != testFixture) {
                    parents[testFixture.id()].set(origin.id());
                    kids[origin.id()].set(testFixture.id());
                }
            }
        });

        model.tests.forEach(test -> {
            elementById.put(test.id(), test);
            for (TypeMirror mir : test.requires()) {
                TestElement origin = model.producerOf(mir);
                if (origin != test) {
                    parents[test.id()].set(origin.id());
                    kids[origin.id()].set(test.id());
                }
            }
        });

        model.postRuns.forEach(pr -> {
            elementById.put(pr.id(), pr);
            for (TypeMirror mir : pr.requires()) {
                TestElement origin = model.producerOf(mir);
                parents[pr.id()].set(origin.id());
                kids[origin.id()].set(pr.id());
            }
        });

        IntGraph ig = IntGraph.create(kids, parents);
        ObjectGraph<TestElement> og = ig.toObjectGraph(new MapIndexRes(elementById));

        Set<ObjectPath<TestElement>> paths = new HashSet<>();
        for (TestElement el : elementById.values()) {
            if (og.closureOf(el).contains(el)) {
                List<ObjectPath<TestElement>> path = og.pathsBetween(el, el);
                if (path != null) {
                    paths.addAll(path);
                }
            }
        }
        if (!paths.isEmpty()) {
            StringBuilder sb = new StringBuilder("Dependency graph contains cycles:");
            Set<Element> found = new HashSet<>();
            paths.forEach(p -> {
                if (p.size() == 2 && (p.first().equals(p.last()))) {
                    return;
                }
                sb.append('\n').append(p);
                p.forEach(el -> {
                    el.collectElements(found);
                });
            });
            for (Element e : found) {
                utils.fail(sb.toString(), e);
            }
        }

        Set<TestElement> elements = new HashSet<>(elementById.values());
        return og.topologicalSort(elements);
    }

    static class MapIndexRes implements IndexedResolvable<TestElement> {

        private final Map<Integer, TestElement> map;

        public MapIndexRes(Map<Integer, TestElement> map) {
            this.map = map;
        }

        @Override
        public TestElement forIndex(int index) {
            return map.get(index);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public int indexOf(Object obj) {
            return ((TestElement) obj).id();
        }
    }

    class PostRunCodeGenerator implements CodeGenerator {

        final PostRun pr;

        public PostRunCodeGenerator(PostRun pr) {
            this.pr = pr;
        }

        @Override
        public ItemKind kind() {
            return pr.kind();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + pr + ")";
        }

        @Override
        public void generateCreationCode(BlockBuilder<?> bb, Set<TypeMirror> generated) {
            if (!generated.contains(pr.type.asType())) {
                generated.add(pr.type.asType());
                bb.assign(model.fieldNameForType(pr.type.asType()))
                        .toNewInstance(nb -> {
                            pr.orderedConstructorArguments().forEach(argType -> {
                                nb.withArgument(model.fieldNameForType(argType));
                            });
                            nb.ofType(typeNameFor(pr.type.asType()));
                        });
            }
        }

        @Override
        public void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated) {
            if (!generated.contains(pr.type.asType())) {
                generated.add(pr.type.asType());
                cb.field(model.fieldNameForType(pr.type.asType()))
                        .withModifier(FINAL, PRIVATE)
                        .ofType(typeNameFor(pr.type.asType()));
            }
        }

        @Override
        public void generateShutdownCode(Consumer<InvocationTarget> c) {
            String nm = pr.type.getQualifiedName() + "." + pr.exe.getSimpleName();
            c.accept(InvocationTarget.of(nm, bb -> {
                bb.invoke(pr.exe.getSimpleName().toString(), ib -> {
                    pr.orderedMethodArguments(pr.exe).forEach(argType -> {
                        ib.withArgument(model.fieldNameForType(argType));
                    });
                    ib.onField(model.fieldNameForType(pr.type.asType())).ofThis();
                });
            }));
        }

        @Override
        public void collectElements(Set<Element> elements) {
            elements.add(pr.exe);
            elements.add(pr.type);
        }

        @Override
        public boolean isInvokable() {
            return true;
        }
    }

    class ArgumentsFixtureGenerator implements CodeGenerator {

        TypeElement argsElement = utils.processingEnv().getElementUtils().getTypeElement(ARGUMENTS_TYPE);

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + ARGUMENTS_TYPE + ")";
        }

        @Override
        public ItemKind kind() {
            return ItemKind.CLASS;
        }

        @Override
        public void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated) {
            if (generated.add(argsElement.asType())) {
                String fname = model.fieldNameForType(argsElement.asType());
                cb.importing(ARGUMENTS_TYPE);
                cb.field(fname).withModifier(STATIC, PRIVATE)
                        .ofType(simpleName(ARGUMENTS_TYPE));
            }
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, T>> void generateMainMethodCode(B bb) {
            String fname = model.fieldNameForType(argsElement.asType());
            bb.assign(fname).toInvocation("create").withArgument("args").on(simpleName(ARGUMENTS_TYPE));
        }

        @Override
        public void collectElements(Set<Element> elements) {
            elements.add(argsElement);
        }

        @Override
        public void generateCreationCode(BlockBuilder<?> bb, Set<TypeMirror> generated) {
        }

        @Override
        public boolean isFixture() {
            return true;
        }
    }

    class ClassTestFixtureGenerator implements CodeGenerator {

        final ClassTestFixture fixture;

        public ClassTestFixtureGenerator(ClassTestFixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public ItemKind kind() {
            return fixture.kind();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + fixture + ")";
        }

        @Override
        public void collectElements(Set<Element> elements) {
            elements.add(fixture.type);
        }

        @Override
        public void generateCreationCode(BlockBuilder<?> bb, Set<TypeMirror> generated) {
            TypeMirror type = fixture.type.asType();
            if (generated.add(type)) {
                bb.assign(model.fieldNameForType(type)).toNewInstance(nb -> {
                    fixture.orderedConstructionArguments().forEach(mir -> {
                        nb.withArgument(model.fieldNameForType(mir));
                    });
                    nb.ofType(typeNameFor(type));
                });
            }
        }

        @Override
        public void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated) {
            fixture.visitGeneratableFields((name, type) -> {
                if (generated.add(type)) {
                    ClassBuilder.FieldBuilder<?> fd = cb.field(name);
                    fd.withModifier(Modifier.FINAL, Modifier.PRIVATE)
                            .ofType(typeNameFor(type));

                    utils.warn("GEN FIELD " + this + " -> " + fd);
                } else {
                    utils.warn("ALREAEDY DID " + this);
                }
            });
        }

        @Override
        public void generateShutdownCode(Consumer<InvocationTarget> c) {

            if (utils.isAssignable(fixture.type.asType(), "java.io.Closeable")) {
                String nm = fixture.type.asType().toString() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + fixture.type.asType() + " as Closeable");
                    bb.invoke("close").on(model.fieldNameForType(fixture.type.asType()));
                }));
            } else if (utils.isAssignable(fixture.type.asType(), "java.lang.AutoCloseable")) {
                String nm = fixture.type.asType().toString() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + fixture.type.asType() + " as AutoCloseable");
                    bb.invoke("close").on(model.fieldNameForType(fixture.type.asType()));
                }));
            } else {
                ExecutableElement shutdownMethod = shutdownMethod(fixture.type);
                if (shutdownMethod != null) {
                    String nm = fixture.type.asType().toString() + ".shutdown";
                    c.accept(InvocationTarget.of(nm, bb -> {
                        bb.lineComment("invoke shutdown() on " + fixture.type.asType());
                        bb.invoke("shutdown", ib -> {
                            for (VariableElement ve : shutdownMethod.getParameters()) {
                                ib.withArgument(model.fieldNameForType(ve.asType()));
                            }
                            ib.onField(model.fieldNameForType(fixture.type.asType()))
                                    .ofThis();
                        });
                    }));
                }
            }
        }
    }

    class MethodTestFixtureGenerator implements CodeGenerator {

        final MethodTestFixture fixture;

        public MethodTestFixtureGenerator(MethodTestFixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public ItemKind kind() {
            return fixture.kind();
        }

        @Override
        public void collectElements(Set<Element> elements) {
            elements.add(fixture.type);
            elements.add(fixture.element);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + fixture + ")";
        }

        @Override
        public void generateCreationCode(BlockBuilder<?> bb, Set<TypeMirror> generated) {
            if (generated.add(fixture.type.asType()) && !fixture.element.getModifiers().contains(Modifier.STATIC)) {
                bb.assign(model.fieldNameForType(fixture.type.asType()))
                        .toNewInstance(nb -> {
                            fixture.orderedConstructionArguments().forEach(mir -> {
                                nb.withArgument(model.fieldNameForType(mir));
                            });
                            nb.ofType(typeNameFor(fixture.type.asType()));
                        });
            }
            if (generated.add(fixture.element.getReturnType())) {
                ClassBuilder.InvocationBuilder<?> inv = bb.assign(
                        model.fieldNameForType(fixture.element.getReturnType()))
                        .toInvocation(fixture.element.getSimpleName().toString());
                fixture.orderedMethodArguments().forEach(arg -> {
                    inv.withArgument(model.fieldNameForType(arg));
                });
                if (!fixture.element.getModifiers().contains(Modifier.STATIC)) {
                    inv.on(model.fieldNameForType(fixture.type.asType()));
                } else {
                    inv.on(typeNameFor(fixture.type.asType()));
                }
            }
        }

        @Override
        public void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated) {
            if (generated.add(fixture.type.asType()) && !fixture.element.getModifiers().contains(Modifier.STATIC)) {
                cb.field(model.fieldNameForType(fixture.type.asType()))
                        .withModifier(PRIVATE, FINAL)
                        .ofType(typeNameFor(fixture.type.asType()));
            }

            if (generated.add(fixture.element.getReturnType())) {
                cb.field(model.fieldNameForType(fixture.element.getReturnType()))
                        .withModifier(PRIVATE, FINAL)
                        .ofType(typeNameFor(fixture.element.getReturnType()));
            }
        }

        @Override
        public void generateShutdownCode(Consumer<InvocationTarget> c) {
            if (utils.isAssignable(fixture.element.getReturnType(), "java.io.Closeable")) {
                String nm = fixture.element.getReturnType() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + fixture.element.getReturnType() + " as Closeable");
                    bb.invoke("close").on(model.fieldNameForType(fixture.element.getReturnType()));
                }));
            } else if (utils.isAssignable(fixture.element.getReturnType(), "java.lang.AutoCloseable")) {
                String nm = fixture.element.getReturnType() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + fixture.element.getReturnType() + " as AutoCloseable");
                    bb.invoke("close").on(model.fieldNameForType(fixture.element.getReturnType()));
                }));
            } else {
                ExecutableElement shutdownMethod = shutdownMethod(fixture.element.getReturnType());
                if (shutdownMethod != null) {
                    String nm = fixture.element.getReturnType() + ".shutdown";
                    c.accept(InvocationTarget.of(nm, bb -> {
                        bb.lineComment("invoke shutdown() on " + fixture.element.getReturnType());
                        bb.invoke("shutdown", ib -> {
                            for (VariableElement ve : shutdownMethod.getParameters()) {
                                ib.withArgument(model.fieldNameForType(ve.asType()));
                            }
                            ib.onField(model.fieldNameForType(fixture.element.getReturnType()))
                                    .ofThis();
                        });
                    }));
                }
            }
            if (utils.isAssignable(fixture.type.asType(), "java.io.Closeable")) {
                String nm = fixture.element.getReturnType() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + fixture.type.asType() + " as Closeable");
                    bb.invoke("close").on(model.fieldNameForType(fixture.type.asType()));
                }));
            } else if (utils.isAssignable(fixture.type.asType(), "java.lang.AutoCloseable")) {
                String nm = fixture.element.getReturnType() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + fixture.type.asType() + " as AutoCloseable");
                    bb.invoke("close").on(model.fieldNameForType(fixture.type.asType()));
                }));
            } else {
                ExecutableElement shutdownMethod = shutdownMethod(fixture.type);
                if (shutdownMethod != null) {
                    String nm = fixture.element.getReturnType() + ".shutdown";
                    c.accept(InvocationTarget.of(nm, bb -> {
                        bb.lineComment("invoke shutdown() on " + fixture.type.asType());
                        bb.invoke("shutdown", ib -> {
                            for (VariableElement ve : shutdownMethod.getParameters()) {
                                ib.withArgument(model.fieldNameForType(ve.asType()));
                            }
                            ib.onField(model.fieldNameForType(fixture.type.asType()))
                                    .ofThis();
                        });
                    }));
                }
            }
        }
    }

    private ExecutableElement shutdownMethod(TypeMirror type) {
        TypeElement te = utils.processingEnv().getElementUtils()
                .getTypeElement(utils.erasureOf(type).toString());
        if (te != null) {
            return shutdownMethod(te);
        }
        return null;
    }

    private ExecutableElement shutdownMethod(TypeElement type) {
        for (Element el : type.getEnclosedElements()) {
            if (el.getKind() == ElementKind.METHOD) {
                if ("shutdown".equals(el.getSimpleName().toString())) {
                    return (ExecutableElement) el;
                }
            }
        }
        return null;
    }

    class ClassTestGenerator implements CodeGenerator {

        final ClassTestable test;

        public ClassTestGenerator(ClassTestable test) {
            this.test = test;
        }

        @Override
        public ItemKind kind() {
            return test.kind();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + test + ")";
        }

        @Override
        public boolean isInvokable() {
            return true;
        }

        @Override
        public void collectElements(Set<Element> elements) {
            elements.add(test.type);
        }

        @Override
        public void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated) {
            if (generated.add(test.type.asType())) {
                cb.field(model.fieldNameForType(test.type.asType()))
                        .withModifier(PRIVATE, FINAL)
                        .ofType(typeNameFor(test.type.asType()));
            }
        }

        @Override
        public void generateCreationCode(BlockBuilder<?> bb, Set<TypeMirror> generated) {
            if (generated.add(test.type.asType())) {
                bb.assign(model.fieldNameForType(test.type.asType()))
                        .toNewInstance(nb -> {
                            test.orderedConstructionArguments().forEach(argType -> {
                                nb.withArgument(model.fieldNameForType(argType));
                            });
                            nb.ofType(typeNameFor(test.type.asType()));
                        });
                bb.lineComment("done");
            }
        }

        @Override
        public void generateInvocationCode(Consumer<InvocationTarget> c) {
            String fld = model.fieldNameForType(test.type.asType());
            for (Element el : test.type.getEnclosedElements()) {
                if (el.getKind() == ElementKind.METHOD) {
                    ExecutableElement ex = (ExecutableElement) el;
                    if (ex.getModifiers().contains(Modifier.PUBLIC)) {
                        String nm = decapitalize(this.test.type.getSimpleName())
                                + "_" + ex.getSimpleName();
                        c.accept(InvocationTarget.of(nm, bb -> {
                            bb.lineComment("Generate for " + ex.getSimpleName());
                            bb.invoke(ex.getSimpleName().toString(), ib -> {
                                for (VariableElement ve : ex.getParameters()) {
                                    String fn = model.fieldNameForType(ve.asType());
                                    ib.withArgument(fn);
                                }
                                ib.on(fld);
                            });
                        }));
                    }
                }
            }
        }

        @Override
        public void generateShutdownCode(Consumer<InvocationTarget> c) {
            if (utils.isAssignable(test.type.asType(), "java.io.Closeable")) {
                String nm = test.type.asType() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + test.type.asType() + " as Closeable");
                    bb.invoke("close").on(model.fieldNameForType(test.type.asType()));
                }));
            } else if (utils.isAssignable(test.type.asType(), "java.io.Closeable")) {
                String nm = test.type.asType() + ".close";
                c.accept(InvocationTarget.of(nm, bb -> {
                    bb.lineComment("invoke close() on " + test.type.asType() + " as AutoCloseable");
                    bb.invoke("close").on(model.fieldNameForType(test.type.asType()));
                }));
            } else {
                ExecutableElement shutdownMethod = shutdownMethod(test.type);
                if (shutdownMethod != null) {
                    String nm = test.type.asType() + ".shutdown";
                    c.accept(InvocationTarget.of(nm, bb -> {
                        bb.lineComment("invoke shutdown() on " + test.type.asType());
                        bb.invoke("shutdown", ib -> {
                            for (VariableElement ve : shutdownMethod.getParameters()) {
                                ib.withArgument(model.fieldNameForType(ve.asType()));
                            }
                            ib.onField(model.fieldNameForType(test.type.asType())).ofThis();
                        });
                    }));
                }
            }
        }
    }

    class MethodTestGenerator implements CodeGenerator {

        final MethodTestable test;

        public MethodTestGenerator(Model.MethodTestable test) {
            this.test = test;
        }

        @Override
        public ItemKind kind() {
            return test.kind();
        }

        @Override
        public void collectElements(Set<Element> elements) {
            elements.add(test.type);
            elements.add(test.element);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + test + ")";
        }

        @Override
        public void generateFields(ClassBuilder<?> cb, Set<TypeMirror> generated) {
            if (!generated.contains(test.type.asType()) && !test.element.getModifiers().contains(Modifier.STATIC)) {
                generated.add(test.type.asType());
                cb.field(model.fieldNameForType(test.type.asType()))
                        .withModifier(PRIVATE, FINAL)
                        .ofType(typeNameFor(test.type.asType()));
            }
        }

        @Override
        public void generateCreationCode(BlockBuilder<?> bb, Set<TypeMirror> generated) {
            if (!generated.contains(test.type.asType()) && !test.element.getModifiers().contains(Modifier.STATIC)) {
                generated.add(test.type.asType());
                bb.assign(model.fieldNameForType(test.type.asType()))
                        .toNewInstance(nb -> {
                            test.orderedConstructionArguments().forEach(argType -> {
                                nb.withArgument(model.fieldNameForType(argType));
                            });
                            nb.ofType(typeNameFor(test.type.asType()));
                        });
            }
        }

        @Override
        public void generateInvocationCode(Consumer<InvocationTarget> c) {
            String nm = decapitalize(test.type.getSimpleName()) + "_" + test.element.getSimpleName();
            c.accept(InvocationTarget.of(nm, bb -> {
                bb.invoke(test.element.getSimpleName().toString(), ib -> {
                    test.orderedMethodArguments().forEach(arg -> {
                        ib.withArgument(model.fieldNameForType(arg));
                    });
                    if (test.element.getModifiers().contains(Modifier.STATIC)) {
                        ib.on(typeNameFor(test.type.asType()));
                    } else {
                        ib.onField(model.fieldNameForType(test.type.asType())).ofThis();
                    }
                });
            }));
        }
    }

    private static String decapitalize(Object what) {
        char[] c = what.toString().toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }
}
