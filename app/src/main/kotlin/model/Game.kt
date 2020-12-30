package model

import model.database.table.GameTable
import model.database.table.RoundTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Game(
	id: EntityID<Int>,
) : IntEntity(id) {
	var state by GameTable.state
	var opponent1 by Opponent referencedOn GameTable.opponent1
	var opponent2 by Opponent referencedOn GameTable.opponent2

	val rounds by Round referrersOn RoundTable.game

	fun toOrganizationGame() = matchmaking.Game(
		state,
		opponent1.toOrganizationOpponent(),
		opponent2.toOrganizationOpponent()
	)

	companion object : IntEntityClass<Game>(GameTable)
}