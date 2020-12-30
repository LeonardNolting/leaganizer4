package model

import model.database.table.StartPreferredTable
import model.database.table.StartTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Start(
	id: EntityID<Int>,
) : IntEntity(id) {
	var start by StartTable.start
	var tournament by Tournament referencedOn StartTable.tournament
	var timeOption by StartTable.timeOption

	val preferredStarts by StartPreferred referrersOn StartPreferredTable.start

	companion object : IntEntityClass<Start>(StartTable)
}