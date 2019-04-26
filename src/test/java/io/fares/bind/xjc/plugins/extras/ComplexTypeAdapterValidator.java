/*
 * Copyright 2019 Niels Bertram
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fares.bind.xjc.plugins.extras;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

public class ComplexTypeAdapterValidator extends TestValidator {

  @Override
  public void visit(ClassOrInterfaceDeclaration n, Void arg) {

    super.visit(n, arg);

    FieldDeclaration price = n.getFieldByName("price")
      .orElseThrow(() -> new IllegalArgumentException("'field price was not found in book"));

    AnnotationExpr xmlAdapterAnnotation = price.getAnnotationByClass(XmlJavaTypeAdapter.class)
      .orElseThrow(() -> new IllegalArgumentException("'XmlJavaTypeAdapter wad not added to field price in book"));

    found = true;

  }

}
