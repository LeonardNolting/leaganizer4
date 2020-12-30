package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.timestamp

object ParticipantTable : IntIdTable() {
	val tournament = reference("tournament", TournamentTable)
	val player = reference("player", PlayerTable)
	val quit = timestamp("quit").nullable()

	val joined = timestamp("joined")
	val team = reference("team", TeamTable, onDelete = ReferenceOption.SET_NULL).nullable()

	val goals = integer("goals").default(0)
	val assists = integer("assists").default(0)
	val cleanSheets = integer("clean_sheets").default(0)

//    override val primaryKey = PrimaryKey(tournament, player, quit, name = "participant_pkey")
}