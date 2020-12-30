package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object StartPreferredTable : IntIdTable("start_preferred") {
	val start = reference("start", StartTable, onDelete = ReferenceOption.CASCADE)
	val participant = reference("participant", ParticipantTable, onDelete = ReferenceOption.CASCADE)
	val timeOption = integer("time_option").nullable()
}