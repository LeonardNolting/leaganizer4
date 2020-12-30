package matchmaking

import PrettyPrintable

data class Game(
	val state: State,
	val team1: Opponent,
	val team2: Opponent,
) : PrettyPrintable, Leveled {
	init {
		require(team1.level == team2.level)
	}

	override val level: Int
		get() = team1.level

	override fun toString(): String {
		return "$team1 vs $team2"
	}

	override fun toPrettyString(indentLevel: Int) =
		"""
Game on level $level:
${team1.team.toPrettyString(indentLevel + 1)}

  vs

${team2.team.toPrettyString(indentLevel + 1)}
""".removePrefix("\n").prependIndent("  ".repeat(indentLevel))

	enum class State {
		PLANNED,
		READY,
		ACTIVE,
		FINISHED
	}
}