package org.astralspun.eleutheriax.dexkit

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.jvm.JvmName

fun Class<*>.findMethod(init: DexMethodQuery.() -> Unit): Method =
    DexResolver.findMethodsInClasses(listOf(this)) {
        apply(init)
        findFirst = true
    }.first()

fun Class<*>.findMethodOrNull(init: DexMethodQuery.() -> Unit): Method? =
    DexResolver.findMethodsInClasses(listOf(this)) {
        apply(init)
        findFirst = true
    }.firstOrNull()

fun Class<*>.findMethods(init: DexMethodQuery.() -> Unit): DexResult<Method> =
    DexResolver.findMethodsInClasses(listOf(this), init)

fun Class<*>.findConstructor(init: DexMethodQuery.() -> Unit): Constructor<*> =
    DexResolver.findConstructorsInClasses(listOf(this)) {
        apply(init)
        findFirst = true
    }.first()

fun Class<*>.findConstructorOrNull(init: DexMethodQuery.() -> Unit): Constructor<*>? =
    DexResolver.findConstructorsInClasses(listOf(this)) {
        apply(init)
        findFirst = true
    }.firstOrNull()

fun Class<*>.findConstructors(init: DexMethodQuery.() -> Unit): DexResult<Constructor<*>> =
    DexResolver.findConstructorsInClasses(listOf(this), init)

fun Class<*>.findField(init: DexFieldQuery.() -> Unit): Field =
    DexResolver.findFieldsInClasses(listOf(this)) {
        apply(init)
        findFirst = true
    }.first()

fun Class<*>.findFieldOrNull(init: DexFieldQuery.() -> Unit): Field? =
    DexResolver.findFieldsInClasses(listOf(this)) {
        apply(init)
        findFirst = true
    }.firstOrNull()

fun Class<*>.findFields(init: DexFieldQuery.() -> Unit): DexResult<Field> =
    DexResolver.findFieldsInClasses(listOf(this), init)

fun DexResult<Class<*>>.findClass(init: DexClassQuery.() -> Unit): Class<*> =
    DexResolver.findClassesInClasses(all()) {
        apply(init)
        findFirst = true
    }.first()

@JvmName("findClassOrNullInClassResult")
fun DexResult<Class<*>>.findClassOrNull(init: DexClassQuery.() -> Unit): Class<*>? =
    DexResolver.findClassesInClasses(all()) {
        apply(init)
        findFirst = true
    }.firstOrNull()

@JvmName("findClassesInClassResult")
fun DexResult<Class<*>>.findClasses(init: DexClassQuery.() -> Unit): DexResult<Class<*>> =
    DexResolver.findClassesInClasses(all(), init)

@JvmName("findMethodInClassResult")
fun DexResult<Class<*>>.findMethod(init: DexMethodQuery.() -> Unit): Method =
    DexResolver.findMethodsInClasses(all()) {
        apply(init)
        findFirst = true
    }.first()

@JvmName("findMethodOrNullInClassResult")
fun DexResult<Class<*>>.findMethodOrNull(init: DexMethodQuery.() -> Unit): Method? =
    DexResolver.findMethodsInClasses(all()) {
        apply(init)
        findFirst = true
    }.firstOrNull()

@JvmName("findMethodsInClassResult")
fun DexResult<Class<*>>.findMethods(init: DexMethodQuery.() -> Unit): DexResult<Method> =
    DexResolver.findMethodsInClasses(all(), init)

@JvmName("findConstructorInClassResult")
fun DexResult<Class<*>>.findConstructor(init: DexMethodQuery.() -> Unit): Constructor<*> =
    DexResolver.findConstructorsInClasses(all()) {
        apply(init)
        findFirst = true
    }.first()

@JvmName("findConstructorOrNullInClassResult")
fun DexResult<Class<*>>.findConstructorOrNull(init: DexMethodQuery.() -> Unit): Constructor<*>? =
    DexResolver.findConstructorsInClasses(all()) {
        apply(init)
        findFirst = true
    }.firstOrNull()

@JvmName("findConstructorsInClassResult")
fun DexResult<Class<*>>.findConstructors(init: DexMethodQuery.() -> Unit): DexResult<Constructor<*>> =
    DexResolver.findConstructorsInClasses(all(), init)

@JvmName("findFieldInClassResult")
fun DexResult<Class<*>>.findField(init: DexFieldQuery.() -> Unit): Field =
    DexResolver.findFieldsInClasses(all()) {
        apply(init)
        findFirst = true
    }.first()

@JvmName("findFieldOrNullInClassResult")
fun DexResult<Class<*>>.findFieldOrNull(init: DexFieldQuery.() -> Unit): Field? =
    DexResolver.findFieldsInClasses(all()) {
        apply(init)
        findFirst = true
    }.firstOrNull()

@JvmName("findFieldsInClassResult")
fun DexResult<Class<*>>.findFields(init: DexFieldQuery.() -> Unit): DexResult<Field> =
    DexResolver.findFieldsInClasses(all(), init)

fun Method.findField(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit): Field =
    DexResolver.findFieldsInMethods(listOf(this), usingType) {
        apply(init)
        findFirst = true
    }.first()

fun Method.findFieldOrNull(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit): Field? =
    DexResolver.findFieldsInMethods(listOf(this), usingType) {
        apply(init)
        findFirst = true
    }.firstOrNull()

fun Method.findFields(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit): DexResult<Field> =
    DexResolver.findFieldsInMethods(listOf(this), usingType, init)

@JvmName("findFieldInMethodResult")
fun DexResult<Method>.findField(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit): Field =
    DexResolver.findFieldsInMethods(all(), usingType) {
        apply(init)
        findFirst = true
    }.first()

@JvmName("findFieldOrNullInMethodResult")
fun DexResult<Method>.findFieldOrNull(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit): Field? =
    DexResolver.findFieldsInMethods(all(), usingType) {
        apply(init)
        findFirst = true
    }.firstOrNull()

@JvmName("findFieldsInMethodResult")
fun DexResult<Method>.findFields(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit): DexResult<Field> =
    DexResolver.findFieldsInMethods(all(), usingType, init)
