package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp

object LevelTable : IntIdTable() {
	val tournament = reference("tournament", TournamentTable)
	val start = timestamp("start").nullable()
	val number = integer("number")
}