package model.database

import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
abstract class WrapperColumnType<T : WrapperColumnType.Wrapper>(private val kClass: KClass<T>) : ColumnType() {
	abstract fun fromValue(value: Any): T
	protected fun unexpectedType(value: Any): Nothing =
		error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")

	protected fun createInstance(value: Any) = kClass.primaryConstructor!!.call(value)

	@JvmName("isInstanceNotNull")
	private fun isInstance(value: Any) = kClass.isInstance(value)
	private fun isInstance(value: Any?) = if (value == null) false else isInstance(value)

	@JvmName("wrapperOrValueNotNull")
	private fun wrapperOrValue(value: Any) =
		if (isInstance(value)) (value as T).value else value

	private fun wrapperOrValue(value: Any?) =
		if (isInstance(value)) (value as T).value else value

	override fun valueFromDB(value: Any): T = when {
		isInstance(value) -> value as T
		else              -> fromValue(value)
	}

	final override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) =
		super.setParameter(stmt, index, wrapperOrValue(value))

	final override fun notNullValueToDB(value: Any) =
		super.notNullValueToDB(wrapperOrValue(value))

	interface Wrapper {
		val value: Any
	}
}

abstract class TextWrapperColumnType<T : WrapperColumnType.Wrapper>(kClass: KClass<T>) : WrapperColumnType<T>(kClass) {
	final override fun sqlType() = currentDialect.dataTypeProvider.textType()
	final override fun fromValue(value: Any) =
		if (value is String) createInstance(value)
		else unexpectedType(value)
}

abstract class IntegerWrapperColumnType<T : WrapperColumnType.Wrapper>(kClass: KClass<T>) :
	WrapperColumnType<T>(kClass) {
	final override fun sqlType() = currentDialect.dataTypeProvider.integerType()
	final override fun fromValue(value: Any) =
		if (value is Int) createInstance(value)
		else unexpectedType(value)
}