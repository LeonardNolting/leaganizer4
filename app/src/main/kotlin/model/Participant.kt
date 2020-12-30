package model

import Config
import com.jessecorbett.diskord.api.model.User
import guild
import model.database.table.ParticipantTable
import model.database.table.PlayerTable
import model.database.table.StartPreferredTable
import model.database.table.TeamTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class Participant(
	id: EntityID<Int>,
) : IntEntity(id) {
	var tournament by Tournament referencedOn ParticipantTable.tournament
	var player by Player referencedOn ParticipantTable.player
	var team by Team optionalReferencedOn ParticipantTable.team
	var goals by ParticipantTable.goals
	var assists by ParticipantTable.assists
	var cleanSheets by ParticipantTable.cleanSheets
	var quit by ParticipantTable.quit
	var joined by ParticipantTable.joined

	val preferredStarts by StartPreferred referrersOn StartPreferredTable.participant
	val leadTeams by Team referrersOn TeamTable.leader
	val leadTeam get() = leadTeams.firstOrNull()
	val isLeader get() = leadTeam != null

	fun toOrganizationParticipant() = teammaking.Participant(
		id.value,
		player.skill!!.value,
		leader = player.trusted
	)

	fun resetStatistics() {
		goals = 0
		assists = 0
		cleanSheets = 0
	}

	suspend fun removeDynamicTournamentRoles() {
		if (team != null) guild.removeMemberRole(player.discordId, team!!.roleId)
	}

	suspend fun removeStaticTournamentRoles() {
		guild.removeMemberRole(player.discordId, Config.Known.Role.PARTICIPANT.id)
		if (isLeader) guild.removeMemberRole(player.discordId, Config.Known.Role.LEADER.id)
	}

	suspend fun removeTournamentRoles() {
		removeStaticTournamentRoles()
		removeDynamicTournamentRoles()
	}

	suspend fun assignTeam(newTeam: Team?) {
		team = newTeam
		if (newTeam == null && team != null) guild.removeMemberRole(player.discordId, team!!.roleId)
		if (newTeam != null) guild.addMemberRole(player.discordId, newTeam.roleId)
	}

	companion object : IntEntityClass<Participant>(ParticipantTable) {
		fun fromPlayerTournament(player: Player, tournament: Tournament = Tournament.current) = transaction {
			Participant.find {
				(ParticipantTable.player eq player.id) and (ParticipantTable.tournament eq tournament.id)
			}
		}

		fun fromUserTournament(user: User, tournament: Tournament = Tournament.current) = transaction {
			Participant.find {
				(ParticipantTable.tournament eq tournament.id) and
					(ParticipantTable.player inSubQuery (PlayerTable.slice(PlayerTable.id).select {
						(PlayerTable.discordId eq user.id) and (ParticipantTable.tournament eq tournament.id)
					}))
			}
		}

		fun fromPlayerTournamentActive(player: Player, tournament: Tournament = Tournament.current) = transaction {
			fromPlayerTournament(player, tournament).find { it.quit == null }
		}

		fun fromUserTournamentActive(user: User, tournament: Tournament = Tournament.current) = transaction {
			fromUserTournament(user, tournament).find { it.quit == null }
		}
	}
}