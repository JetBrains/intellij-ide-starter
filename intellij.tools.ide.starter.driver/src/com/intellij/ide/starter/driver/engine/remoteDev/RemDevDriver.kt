package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.driver.client.impl.DriverImpl
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.remoteDev.BeControlAdapter
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.BeControlComponentBase
import java.lang.IllegalArgumentException
import kotlin.collections.any
import kotlin.collections.filterIsInstance
import kotlin.collections.firstOrNull
import kotlin.reflect.KClass

class RemDevDriver(host: JmxHost?) : DriverImpl(host, true) {
  override fun <T : Any> cast(instance: Any, clazz: KClass<T>): T {
    if (instance is BeControlComponentBase) {
      val builderClass = clazz.annotations
                           .filterIsInstance<BeControlClass>()
                           .firstOrNull()
                           ?.value ?: throw IllegalArgumentException("Class $clazz should be annotated with BeControlClass")

      val builder = builderClass.constructors.firstOrNull()?.call()
                    ?: throw IllegalArgumentException("Can not construct a class $builderClass")

      @Suppress("UNCHECKED_CAST")
      return builder.build(this, instance.frontendComponent, instance.backendComponent) as T
    }

    return super.cast(instance, clazz)
  }

  override fun <T : Any> new(clazz: KClass<T>, vararg args: Any?, rdTarget: RdTarget): T {
    val hasBeControlArg = args.any { it is BeControlComponentBase }
    val adapterClass = clazz.annotations
      .filterIsInstance<BeControlAdapter>()
      .firstOrNull()?.value

    if (hasBeControlArg) {
      if (adapterClass == null) {
        val beControlArg = args.firstOrNull { it is BeControlComponentBase }
        throw IllegalArgumentException("Instance of class $clazz can not be created, because one of its argument is BeControl ($beControlArg) and there no BeControlAdapter for it")
      }

      @Suppress("UNCHECKED_CAST")
      return adapterClass.constructors.firstOrNull()?.call(*args) as T
    }

    return super.new(clazz, *args, rdTarget = rdTarget)
  }
}