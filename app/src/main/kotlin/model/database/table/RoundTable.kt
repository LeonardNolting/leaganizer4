package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.timestamp

object RoundTable : IntIdTable() {
	val game = reference("game", GameTable, onDelete = ReferenceOption.CASCADE)
	val opponent1Goals = integer("opponent1_goals").default(0)
	val opponent2Goals = integer("opponent2_goals").default(0)
	val start = timestamp("start")
}