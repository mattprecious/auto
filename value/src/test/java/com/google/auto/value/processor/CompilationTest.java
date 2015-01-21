/*
 * Copyright (C) 2014 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.testing.compile.JavaFileObjects;

import junit.framework.TestCase;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class CompilationTest extends TestCase {
  public void testCompilation() {
    // Positive test case that ensures we generate the expected code for at least one case.
    // Most AutoValue code-generation tests are functional, meaning that we check that the generated
    // code does the right thing rather than checking what it looks like, but this test is a sanity
    // check that we are not generating correct but weird code.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int buh();",
        "",
        "  public static Baz create(int buh) {",
        "    return new AutoValue_Baz(buh);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import java.lang.Object;",
        "import java.lang.Override;",
        "import java.lang.String;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
        "final class AutoValue_Baz extends Baz {",
        "  private final int buh;",
        "",
        "  AutoValue_Baz(int buh) {",
        "    this.buh = buh;",
        "  }",
        "",
        "  @Override",
        "  public int buh() {",
        "    return buh;",
        "  }",
        "",
        "  @Override",
        "  public String toString() {",
        "    return \"Baz{\"",
        "        + \"buh=\" + buh",
        "        + \"}\";",
        "  }",
        "",
        "  @Override",
        "  public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof Baz) {",
        "      Baz that = (Baz) o;",
        "      return (this.buh == that.buh());",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override",
        "  public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= buh;",
        "    return h;",
        "  }",
        "}"
    );
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testImports() {
    // Test that referring to the same class in two different ways does not confuse the import logic
    // into thinking it is two different classes and that therefore it can't import. The code here
    // is nonsensical but successfully reproduces a real problem, which is that a TypeMirror that is
    // extracted using Elements.getTypeElement(name).asType() does not compare equal to one that is
    // extracted from ExecutableElement.getReturnType(), even though Types.isSameType considers them
    // equal. So unless we are careful, the java.util.Arrays that we import explicitly to use its
    // methods will appear different from the java.util.Arrays that is the return type of the
    // arrays() method here.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "import java.util.Arrays;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int[] ints();",
        "  public abstract Arrays arrays();",
        "",
        "  public static Baz create(int[] ints, Arrays arrays) {",
        "    return new AutoValue_Baz(ints, arrays);",
        "  }",
        "}");
    JavaFileObject expectedOutput = JavaFileObjects.forSourceLines(
        "foo.bar.AutoValue_Baz",
        "package foo.bar;",
        "",
        "import java.lang.NullPointerException;",
        "import java.lang.Object;",
        "import java.lang.Override;",
        "import java.lang.String;",
        "import java.util.Arrays;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"" + AutoValueProcessor.class.getName() + "\")",
        "final class AutoValue_Baz extends Baz {",
        "  private final int[] ints;",
        "  private final Arrays arrays;",
        "",
        "  AutoValue_Baz(int[] ints, Arrays arrays) {",
        "    if (ints == null) {",
        "      throw new NullPointerException(\"Null ints\");",
        "    }",
        "    this.ints = ints;",
        "    if (arrays == null) {",
        "      throw new NullPointerException(\"Null arrays\");",
        "    }",
        "    this.arrays = arrays;",
        "  }",
        "",
        "  @Override",
        "  public int[] ints() {",
        "    return ints.clone();",
        "  }",
        "",
        "  @Override",
        "  public Arrays arrays() {",
        "    return arrays;",
        "  }",
        "",
        "  @Override",
        "  public String toString() {",
        "    return \"Baz{\"",
        "        + \"ints=\" + Arrays.toString(ints) + \", \"",
        "        + \"arrays=\" + arrays",
        "        + \"}\";",
        "  }",
        "",
        "  @Override",
        "  public boolean equals(Object o) {",
        "    if (o == this) {",
        "      return true;",
        "    }",
        "    if (o instanceof Baz) {",
        "      Baz that = (Baz) o;",
        "      return (Arrays.equals(this.ints, (that instanceof AutoValue_Baz) "
                      + "? ((AutoValue_Baz) that).ints : that.ints()))",
        "          && (this.arrays.equals(that.arrays()));",
        "    }",
        "    return false;",
        "  }",
        "",
        "  @Override",
        "  public int hashCode() {",
        "    int h = 1;",
        "    h *= 1000003;",
        "    h ^= Arrays.hashCode(ints);",
        "    h *= 1000003;",
        "    h ^= arrays.hashCode();",
        "    return h;",
        "  }",
        "}"
    );
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedOutput);
  }

  public void testNoMultidimensionalPrimitiveArrays() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int[][] ints();",
        "",
        "  public static Baz create(int[][] ints) {",
        "    return new AutoValue_Baz(ints);",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue class cannot define an array-valued property "
            + "unless it is a primitive array")
        .in(javaFileObject).onLine(7);
  }

  public void testNoObjectArrays() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract String[] strings();",
        "",
        "  public static Baz create(String[] strings) {",
        "    return new AutoValue_Baz(strings);",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue class cannot define an array-valued property "
            + "unless it is a primitive array")
        .in(javaFileObject).onLine(7);
  }

  public void testAnnotationOnInterface() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public interface Baz {}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue only applies to classes")
        .in(javaFileObject).onLine(6);
  }

  public void testAnnotationOnEnum() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public enum Baz {}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("AutoValue only applies to classes")
        .in(javaFileObject).onLine(6);
  }

  public void testExtendAutoValue() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Outer",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "public class Outer {",
        "  @AutoValue",
        "  static abstract class Parent {",
        "    static Parent create(int randomProperty) {",
        "      return new AutoValue_Outer_Parent(randomProperty);",
        "    }",
        "",
        "    abstract int randomProperty();",
        "  }",
        "",
        "  @AutoValue",
        "  static abstract class Child extends Parent {",
        "    static Child create(int randomProperty) {",
        "      return new AutoValue_Outer_Child(randomProperty);",
        "    }",
        "",
        "    abstract int randomProperty();",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("may not extend")
        .in(javaFileObject).onLine(16);
  }

  public void testBogusSerialVersionUID() throws Exception {
    String[] mistakes = {
      "final long serialVersionUID = 1234L", // not static
      "static long serialVersionUID = 1234L", // not final
      "static final Long serialVersionUID = 1234L", // not long
      "static final long serialVersionUID = (Long) 1234L", // not a compile-time constant
    };
    for (String mistake : mistakes) {
      JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
          "foo.bar.Baz",
          "package foo.bar;",
          "",
          "import com.google.auto.value.AutoValue;",
          "",
          "@AutoValue",
          "public abstract class Baz implements java.io.Serializable {",
          "  " + mistake + ";",
          "",
          "  public abstract int foo();",
          "}");
      assert_().about(javaSource())
          .that(javaFileObject)
          .processedWith(new AutoValueProcessor())
          .failsToCompile()
          .withErrorContaining(
              "serialVersionUID must be a static final long compile-time constant")
          .in(javaFileObject).onLine(7);
    }
  }

  public void testNonExistentSuperclass() throws Exception {
    // The main purpose of this test is to check that AutoValueProcessor doesn't crash the
    // compiler in this case.
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Existent extends NonExistent {",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("NonExistent")
        .in(javaFileObject).onLine(6);
  }

  public void testCannotImplementAnnotation() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.RetentionImpl",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import java.lang.annotation.Retention;",
        "import java.lang.annotation.RetentionPolicy;",
        "",
        "@AutoValue",
        "public abstract class RetentionImpl implements Retention {",
        "  public static Retention create(RetentionPolicy policy) {",
        "    return new AutoValue_RetentionImpl(policy);",
        "  }",
        "",
        "  @Override public Class<? extends Retention> annotationType() {",
        "    return Retention.class;",
        "  }",
        "",
        "  @Override public boolean equals(Object o) {",
        "    return (o instanceof Retention && value().equals((Retention) o).value());",
        "  }",
        "",
        "  @Override public int hashCode() {",
        "    return (\"value\".hashCode() * 127) ^ value().hashCode();",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("may not be used to implement an annotation interface")
        .in(javaFileObject).onLine(8);
  }

  public void testMissingPropertyType() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract MissingType missingType();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(7);
  }

  public void testMissingGenericPropertyType() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract MissingType<?> missingType();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(7);
  }

  public void testMissingComplexGenericPropertyType() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "import java.util.Map;",
        "import java.util.Set;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract Map<Set<?>, MissingType<?>> missingType();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(10);
  }

  public void testMissingSuperclassGenericParameter() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz<T extends MissingType<?>> {",
        "  public abstract int foo();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("MissingType")
        .in(javaFileObject).onLine(6);
  }

  public void testGetFooIsFoo() throws Exception {
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  abstract int getFoo();",
        "  abstract boolean isFoo();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new AutoValueProcessor())
        .failsToCompile()
        .withErrorContaining("More than one @AutoValue property called foo")
        .in(javaFileObject).onLine(8);
  }

  private static class PoisonedAutoValueProcessor extends AutoValueProcessor {
    private final IllegalArgumentException filerException;

    PoisonedAutoValueProcessor(IllegalArgumentException filerException) {
      this.filerException = filerException;
    }

    private class ErrorInvocationHandler implements InvocationHandler {
      private final ProcessingEnvironment originalProcessingEnv;

      ErrorInvocationHandler(ProcessingEnvironment originalProcessingEnv) {
        this.originalProcessingEnv = originalProcessingEnv;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("getFiler")) {
          throw filerException;
        } else {
          return method.invoke(originalProcessingEnv, args);
        }
      }
    };

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      ProcessingEnvironment poisonedProcessingEnv = (ProcessingEnvironment) Proxy.newProxyInstance(
          getClass().getClassLoader(),
          new Class<?>[] {ProcessingEnvironment.class},
          new ErrorInvocationHandler(processingEnv));
      processingEnv = poisonedProcessingEnv;
      return super.process(annotations, roundEnv);
    }
  }

  public void testExceptionBecomesError() throws Exception {
    // Ensure that if the annotation processor code gets an unexpected exception, it is converted
    // into a compiler error rather than being propagated. Otherwise the output can be very
    // confusing to the user who stumbles into a bug that causes an exception, whether in
    // AutoValueProcessor or javac.
    // We inject an exception by subclassing AutoValueProcessor in order to poison its processingEnv
    // in a way that will cause an exception the first time it tries to get the Filer.
    IllegalArgumentException exception =
        new IllegalArgumentException("I don't understand the question, and I won't respond to it");
    JavaFileObject javaFileObject = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "public abstract class Baz {",
        "  public abstract int foo();",
        "}");
    assert_().about(javaSource())
        .that(javaFileObject)
        .processedWith(new PoisonedAutoValueProcessor(exception))
        .failsToCompile()
        .withErrorContaining(exception.toString())
        .in(javaFileObject).onLine(6);
  }
}
