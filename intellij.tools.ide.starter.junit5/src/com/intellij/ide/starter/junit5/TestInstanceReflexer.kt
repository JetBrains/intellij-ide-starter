package com.intellij.ide.starter.junit5

import com.intellij.tools.ide.util.common.logOutput
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.staticProperties
import kotlin.reflect.jvm.javaField

object TestInstanceReflexer {
  fun getProperty(testInstance: Any, propertyType: KClass<*>): KProperty<*>? {
    val properties: List<KProperty<*>> = testInstance::class.memberProperties.plus(testInstance::class.staticProperties)

    try {
      val contextField = properties.single { property ->
        if (property.javaField == null) false
        else property.javaField!!.type.equals(propertyType.javaObjectType)
      }
      return contextField
    }
    catch (t: Throwable) {
      logOutput("Unable to get property of type ${propertyType.simpleName} in ${testInstance::class.qualifiedName}")
    }

    return null
  }

  fun getPropertyIfPresent(testInstance: Any, propertyType: KClass<*>): KProperty<*>? {
    val properties = testInstance::class.memberProperties.plus(testInstance::class.staticProperties)

    return properties.firstOrNull { property ->
      property.javaField?.type?.equals(propertyType.javaObjectType) == true
    }
  }
}