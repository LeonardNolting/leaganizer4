package model

import model.database.table.RoundTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID


class Round(
	id: EntityID<Int>,
) : IntEntity(id) {
	var game by Game referencedOn RoundTable.game
	var opponent1Goals by RoundTable.opponent1Goals
	var opponent2Goals by RoundTable.opponent2Goals
	var start by RoundTable.start

	companion object : IntEntityClass<Round>(RoundTable)
}