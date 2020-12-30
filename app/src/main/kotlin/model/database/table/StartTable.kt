package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp

object StartTable : IntIdTable() {
	val start = timestamp("start")
	val tournament = reference("tournament", TournamentTable)
	val timeOption = integer("time_option").nullable()
}