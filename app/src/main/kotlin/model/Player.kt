package model

import Config
import Temporal
import bot
import com.jessecorbett.diskord.api.model.User
import com.jessecorbett.diskord.util.changeNickname
import command.Command
import command.Permission
import command.transactionSuspend
import guild
import kotlinx.coroutines.runBlocking
import model.database.table.PlayerTable
import model.database.table.TournamentTable
import model.type.IGN
import model.type.Nick
import model.type.Skill
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import send
import userNameDiscriminator
import withMultiLineCode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Player(
	id: EntityID<Int>,
) : IntEntity(id) {
	var ign by PlayerTable.ign
	var nick by PlayerTable.nick
	var skill by PlayerTable.skill
	var verified by PlayerTable.verified
	var verifiedAt by PlayerTable.verifiedAt
	var verifiedBy by Player optionalReferencedOn PlayerTable.verifiedBy
	var minecraftId by PlayerTable.minecraftId
	var hypixelId by PlayerTable.hypixelId
	var discordId by PlayerTable.discordId
	var appliedAt by PlayerTable.appliedAt
	var zoneId by PlayerTable.zoneId
	var birthday by PlayerTable.birthday
	var trusted by PlayerTable.trusted
	var ref by PlayerTable.ref
	var valid by PlayerTable.valid

	val verifiedPlayers by Player optionalReferrersOn PlayerTable.verifiedBy
	val createdTournaments by Tournament referrersOn TournamentTable.createdBy

	val user get() = runBlocking { bot.clientStore.discord.getUser(discordId) }

	val alternativeName get() = nick?.value ?: ign?.value
	val name get() = alternativeName ?: user.username

	fun refreshIGN(refreshAnnouncement: Boolean = true) = transactionSuspend {
		val entries = Config.Api.Mojang.UID(minecraftId).call()
		ign = IGN(entries.last().name)
		refreshNick(refreshAnnouncement)
	}

	suspend fun refreshNick(refreshAnnouncement: Boolean = true) =
		user.refreshNick(refreshAnnouncement, alternativeName)

	suspend fun trust() {
		require(this@Player.verified) { "Please verify this player first (`-verify`)." }
		require(!this@Player.trusted) { "Player is already trusted." }
		transaction {
			trusted = true
		}
		guild.addMemberRole(discordId, Config.Known.Role.TRUSTED.id)
		send(Config.Known.Channel.TRUSTED) {
			text = Config.Tournaments.Trusted.onPromote(user)
		}
	}

	suspend fun distrust(reason: String? = null) {
		transaction {
			require(this@Player.trusted) { "This player has not been trusted before already." }
			trusted = false
		}
		guild.removeMemberRole(discordId, Config.Known.Role.TRUSTED.id)
		send(Config.Known.Channel.TRUSTED) {
			text = Config.Tournaments.Trusted.onDemote(user, reason)
		}
	}

	suspend fun ref() {
		transaction {
			require(this@Player.verified) { "Please verify this player first (`-verify`)." }
			require(this@Player.trusted) { "Player needs to be trusted first." }
			require(!this@Player.ref) { "This player was already a ref." }
			ref = true
		}
		guild.addMemberRole(discordId, Config.Known.Role.REF.id)
		send(Config.Known.Channel.REFS) {
			text = Config.Tournaments.Ref.onPromote(user)
		}
	}

	suspend fun unref(reason: String? = null) {
		transaction {
			require(this@Player.ref) { "This player already wasn't a ref." }
			ref = false
		}
		guild.removeMemberRole(discordId, Config.Known.Role.REF.id)
		send(Config.Known.Channel.REFS) {
			text = Config.Tournaments.Ref.onDemote(user, reason)
		}
	}

	companion object : IntEntityClass<Player>(PlayerTable) {
		fun allValid() = all().filter {
			println("${it.name}: ${it.valid}")
			!it.valid
		}

		fun fromDiscordId(discordId: String) = transaction {
			Player.find {
				PlayerTable.discordId eq discordId
			}.limit(1).firstOrNull()
		}

		fun fromUser(user: User) = fromDiscordId(user.id)

		fun fromIgn(ign: IGN) = transaction {
			Player.find {
				PlayerTable.ign eq ign
			}.limit(1).firstOrNull()
		}

		@Command(
			"trusted", "add",
			permission = Permission.STAFF,
			description = "Trust a player. Mainly allows them to be team leader.",
			onSuccess = "This player is now trusted."
		)
		suspend fun trust(
			player: Player
		) = player.trust()

		@Command(
			"trusted", "remove",
			permission = Permission.STAFF,
			description = "Distrust a player. Mainly means they'll not be allowed to be team leader any more.",
			onSuccess = "Player is not trusted any more. They'll not be a leader in future tournaments any more. If you also want to take their leadership in the current tournament, please use `-team-setLeader` or `-team-setLeaderForce`." // TODO
		)
		suspend fun distrust(
			player: Player,
			reason: String? = null
		) = player.distrust(reason)

		@Command(
			"trusted", "list",
			permission = Permission.VERIFIED,
			description = "Get a list of all trusted players."
		)
		fun trustedList() = transaction {
			find { PlayerTable.trusted eq true }.toList().displayFull()
		}

		@Command(
			"ref", "add",
			permission = Permission.STAFF,
			description = "Allow a player to be a ref at games.",
			onSuccess = "Marked player as ref."
		)
		suspend fun ref(
			player: Player
		) = player.ref()

		@Command(
			"ref", "remove",
			permission = Permission.STAFF,
			description = "Disallow a player to be a ref at games.",
			onSuccess = "Player cannot be ref at games any more."
		)
		suspend fun unref(
			player: Player,
			reason: String? = null
		) = player.unref(reason)

		@Command(
			"ref", "list",
			permission = Permission.VERIFIED,
			description = "Get a list of all refs."
		)
		fun refList() = transaction {
			find { PlayerTable.ref eq true }.toList().displayFull()
		}

		@Command(
			"apply",
			description = Config.Commands.Apply.description,
			onSuccess = Config.Commands.Apply.onSuccess
		)
		/**
		 * Apply a user and notify staff.
		 */
		suspend fun apply(
			@Command.Meta
			user: User,
			ign: IGN,
		) = applyRaw(user, ign).also {
			send(Config.Known.Channel.STAFF) {
				text = Config.Commands.Apply.notification(user, ign)
			}
		}

		/**
		 * Apply a user.
		 */
		private suspend fun applyRaw(user: User, ign: IGN): Player {
			val player = fromDiscordId(user.id)

			require(player == null) {
				if (player!!.verified) Config.Commands.Apply.alreadyVerified
				else Config.Commands.Apply.alreadyApplied
			}

			require(fromIgn(ign) == null) { Config.Commands.Apply.ignAlreadyUsed }

			val hypixel = Config.Api.Hypixel(ign).call()

			requireNotNull(hypixel.player) { Config.Api.Hypixel.noPlayer }
			val discordTag = hypixel.player.socialMedia?.links?.discord
				?: error(Config.Api.Hypixel.noDiscord)
			require(discordTag == user.userNameDiscriminator) { Config.Commands.Apply.wrongDiscord }

			val mojang = Config.Api.Mojang.IGN(ign).call()

			return transaction {
				Player.new {
					this.ign = ign
					hypixelId = user.id
					minecraftId = mojang.id
					discordId = user.id
					appliedAt = Instant.now()
				}
			}
		}

		@Command(
			"reject",
			permission = Permission.STAFF,
			description = Config.Commands.Reject.description,
			onSuccess = Config.Commands.Reject.onSuccess,
		)
		suspend fun reject(
			@Command.Meta
			userThatRejects: User,
			userToBeRejected: User,
			reason: String? = null
		) {
			val playerToBeRejected = fromDiscordId(userToBeRejected.id)
				?: error("This player hasn't applied. If you want to apply them as well, use register.")

			transaction {
				playerToBeRejected.delete()
			}

			send(Config.Known.Channel.WELCOME) {
				text = Config.Commands.Reject.onSuccessDm(userToBeRejected, reason)
			}
		}

		@Command(
			"verify",
			permission = Permission.STAFF,
			description = Config.Commands.Verify.description,
			onSuccess = Config.Commands.Verify.onSuccess
		)
		suspend fun verify(
			@Command.Meta
			userThatVerifies: User,
			userToBeVerified: User,
			skill: Skill,
		) {
			val playerThatVerifies = fromDiscordId(userThatVerifies.id)!!
			val playerToBeVerified = fromDiscordId(userToBeVerified.id)
				?: error("This player hasn't applied. If you want to apply them as well, use register.")

			transaction {
				playerToBeVerified.verified = true
				playerToBeVerified.verifiedAt = Instant.now()
				playerToBeVerified.verifiedBy = playerThatVerifies
				playerToBeVerified.skill = skill
			}

			guild.addMemberRole(userToBeVerified.id, Config.Known.Role.VERIFIED.id)

			playerToBeVerified.refreshNick()

			send(Config.Known.Channel.WELCOME) {
				text = Config.Commands.Verify.onSuccessDm(userToBeVerified)
			}
		}

		@Command(
			"register",
			permission = Permission.STAFF,
			description = Config.Commands.Register.description,
			onSuccess = Config.Commands.Register.onSuccess
		)
		suspend fun register(
			@Command.Meta
			userThatRegisters: User,
			userToBeRegistered: User,
			ign: IGN,
			skill: Skill,
		) {
			// Apply
			try {
				applyRaw(userToBeRegistered, ign)
			} catch (e: Exception) {
				error(Config.Commands.Register.applyingFailed(e, userToBeRegistered))
			}

			// Verify
			try {
				verify(userThatRegisters, userToBeRegistered, skill)
			} catch (e: Exception) {
				error(Config.Commands.Register.verifyingFailed(e, userToBeRegistered))
			}
		}

		@Command(
			"verified", "list",
			permission = Permission.VERIFIED,
			description = "Get a list of all players."
		)
		fun verifiedList() = transaction {
			find { PlayerTable.verified eq true }.toList().displayFull()
		}

		@Command(
			"settings", "timezone", "set",
			permission = Permission.VERIFIED,
			description = "Set your timezone.",
			onSuccess = "Timezone was set. All your dates will now be in that timezone!"
		)
		fun setZoneId(
			@Command.Meta
			player: Player,
			zoneId: ZoneId
		) = transaction {
			player.zoneId = zoneId
		}

		@Command(
			"settings", "timezone", "get",
			permission = Permission.VERIFIED,
			description = "Get your timezone.",
		)
		fun getZoneId(
			@Command.Meta
			zoneId: ZoneId
		) = "$zoneId\nYou can change this with `-settings-timezone-set`."

		@Command(
			"settings", "birthday", "set",
			permission = Permission.VERIFIED,
			description = "Set your birthday.",
			onSuccess = "Birthday was set."
		)
		fun setBirthday(
			@Command.Meta
			player: Player,
			birthday: LocalDate
		) = transaction {
			player.birthday = birthday
		}

		@Command(
			"settings", "birthday", "get",
			permission = Permission.VERIFIED,
			description = "See your birthday."
		)
		fun getBirthday(
			@Command.Meta
			player: Player
		) = transaction {
			val birthday = player.birthday ?: error("You've not set your birthday yet.")
			"${Temporal.displayDate(birthday)}\nYou can change this with `-settings-birthday-set`."
		}

		@Command(
			"settings", "nick", "set",
			permission = Permission.TRUSTED,
			description = "Set a player's nick.",
			onSuccess = "Nick was set."
		)
		fun setNick(
			player: Player,
			nick: Nick
		) = transactionSuspend {
			player.nick = nick
			player.refreshNick()
		}

		@Command(
			"settings", "nick", "get",
			permission = Permission.TRUSTED,
			description = "Get a player's nick.",
		)
		fun getNick(
			player: Player,
		) =
			"${player.nick?.value ?: "This player has no explicit nickname set."}\nYou can change this with `-settings-nick-set`."
	}
}

fun List<Player>.display(withCode: Boolean = true): String {
	val string = joinToString("\n") { "> ${it.name}" }
	return if (withCode) string.withMultiLineCode() else string
}

fun List<Player>.displayFull(withCode: Boolean = true) = "Sum: " + size.toString() + display(withCode)

suspend fun User.refreshNick(refreshAnnouncement: Boolean = true, alternative: String? = null) {
	val nick = alternative ?: username
	try {
		guild.changeNickname(id, Config.johnsonify(nick))
	} catch (e: Exception) {
		println("Couldn't change nickname of $nick.")
	}

	if (refreshAnnouncement) Tournament.currentOrNull?.refreshAnnouncement(reason = "$nick renamed.")
}