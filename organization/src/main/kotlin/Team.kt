import teammaking.Participant
import teammaking.Skill
import kotlin.random.Random

data class Team(
	val members: MutableList<Participant>,
//	var level: Int = 0,
	val id: Int = counter++
) : MutableList<Participant> by members, PrettyPrintable {
	val skill: Skill
		get() = members.sumOf(Participant::skill)

	val priorityBonus: Int
		get() = members.sumOf(Participant::priority)

	val relationsBonus: Skill
		get() = members.sumOf { a -> members.sumOf { b -> a.relations.getOrDefault(b.id, 0) } }

	val leaderBonus: Double
		get() = if (members.any(Participant::leader)) 1000.0 else 0.0

	override fun toPrettyString(indentLevel: Int) = """
___$id skill = $skill; priority bonus = $priorityBonus
|${members.joinToString("\n|") { it.toPrettyString(0) }}"""
		.removePrefix("\n").prependIndent("  ".repeat(indentLevel))

	override fun toString() =
		"<${'A' + id}>"

	companion object {
		var counter = 0

		@ExperimentalStdlibApi
		fun generateRandom(
			teamSize: Int = 4,
			skill: IntRange = 70..140,
			priority: IntRange = -20..40,
			leaderChance: Double = 0.25,
			relationCount: IntRange = 0..0,
			relations: IntRange = 0..0,
			possiblyRelatedParticipants: List<Participant> = emptyList(),
			random: Random = Random.Default
		) =
			Team(MutableList(teamSize) {
				Participant.generateRandom(
					skill,
					priority,
					leaderChance,
					relationCount,
					relations,
					possiblyRelatedParticipants,
					random
				)
			})
	}
}