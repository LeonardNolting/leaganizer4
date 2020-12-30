package matchmaking

import PrettyPrintable
import Team

data class Opponent(val team: Team, override val level: Int, val isPushed: Boolean = false) : Leveled, PrettyPrintable {
	override fun toString() =
		"<${'A' + team.id}$level>"

	override fun toPrettyString(indentLevel: Int) = """
__$level${team.id} skill = ${team.skill}; priority bonus = ${team.priorityBonus}
|${team.members.joinToString("\n|") { it.toPrettyString(0) }}"""
		.removePrefix("\n").prependIndent("  ".repeat(indentLevel))
}