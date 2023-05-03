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
import static com.mastfrog.http.test.microframework.processor.HttpTestAnnotationProcessor.HTTP_TEST_ANNOTATION;
import static com.mastfrog.http.test.microframework.processor.HttpTestAnnotationProcessor.TEXT_FIXTURE_ANNOTATION;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import static javax.lang.model.SourceVersion.RELEASE_17;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 *
 * @author Tim Boudreau
 */
@SupportedAnnotationTypes({HTTP_TEST_ANNOTATION, TEXT_FIXTURE_ANNOTATION})
@SupportedSourceVersion(RELEASE_17)
public class HttpTestAnnotationProcessor extends AbstractProcessor {

    private AnnotationUtils utils;

    public static final String ANNOTATIONS_PACKAGE = "com.mastfrog.http.test.microframework";
    public static final String HTTP_TEST_ANNOTATION = ANNOTATIONS_PACKAGE + ".Invokable";
    public static final String TEXT_FIXTURE_ANNOTATION = ANNOTATIONS_PACKAGE + ".Fixture";
    public static final String POST_RUN_ANNOTATION = ANNOTATIONS_PACKAGE + ".PostRun";
    public static final String ARGUMENTS_TYPE = ANNOTATIONS_PACKAGE + ".Arguments";
    public static final String ON_ERROR_TYPE = ANNOTATIONS_PACKAGE + ".OnError";
    private Model model;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        utils = new AnnotationUtils(processingEnv, getSupportedAnnotationTypes(), HttpTestAnnotationProcessor.class);
        if (model == null) {
            model = new Model(utils);
        }
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        utils.findAnnotatedElements(roundEnv, HTTP_TEST_ANNOTATION)
                .forEach(element -> {
                    AnnotationMirror anno = utils.findAnnotationMirror(element,
                            HTTP_TEST_ANNOTATION);
                    switch (element.getKind()) {
                        case CLASS:
                            TypeElement type = (TypeElement) element;
                            onHttpTestAnnotatedClass(anno, type);
                            break;
                        case METHOD:
                            ExecutableElement exe = (ExecutableElement) element;
                            TypeElement te = owningTypeElement(exe, anno);
                            if (te != null) {
                                onHttpTestAnnotatedMethod(anno, te, exe);
                            }
                            break;
                        default:
                            utils.fail("@" + simpleName(HTTP_TEST_ANNOTATION)
                                    + " cannot be applied to a "
                                    + element.getKind(), element, anno);
                    }
                });
        utils.findAnnotatedElements(roundEnv, TEXT_FIXTURE_ANNOTATION)
                .forEach(element -> {
                    AnnotationMirror anno = utils.findAnnotationMirror(element,
                            TEXT_FIXTURE_ANNOTATION);
                    switch (element.getKind()) {
                        case CLASS:
                            TypeElement type = (TypeElement) element;
                            onTestFixtureAnnotatedClass(anno, type);
                            break;
                        case METHOD:
                            ExecutableElement exe = (ExecutableElement) element;
                            TypeElement te = owningTypeElement(exe, anno);
                            if (te != null) {
                                onTestFixtureAnnotatedMethod(anno, te, exe);
                            }
                            break;
                        default:
                            utils.fail("@" + simpleName(TEXT_FIXTURE_ANNOTATION)
                                    + " cannot be applied to a "
                                    + element.getKind(), element, anno);
                    }
                });

        utils.findAnnotatedElements(roundEnv, POST_RUN_ANNOTATION)
                .forEach(element -> {
                    AnnotationMirror anno = utils.findAnnotationMirror(element,
                            POST_RUN_ANNOTATION);
                    switch (element.getKind()) {
                        case METHOD:
                            ExecutableElement exe = (ExecutableElement) element;
                            TypeElement te = owningTypeElement(exe, anno);
                            if (te != null) {
                                onPostRun(anno, te, exe);
                            }
                            break;
                        default:
                            utils.fail("@" + simpleName(POST_RUN_ANNOTATION)
                                    + " cannot be applied to a "
                                    + element.getKind(), element, anno);
                    }
                });

        if (roundEnv.processingOver()) {
            if (!roundEnv.errorRaised()) {
                generate(roundEnv);
            }
            return true;
        }
        return false;
    }

    protected TypeElement owningTypeElement(Element el, AnnotationMirror mir) {
        Element orig = el;
        while (el != null && el.getKind() != ElementKind.CLASS) {
            el = el.getEnclosingElement();
        }
        if (el == null) {
            utils.fail("Not owned by a class.", orig, mir);
        }
        return (TypeElement) el;
    }

    private void generate(RoundEnvironment roundEnv) {
        model.generate(roundEnv);
    }

    private boolean onTestFixtureAnnotatedMethod(AnnotationMirror anno,
            TypeElement te, ExecutableElement exe) {
        return model.addTestFixtureMethod(anno, te, exe);
    }

    private boolean onTestFixtureAnnotatedClass(AnnotationMirror anno,
            TypeElement type) {
        return model.addTestFixtureClass(anno, type);
    }

    private boolean onHttpTestAnnotatedMethod(AnnotationMirror anno,
            TypeElement te, ExecutableElement exe) {
        return model.addTestMethod(anno, te, exe);
    }

    private boolean onHttpTestAnnotatedClass(AnnotationMirror anno,
            TypeElement type) {
        return model.addTestClass(anno, type);
    }

    private boolean onPostRun(AnnotationMirror anno, TypeElement te, ExecutableElement exe) {
        return model.addPostRun(anno, te, exe);
    }
}
