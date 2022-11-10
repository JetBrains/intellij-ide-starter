package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.utils.logError
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

object TestInstanceReflexer {
  fun getProperty(testInstance: Any, propertyType: KClass<*>): KProperty1<out Any, *>? {
    val properties = testInstance::class.memberProperties

    try {
      val contextField = properties.single { property ->
        if (property.javaField == null) false
        else property.javaField!!.type.equals(propertyType.javaObjectType)
      }
      return contextField
    }
    catch (t: Throwable) {
      logError("Unable to get property of type ${propertyType.simpleName} in ${testInstance::class.qualifiedName}")
    }

    return null
  }
}