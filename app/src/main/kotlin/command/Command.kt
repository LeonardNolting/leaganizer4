package command

import Config
import Config.Commands.missingParameters
import Config.Format.heading
import bot
import com.jessecorbett.diskord.api.model.Message
import com.jessecorbett.diskord.dsl.CombinedMessageEmbed
import com.jessecorbett.diskord.util.authorId
import displayMessage
import kotlinx.coroutines.*
import org.reflections8.Reflections
import org.reflections8.scanners.MethodAnnotationsScanner
import send
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.kotlinFunction
import kotlin.system.*

@Target(AnnotationTarget.FUNCTION)
annotation class Command(
	vararg val parts: String,

	// Should be KClass<out Mode<D>> -> KTypes not allowed as annotation parameter
	val mode: KClass<out Mode<*>> = Mode.Default::class,

	val permission: Permission = Permission.EVERYBODY,
	val description: String = "",
	val onSuccess: String = "",
	val warning: String = "",
	val channels: Channels = Channels.ANY
) {
	@Target(AnnotationTarget.VALUE_PARAMETER)
	annotation class Alias(
		val name: String,
		vararg val also: String
	)

	@Target(AnnotationTarget.VALUE_PARAMETER)
	annotation class Meta

	abstract class Mode<D : Mode.Data>(
		val builder: Builder<D>,
		protected val data: D,
	) {
		var counter = 0
		private val needsToSendWarning = builder.command.warning.isNotBlank()
		private var sentWarning = false

		fun message(message: Message) = runBlocking {
			async { bot.clientStore.channels[data.channelId].triggerTypingIndicator() }
			if (!sentWarning && needsToSendWarning) {
				send { text = heading("Warning", linebreak = false) + builder.command.warning }
				async { bot.clientStore.channels[data.channelId].triggerTypingIndicator() }
				delay(1000)
				sentWarning = true
			}

			process(message)
			counter++
		}

		suspend fun run() = try {
			builder.run()
		} catch (e: Exception) {
			exit()
			send { text = Config.Commands.runningFailed(e) }
			throw e
		}

		fun exit() = builder.exit()

		suspend fun processParameters(message: Message) = try {
			val content = message.content
			val parameters = if (content.isCommand) parameters(content) else content
			builder.parameters.process(parameters)
		} catch (e: Exception) {
			send { text = e.displayMessage }
			throw e
		}

		protected suspend fun send(block: suspend CombinedMessageEmbed.() -> Unit) = send(data.channelId, block)

		abstract suspend fun process(message: Message)

		companion object {
			val commands = Reflections("", MethodAnnotationsScanner()).getMethodsAnnotatedWith(Command::class.java)
				.map { function -> function.kotlinFunction!! }
				.onEach { function ->
					require(function.isValidCommand) { Config.Commands.invalidCommand(function) }
				}

			@Suppress("UNCHECKED_CAST")
			suspend fun from(message: Message): Mode<Data> {
				require(message.content.isCommand) { Config.Commands.notACommand }

				val parts = splitCommand(message.content)
					.first()
					.split(Config.Commands.prefix)
					.filter { it.isNotBlank() }

				val action =
					Builder.Companion.Actions.all.find { action ->
						parts.takeLast(action.strings.size) == action.strings.toList() && (action.canBeOnlyPart || action.strings.size < parts.size)
					}!!

				val function = action.function(commands.find { function ->
					function.commandAnnotation!!.parts.toList()
						.filter { it.isNotBlank() } == parts.filter { it.isNotBlank() }
						.dropLast(action.strings.size)
				})

				require(message.author.permitted(function.commandAnnotation!!.permission)) {
					Config.Commands.noPermission
				}

				require(function.commandAnnotation!!.channels.fine(message.channelId, message.authorId)) {
					Config.Commands.badChannel(function.commandAnnotation!!.channels.ids)
				}

				action.command = function.commandAnnotation!!

				val builder = Builder(
					Builder.Data(
						message.author, bot.clientStore.channels[message.channelId]
					), function, action, parts
				)

				return action.mode.primaryConstructor!!.call(builder, Data(message.channelId)) as Mode<Data>
			}
		}

		open class Data(val channelId: String)

		class Default<D : Data>(
			builder: Builder<D>,
			data: D
		) : Mode<D>(builder, data) {
			override suspend fun process(message: Message) {
				exit()
				processParameters(message)
				run()
			}
		}

		open class Keywords<D : Data>(
			builder: Builder<D>,
			data: D
		) : Mode<D>(builder, data) {
			open val keywords: List<Keyword<D>> = listOf()
			final override suspend fun process(message: Message) {
				val content = message.content
				val matches = keywords.mapNotNull { keyword ->
					val identifier = (keyword.alias + keyword.name).find { it == content.trim() }
					if (identifier != null) keyword to identifier else null
				}

				if (matches.isNotEmpty()) {
					val (keyword, identifier) = matches.first()
					with(keyword) {
						action(this@Keywords, message, identifier)
					}
				} else processNotKeyword(message)
			}

			open suspend fun processNotKeyword(message: Message) = processParameters(message)
		}

		abstract class Continuous<D : Data>(
			builder: Builder<D>,
			data: D
		) : Keywords<D>(builder, data) {
			open val customKeywords: MutableList<Keyword<D>> = mutableListOf()
			override val keywords
				get() =
					listOf<Keyword<D>>(
						Keyword(Config.Commands.Continuous.Keywords.help) {
							send { text = Config.Commands.Continuous.help(helpAddition) }
						},
						Keyword(Config.Commands.Continuous.Keywords.run, alsoParseString = true) {
							run()
						},
						Keyword(Config.Commands.Continuous.Keywords.exit) {
							send { text = Config.Commands.Continuous.exit }
							exit()
						}
					) + customKeywords

			final override suspend fun processNotKeyword(message: Message) {
				super.processNotKeyword(message)
				when {
					shouldRun() -> run()
					couldRun()  -> send { text = couldRunMessage }
					else        -> send { text = reply }
				}
			}

			open val couldRunMessage = Config.Commands.Continuous.defaultCouldRunMessage
			open val reply
				get() = if (counter == 0) Config.Commands.Continuous.info(
					keywords,
					builder.parameters
				) else missingParameters(builder.parameters, "Missing parameters")

			abstract suspend fun couldRun(): Boolean
			abstract suspend fun shouldRun(): Boolean

			abstract val helpAddition: String

			@Name("Optionally Continuous")
			open class Possible<D : Data>(
				builder: Builder<D>,
				data: D
			) : Continuous<D>(builder, data) {
				override suspend fun couldRun() = builder.isReady
				override suspend fun shouldRun() = builder.isFilled
				override val helpAddition = Config.Commands.Continuous.Possible.helpAddition
			}

			@Name("Always Continuous")
			class Always<D : Data>(
				builder: Builder<D>,
				data: D
			) : Continuous<D>(builder, data) {
				override suspend fun couldRun() = false
				override suspend fun shouldRun() = false
				override val helpAddition = Config.Commands.Continuous.Always.helpAddition
			}
		}

		annotation class Name(val value: String)

		data class Keyword<D : Data>(
			val name: String,
			val description: String,
			val alsoParseString: Boolean = false,
			val alias: List<String> = listOf(),
			private val actionBlock: suspend Mode<D>.(String) -> Unit
		) {
			suspend fun action(mode: Mode<D>, message: Message, identifier: String) {
				if (alsoParseString) mode.processParameters(message)
				mode.actionBlock(message.content.trim().removePrefix(identifier).trim())
			}

			constructor(
				pair: Pair<String, String>,
				alsoParseString: Boolean = false,
				alias: List<String> = listOf(),
				actionBlock: suspend Mode<D>.(String) -> Unit,
			) : this(pair.first, pair.second, alsoParseString, alias, actionBlock)

			fun toStringDiscord() = "`$name`: $description"
		}
	}
}
