package teammaking

import PrettyPrintable
import kotlin.random.Random

typealias ParticipantId = Int
typealias RelationValue = Int
typealias RelationsFactor = Double
typealias Relations = Map<ParticipantId, RelationValue>
typealias TeamId = Int
typealias Skill = Int
typealias Priority = Int

data class Participant(
	val id: ParticipantId = counter++,
//	val teamId: TeamId? = null,
	val skill: Skill,
	val priority: Priority = 0,
	val leader: Boolean = false,
	val relations: Relations = emptyMap()  // -10..+10
) : PrettyPrintable {
	companion object {
		var counter = 0

		@ExperimentalStdlibApi
		fun generateRandom(
			skill: IntRange = 70..140,
			priority: IntRange = -20..40,
			leaderChance: Double = 0.25,
			relationCount: IntRange = 0..0,
			relations: IntRange = 0..0,
			possiblyRelatedParticipants: List<Participant> = emptyList(),
			random: Random = Random.Default
		): Participant {
			return Participant(
				id = counter++,
				random.nextInt(skill),
				random.nextInt(priority),
				random.nextDouble() < leaderChance,
				buildMap {
					for (participant in possiblyRelatedParticipants.shuffled(random)
						.take(random.nextInt(relationCount))) {
						put(participant.id, random.nextInt(relations))
					}
				}
			)
		}
	}

	override fun toPrettyString(indentLevel: Int) =
		"$id \twith skill $skill \tand priority $priority${if (leader) "  \t[LEADER]" else ""}"
			.prependIndent("  ".repeat(indentLevel))
}

private fun Random.nextInt(range: IntRange): Int {
	return nextInt(range.first, range.last + 1)
}
