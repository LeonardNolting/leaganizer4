package teammaking

import PrettyPrintable
import Team
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.random.nextInt

typealias Participants = List<Participant>
typealias TeamSize = Int
typealias Iterations = Int

data class Teams(
	val teams: List<Team>,
	val substitutes: MutableList<Participant>,
	val relationsFactor: RelationsFactor = 1.0,
) : PrettyPrintable {
	private operator fun get(index: Int): Participant {
		var i = 0
		teams.forEach { team ->
			if (index < (i + team.size))
				return team[index - i]
			i += team.size
		}
		return substitutes[index - i]
	}

	private operator fun set(index: Int, value: Participant) {
		var i = 0
		teams.forEach { team ->
			if (index < (i + team.size)) {
				team[index - i] = value
				return
			}
			i += team.size
		}
		substitutes[index - i] = value
	}

	private val totalSkill
		get() = teams.sumOf(Team::skill) + relationsFactor * teams.sumOf(Team::relationsBonus)

	private val averageSkill
		get() = totalSkill / teams.size

	private val totalSkillDeviation
		get() = teams.sumOf { abs(it.skill - averageSkill) }

	private val averageSkillDeviation
		get() = totalSkillDeviation / teams.size

	private val totalPriorityBonus
		get() = teams.sumOf(Team::priorityBonus)

	private val averagePriorityBonus
		get() = totalPriorityBonus.toDouble() / teams.size

	private val totalLeaderBonus
		get() = teams.sumOf(Team::leaderBonus)

	private fun computeCost(): Double {
		return averageSkillDeviation - averagePriorityBonus - totalLeaderBonus
	}

	companion object {
		/**
		 * @return pair of teams and substitutes
		 */
		fun distribute(
			participants: Participants,
			teamSize: TeamSize,
			iterations: Iterations = 2,
			relationsFactor: RelationsFactor = 1.0,
		): Pair<List<Team>, List<Participant>> {
			require(iterations > 0) { "There's no guarantee that there'll be at least one leader per team." }
			require(teamSize > 0) { "Team size must be greater than 0." }

			val chunked = participants.chunked(teamSize)
			val teams = Teams(
				chunked.dropLast(1).map { Team(it.toMutableList()) },
				chunked.last().toMutableList(),
				relationsFactor
			)

			repeat(iterations) { teams.optimize() }

			require(teams.teams.all { team -> team.members.any { it.leader } }) { "There are teams without a leader." }

			return teams.teams to teams.substitutes
		}

		fun substitute(
			teams: List<Team>,
			substitutes: List<Participant>,
			droppedParticipant: Participant,
			relationsFactor: RelationsFactor = 1.0,
		): Participant {
			val team = teams.find { droppedParticipant in it.members }!!
			return substitutes.minByOrNull { substitute ->
				val team1 =
					Team(team.members.map { if (it == droppedParticipant) substitute else it }.toMutableList(), team.id)
				val teams1 = Teams(
					teams.map { if (it == team) team1 else it },
					(substitutes - substitute).toMutableList(),
					relationsFactor
				)
				teams1.computeCost()
			} ?: throw IllegalArgumentException("Can't substitute without substitutes.")
		}

		@ExperimentalStdlibApi
		fun generateRandom(
			playerCount: IntRange = 40..60,
			minSubstituteCount: IntRange = 0..3,
			teamSize: Int = 4,
			skill: IntRange = 70..140,
			priority: IntRange = -20..40,
			leaderChance: Double = 0.25,
			relationCount: IntRange = 0..0,
			relations: IntRange = 0..0,
			possiblyRelatedParticipants: List<Participant> = emptyList(),
			random: Random = Random.Default
		): Teams {
			val actualPlayerCount = random.nextInt(playerCount)
			val teamCount = actualPlayerCount / teamSize
			val substituteCount = max(random.nextInt(minSubstituteCount), actualPlayerCount % teamSize)
			val teams = List(teamCount) {
				Team.generateRandom(
					teamSize,
					skill,
					priority,
					leaderChance,
					relationCount,
					relations,
					possiblyRelatedParticipants,
					random
				)
			}
			val substitutes = MutableList(substituteCount) {
				Participant.generateRandom(
					skill,
					priority,
					leaderChance,
					relationCount,
					relations,
					possiblyRelatedParticipants,
					random
				)
			}
			return Teams(teams, substitutes)
		}
	}

	internal fun optimize() {
		var i = -1
		val playerCountWithAside = teams.sumBy { it.size } + substitutes.size
		while (++i < playerCountWithAside) {
			var j = -1
			while (++j < playerCountWithAside) {
				val clone = clone()
				val atI = this[i]
				val atJ = this[j]
				clone[i] = atJ
				clone[j] = atI
				if (clone.computeCost() < this.computeCost()) {
					this[i] = atJ
					this[j] = atI
				}
			}
		}
	}

	private fun clone(): Teams =
		Teams(teams.map { Team(it.members.toMutableList()) }, substitutes.toMutableList())

	override fun toPrettyString(indentLevel: Int) =
		"""
----- TEAMS -----
           player count = ${teams.sumOf { it.size } + substitutes.size}
              team size = ${teams.map { it.size }.average()}
             team count = ${teams.size}

            total skill = $totalSkill
          average skill = $averageSkill

  total skill deviation = $totalSkillDeviation
average skill deviation = $averageSkillDeviation

   total priority bonus = $totalPriorityBonus
 average priority bonus = $averagePriorityBonus

                   COST = ${computeCost()}


${teams.joinToString("\n\n") { it.toPrettyString(indentLevel + 1) }}


substitutes: 
${substitutes.joinToString("\n") { it.toPrettyString(indentLevel + 1) }}"""
			.removePrefix("\n").prependIndent("  ".repeat(indentLevel))
}