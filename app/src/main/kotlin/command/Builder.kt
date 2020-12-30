package command

import Config
import Config.Commands.Parameter.toStringDiscord
import Config.Format.heading
import com.jessecorbett.diskord.api.model.User
import com.jessecorbett.diskord.api.rest.client.ChannelClient
import com.jessecorbett.diskord.dsl.CombinedMessageEmbed
import com.jessecorbett.diskord.util.withSingleLineCode
import kotlinx.coroutines.runBlocking
import org.reflections8.Reflections
import org.reflections8.scanners.MethodAnnotationsScanner
import send
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

data class Builder<D : Command.Mode.Data>(
	val data: Data,
	val function: KFunction<*>,

//	val action: Action<D> = Actions.default,
	val action: Action<D> = Action(useCommandMode = true) {
		val result = call()

		val onSuccess = command.onSuccess

		if (onSuccess.isNotBlank()) send(data.channel.channelId) { text = onSuccess }
		else when (result) {
			is Unit                 -> send(data.channel.channelId) { text = "Finished command." }
			is CombinedMessageEmbed -> send(data.channel.channelId, result)
			else                    -> send(data.channel.channelId) {
				text = result.toString()
			}
		}
	},

	val parts: List<String> = listOf(),
) {
	var state = State.ACTIVE
	val command = function.commandAnnotation!!
	val parameters = Parameters(data, function.valueParameters)
	val isReady: Boolean
		get() = parameters.isReady
	val isFilled: Boolean
		get() = parameters.isFilled

	private val partsString get() = command.parts.toList().joinToString("") { Config.Commands.prefix + it }

	private val syntax
		get() = heading("syntax") +
			(partsString + " " + parameters.public.joinToString(" ") { it.toStringDiscord() }).trim()
				.withSingleLineCode()

	private val syntaxDetailed get() = parameters.list(parameters.public)

	private val description
		get() = if (command.description.isBlank()) ""
		else heading("description") + command.description

	private val requestedModeString: String
		get() {
			val name = action.requestedMode.findAnnotation<Command.Mode.Name>()?.value
			return if (name != null) heading("mode", linebreak = false) + name else ""
		}

	private fun help() = (description + "\n\n" + syntax + "\n" + syntaxDetailed + "\n\n" + requestedModeString).trim()

	suspend fun run() = action.run(this)

	fun exit() {
		state = State.INACTIVE
	}

	suspend fun call(): Any? {
		check(parameters.isReady) {
			Config.Commands.missingParameters(
				parameters,
				"There are missing parameters"
			)
		}

		return if (function.isSuspend) function.callSuspendBy(parameters.out())
		else function.callBy(parameters.out())
	}

	init {
		runBlocking {
			parameters.meta()
			parameters.instance(function)
		}
	}

	enum class State {
		ACTIVE,
		INACTIVE
	}

	data class Data(
		val user: User,
		val channel: ChannelClient,
	)

	companion object {
		val commands = Reflections("", MethodAnnotationsScanner()).getMethodsAnnotatedWith(Command::class.java)
			.map { function -> function.kotlinFunction!! }
			.onEach { function ->
				require(function.isValidCommand) { Config.Commands.invalidCommand(function) }
			}

		object Actions {
			val default = Action<Command.Mode.Data>(useCommandMode = true) {
				val result = call()

				val onSuccess = command.onSuccess

				if (onSuccess.isNotBlank()) send(data.channel.channelId) { text = onSuccess }
				else when (result) {
					is Unit                 -> send(data.channel.channelId) { text = "Finished command." }
					is CombinedMessageEmbed -> send(data.channel.channelId, result)
					else                    -> send(data.channel.channelId) {
						text = result.toString()
					}
				}
			}

			val help = Action<Command.Mode.Data>("help", canBeOnlyPart = false) {
				send(data.channel.channelId) {
					text = help()
				}
			}

			val commands = Action<Command.Mode.Data>("commands", canBeOnlyPart = true, usePlaceholderFunction = true) {
				send(data.channel.channelId) {
					var groupingTip = false
					var locationTip = false
					val alternativeChannelIds = mutableListOf<String>()
					var permissionTip = false

					@Suppress("RedundantCompanionReference")
					text = Companion.commands.asSequence()
						.map { it.commandAnnotation!! }
						.filter { currentCommandAnnotation ->
							val currentParts = currentCommandAnnotation.parts.filter { it.isNotBlank() }
							val parts = (parts - action.strings).filter { it.isNotBlank() }

							currentParts.take(parts.size).containsAll(parts) &&
								currentParts.size > parts.size
						}
						.filter { currentCommandAnnotation ->
							runBlocking {
								when {
									!currentCommandAnnotation.channels.fine(data.channel.channelId, data.user.id) -> {
										locationTip = true
										alternativeChannelIds += currentCommandAnnotation.channels.ids
										false
									}
									!data.user.permitted(currentCommandAnnotation.permission)                     -> {
										permissionTip = true
										false
									}
									else                                                                          -> true
								}
							}
						}
						.map { it.parts.toList() }
						.sortedBy { it.first() }
						.groupBy { it[parts.size - action.strings.size] }
						.values
						.flatMap { commands ->
							if (commands.size > 1) {
								groupingTip = true
								val group = commands.first().take(parts.size - action.strings.size + 1) + "..."
								val rootCommand =
									commands.firstOrNull { it.size == parts.size - action.strings.size + 1 }
								if (rootCommand != null) listOf(rootCommand, group)
								else listOf(group)
							} else listOf(commands.first())
						}
						.joinToString("\n") { it.asCommandStringStyled }
						.ifBlank { Config.Commands.noneFound }

					fun tip(content: String) {
						text += "\n\n" + heading("Tip", linebreak = false) + content
					}
					if (groupingTip) tip("Some commands were grouped. To see more detail, just use the beginning and append `-commands`, for example `-help-commands`!")
					if (locationTip) tip("Some commands are not available in this channel. Please run this command in the according channels (found commands of ${alternativeChannelIds.joinToString { it.toChannelMention() }}).")
					if (permissionTip) tip("Some commands are not available to you, because you don't have sufficient permissions. For example, only while participating in a tournament you're allowed to use `-team-members`, for the rest of the time, you won't be able to see this command.")
				}
			}

			val all = listOf(commands, help, default)
		}

		/*suspend fun from(message: Message): Builder<Command.Mode.Data> {
			require(message.content.isCommand) { Config.Commands.notACommand }
			val parts = splitCommand(message.content)
				.first()
				.split(Config.Commands.prefix)
				.filter { it.isNotBlank() }
			val action =
				Actions.all.find { action ->
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

			return Builder<Command.Mode.Data>(
				Data(
					message.author, bot.clientStore.channels[message.channelId]
				), function, action, parts
			)
		}*/

		@Command("")
		fun nothing(): Nothing = error(Config.Commands.notFound)
	}
}