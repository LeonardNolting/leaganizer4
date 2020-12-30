package command

import Config
import Config.Commands.Parameter.toStringDiscordDetailed
import com.jessecorbett.diskord.util.withSingleLineCode
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

class Parameters(private val data: Builder.Data, private val list: List<KParameter> = mutableListOf()) :
	MutableMap<KParameter, Parameters.Optional> by (list.associateWith { Optional.Nothing }.toMutableMap()) {
	val isReady: Boolean
		get() = missing.isEmpty()
	val isFilled: Boolean
		get() = empty.isEmpty()

	val missing
		get() = this
			.filter { (parameter, value) -> parameter.kind == KParameter.Kind.VALUE && value is Optional.Nothing && !parameter.isMeta && !parameter.isOptional }
			.map { (parameter, _) -> parameter }

	val empty
		get() = this
			.filter { (parameter, value) -> parameter.kind == KParameter.Kind.VALUE && value is Optional.Nothing && !parameter.isMeta }
			.map { (parameter, _) -> parameter }

	val public get() = keys.filter { parameter -> parameter.kind == KParameter.Kind.VALUE && !parameter.isMeta }

	fun list(parameters: List<KParameter>) = parameters.joinToString("\n") { "- " + it.toStringDiscordDetailed() }

	override fun toString() = "[" + entries.joinToString(", ") { (parameter, value) ->
		"${parameter.name ?: parameter.kind}=$value"
	} + "]"

	fun instance(function: KFunction<*>) {
		if (function.instanceParameter != null)
			this[function.instanceParameter!!] = Optional.Value(function.objectInstance)
	}

	suspend fun meta() {
		filter { (parameter, _) -> parameter.isMeta }.map { (parameter, _) ->
			val type = Type.get(parameter.type)
			val typeMeta = type::meta

			this[parameter] = Optional.Value(
				try {
					typeMeta(data)
				} catch (e: Exception) {
					error(Config.Commands.Type.unableToParseMetaType(type, e))
				}
			)
		}
	}

	private suspend fun getText(parameter: Parameter.Text, kType: KType) =
		if (parameter !is Parameter.Text.Quoted && parameter.value == Config.Commands.nothing) {
			require(kType.isMarkedNullable) { Config.Commands.Parameters.nothingProhibited }
			null
		} else {
			val type = Type.get(kType)
			try {
				type.parse(data, parameter.value)
			} catch (e: Exception) {
				error(Config.Commands.Type.unableToParseType(type, parameter, e))
			}
		}

	private suspend fun getNamedList(parameter: Parameter.List, kType: KType): Map<String, Any?> =
		parameter.value.map { nestedParameter ->
			getTextOrListNamed(nestedParameter, kType.arguments[1].type!!)
		}.toMap()

	private suspend fun getUnnamedList(parameter: Parameter.List, kType: KType): List<Any?> =
		parameter.value.map { nestedParameter ->
			getTextOrListUnnamed(nestedParameter, kType.arguments[0].type!!)
		}

	private suspend fun getList(parameter: Parameter.List, kType: KType): Any = when {
		kType.isSubtypeOf(typeOf<List<*>>()) -> getUnnamedList(parameter, kType)
		kType.isSubtypeOf(typeOf<Map<*, *>>()) -> getNamedList(parameter, kType)
		else -> error(Config.Commands.Parameters.notAList)
	}

	private suspend fun getTextOrList(parameter: Parameter, kType: KType) =
		if (parameter is Parameter.Text) getText(parameter, kType)
		else getList(parameter as Parameter.List, kType)

	private suspend fun getTextOrListUnnamed(parameter: Parameter, kType: KType): Any? {
		require(parameter.name == null) { Config.Commands.Parameters.notANamedList }
		return getTextOrList(parameter, kType)
	}

	private suspend fun getTextOrListNamed(parameter: Parameter, kType: KType) =
		(parameter.name ?: error(Config.Commands.Parameters.isANamedList)) to getTextOrList(parameter, kType)

	private fun kParameter(name: String?, value: Any?) =
		if (name == null) empty.firstOrNull()
			?: error(Config.Commands.Parameters.noEmptyParameter)
		else keys.filter { kParameter -> !kParameter.isMeta && name in kParameter.validNames }
			.minByOrNull { kParameter -> kParameter.validNames.indexOf(name) }
			?: error(Config.Commands.Parameters.noParameterWithName(name, value))

	private suspend fun set(parameter: Parameter) {
		val kParameter = kParameter(parameter.name, parameter.value)
		this[kParameter] = Optional.Value(getTextOrList(parameter, kParameter.type))
	}

	fun set(name: String?, value: Any?) {
		this[kParameter(name, value)] = Optional.Value(value)
	}

	suspend fun process(string: String) {
		val result = Parser("[$string]").parseDelimitedParameterList()

		require(result is Parser.Result.Value) { (result as Parser.Result.Error).message }
		result.value.forEach { parameter ->
			try {
				set(parameter)
			} catch (e: Exception) {
				error(Config.Commands.Parameters.processingParameterFailed(parameter, e))
			}
		}
	}

	fun out(): Map<KParameter, Any?> = this
		.filter { (_, value) -> value is Optional.Value }
		.map { (parameter, value) -> parameter to (value as Optional.Value).value }
		.toMap()

	private sealed class Optional {
		object Nothing : Optional()
		data class Value(val value: Any?) : Optional()
	}

	sealed class Parameter(val name: String?, open val value: Any) {
		sealed class Text(name: String?, final override val value: String) : Parameter(name, value) {
			class Normal(name: String?, value: String) : Text(name, value)
			class Quoted(name: String?, value: String) : Text(name, value)
		}

		class List(name: String?, override val value: kotlin.collections.List<Parameter>) : Parameter(name, value)

		override fun toString(): String {
			return "${this::class.simpleName}(name=$name, value=$value)"
		}
	}

	class Parser(private val input: String) {
		private var index = 0
		private val next: Char?
			get() = input.getOrNull(index)
		private val last: Char
			get() = input[index - 1]

		private fun next(): Char? =
			input.getOrNull(index++)

		private fun <T> onBranchMergingOnSuccess(f: ParserFunction<T>): Result<T> {
			val backupIndex = index
			val result = f()
			if (result is Result.Failure)
				index = backupIndex
			return result
		}

		private fun <T> onBranch(f: ParserFunction<T>): Result<T> {
			val backupIndex = index
			val result = f()
			index = backupIndex
			return result
		}

		private fun <T, U> untilSuccess(test: ParserFunction<T>, f: ParserFunction<U>): Result<List<U>> {
			val results = mutableListOf<U>()
			var onBranch = onBranch(test)
			while (onBranch is Result.Failure) {
				results += onBranchMergingOnSuccess(f).onFailure { return it }
				onBranch = onBranch(test)
			}
			return Result.Value(results.toList())
		}

		private fun parseCharOrEscapeSequence(): Result<Char> {
			return if (next() == '\\' && index < input.length) Result.Value(next()!!)
			else Result.Value(last)
		}

		private fun parseChar(char: Char): Result<Char> {
			return if (next() == char) Result.Value(char)
			else Result.Error("Expected $char, found $next")
		}

		private fun parseChar(chars: List<Char>): Result<Char> {
			return if (next()?.let { chars.contains(it) } == true) Result.Value(last)
			else Result.Error(
				"Expected one of: ${
					chars.joinToString {
						it.toString().withSingleLineCode()
					}
				}, found $next"
			)
		}

		private fun parseQuotedString(): Result<String> {
			val quotes = Config.Commands.quotes
			parseChar(quotes).onFailure { return it }
			val content =
				untilSuccess({ parseChar(quotes) }, { parseCharOrEscapeSequence() }).onFailure { return it }
					.joinToString("")
			parseChar(quotes).onFailure { return it }
			return Result.Value(content)
		}

		private fun parseUndelimitedParameterList(): Result<List<Parameter>> {
			val output = mutableListOf<Parameter>()
			(parseParameter() as? Result.Success<Parameter>)?.value?.let { output.add(it) }
			return Result.Value(output)
		}

		fun parseDelimitedParameterList(): Result<List<Parameter>> {
			parseChar('[').onFailure { return it }
			optional { parseWhitespace() }
			val output = untilSuccess({ parseChar(']') }) {
				val result = parseParameter()
				if (onBranch { parseChar(']') } is Result.Success) optional { parseWhitespace() }
				else parseWhitespace().onFailure { return@untilSuccess it }
				result
			}.onFailure { return it }
			parseChar(']').onFailure { return it }
			return Result.Value(output)
		}

		private fun parseWhitespace(): Result<String> {
			var total = ""
			while (next?.isWhitespace() == true) total += next()
			return if (total.isEmpty()) Result.Error("Expected whitespace")
			else Result.Value(total)
		}

		private fun <T> optional(f: ParserFunction<T>): T? {
			val result = onBranchMergingOnSuccess(f)
			return if (result is Result.Success) result.value
			else null
		}

		private fun parseWord(wordEndingChars: CharSequence): Result<String> {
			var total = ""
			while (!(next?.isWhitespace() == true || next?.let { wordEndingChars.contains(it) } == true)) total += next()
			return if (total.isEmpty()) Result.Error("Expected a parameter")
			else Result.Value(total)
		}

		private fun parseParameter(): Result<Parameter> {
			val key: String? =
				optional {
					val result = parseWord("=][").onFailure { return@optional it }
					optional { parseWhitespace() }
					parseChar('=').onFailure { return@optional it }
					optional { parseWhitespace() }
					Result.Value(result)
				}
			onBranchMergingOnSuccess { parseQuotedString() }.also {
				if (it is Result.Success<String>) return Result.Value(
					Parameter.Text.Quoted(
						key,
						it.value
					)
				)
			}
			onBranchMergingOnSuccess { parseDelimitedParameterList() }.also {
				if (it is Result.Success<List<Parameter>>) return Result.Value(
					Parameter.List(
						key,
						it.value
					)
				)
			}
			onBranchMergingOnSuccess { parseWord("][") }.also {
				if (it is Result.Success<String>) return Result.Value(
					Parameter.Text.Normal(
						key,
						it.value
					)
				)
			}
			return Result.Error("not a valid parameter")
		}

		sealed class Result<out T> {
			abstract class Success<T>(val value: T) : Result<T>() {
				override fun toString() = "Success($value)"
			}

			class Value<T>(value: T) : Success<T>(value)

			abstract class Failure(val message: String) : Result<Nothing>() {
				override fun toString() = "Failure($message)"
			}

			class Error(message: String) : Failure(message)

			inline fun onFailure(f: (Failure) -> Nothing): T {
				if (this is Failure) f(this)
				return (this as Success<T>).value
			}
		}
	}
}

typealias ParserFunction<T> = Parameters.Parser.() -> Parameters.Parser.Result<T>