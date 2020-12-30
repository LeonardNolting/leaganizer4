package command

import Temporal
import bot
import com.jessecorbett.diskord.api.model.User
import com.jessecorbett.diskord.api.rest.client.ChannelClient
import model.Participant
import model.Player
import model.Team
import model.Tournament
import model.type.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.withNullability
import kotlin.reflect.typeOf
import Config as GlobalConfig
import Config.Commands.Type.Config as GlobalTypeConfig

typealias TypeGenerator<T, C> = (KType) -> Type<T, C>

data class Type<T, C : Type.Config>(
	val kType: KType,
	val config: C,
	private val meta: (suspend C.(Builder.Data) -> T)? = null,
	private val parse: (suspend C.(Builder.Data, String) -> T)? = null,
) {
	suspend fun meta(data: Builder.Data) =
		(meta ?: error(GlobalConfig.Commands.Type.noMetaMethod(this))).invoke(config, data)

	suspend fun parse(data: Builder.Data, string: String) =
		(parse ?: error(GlobalConfig.Commands.Type.noParseMethod(this))).invoke(config, data, string)

	companion object {
		@Suppress("RemoveExplicitTypeArguments")
		private val staticTypes = listOf(
			Type<ChannelClient, GlobalTypeConfig.Channel>(meta = { data ->
				bot.clientStore.channels[data.channel.channelId]
			}) { _, parameter ->
				val match = Regex("^<#(\\d+)>$").find(parameter) ?: error()
				return@Type (bot.clientStore.channels[match.groupValues[1]])
			},
			Type<User, GlobalTypeConfig.User>(meta = { data -> data.user }) { _, parameter ->
				val match = Regex("^<@!?(\\d+)>$").find(parameter) ?: error()
				bot.clientStore.discord.getUser(match.groupValues[1])
			},
			Type<IGN, GlobalTypeConfig.IGN> { _, parameter ->
				IGN(parameter)
			},
			Type<Nick, GlobalTypeConfig.Nick> { _, parameter ->
				Nick(parameter)
			},
			Type<TeamSize, GlobalTypeConfig.TeamSize> { _, parameter ->
				TeamSize(parameter.toInt())
			},
			Type<Team?, GlobalTypeConfig.Team>(meta = { data ->
				transactionSuspend {
					get<Participant>().meta(data).team
				}
			}) { _, parameter ->
				Team.fromName(parameter, Tournament.current)
			},
			Type<Team, GlobalTypeConfig.Team>(meta = { data ->
				get<Team?>().meta(data) ?: error(notFound)
			}) { data, parameter ->
				get<Team?>().parse(data, parameter) ?: error(notFound)
			},
			/*Type<Opponent, GlobalTypeConfig.Team>(meta = { data ->
				get<Team>().meta(data).currentOpponent
			}) { data, parameter ->
				get<Team>().parse(data, parameter).currentOpponent
			},*/
			Type<Player?, GlobalTypeConfig.Player>(meta = { data ->
				Player.fromUser(get<User>().meta(data))!!
			}) { data, parameter ->
				try {
					Player.fromUser(get<User>().parse(data, parameter))
				} catch (e: Exception) {
					try {
						Player.fromIgn(get<IGN>().parse(data, parameter))
					} catch (e: Exception) {
						error()
					}
				}
			},
			Type<Player, GlobalTypeConfig.Player>(meta = { data ->
				get<Player?>().meta(data) ?: error(notFound)
			}) { data, parameter ->
				get<Player?>().parse(data, parameter) ?: error(notFound)
			},
			Type<Participant?, GlobalTypeConfig.Participant>(meta = { data ->
				Participant.fromUserTournamentActive(get<User>().meta(data))
			}) { data, parameter ->
				try {
					Participant.fromPlayerTournamentActive(get<Player>().parse(data, parameter))
				} catch (e: Exception) {
					error()
				}
			},
			Type<Participant, GlobalTypeConfig.Participant>(meta = { data ->
				get<Participant?>().meta(data) ?: error(notFound)
			}) { data, parameter ->
				get<Participant?>().parse(data, parameter) ?: error(notFound)
			},
			Type<Skill, GlobalTypeConfig.Skill> { _, parameter ->
				Skill(parameter.toInt())
			},
			Type<TimeOptions, GlobalTypeConfig.TimeOptions> { _, parameter ->
				TimeOptions(parameter.toInt())
			},
			Type<TimeInterval, GlobalTypeConfig.TimeInterval> { _, parameter ->
				TimeInterval(parameter.toInt())
			},
			Type<LocalDateTime, GlobalTypeConfig.LocalDateTime> { _, parameter ->
				Temporal.parseDateTime(parameter)
			},
			Type<LocalDate, GlobalTypeConfig.LocalDate> { _, parameter ->
				Temporal.parseDate(parameter)
			},
			Type<ZonedDateTime, GlobalTypeConfig.ZonedDateTime> { data, parameter ->
				Temporal.parseDateTimeZoned(parameter, Player.fromUser(data.user)?.zoneId)
			},
			Type<ZoneId?, GlobalTypeConfig.ZoneId>(meta = { data ->
				get<Player?>().meta(data)?.zoneId
			}) { _, parameter ->
				try {
					ZoneId.of(parameter)
				} catch (e: Exception) {
					null
				}
			},
			Type<ZoneId, GlobalTypeConfig.ZoneId>(meta = { data ->
				get<ZoneId?>().meta(data)
					?: error("Timezone was not found. Please set your timezone via `-settings-timezone-set`.")
			}) { _, parameter ->
				ZoneId.of(parameter)
			},
			Type<Boolean, GlobalTypeConfig.Boolean> { _, parameter ->
				when (parameter) {
					"true", "yes", "y" -> true
					"false", "no", "n" -> false
					else               -> error()
				}
			},
			Type<Int, GlobalTypeConfig.Int> { _, parameter -> parameter.toInt() },
			Type<Float, GlobalTypeConfig.Float> { _, parameter -> parameter.toFloat() },
			Type<String, GlobalTypeConfig.String> { _, parameter -> parameter },
		)

		@Suppress("RemoveExplicitTypeArguments")
		private val dynamicTypes = listOf(
			TypeGenerator<List<Any?>, GlobalTypeConfig.ListGenerator>(),
			TypeGenerator<Map<String, Any?>, GlobalTypeConfig.MapGenerator>()
		)

		private fun find(actualKType: KType, types: List<Type<*, *>>) = types.find { type ->
			if (type.kType.isMarkedNullable && !actualKType.isMarkedNullable) false
			else actualKType.withNullability(false).isSubtypeOf(type.kType)
		}

		@Suppress("UNCHECKED_CAST") // kType == T
		fun <T> get(kType: KType): Type<out T, *> = (find(kType, staticTypes)
			?: find(kType, dynamicTypes.map { it(kType) })) as Type<out T, *>?
			?: error(GlobalConfig.Commands.Type.noTypeObject(kType))

		inline fun <reified T> get() = get<T>(typeOf<T>())

		@JvmName("getWithoutGeneric")
		fun get(kType: KType) = get<Any>(kType)

		@Suppress("FunctionName")
		private inline fun <reified T, reified C : Config> Type(
			noinline meta: (suspend C.(Builder.Data) -> T)? = null,
			noinline parse: (suspend C.(Builder.Data, String) -> T)? = null,
		) = Type(typeOf<T>(), C::class.objectInstance!!, meta, parse)

		@Suppress("FunctionName")
		private inline fun <reified T, reified C : Config.Generator> TypeGenerator(
			crossinline generate: Config.(KType, KType) -> Type<T, Config> =
				{ _, kType -> Type(kType, this) },
		): TypeGenerator<T, Config> = { actualKType ->
			generate(C::class.objectInstance!!.generator(actualKType), actualKType, typeOf<T>())
		}
	}

	open class Config(
		val name: String,
		val description: String = "",
		val examples: List<String> = listOf(),
	) {
		open val error = GlobalConfig.Commands.Type.unableToParse(name)
		fun error(message: String = error): Nothing = kotlin.error(message)

		abstract class Generator(val generator: (KType) -> Config)
	}
}