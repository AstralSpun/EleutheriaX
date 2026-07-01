package org.astralspun.eleutheriax

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

object ReflectionUtils {

    private val classLoaderContext = ThreadLocal<ClassLoader?>()

    class Ignore private constructor()

    class ReflectionException(
        message: String,
        cause: Throwable? = null
    ) : RuntimeException(message, cause)

    class Result<T> internal constructor(
        private val sign: String,
        values: List<T>
    ) : AbstractList<T>() {
        private val values = values.onEach {
            if (it is AccessibleObject) it.isAccessible = true
        }

        override val size: Int get() = values.size

        fun all(): List<T> = values

        override operator fun get(index: Int): T {
            if (values.isEmpty()) throw ReflectionException("can not find $sign")
            if (index !in values.indices) {
                throw ReflectionException("The resulting number is ${values.size} and the index is $index: $sign")
            }
            return values[index]
        }

        fun first(): T = get(0)

        fun firstOrNull(): T? = values.firstOrNull()

        fun last(): T {
            if (values.isEmpty()) throw ReflectionException("can not find $sign")
            return values.last()
        }

        fun lastOrNull(): T? = values.lastOrNull()
    }

    class ClassFinder internal constructor() {
        var name: String? = null
        var loader: ClassLoader? = null

        private var searchSuperclasses: Boolean = false

        fun superClass() {
            searchSuperclasses = true
        }

        fun find(): Class<*> = findAll().first()

        fun findAll(): Result<Class<*>> {
            val source = findClass(name ?: throw ReflectionException("class name is null"), loader)
            return Result(
                "class:$name",
                if (searchSuperclasses) superclassesOf(source) else listOf(source)
            )
        }

        fun findOrNull(): Class<*>? = runCatching { find() }.getOrNull()
    }

    class MethodFinder internal constructor() {
        var declaredClass: Class<*>? = null
        var declaredClassName: String? = null
        var loader: ClassLoader? = null
        var methodName: String? = null
        var returnType: Class<*>? = null
        var parameterCount: Int? = null
        var matchParentClass: Boolean = false
        private var searchSuperclasses: Boolean = false

        private var parameterTypes: Array<out Class<*>>? = null

        fun from(target: Any) {
            declaredClass = target.javaClass
        }

        fun superClass() {
            searchSuperclasses = true
        }

        fun parameterTypes(vararg types: Class<*>) {
            parameterTypes = types
            parameterCount = types.size
        }

        fun find(): Result<Method> {
            val source = resolveDeclaredClass(declaredClass, declaredClassName, loader)
            return Result(buildSign(source), findInHierarchy(source, searchSuperclasses) { clazz ->
                clazz.declaredMethods
                    .filter { method -> methodName == null || method.name == methodName }
                    .filter { method -> returnType == null || typeMatches(method.returnType, returnType!!, matchParentClass) }
                    .filter { method -> parameterCount == null || method.parameterCount == parameterCount }
                    .filter { method -> parameterTypes == null || parameterTypesMatch(method.parameterTypes, parameterTypes!!, matchParentClass) }
            })
        }

        private fun buildSign(source: Class<*>): String =
            "method:${source.name} $returnType $methodName($parameterCount ${parameterTypes.contentToString()})"
    }

    class ConstructorFinder internal constructor() {
        var declaredClass: Class<*>? = null
        var declaredClassName: String? = null
        var loader: ClassLoader? = null
        var parameterCount: Int? = null
        var matchParentClass: Boolean = false

        private var parameterTypes: Array<out Class<*>>? = null

        fun from(target: Any) {
            declaredClass = target.javaClass
        }

        fun parameterTypes(vararg types: Class<*>) {
            parameterTypes = types
            parameterCount = types.size
        }

        fun find(): Result<Constructor<*>> {
            val source = resolveDeclaredClass(declaredClass, declaredClassName, loader)
            return Result(buildSign(source), source.declaredConstructors
                .filter { constructor -> parameterCount == null || constructor.parameterCount == parameterCount }
                .filter { constructor -> parameterTypes == null || parameterTypesMatch(constructor.parameterTypes, parameterTypes!!, matchParentClass) })
        }

        private fun buildSign(source: Class<*>): String =
            "constructor:${source.name} $parameterCount ${parameterTypes.contentToString()}"
    }

    class FieldFinder internal constructor() {
        var declaredClass: Class<*>? = null
        var declaredClassName: String? = null
        var loader: ClassLoader? = null
        var fieldName: String? = null
        var fieldType: Class<*>? = null
        var matchParentClass: Boolean = false
        private var searchSuperclasses: Boolean = false

        fun from(target: Any) {
            declaredClass = target.javaClass
        }

        fun superClass() {
            searchSuperclasses = true
        }

        fun find(): Result<Field> {
            val source = resolveDeclaredClass(declaredClass, declaredClassName, loader)
            return Result(buildSign(source), findInHierarchy(source, searchSuperclasses) { clazz ->
                clazz.declaredFields
                    .filter { field -> fieldName == null || field.name == fieldName }
                    .filter { field -> fieldType == null || typeMatches(field.type, fieldType!!, matchParentClass) }
            })
        }

        private fun buildSign(source: Class<*>): String =
            "field:${source.name} $fieldType $fieldName"
    }

    fun findClass(init: ClassFinder.() -> Unit): Class<*> =
        ClassFinder().apply(init).find()

    fun findClasses(init: ClassFinder.() -> Unit): Result<Class<*>> =
        ClassFinder().apply(init).findAll()

    fun findClassOrNull(init: ClassFinder.() -> Unit): Class<*>? =
        ClassFinder().apply(init).findOrNull()

    fun findClass(name: String, loader: ClassLoader? = null): Class<*> {
        val className = normalizeClassName(name)
        primitiveTypes[className]?.let { return it }
        simplePrimitiveTypes[className]?.let { return it }
        return if (className.endsWith("[]")) {
            findArrayClass(className, loader)
        } else {
            Class.forName(className, false, loader ?: defaultClassLoader())
        }
    }

    fun findClassOrNull(name: String, loader: ClassLoader? = null): Class<*>? =
        runCatching { findClass(name, loader) }.getOrNull()

    fun findClasses(name: String, loader: ClassLoader? = null): Result<Class<*>> =
        Result("class:$name", listOf(findClass(name, loader)))

    fun findMethod(init: MethodFinder.() -> Unit): Method =
        findMethods(init).first()

    fun findMethods(init: MethodFinder.() -> Unit): Result<Method> =
        MethodFinder().apply(init).find()

    fun findConstructor(init: ConstructorFinder.() -> Unit): Constructor<*> =
        findConstructors(init).first()

    fun findConstructors(init: ConstructorFinder.() -> Unit): Result<Constructor<*>> =
        ConstructorFinder().apply(init).find()

    fun findField(init: FieldFinder.() -> Unit): Field =
        findFields(init).first()

    fun findFields(init: FieldFinder.() -> Unit): Result<Field> =
        FieldFinder().apply(init).find()

    private fun resolveDeclaredClass(
        declaredClass: Class<*>?,
        declaredClassName: String?,
        loader: ClassLoader?
    ): Class<*> = declaredClass
        ?: declaredClassName?.let { findClass(it, loader) }
        ?: throw ReflectionException("declaredClass is null")

    internal fun <T> withDefaultClassLoader(loader: ClassLoader, block: () -> T): T {
        val last = classLoaderContext.get()
        classLoaderContext.set(loader)
        return try {
            block()
        } finally {
            if (last == null) {
                classLoaderContext.remove()
            } else {
                classLoaderContext.set(last)
            }
        }
    }

    private fun defaultClassLoader(): ClassLoader =
        classLoaderContext.get() ?: ClassLoader.getSystemClassLoader()

    private fun superclassesOf(source: Class<*>): List<Class<*>> {
        val result = mutableListOf<Class<*>>()
        var current: Class<*>? = source.superclass
        while (current != null && current != Any::class.java) {
            result.add(current)
            current = current.superclass
        }
        return result
    }

    private fun <T : AccessibleObject> findInHierarchy(
        source: Class<*>,
        searchSuperclasses: Boolean,
        block: (Class<*>) -> List<T>
    ): List<T> {
        var current: Class<*>? = source
        while (current != null && current != Any::class.java) {
            val result = block(current)
            if (result.isNotEmpty() || !searchSuperclasses) return result
            current = current.superclass
        }
        return emptyList()
    }

    private fun parameterTypesMatch(
        actualTypes: Array<Class<*>>,
        expectedTypes: Array<out Class<*>>,
        matchParentClass: Boolean
    ): Boolean {
        if (actualTypes.size != expectedTypes.size) return false
        return actualTypes.indices.all { index ->
            val expected = expectedTypes[index]
            expected == Ignore::class.java || typeMatches(actualTypes[index], expected, matchParentClass)
        }
    }

    private fun typeMatches(
        actual: Class<*>,
        expected: Class<*>,
        matchParentClass: Boolean
    ): Boolean {
        if (actual == expected) return true
        if (primitiveWrappers[actual] == expected || primitiveWrappers[expected] == actual) return true
        if (matchParentClass && (actual.isAssignableFrom(expected) || expected.isAssignableFrom(actual))) return true
        return false
    }

    private fun normalizeClassName(name: String): String {
        var className = name.replace('/', '.')
        if (className.startsWith("[L") && className.endsWith(";")) return className
        if (className.startsWith("[")) return className
        if (className.startsWith("L") || className.endsWith(";")) {
            if (className.startsWith("L")) className = className.substring(1)
            if (className.endsWith(";")) className = className.dropLast(1)
        }
        return className
    }

    private fun findArrayClass(name: String, loader: ClassLoader?): Class<*> {
        var componentName = name
        var dimension = 0
        while (componentName.endsWith("[]")) {
            componentName = componentName.dropLast(2)
            dimension++
        }
        var component = findClass(componentName, loader)
        repeat(dimension) {
            component = ReflectArray.newInstance(component, 0).javaClass
        }
        return component
    }

    private val primitiveTypes = mapOf(
        "void" to Void.TYPE,
        "boolean" to Boolean::class.javaPrimitiveType!!,
        "byte" to Byte::class.javaPrimitiveType!!,
        "short" to Short::class.javaPrimitiveType!!,
        "char" to Char::class.javaPrimitiveType!!,
        "int" to Int::class.javaPrimitiveType!!,
        "long" to Long::class.javaPrimitiveType!!,
        "float" to Float::class.javaPrimitiveType!!,
        "double" to Double::class.javaPrimitiveType!!
    )

    private val simplePrimitiveTypes = mapOf(
        "V" to Void.TYPE,
        "Z" to Boolean::class.javaPrimitiveType!!,
        "B" to Byte::class.javaPrimitiveType!!,
        "S" to Short::class.javaPrimitiveType!!,
        "C" to Char::class.javaPrimitiveType!!,
        "I" to Int::class.javaPrimitiveType!!,
        "J" to Long::class.javaPrimitiveType!!,
        "F" to Float::class.javaPrimitiveType!!,
        "D" to Double::class.javaPrimitiveType!!
    )

    private val primitiveWrappers = mapOf(
        Boolean::class.javaPrimitiveType!! to Boolean::class.javaObjectType,
        Byte::class.javaPrimitiveType!! to Byte::class.javaObjectType,
        Short::class.javaPrimitiveType!! to Short::class.javaObjectType,
        Char::class.javaPrimitiveType!! to Char::class.javaObjectType,
        Int::class.javaPrimitiveType!! to Int::class.javaObjectType,
        Long::class.javaPrimitiveType!! to Long::class.javaObjectType,
        Float::class.javaPrimitiveType!! to Float::class.javaObjectType,
        Double::class.javaPrimitiveType!! to Double::class.javaObjectType,
        BooleanArray::class.java to Array<Boolean>::class.java,
        ByteArray::class.java to Array<Byte>::class.java,
        ShortArray::class.java to Array<Short>::class.java,
        CharArray::class.java to Array<Char>::class.java,
        IntArray::class.java to Array<Int>::class.java,
        LongArray::class.java to Array<Long>::class.java,
        FloatArray::class.java to Array<Float>::class.java,
        DoubleArray::class.java to Array<Double>::class.java
    )
}
