package model

import model.database.table.LevelTable
import model.database.table.OpponentTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import java.time.Instant

class Level(
	id: EntityID<Int>,
) : IntEntity(id) {

	var tournament by Tournament referencedOn LevelTable.tournament
	var start by LevelTable.start
	var number by LevelTable.number

	val opponents by Opponent referrersOn OpponentTable.level

	companion object : IntEntityClass<Level>(LevelTable) {
		fun getOrCreate(number: Int, tournament: Tournament) =
			find {
				LevelTable.tournament eq tournament.id and
					(LevelTable.number eq number)
			}.limit(1).firstOrNull()
				?: new {
					this.tournament = tournament
					this.number = number
					start = Instant.now()
				}
	}
}