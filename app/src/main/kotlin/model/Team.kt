package model

import Config
import Team
import bot
import com.jessecorbett.diskord.api.model.ChannelType
import com.jessecorbett.diskord.api.model.Overwrite
import com.jessecorbett.diskord.api.model.OverwriteType
import com.jessecorbett.diskord.api.model.Permission.*
import com.jessecorbett.diskord.api.model.Permissions
import com.jessecorbett.diskord.api.rest.CreateChannel
import com.jessecorbett.diskord.api.rest.CreateGuildRole
import com.jessecorbett.diskord.api.rest.GuildPosition
import command.Command
import command.Permission
import command.transactionSuspend
import guild
import model.database.table.OpponentTable
import model.database.table.ParticipantTable
import model.database.table.TeamTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import send
import withMultiLineCode

class Team(
	id: EntityID<Int>,
) : IntEntity(id) {
	var name by TeamTable.name
	var nick by TeamTable.nick
	var tournament by Tournament referencedOn TeamTable.tournament
	var leader by Participant referencedOn TeamTable.leader
	var roleId by TeamTable.roleId
	var textChannelId by TeamTable.textChannelId
	var voiceChannelId by TeamTable.voiceChannelId
	var categoryId by TeamTable.categoryId

	val members by Participant optionalReferrersOn ParticipantTable.team
	val currentMembers get() = members.filter { it.quit == null }
	val opponents by Opponent referrersOn OpponentTable.team

	val currentOpponent get() = opponents.maxByOrNull { opponent -> opponent.level.number }!!
	val disbanded get() = opponents.any { it.disbanded }

	fun toOrganizationTeam() = Team(
		currentMembers.map { it.toOrganizationParticipant() }.toMutableList(), id.value,
	)

	suspend fun substitute(remove: Participant, add: Participant) {
		remove.assignTeam(null)
		add.assignTeam(this)
	}

	suspend fun disband() {
		currentMembers.forEach { it.assignTeam(null) }
		currentOpponent.disbanded = true
	}

	fun displayOrNull(excludeLeader: Boolean = false, showTrusted: Boolean = true) = transaction {
		val leader = leader
		members
			.sortedBy { it.quit }
			.filter { !excludeLeader || it != leader }
			.joinToString("\n") { participant ->
				val player = participant.player
				val prefix = when {
					participant.quit != null    -> "-" // Red
					participant.id == leader.id -> "+" // Green
					else                        -> ">" // Normal
				}
				"$prefix ${player.ign!!.value}" + if (player.trusted && showTrusted) " (Trusted)" else ""
			}
			.withMultiLineCode("diff")
			.ifEmpty { null }
	}

	fun display(excludeLeader: Boolean = false, showTrusted: Boolean = true) =
		displayOrNull(excludeLeader, showTrusted) ?: "No players."

	suspend fun deleteRole() = guild.deleteRole(roleId)
	suspend fun deleteChannels() {
		bot.clientStore.channels[textChannelId].delete()
		bot.clientStore.channels[voiceChannelId].delete()
		bot.clientStore.channels[categoryId].delete()
	}

	companion object : IntEntityClass<model.Team>(TeamTable) {
		fun fromName(name: String, tournament: Tournament) = transaction {
			model.Team.find {
				TeamTable.tournament eq tournament.id and
					(TeamTable.name eq name)
			}.limit(1).firstOrNull()
		}

		suspend fun new(
			tournament: Tournament,
			index: Int,
			leader: Participant,
			members: List<Participant>
		) {
			val name = "T" + (index + 1)
			val role = guild.createRole(
				CreateGuildRole(
					name,
					Permissions(0),
					0xff3d8b,
					mentionable = true,
					displayedSeparately = false
				)
			)
			guild.modifyRolePositions(
				listOf(
					GuildPosition(
						role.id,
						Config.Known.Role.PARTICIPANT.discordRole.position + 1
					)
				)
			)

			guild.addMemberRole(leader.player.discordId, Config.Known.Role.LEADER.id)

			val category = guild.createChannel(
				CreateChannel(
					name,
					ChannelType.GUILD_CATEGORY,
					overwrites = listOf(
						Overwrite(
							Config.Known.Role.EVERYONE.id,
							OverwriteType.ROLE,
							allowed = Permissions.NONE,
							denied = Permissions.ALL
						),
						Overwrite(
							role.id, OverwriteType.ROLE, allowed = Permissions.of(
								VIEW_CHANNEL,
								SEND_MESSAGES,
								SEND_TTS_MESSAGES,
								EMBED_LINKS,
								ATTACH_FILES,
								READ_MESSAGE_HISTORY,
								MENTION_EVERYONE,
								USE_EXTERNAL_EMOJIS,
								ADD_REACTIONS,

								VIEW_CHANNEL,
								CONNECT,
								SPEAK
							), denied = Permissions.NONE
						)
					)
				)
			)
			bot.clientStore.channels[category.id].editPermissions(
				Overwrite(
					Config.Known.Role.EVERYONE.id,
					OverwriteType.ROLE,
					allowed = Permissions.NONE,
					denied = Permissions.ALL
				),
			)
			bot.clientStore.channels[category.id].editPermissions(
				Overwrite(
					role.id, OverwriteType.ROLE, allowed = Permissions.of(
						VIEW_CHANNEL,
						SEND_MESSAGES,
						SEND_TTS_MESSAGES,
						EMBED_LINKS,
						ATTACH_FILES,
						READ_MESSAGE_HISTORY,
						MENTION_EVERYONE,
						USE_EXTERNAL_EMOJIS,
						ADD_REACTIONS,

						VIEW_CHANNEL,
						CONNECT,
						SPEAK
					), denied = Permissions.NONE
				)
			)

			val textChannel = guild.createChannel(
				CreateChannel(
					"text",
					ChannelType.GUILD_TEXT,
					categoryId = category.id
				)
			)
			val voiceChannel = guild.createChannel(
				CreateChannel(
					"voice",
					ChannelType.GUILD_VOICE,
					categoryId = category.id
				)
			)
			/*val textChannel = guild.createChannel(
				CreateChannel(
					name,
					ChannelType.GUILD_TEXT,
					categoryId = Config.Known.Category.TOURNAMENT.id,
					overwrites = listOf(
						Overwrite(
							Config.Known.Role.EVERYONE.id,
							OverwriteType.ROLE,
							allowed = Permissions.NONE,
							denied = Permissions.ALL
						),
						Overwrite(
							role.id, OverwriteType.ROLE, allowed = Permissions.of(
								VIEW_CHANNEL,
								SEND_MESSAGES,
								SEND_TTS_MESSAGES,
								EMBED_LINKS,
								ATTACH_FILES,
								READ_MESSAGE_HISTORY,
								MENTION_EVERYONE,
								USE_EXTERNAL_EMOJIS,
								ADD_REACTIONS
							), denied = Permissions.NONE
						)
					)
				)
			)*/
			/*val voiceChannel = guild.createChannel(
				CreateChannel(
					name,
					ChannelType.GUILD_VOICE,
					categoryId = Config.Known.Category.TOURNAMENT.id,
					overwrites = listOf(
						Overwrite(
							Config.Known.Role.EVERYONE.id,
							OverwriteType.ROLE,
							allowed = Permissions.NONE,
							denied = Permissions.ALL
						),
						Overwrite(
							role.id, OverwriteType.ROLE, allowed = Permissions.of(
								VIEW_CHANNEL,
								CONNECT,
								SPEAK
							) + 0x00000200, denied = Permissions.NONE
						)
					)
				)
			)*/

			val teamRow = new {
				this.tournament = tournament
				this.leader = leader
				this.name = name
				roleId = role.id
				textChannelId = textChannel.id
				voiceChannelId = voiceChannel.id
				categoryId = category.id
			}

			send(textChannel.id) {
				text = Config.Tournaments.customChannelWelcome(teamRow)
			}

			members.forEach { it.assignTeam(teamRow) }

			Opponent.new {
				team = teamRow
				level = Level.getOrCreate(0, tournament)
			}
		}

		@Command(
			"team", "setLeader",
			permission = Permission.LEADER_OR_STAFF,
			onSuccess = "Set team's leader.",
			description = "Set a team's leader."
		)
		fun setLeader(
			participant: Participant,
		) = transactionSuspend {
			require(participant.player.trusted) { "This player is not allowed to be leader of a team. Please use `-trusted-add` first." }
			setLeaderForce(participant)
		}

		@Command(
			"team", "setLeaderForce",
			permission = Permission.STAFF,
			onSuccess = "Set team's leader.",
			description = "Set a team's leader. If the player is not trusted, make them trusted before.",
			warning = "By forcing this action, the participant might not be familiar with the leader reference immediately."
		)
		suspend fun setLeaderForce(
			participant: Participant,
		) = transactionSuspend {
			val team = participant.team
				?: error("The participant is not in a team, of which they could be leader.")
			require(team.leader.id.value != participant.id.value) { "This participant already is this team's leader." }
			val player = participant.player
			if (!player.trusted) player.trust()
			guild.removeMemberRole(team.leader.player.user.id, Config.Known.Role.LEADER.id)
			guild.addMemberRole(participant.player.user.id, Config.Known.Role.LEADER.id)
			team.leader = participant
			Tournament.current.refreshAnnouncement(reason = "${team.name} changed their leader.")
		}

		@Command(
			"team", "mates",
			permission = Permission.PARTICIPANT,
			description = "See your teammates. Your team's leader will be green."
		)
		fun getMates(
			@Command.Meta
			participant: Participant,
		) = transaction {
			participant.team?.display(excludeLeader = true) ?: error("You're not in a team yet.")
		}

		@Command(
			"team", "members",
			description = "See a team's members. The leader will be green, participants who quit the tournament red."
		)
		fun getMembers(
			team: model.Team
		) = team.display()

		@Command(
			"team", "name",
			permission = Permission.PARTICIPANT,
			description = "See your team's name."
		)
		fun getName(
			@Command.Meta
			participant: Participant,
			alternativeParticipant: Participant? = null
		) = transaction {
			(alternativeParticipant ?: participant).team?.name
				?: error(if (alternativeParticipant == null) "You're" else "That participant is" + " not in a team yet.")
		}

		@Command(
			"team", "nick",
			permission = Permission.PARTICIPANT,
			description = "See your team's nick."
		)
		fun getNick(
			@Command.Meta
			participant: Participant,
		) = transaction {
			(participant.team ?: error("You're not in a team yet.")).nick
				?: "Your team doesn't have a nick set. Your leader can change it via `-team-setNick`."
		}

		@Command(
			"team", "setNick",
			permission = Permission.LEADER,
			description = "Set your team's nick.",
			onSuccess = "Set your team's nick."
		)
		fun setNick(
			@Command.Meta
			participant: Participant,
			newNick: String,
		) = transaction {
			requireNotNull(participant.team) { "You're not in a team yet." }
			require(newNick.length < 20) { "Nick must be shorter than 20 characters." }
			participant.team!!.nick = newNick
			Tournament.current.refreshAnnouncement(reason = "${participant.team!!.name} changed their nick.")
		}
	}
}