package model

import model.database.table.GameTable
import model.database.table.OpponentTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction

class Opponent(
	id: EntityID<Int>,
) : IntEntity(id) {
	var level by Level referencedOn OpponentTable.level
	var team by Team referencedOn OpponentTable.team
	var disbanded by OpponentTable.disbanded

	private val gamesAsOpponent1 by Game referrersOn GameTable.opponent1
	private val gamesAsOpponent2 by Game referrersOn GameTable.opponent2
	val games get() = transaction { gamesAsOpponent1 + gamesAsOpponent2 }

	val tournament get() = level.tournament

	fun toOrganizationOpponent() = matchmaking.Opponent(
		team.toOrganizationTeam(), level.number
	)

	companion object : IntEntityClass<Opponent>(OpponentTable)
}