package model

import model.database.table.StartPreferredTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class StartPreferred(
	id: EntityID<Int>,
) : IntEntity(id) {
	var start by Start referencedOn StartPreferredTable.start
	var participant by Participant referencedOn StartPreferredTable.participant
	var timeOption by StartPreferredTable.timeOption

	companion object : IntEntityClass<StartPreferred>(StartPreferredTable)
}