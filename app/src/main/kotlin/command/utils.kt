package command

import Config
import com.jessecorbett.diskord.api.rest.client.ChannelClient
import com.jessecorbett.diskord.api.rest.client.GuildClient
import com.jessecorbett.diskord.util.withItalics
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.javaMethod

val String.isCommand: Boolean
	get() = startsWith(Config.Commands.prefix)

fun parameters(string: String) = splitCommand(string).last().trim()

val KType.typeObject get() = Type.get(this)

val KParameter.isMeta get() = hasAnnotation<Command.Meta>()

val KParameter.aliasAnnotation get() = findAnnotation<Command.Alias>()

val KParameter.displayName get() = aliasAnnotation?.name ?: name
val KParameter.validNames: List<String?>
	get() {
		val alias = aliasAnnotation
		return if (alias == null) listOf(name) else listOf(alias.name, name) + alias.also
	}

val KFunction<*>.commandAnnotation get() = findAnnotation<Command>()

val KFunction<*>.isValidCommand: Boolean
	get() = commandAnnotation != null &&
		(instanceParameter == null || // Top level functions
			try {
				objectInstance != null // If null, there is no objectInstance, thus the function is inside a class
			} catch (e: Exception) {
				false // Top level functions (which get handled earlier, so this shouldn't happen)
			})

val KFunction<*>.objectInstance: Any?
	get() = javaMethod!!.declaringClass.kotlin.objectInstance

val List<String>.asCommandString get() = joinToString("") { Config.Commands.prefix + it }
val List<String>.asCommandStringStyled get() = if (last() == "...") asCommandString.withItalics() else asCommandString
val KFunction<*>.partsString get() = commandAnnotation?.parts?.toList()?.asCommandString

suspend fun GuildClient.getRoleOrNull(id: String) = getRoles().find { role -> role.id == id }
suspend fun GuildClient.getRole(id: String) = getRoles().find { role -> role.id == id }!!

fun splitCommand(string: String) =
	if (string.isCommand) ("$string ").split(' ', limit = 2)
	else listOf(string)

fun String.toChannelMention() = "<#$this>"

suspend fun ChannelClient.messageExists(id: String) = try {
	getMessage(id)
	true
} catch (e: Exception) {
	false
}

fun <T> transactionSuspend(db: Database? = null, statement: suspend Transaction.() -> T): T =
	transaction(db) {
		runBlocking {
			statement()
		}
	}