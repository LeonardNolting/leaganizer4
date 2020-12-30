package command

import Config
import command.Builder.Companion.nothing
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class Action<D : Command.Mode.Data>(
	vararg val strings: String,
	private val useCommandMode: Boolean,
	val canBeOnlyPart: Boolean = false,
	private val usePlaceholderFunction: Boolean = false,
	val run: suspend Builder<D>.() -> Unit,
) {
	fun function(function: KFunction<*>?) = when {
		usePlaceholderFunction -> ::nothing
		function == null       -> throw Exception(Config.Commands.notFound)
		else                   -> function
	}

	private var _command: Command? = null
	var command: Command?
		get() = _command ?: error("Command wasn't initialized yet.")
		set(command) {
			requireNotNull(command) { "Cannot set command to null." }
			if (useCommandMode) mode = command.mode
			requestedMode = command.mode
			_command = command
		}

	lateinit var mode: KClass<out Command.Mode<*>>
	lateinit var requestedMode: KClass<out Command.Mode<*>>

	constructor(
		vararg strings: String,
		mode: KClass<out Command.Mode<*>> = Command.Mode.Default::class,
		canBeOnlyPart: Boolean = false,
		usePlaceholderFunction: Boolean = false,
		run: suspend Builder<D>.() -> Unit,
	) : this(
		*strings,
		useCommandMode = false,
		canBeOnlyPart = canBeOnlyPart,
		usePlaceholderFunction = usePlaceholderFunction,
		run = run
	) {
		this.mode = mode
	}
}