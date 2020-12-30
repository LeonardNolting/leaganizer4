import com.jessecorbett.diskord.api.model.Message
import com.jessecorbett.diskord.api.rest.client.GuildClient
import com.jessecorbett.diskord.dsl.Bot
import com.jessecorbett.diskord.dsl.bot
import com.jessecorbett.diskord.util.isFromUser
import command.*
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.Database

internal val env = dotenv {
	ignoreIfMissing = true // Heroku
}

internal lateinit var bot: Bot
internal lateinit var guild: GuildClient
internal lateinit var client: HttpClient
internal lateinit var database: Database

val modes = mutableListOf<Command.Mode<out Command.Mode.Data>>()
private val Message.mode
	get() = modes.find { mode ->
		val builder = mode.builder
		builder.data.user == author &&
			builder.state == Builder.State.ACTIVE &&
			builder.data.channel.channelId == channelId
	}

@KtorExperimentalAPI
suspend fun main() {
	client = HttpClient(CIO) {
		install(JsonFeature) {
			serializer = GsonSerializer()
		}
	}

	database = Database.connect()

	bot(env["BOT_TOKEN"]!!) {
		bot = this
		guild = clientStore.guilds[Config.Known.Guild.MAIN.id]

		messageCreated { message ->
			if (!message.isFromUser) return@messageCreated

			var mode = message.mode

			if (message.content.isCommand) {
				if (mode != null) {
					mode.builder.state = Builder.State.INACTIVE
					message.reply(Config.Commands.stopped)
				}

				mode = try {
					Command.Mode.from(message)
				} catch (e: Exception) {
					message.reply(Config.Commands.creatingFailed(e))
					throw e
				}

				if (mode != null) modes += mode
			}

			mode?.message(message)
		}

		/*roleCreated { event ->
			val role = event.role
			val knownRole = Config.Known.Role.values().find { it.id == role.id }
			//! TEST IF BY USER OR BOT
			if (knownRole != null && knownRole.managedByBot) send(Config.Known.Channel.STAFF) {
				description = "A role that's managed by the bot was added manually. Please undo this, as this will not be saved. For reference, use `-reference-roles`."
				color = Config.Color.RED.main
			}
		}*/
	}
}
