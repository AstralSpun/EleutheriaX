package org.astralspun.eleutheriax.dexkit

import org.luckypray.dexkit.query.BatchFindClassUsingStrings
import org.luckypray.dexkit.query.BatchFindMethodUsingStrings
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.MatchType as DexKitMatchType
import org.luckypray.dexkit.query.enums.StringMatchType as DexKitStringMatchType
import org.luckypray.dexkit.query.enums.UsingType as DexKitUsingType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.util.DexSignUtil

enum class DexMatchType {
    Contains,
    Equals
}

enum class DexStringMatchType {
    Contains,
    StartsWith,
    EndsWith,
    SimilarRegex,
    Equals
}

enum class DexUsingType {
    Any,
    Read,
    Write
}

private sealed class DexTypeMatcher {
    abstract fun applyToClass(matcher: ClassMatcher)
    abstract fun applyToMethodDeclaredClass(matcher: MethodMatcher)
    abstract fun applyToMethodReturnType(matcher: MethodMatcher)
    abstract fun applyToFieldDeclaredClass(matcher: FieldMatcher)
    abstract fun applyToFieldType(matcher: FieldMatcher)

    private class Name(
        private val name: String,
        private val matchType: DexStringMatchType = DexStringMatchType.Equals,
        private val ignoreCase: Boolean = false
    ) : DexTypeMatcher() {
        override fun applyToClass(matcher: ClassMatcher) {
            matcher.className(name, matchType.toDexKit(), ignoreCase)
        }

        override fun applyToMethodDeclaredClass(matcher: MethodMatcher) {
            matcher.declaredClass(name, matchType.toDexKit(), ignoreCase)
        }

        override fun applyToMethodReturnType(matcher: MethodMatcher) {
            matcher.returnType(name, matchType.toDexKit(), ignoreCase)
        }

        override fun applyToFieldDeclaredClass(matcher: FieldMatcher) {
            matcher.declaredClass(name, matchType.toDexKit(), ignoreCase)
        }

        override fun applyToFieldType(matcher: FieldMatcher) {
            matcher.type(name, matchType.toDexKit(), ignoreCase)
        }

        override fun toString(): String = "Name($name,$matchType,$ignoreCase)"
    }

    private class Type(private val type: Class<*>) : DexTypeMatcher() {
        override fun applyToClass(matcher: ClassMatcher) {
            matcher.className(DexSignUtil.getTypeName(type))
        }

        override fun applyToMethodDeclaredClass(matcher: MethodMatcher) {
            matcher.declaredClass(type)
        }

        override fun applyToMethodReturnType(matcher: MethodMatcher) {
            matcher.returnType(type)
        }

        override fun applyToFieldDeclaredClass(matcher: FieldMatcher) {
            matcher.declaredClass(type)
        }

        override fun applyToFieldType(matcher: FieldMatcher) {
            matcher.type(type)
        }

        override fun toString(): String = "Type(${type.name})"
    }

    companion object {
        internal fun name(
            name: String,
            matchType: DexStringMatchType = DexStringMatchType.Equals,
            ignoreCase: Boolean = false
        ): DexTypeMatcher = Name(name, matchType, ignoreCase)

        internal fun type(type: Class<*>): DexTypeMatcher = Type(type)
    }
}

abstract class DexBaseQuery internal constructor() {
    private val searchPackages = mutableListOf<String>()
    private val excludePackages = mutableListOf<String>()
    var ignorePackagesCase: Boolean = false
    var findFirst: Boolean = false

    fun searchPackages(vararg name: String) {
        searchPackages.addAll(name)
    }

    fun excludePackages(vararg name: String) {
        excludePackages.addAll(name)
    }

    fun ignorePackagesCase(ignorePackagesCase: Boolean) {
        this.ignorePackagesCase = ignorePackagesCase
    }

    fun findFirst(findFirst: Boolean) {
        this.findFirst = findFirst
    }

    protected fun applyToFindClass(target: Any) {
        val findClass = target as FindClass
        if (searchPackages.isNotEmpty()) findClass.searchPackages(searchPackages)
        if (excludePackages.isNotEmpty()) findClass.excludePackages(excludePackages)
        findClass.ignorePackagesCase(ignorePackagesCase)
        findClass.findFirst = findFirst
    }

    protected fun applyToFindMethod(target: Any) {
        val findMethod = target as FindMethod
        if (searchPackages.isNotEmpty()) findMethod.searchPackages(searchPackages)
        if (excludePackages.isNotEmpty()) findMethod.excludePackages(excludePackages)
        findMethod.ignorePackagesCase(ignorePackagesCase)
        findMethod.findFirst = findFirst
    }

    protected fun applyToFindField(target: Any) {
        val findField = target as FindField
        if (searchPackages.isNotEmpty()) findField.searchPackages(searchPackages)
        if (excludePackages.isNotEmpty()) findField.excludePackages(excludePackages)
        findField.ignorePackagesCase(ignorePackagesCase)
        findField.findFirst = findFirst
    }

    protected fun baseSign(): String =
        "search=$searchPackages,exclude=$excludePackages,ignoreCase=$ignorePackagesCase,first=$findFirst"
}

class DexClassQuery internal constructor() : DexBaseQuery() {
    var className: Any? = null
    var superClass: Any? = null
    var modifiers: Int? = null
    var modifiersMatchType: DexMatchType = DexMatchType.Contains

    private val interfaces = mutableListOf<DexTypeMatcher>()
    private val usedStrings = mutableListOf<String>()
    private val fieldQueries = mutableListOf<DexFieldQuery>()
    private val methodQueries = mutableListOf<DexMethodQuery>()

    fun className(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        className = DexTypeMatcher.name(name, matchType, ignoreCase)
    }

    fun className(type: Class<*>) {
        className = type
    }

    fun superClass(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        superClass = DexTypeMatcher.name(name, matchType, ignoreCase)
    }

    fun superClass(type: Class<*>) {
        superClass = type
    }

    fun addInterface(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        interfaces.add(DexTypeMatcher.name(name, matchType, ignoreCase))
    }

    fun addInterface(type: Class<*>) {
        interfaces.add(DexTypeMatcher.type(type))
    }

    fun modifiers(modifiers: Int, matchType: DexMatchType = DexMatchType.Contains) {
        this.modifiers = modifiers
        modifiersMatchType = matchType
    }

    fun usingStrings(vararg value: String) {
        usedStrings.addAll(value)
    }

    fun fields(init: DexFieldQuery.() -> Unit) {
        fieldQueries.add(DexFieldQuery().apply(init))
    }

    fun methods(init: DexMethodQuery.() -> Unit) {
        methodQueries.add(DexMethodQuery().apply(init))
    }

    internal fun buildFindClass(): Any {
        val findClass = FindClass.create()
        applyToFindClass(findClass)
        return findClass.matcher(buildMatcher() as ClassMatcher)
    }

    internal fun buildMatcher(): Any {
        val matcher = ClassMatcher.create()
        className?.let { toTypeMatcher(it).applyToClass(matcher) }
        superClass?.let { toTypeMatcher(it).also { type -> matcher.superClass(ClassMatcher.create().also(type::applyToClass)) } }
        modifiers?.let { matcher.modifiers(it, modifiersMatchType.toDexKit()) }
        interfaces.forEach { matcher.addInterface(ClassMatcher.create().also(it::applyToClass)) }
        if (usedStrings.isNotEmpty()) matcher.usingStrings(usedStrings)
        fieldQueries.forEach { matcher.addField(it.buildMatcher() as FieldMatcher) }
        methodQueries.forEach { matcher.addMethod(it.buildMatcher() as MethodMatcher) }
        return matcher
    }

    internal fun sign(): String =
        "class(${baseSign()},class=$className,super=$superClass,mod=$modifiers,$modifiersMatchType," +
            "interfaces=$interfaces,strings=$usedStrings,fields=${fieldQueries.map { it.sign() }}," +
            "methods=${methodQueries.map { it.sign() }})"
}

class DexMethodQuery internal constructor() : DexBaseQuery() {
    var descriptor: String? = null
    var declaredClass: Any? = null
    var methodName: String? = null
    var methodNameMatchType: DexStringMatchType = DexStringMatchType.Equals
    var methodNameIgnoreCase: Boolean = false
    var returnType: Any? = null
    var parameterCount: Int? = null
    var modifiers: Int? = null
    var modifiersMatchType: DexMatchType = DexMatchType.Contains

    private var parameterTypes: List<Any?>? = null
    private val usedStrings = mutableListOf<String>()
    private val usedNumbers = mutableListOf<Number>()
    private val usedFields = mutableListOf<Pair<DexFieldQuery, DexUsingType>>()
    private val invokeMethods = mutableListOf<DexMethodQuery>()
    private val callerMethods = mutableListOf<DexMethodQuery>()

    fun descriptor(descriptor: String) {
        this.descriptor = descriptor
    }

    fun declaredClass(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        declaredClass = DexTypeMatcher.name(name, matchType, ignoreCase)
    }

    fun declaredClass(type: Class<*>) {
        declaredClass = type
    }

    fun methodName(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        methodName = name
        methodNameMatchType = matchType
        methodNameIgnoreCase = ignoreCase
    }

    fun returnType(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        returnType = DexTypeMatcher.name(name, matchType, ignoreCase)
    }

    fun returnType(type: Class<*>) {
        returnType = type
    }

    fun parameterTypes(vararg type: String?) {
        parameterTypes = type.toList()
        parameterCount = type.size
    }

    fun parameterTypes(vararg type: Class<*>?) {
        parameterTypes = type.toList()
        parameterCount = type.size
    }

    fun parameterCount(count: Int) {
        parameterCount = count
    }

    fun modifiers(modifiers: Int, matchType: DexMatchType = DexMatchType.Contains) {
        this.modifiers = modifiers
        modifiersMatchType = matchType
    }

    fun usingStrings(vararg value: String) {
        usedStrings.addAll(value)
    }

    fun usingNumbers(vararg value: Number) {
        usedNumbers.addAll(value)
    }

    fun usingFields(usingType: DexUsingType = DexUsingType.Any, init: DexFieldQuery.() -> Unit) {
        usedFields.add(DexFieldQuery().apply(init) to usingType)
    }

    fun invokeMethods(init: DexMethodQuery.() -> Unit) {
        invokeMethods.add(DexMethodQuery().apply(init))
    }

    fun callerMethods(init: DexMethodQuery.() -> Unit) {
        callerMethods.add(DexMethodQuery().apply(init))
    }

    internal fun buildFindMethod(): Any {
        val findMethod = FindMethod.create()
        applyToFindMethod(findMethod)
        return findMethod.matcher(buildMatcher() as MethodMatcher)
    }

    internal fun buildMatcher(): Any {
        val matcher = MethodMatcher.create()
        descriptor?.let { return matcher.descriptor(it) }
        declaredClass?.let { toTypeMatcher(it).applyToMethodDeclaredClass(matcher) }
        methodName?.let { matcher.name(it, methodNameMatchType.toDexKit(), methodNameIgnoreCase) }
        returnType?.let { toTypeMatcher(it).applyToMethodReturnType(matcher) }
        parameterTypes?.let { types ->
            when {
                types.all { it == null || it is String } -> {
                    @Suppress("UNCHECKED_CAST")
                    matcher.paramTypes(*(types as List<String?>).toTypedArray())
                }
                types.all { it == null || it is Class<*> } -> {
                    @Suppress("UNCHECKED_CAST")
                    matcher.paramTypes(*(types as List<Class<*>?>).toTypedArray())
                }
                else -> throw DexResolverException("parameterTypes only supports String? or Class<*>?")
            }
        }
        parameterCount?.let { if (parameterTypes == null) matcher.paramCount(it) }
        modifiers?.let { matcher.modifiers(it, modifiersMatchType.toDexKit()) }
        if (usedStrings.isNotEmpty()) matcher.usingStrings(usedStrings)
        if (usedNumbers.isNotEmpty()) matcher.usingNumbers(usedNumbers)
        usedFields.forEach { matcher.addUsingField(it.first.buildMatcher() as FieldMatcher, it.second.toDexKit()) }
        invokeMethods.forEach { matcher.addInvoke(it.buildMatcher() as MethodMatcher) }
        callerMethods.forEach { matcher.addCaller(it.buildMatcher() as MethodMatcher) }
        return matcher
    }

    internal fun sign(): String =
        "method(${baseSign()},descriptor=$descriptor,class=$declaredClass,name=$methodName,return=$returnType," +
            "nameMatch=$methodNameMatchType,nameIgnore=$methodNameIgnoreCase," +
            "params=$parameterTypes,count=$parameterCount,mod=$modifiers,$modifiersMatchType," +
            "strings=$usedStrings,numbers=$usedNumbers,fields=${usedFields.map { it.first.sign() to it.second }}," +
            "invoke=${invokeMethods.map { it.sign() }},caller=${callerMethods.map { it.sign() }})"
}

class DexFieldQuery internal constructor() : DexBaseQuery() {
    var descriptor: String? = null
    var declaredClass: Any? = null
    var fieldName: String? = null
    var fieldNameMatchType: DexStringMatchType = DexStringMatchType.Equals
    var fieldNameIgnoreCase: Boolean = false
    var fieldType: Any? = null
    var modifiers: Int? = null
    var modifiersMatchType: DexMatchType = DexMatchType.Contains

    private val readMethods = mutableListOf<DexMethodQuery>()
    private val writeMethods = mutableListOf<DexMethodQuery>()

    fun descriptor(descriptor: String) {
        this.descriptor = descriptor
    }

    fun declaredClass(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        declaredClass = DexTypeMatcher.name(name, matchType, ignoreCase)
    }

    fun declaredClass(type: Class<*>) {
        declaredClass = type
    }

    fun fieldName(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        fieldName = name
        fieldNameMatchType = matchType
        fieldNameIgnoreCase = ignoreCase
    }

    fun fieldType(
        name: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false
    ) {
        fieldType = DexTypeMatcher.name(name, matchType, ignoreCase)
    }

    fun fieldType(type: Class<*>) {
        fieldType = type
    }

    fun modifiers(modifiers: Int, matchType: DexMatchType = DexMatchType.Contains) {
        this.modifiers = modifiers
        modifiersMatchType = matchType
    }

    fun readMethods(init: DexMethodQuery.() -> Unit) {
        readMethods.add(DexMethodQuery().apply(init))
    }

    fun writeMethods(init: DexMethodQuery.() -> Unit) {
        writeMethods.add(DexMethodQuery().apply(init))
    }

    internal fun buildFindField(): Any {
        val findField = FindField.create()
        applyToFindField(findField)
        return findField.matcher(buildMatcher() as FieldMatcher)
    }

    internal fun buildMatcher(): Any {
        val matcher = FieldMatcher.create()
        descriptor?.let { return matcher.descriptor(it) }
        declaredClass?.let { toTypeMatcher(it).applyToFieldDeclaredClass(matcher) }
        fieldName?.let { matcher.name(it, fieldNameMatchType.toDexKit(), fieldNameIgnoreCase) }
        fieldType?.let { toTypeMatcher(it).applyToFieldType(matcher) }
        modifiers?.let { matcher.modifiers(it, modifiersMatchType.toDexKit()) }
        readMethods.forEach { matcher.addReadMethod(it.buildMatcher() as MethodMatcher) }
        writeMethods.forEach { matcher.addWriteMethod(it.buildMatcher() as MethodMatcher) }
        return matcher
    }

    internal fun sign(): String =
        "field(${baseSign()},descriptor=$descriptor,class=$declaredClass,name=$fieldName,type=$fieldType," +
            "nameMatch=$fieldNameMatchType,nameIgnore=$fieldNameIgnoreCase," +
            "mod=$modifiers,$modifiersMatchType,read=${readMethods.map { it.sign() }}," +
            "write=${writeMethods.map { it.sign() }})"
}

class DexBatchUsingStringsQuery internal constructor() {
    private val searchPackages = mutableListOf<String>()
    private val excludePackages = mutableListOf<String>()
    private val groups = linkedMapOf<String, List<String>>()
    var ignorePackagesCase: Boolean = false
    var stringMatchType: DexStringMatchType = DexStringMatchType.Contains
    var ignoreCase: Boolean = false

    fun searchPackages(vararg name: String) {
        searchPackages.addAll(name)
    }

    fun excludePackages(vararg name: String) {
        excludePackages.addAll(name)
    }

    fun group(key: String, vararg usingStrings: String) {
        groups[key] = usingStrings.toList()
    }

    fun ignorePackagesCase(ignorePackagesCase: Boolean) {
        this.ignorePackagesCase = ignorePackagesCase
    }

    fun stringMatchType(stringMatchType: DexStringMatchType) {
        this.stringMatchType = stringMatchType
    }

    fun ignoreCase(ignoreCase: Boolean) {
        this.ignoreCase = ignoreCase
    }

    internal fun buildClassQuery(): Any {
        val query = BatchFindClassUsingStrings.create()
        if (searchPackages.isNotEmpty()) query.searchPackages(searchPackages)
        if (excludePackages.isNotEmpty()) query.excludePackages(excludePackages)
        query.ignorePackagesCase(ignorePackagesCase)
        return query.groups(groups, stringMatchType.toDexKit(), ignoreCase)
    }

    internal fun buildMethodQuery(): Any {
        val query = BatchFindMethodUsingStrings.create()
        if (searchPackages.isNotEmpty()) query.searchPackages(searchPackages)
        if (excludePackages.isNotEmpty()) query.excludePackages(excludePackages)
        query.ignorePackagesCase(ignorePackagesCase)
        return query.groups(groups, stringMatchType.toDexKit(), ignoreCase)
    }

    internal fun sign(): String =
        "batch(search=$searchPackages,exclude=$excludePackages,ignorePackagesCase=$ignorePackagesCase," +
            "match=$stringMatchType,ignoreCase=$ignoreCase,groups=$groups)"
}

private fun toTypeMatcher(value: Any): DexTypeMatcher = when (value) {
    is DexTypeMatcher -> value
    is Class<*> -> DexTypeMatcher.type(value)
    is String -> DexTypeMatcher.name(value)
    else -> throw DexResolverException("Unsupported dex type value: $value")
}

private fun DexMatchType.toDexKit(): DexKitMatchType = when (this) {
    DexMatchType.Contains -> DexKitMatchType.Contains
    DexMatchType.Equals -> DexKitMatchType.Equals
}

private fun DexStringMatchType.toDexKit(): DexKitStringMatchType = when (this) {
    DexStringMatchType.Contains -> DexKitStringMatchType.Contains
    DexStringMatchType.StartsWith -> DexKitStringMatchType.StartsWith
    DexStringMatchType.EndsWith -> DexKitStringMatchType.EndsWith
    DexStringMatchType.SimilarRegex -> DexKitStringMatchType.SimilarRegex
    DexStringMatchType.Equals -> DexKitStringMatchType.Equals
}

private fun DexUsingType.toDexKit(): DexKitUsingType = when (this) {
    DexUsingType.Any -> DexKitUsingType.Any
    DexUsingType.Read -> DexKitUsingType.Read
    DexUsingType.Write -> DexKitUsingType.Write
}
