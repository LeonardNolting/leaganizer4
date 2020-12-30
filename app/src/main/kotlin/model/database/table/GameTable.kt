package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object GameTable : IntIdTable() {
	val state = enumeration("state", matchmaking.Game.State::class)
	val opponent1 = reference("opponent1", OpponentTable, onDelete = ReferenceOption.CASCADE)
	val opponent2 = reference("opponent2", OpponentTable, onDelete = ReferenceOption.CASCADE)
}