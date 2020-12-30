package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object OpponentTable : IntIdTable() {
	val level = reference("level", LevelTable, onDelete = ReferenceOption.CASCADE)
	val team = reference("team", TeamTable, onDelete = ReferenceOption.CASCADE)
	val disbanded = bool("disbanded").default(false)
}