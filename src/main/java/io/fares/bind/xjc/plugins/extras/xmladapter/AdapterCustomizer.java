/*
 * Copyright 2019 Niels Bertram
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fares.bind.xjc.plugins.extras.xmladapter;

import com.sun.tools.xjc.model.*;
import com.sun.tools.xjc.model.nav.NType;
import io.fares.bind.xjc.plugins.extras.Utils;
import org.w3c.dom.Element;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Stack;

import static io.fares.bind.xjc.plugins.extras.SecureLoader.getContextClassLoader;
import static io.fares.bind.xjc.plugins.extras.Utils.findCustomizations;
import static io.fares.bind.xjc.plugins.extras.xmladapter.AdapterPlugin.*;
import static java.util.Optional.ofNullable;

public class AdapterCustomizer implements CPropertyVisitor<CPropertyInfo> {

  @SuppressWarnings("unused") // for later
  private final Model model;

  @SuppressWarnings("unused") /// for reconciliation of actually applied customisations
  private final AdapterInspector inspector;

  AdapterCustomizer(final Model model, final AdapterInspector inspector) {
    this.model = model;
    this.inspector = inspector;
  }

  @Override
  public CPropertyInfo onElement(final CElementPropertyInfo propertyInfo) {
    findXmlAdapter(propertyInfo).ifPresent(adapter -> setElementAdapter(propertyInfo, adapter));
    return null;
  }

  @Override
  public CPropertyInfo onAttribute(final CAttributePropertyInfo propertyInfo) {
    findXmlAdapter(propertyInfo).ifPresent(adapter -> setAttributeAdapter(propertyInfo, adapter));
    return null;
  }

  private Optional<CAdapter> findXmlAdapter(CPropertyInfo propertyInfo) {

    Stack<CPluginCustomization> customizations = new Stack<>();

    findCustomizations(propertyInfo, COMPLEX_XML_ADAPTER_NAME).forEach(customizations::push);

    for (CTypeInfo ref : propertyInfo.ref()) {
      findCustomizations(ref, COMPLEX_XML_ADAPTER_NAME).forEach(customizations::push);
    }

    if (customizations.empty()) {
      return Optional.empty();
    } else {
      // top most trumps all others

      // property customizations trump type ones

      // get the adapter class
      Element ce = customizations.pop().element;

      String adapterClassName = ofNullable(ce.getAttribute("name"))
        .map(String::trim)
        .filter(Utils::isNotEmpty)
        .orElseThrow(
          () -> new IllegalArgumentException(COMPLEX_XML_ADAPTER_NAME.toString() + " must specify the XML adapter class with the name attribute")
        );

      // FIXME is there a way to lazy load?
      //  if we have an adapter that adapts to types generated by this compilation, the adapter class itself does not yet exist
      Class<XmlAdapter> adapterClass = loadAdapterClass(adapterClassName);
      // tried this but a com.sun.tools.xjc.model.nav.NavigatorImpl.getTypeArgument() would not return
      // appropriate type information and the setup of defaultType and customType in
      // com.sun.xml.bind.v2.model.core.Adapter.Adapter(ClassDeclT, com.sun.xml.bind.v2.model.nav.Navigator<TypeT,ClassDeclT,?,?>)
      // would default to java.lang.Object.
      // JClass adapterClass = model.codeModel.ref(adapter);
      return Optional.of(new CAdapter(adapterClass, false));

    }

  }

  @SuppressWarnings("unchecked")
  private Class<XmlAdapter> loadAdapterClass(String adapterClassName) {

    try {

      Class<?> adapterClass = getContextClassLoader().loadClass(adapterClassName);

      if (XmlAdapter.class.isAssignableFrom(adapterClass)) {
        return (Class<XmlAdapter>) adapterClass;
      } else {
        throw new IllegalArgumentException("adapter class " + adapterClassName + " does not extend jakarta.xml.bind.annotation.adapters.XmlAdapter");
      }

    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("failed to process customization " + COMPLEX_XML_ADAPTER_NAME.toString(), e);
    }

  }

  // region internals to hack the types

  private void setElementAdapter(final CElementPropertyInfo propertyInfo, CAdapter adapter) {

    String adaptedClassName = adapter.defaultType.fullName();

    //  test that adapter.defaultType matches the type definition or ref on the field

    for (CTypeInfo refType : propertyInfo.ref()) {

      // for some reason adapter.defaultType != refType.getType() even though they are
      if (adaptedClassName.equals(refType.getType().fullName())) {
        logReport("   [+]: modify {} element with {} {}", propertyInfo.displayName(), COMPLEX_XML_ADAPTER, adapter.adapterType.fullName());
        if (propertyInfo.getAdapter() == null) {
          propertyInfo.setAdapter(adapter);
        } else {
          // there is a useless assert check in CElementPropertyInfo that will prevent overriding an existing adapter
          forceTypeField(propertyInfo, "adapter", adapter);
        }
      } else if (refType instanceof CClassInfo) {
        CClassInfo classInfo = (CClassInfo) refType;
        for (CPropertyInfo classPropertyInfo : classInfo.getProperties()) {
          if (classPropertyInfo instanceof CValuePropertyInfo) {
            CValuePropertyInfo valuePropertyInfo = (CValuePropertyInfo) classPropertyInfo;
            NType propertyValueType = valuePropertyInfo.getTarget().getType();
            // if the value property can be adapted modify its type rather than modify the element
            if (adaptedClassName.equals(propertyValueType.fullName())) {
              // FIXME modify text
              logReport("   [+]: modify {} type extension with {} {}", propertyInfo.displayName(), COMPLEX_XML_ADAPTER, adapter.adapterType.fullName());
              setValuePropertyAdapter(valuePropertyInfo, adapter);
            }
          }
        }
      } else {
        logError("    [!]: {} was not attached to {}", COMPLEX_XML_ADAPTER, propertyInfo.displayName());
      }
    }

  }

  private void setAttributeAdapter(final CAttributePropertyInfo propertyInfo, CAdapter adapter) {
    logReport("   [+]: modify {} attribute with {} {}", propertyInfo.displayName(), COMPLEX_XML_ADAPTER, adapter.adapterType.fullName());
    TypeUse adaptedType = TypeUseFactory.adapt(propertyInfo.getTarget(), adapter);
    forceTypeField(propertyInfo, "type", adaptedType);
  }

  private void setValuePropertyAdapter(final CValuePropertyInfo propertyInfo, CAdapter adapter) {
    TypeUse adaptedType = TypeUseFactory.adapt(propertyInfo.getTarget(), adapter);
    forceTypeField(propertyInfo, "type", adaptedType);
  }

  /**
   * Forces a value into a field using reflection
   *
   * @param propertyInfo the object to modify
   * @param name         the name of the field
   * @param value        the value to set
   */
  private void forceTypeField(final CPropertyInfo propertyInfo, String name, Object value) {
    try {
      Field typeField = findField(propertyInfo.getClass(), name);
      typeField.setAccessible(true);
      typeField.set(propertyInfo, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("failed to apply " + COMPLEX_XML_ADAPTER_NAME + " to property " + propertyInfo.displayName(), e);
    }
  }

  private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
    try {
      return type.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      if (type.getSuperclass() == null) {
        throw e;
      } else {
        return findField(type.getSuperclass(), fieldName);
      }
    }
  }

  // endregion

  // region not used

  @Override
  public CPropertyInfo onValue(CValuePropertyInfo propertyInfo) {
    return null;
  }

  @Override
  public CPropertyInfo onReference(CReferencePropertyInfo propertyInfo) {
    return null;
  }

  // endregion

}
