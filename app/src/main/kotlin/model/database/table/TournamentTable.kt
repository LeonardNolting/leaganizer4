package model.database.table

import model.Tournament
import model.database.PGEnum
import model.type.teamSize
import model.type.timeInterval
import model.type.timeOptions
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.timestamp

object TournamentTable : IntIdTable() {
	val teamSize = teamSize("team_size")
	val announcementId = text("announcement_id").nullable()
	val createdBy = reference("created_by", PlayerTable)
	val createdAt = timestamp("created_at")
	val state = customEnumeration("state", "tournament_state", { value ->
		Tournament.State.valueOf(value as String)
	}, { PGEnum("tournament_state", it) })

	//    val state = enumeration("state", Tournament.State::class)
	val timeOptions = timeOptions("time_options")
	val timeInterval = timeInterval("time_interval")
	val open = bool("open").default(false)
	val currentLevel = reference("current_level", LevelTable, onDelete = ReferenceOption.SET_NULL).nullable()
}