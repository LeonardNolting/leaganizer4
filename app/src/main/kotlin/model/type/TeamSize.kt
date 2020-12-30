package model.type

import model.database.IntegerWrapperColumnType
import model.database.WrapperColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

// https://youtrack.jetbrains.com/issue/KT-34024
data class TeamSize(override val value: Int) : WrapperColumnType.Wrapper {
	init {
		require(value > 0) { "Team size must be greater than 0." }
		require(value <= 5) { "Team size must be smaller than or equal 5." }
	}
}

class TeamSizeColumnType : IntegerWrapperColumnType<TeamSize>(TeamSize::class)

fun Table.teamSize(name: String): Column<TeamSize> = registerColumn(name, TeamSizeColumnType())