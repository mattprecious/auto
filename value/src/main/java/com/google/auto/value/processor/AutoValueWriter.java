package com.google.auto.value.processor;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.Types;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Generated;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor7;

import static com.google.auto.value.processor.AutoValueProcessor.Property;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

public class AutoValueWriter {
  private static final WildcardType ANY = Types.subtypeOf(Object.class); // <?>.

  private final String packageName;
  private final String className;
  private final List<Property> properties;
  private final ClassName rawSupertype;
  private final Type supertype;
  private final Type superTypeWithWildCards;
  private final List<TypeVariable<?>> typeVariables;
  private final boolean emitToString;
  private final boolean emitEquals;
  private final boolean emitHashCode;
  private final String serialVersionUID;

  public AutoValueWriter(String packageName, TypeElement superType, String className,
      List<Property> properties, AutoValueTemplateVars vars) {
    this.packageName = packageName;
    this.className = className;
    this.properties = properties;
    this.rawSupertype = ClassName.get(superType);
    this.emitToString = vars.toString;
    this.emitEquals = vars.equals;
    this.emitHashCode = vars.hashCode;
    this.serialVersionUID = vars.serialVersionUID;

    List<? extends TypeParameterElement> typeParameters = superType.getTypeParameters();
    if (typeParameters.isEmpty()) {
      this.typeVariables = ImmutableList.of();
      this.supertype = this.rawSupertype;
      this.superTypeWithWildCards = this.rawSupertype;
    } else {
      ImmutableList.Builder<TypeVariable<?>> typeVariablesBuilder = ImmutableList.builder();
      for (TypeParameterElement typeParameter : typeParameters) {
        typeVariablesBuilder.add((TypeVariable<?>) Types.get(typeParameter.asType()));
      }

      this.typeVariables = typeVariablesBuilder.build();
      this.supertype =
          Types.parameterizedType(rawSupertype, (List) typeVariables); // TODO: Nuke cast.
      this.superTypeWithWildCards = Types.parameterizedType(rawSupertype,
          Collections.<Type>nCopies(typeParameters.size(), ANY));
    }
  }


  public String write() {
    TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className) //
        .addAnnotation(AnnotationSpec.builder(Generated.class) //
            .addMember("value", "$S", AutoValueProcessor.class.getName()) //
            .build()) //
        .superclass(supertype)
        .addModifiers(FINAL);

    for (Type typeVariable : typeVariables) {
      classBuilder.addTypeVariable((TypeVariable<?>) typeVariable);
    }

    if (serialVersionUID != null && !serialVersionUID.isEmpty()) {
      classBuilder.addField(String.class, "serialVersionUID", PRIVATE, STATIC);
    }

    if (properties != null) {
      for (Property property : properties) {
        classBuilder.addField(fieldSpec(property));
        classBuilder.addMethod(getterSpec(property));
      }

      classBuilder.addMethod(constructorSpec());
      if (emitToString) classBuilder.addMethod(toStringSpec());
      if (emitEquals) classBuilder.addMethod(equalsSpec());
      if (emitHashCode) classBuilder.addMethod(hashCodeSpec());
    }

    return JavaFile.builder(packageName, classBuilder.build()).build().toString();
  }

  public FieldSpec fieldSpec(Property property) {
    TypeMirror propertyType = property.getTypeMirror();
    return FieldSpec.builder(Types.get(propertyType), property.toString(), PRIVATE, FINAL).build();
  }

  public MethodSpec getterSpec(Property property) {
    String fieldName = property.toString();

    MethodSpec.Builder builder = MethodSpec.methodBuilder(property.getGetter())
        .addAnnotation(Override.class)
        .returns(Types.get(property.getTypeMirror()));

    for (AnnotationMirror annotationMirror : property.getAnnotations()) {
      builder.addAnnotation(createAnnotationSpec(annotationMirror));
    }

    if (property.getAccess() != null) {
      builder.addModifiers(property.getAccess());
    }

    if (property.getKind() == TypeKind.ARRAY) {
      if (property.isNullable()) {
        builder.addStatement("return $N == null ? null : $N.clone()", fieldName, fieldName);
      } else {
        builder.addStatement("return $N.clone()", fieldName);
      }
    } else {
      builder.addStatement("return $N", fieldName);
    }

    return builder.build();
  }

  public MethodSpec constructorSpec() {
    MethodSpec.Builder builder = MethodSpec.constructorBuilder();

    for (Property property : properties) {
      Type propertyType = Types.get(property.getTypeMirror());
      String propertyName = property.toString();

      builder.addParameter(propertyType, propertyName);

      if (!property.getKind().isPrimitive() && !property.isNullable()) {
        builder.beginControlFlow("if ($N == null)", propertyName)
            .addStatement("throw new $T(\"Null $N\")", NullPointerException.class,
                property.getName())
            .endControlFlow();
      }

      builder.addStatement("this.$N = $N", propertyName, propertyName);
    }

    return builder.build();
  }

  public MethodSpec toStringSpec() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("toString") //
        .returns(String.class) //
        .addAnnotation(Override.class) //
        .addModifiers(PUBLIC)
        .addCode("return \"$L{\"\n", rawSupertype.simpleName());

    for (int i = 0; i < properties.size(); i++) {
      Property property = properties.get(i);

      String fieldName = property.toString();
      builder.addCode("    + \"$N=\" + ", property.getName());

      if (property.getKind() == TypeKind.ARRAY) {
        builder.addCode("$T.toString($N)", Arrays.class, fieldName);
      } else {
        builder.addCode("$N", fieldName);
      }

      if (i < properties.size() - 1) builder.addCode(" + \", \"");
      builder.addCode("\n");
    }

    builder.addStatement("    + \"}\"");
    return builder.build();
  }

  public MethodSpec equalsSpec() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("equals")
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(boolean.class)
        .addParameter(Object.class, "o")
        .beginControlFlow("if (o == this)")
        .addStatement("return true")
        .endControlFlow()
        .beginControlFlow("if (o instanceof $T)", rawSupertype)
        .addStatement("$T that = ($T) o", superTypeWithWildCards, superTypeWithWildCards);

    if (!properties.isEmpty()) builder.addCode("return ");
    for (int i = 0; i < properties.size(); i++) {
      Property property = properties.get(i);
      if (i > 0) builder.addCode("    && ");
      builder.addCode("(");

      String fieldName = property.toString();
      String getterName = property.getGetter();
      switch (property.getKind()) {
        case FLOAT:
          builder.addCode("$T.floatToIntBits(this.$N) == $T.floatToIntBits(that.$N())",
              Float.class, fieldName, getterName);
          break;
        case DOUBLE:
          builder.addCode("$T.doubleToLongBits(this.$N) == $T.doubleToLongBits(that.$N())",
              Double.class, fieldName, getterName);
          break;
        case ARRAY:
          builder.addCode("$T.equals(this.$N, (that instanceof $L) ? (($L) that).$N : that.$N())",
              Arrays.class, fieldName, className, className, fieldName, getterName);
          break;
        default:
          if (property.getKind().isPrimitive()) {
            builder.addCode("this.$N == that.$N()", fieldName, getterName);
          } else if (property.isNullable()) {
            builder.addCode("(this.$N == null) ? (that.$N() == null) : this.$N.equals(that.$N())",
                fieldName, getterName, fieldName, getterName);
          } else {
            builder.addCode("this.$N.equals(that.$N())", fieldName, getterName);
          }
          break;
      }
      builder.addCode(")");

      if (i == properties.size() - 1) builder.addCode(";");
      builder.addCode("\n");
    }

    builder.endControlFlow().addStatement("return false");
    return builder.build();
  }

  private MethodSpec hashCodeSpec() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("hashCode")
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(int.class)
        .addStatement("int h = 1");

    for (Property property : properties) {
      String fieldName = property.toString();

      builder.addStatement("h *= 1000003")
          .addCode("h ^= ");

      switch (property.getKind()) {
        case BYTE:
        case SHORT:
        case CHAR:
        case INT:
          builder.addStatement("$N", fieldName);
          break;
        case LONG:
          builder.addStatement("($N >>> 32) ^ $N", fieldName, fieldName);
          break;
        case FLOAT:
          builder.addStatement("$T.floatToIntBits($N)", Float.class, fieldName);
          break;
        case DOUBLE:
          builder.addStatement("($T.doubleToLongBits($N) >>> 32) ^ $T.doubleToLongBits($N)",
              Double.class, fieldName, Double.class, fieldName);
          break;
        case BOOLEAN:
          builder.addStatement("$N ? 1231 : 1237", fieldName);
          break;
        case ARRAY:
          builder.addStatement("$T.hashCode($N)", Arrays.class, fieldName);
          break;
        default:
          if (property.isNullable()) {
            builder.addStatement("($N == null) ? 0 : $N.hashCode()", fieldName, fieldName);
          } else {
            builder.addStatement("$N.hashCode()", fieldName);
          }
          break;
      }
    }

    builder.addStatement("return h");
    return builder.build();
  }

  // TODO: Move this into JavaPoet.
  private AnnotationSpec createAnnotationSpec(AnnotationMirror annotationMirror) {
    Type annotationType = Types.get(annotationMirror.getAnnotationType());
    AnnotationSpec.Builder annotation = AnnotationSpec.builder(annotationType);

    Set<? extends Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> values =
        annotationMirror.getElementValues().entrySet();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> value : values) {
      fillAnnotationValues(annotation, value.getKey().getSimpleName().toString(), value.getValue());
    }
    return annotation.build();
  }

  // TODO: Move this into JavaPoet.
  private void fillAnnotationValues(final AnnotationSpec.Builder annotation, final String name,
      AnnotationValue value) {
    value.accept(new SimpleAnnotationValueVisitor7<Void, Void>() {

      @Override
      public Void visitBoolean(boolean b, Void p) {
        annotation.addMember(name, "$L", b);
        return defaultAction(b, p);
      }

      @Override
      public Void visitByte(byte b, Void p) {
        annotation.addMember(name, "$L", b);
        return defaultAction(b, p);
      }

      @Override
      public Void visitChar(char c, Void p) {
        annotation.addMember(name, "'$L'", c);
        return defaultAction(c, p);
      }

      @Override
      public Void visitDouble(double d, Void p) {
        annotation.addMember(name, "$L", d);
        return defaultAction(d, p);
      }

      @Override
      public Void visitFloat(float f, Void p) {
        annotation.addMember(name, "$LF", f);
        return defaultAction(f, p);
      }

      @Override
      public Void visitInt(int i, Void p) {
        annotation.addMember(name, "$L", i);
        return defaultAction(i, p);
      }

      @Override
      public Void visitLong(long i, Void p) {
        annotation.addMember(name, "$LL", i);
        return defaultAction(i, p);
      }

      @Override
      public Void visitShort(short s, Void p) {
        annotation.addMember(name, "$L", s);
        return defaultAction(s, p);
      }

      @Override
      public Void visitString(String s, Void p) {
        annotation.addMember(name, "$S", s);
        return defaultAction(s, p);
      }

      @Override
      public Void visitType(TypeMirror t, Void p) {
        annotation.addMember(name, "$T.class", Types.get(t));
        return defaultAction(t, p);
      }

      @Override
      public Void visitEnumConstant(VariableElement c, Void p) {
        annotation.addMember(name, "$T.$L", Types.get(c.asType()), c.getSimpleName());
        return defaultAction(c, p);
      }

      @Override
      public Void visitAnnotation(AnnotationMirror a, Void p) {
        annotation.addMember(name, "$L", createAnnotationSpec(a));
        return defaultAction(a, p);
      }

      @Override
      public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
        for (AnnotationValue v : vals) {
          fillAnnotationValues(annotation, name, v);
        }
        return defaultAction(vals, p);
      }
    }, null);
  }
}
